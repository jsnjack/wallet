package io.github.jsnjack.wallet.drive

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import io.github.jsnjack.wallet.LOG_TAG
import io.github.jsnjack.wallet.core.CardJson
import io.github.jsnjack.wallet.core.SyncRules
import io.github.jsnjack.wallet.store.CardStore
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Why the last sync did not complete. */
enum class SyncProblem { NEEDS_SIGN_IN, FAILED }

/** What Settings and the grid show about sync. */
data class SyncStatus(
    val enabled: Boolean,
    val running: Boolean,
    val lastSyncAt: Long,
    val problem: SyncProblem?,
    val account: String?,
    val useMobileData: Boolean,
)

/**
 * Keeps the card store and the Drive app folder in step. Runs happen in
 * [SyncWorker] (after changes, at app start and every few hours) or directly
 * from the UI right after the user grants access. [onRemoteChange] runs when
 * a sync brought in changed cards.
 */
class SyncController(
    private val context: Context,
    private val store: CardStore,
    private val onRemoteChange: () -> Unit,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val runLock = Mutex()
    private val _status = MutableStateFlow(readStatus(running = false))

    /** Current sync state. */
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** True once the user turned sync on. */
    val enabled: Boolean get() = prefs.getBoolean(KEY_ENABLED, false)

    /** True once the first-run screen was answered. */
    var firstRunDone: Boolean
        get() = prefs.getBoolean(KEY_FIRST_RUN_DONE, false)
        set(value) = prefs.edit { putBoolean(KEY_FIRST_RUN_DONE, value) }

    /** Turns sync on with a fresh [token], runs the first sync and schedules the rest. */
    suspend fun enable(token: String) {
        val email = withContext(Dispatchers.IO) {
            try {
                DriveClient(token).accountEmail()
            } catch (e: IOException) {
                Log.w(LOG_TAG, "could not read the Drive account address: ${e.message}")
                null
            }
        }
        prefs.edit {
            putBoolean(KEY_ENABLED, true)
            putString(KEY_ACCOUNT, email)
        }
        publish()
        syncWithToken(token)
        schedulePeriodic()
    }

    /** Turns sync off; cards stay on the phone and in Drive. */
    fun disable() {
        val work = WorkManager.getInstance(context)
        work.cancelUniqueWork(WORK_NOW)
        work.cancelUniqueWork(WORK_PERIODIC)
        prefs.edit {
            putBoolean(KEY_ENABLED, false)
            remove(KEY_ACCOUNT)
            remove(KEY_PROBLEM)
        }
        publish()
    }

    /** Allows or forbids syncing over mobile data. */
    fun setUseMobileData(allowed: Boolean) {
        prefs.edit { putBoolean(KEY_MOBILE_DATA, allowed) }
        publish()
        if (enabled) schedulePeriodic()
    }

    /** Schedules a sync a few seconds after the user changed a card. */
    fun onLocalChange() {
        if (enabled) enqueue(CHANGE_DELAY_SECONDS)
    }

    /** Schedules a sync as soon as the network allows. */
    fun requestNow() {
        if (enabled) enqueue(0)
    }

    /** Records that Google needs the user to approve access again. */
    fun markNeedsSignIn() {
        prefs.edit { putString(KEY_PROBLEM, SyncProblem.NEEDS_SIGN_IN.name) }
        publish()
    }

    /**
     * One sync run: download newer remote cards, then upload newer local ones.
     * Throws [IOException] when Drive cannot be reached; the problem is recorded.
     */
    suspend fun syncWithToken(token: String): Unit = runLock.withLock {
        _status.value = _status.value.copy(running = true)
        try {
            store.load()
            val (downloaded, uploaded) = withContext(Dispatchers.IO) { transfer(DriveClient(token)) }
            Log.i(LOG_TAG, "sync done: $downloaded downloaded, $uploaded uploaded")
            if (downloaded > 0) onRemoteChange()
            prefs.edit {
                putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                remove(KEY_PROBLEM)
            }
        } catch (e: IOException) {
            Log.w(LOG_TAG, "sync failed: ${e.message}")
            prefs.edit { putString(KEY_PROBLEM, SyncProblem.FAILED.name) }
            throw e
        } finally {
            publish(running = false)
        }
    }

    private suspend fun transfer(drive: DriveClient): Pair<Int, Int> {
        val remote = SyncRules.newestPerCard(drive.listCardFiles())
        val local = store.snapshot()
        val plan = SyncRules.plan(local.associate { it.id to it.updatedAt }, remote)
        val incoming = plan.download.mapNotNull { entry ->
            CardJson.decode(drive.download(entry.fileId)).also {
                if (it == null) Log.w(LOG_TAG, "skipping an unreadable card file")
            }
        }
        val downloaded = store.applyIncoming(incoming)
        val byId = local.associateBy { it.id }
        plan.upload.forEach { id ->
            val card = byId.getValue(id)
            val json = CardJson.encode(card)
            val existing = remote[id]
            if (existing != null) drive.update(existing.fileId, json, card.updatedAt) else drive.create(id, json, card.updatedAt)
        }
        return downloaded to plan.upload.size
    }

    private fun constraints() = Constraints.Builder()
        .setRequiredNetworkType(if (useMobileData()) NetworkType.CONNECTED else NetworkType.UNMETERED)
        .build()

    private fun enqueue(delaySeconds: Long) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setConstraints(constraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.REPLACE, request)
    }

    private fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun useMobileData() = prefs.getBoolean(KEY_MOBILE_DATA, true)

    private fun publish(running: Boolean = _status.value.running) {
        _status.value = readStatus(running)
    }

    private fun readStatus(running: Boolean) = SyncStatus(
        enabled = enabled,
        running = running,
        lastSyncAt = prefs.getLong(KEY_LAST_SYNC, 0L),
        problem = prefs.getString(KEY_PROBLEM, null)?.let { name -> SyncProblem.entries.firstOrNull { it.name == name } },
        account = prefs.getString(KEY_ACCOUNT, null),
        useMobileData = useMobileData(),
    )

    private companion object {
        const val PREFS = "sync"
        const val KEY_ENABLED = "enabled"
        const val KEY_ACCOUNT = "account"
        const val KEY_LAST_SYNC = "last_sync_at"
        const val KEY_PROBLEM = "problem"
        const val KEY_MOBILE_DATA = "use_mobile_data"
        const val KEY_FIRST_RUN_DONE = "first_run_done"
        const val WORK_NOW = "sync-now"
        const val WORK_PERIODIC = "sync-periodic"
        const val CHANGE_DELAY_SECONDS = 5L
        const val BACKOFF_SECONDS = 30L
        const val PERIOD_HOURS = 6L
    }
}
