package io.github.jsnjack.wallet.surface

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.jsnjack.wallet.LOG_TAG
import io.github.jsnjack.wallet.WalletApplication
import io.github.jsnjack.wallet.core.Agenda
import io.github.jsnjack.wallet.core.Card
import io.github.jsnjack.wallet.core.CardList
import io.github.jsnjack.wallet.core.Trips
import io.github.jsnjack.wallet.store.AppSettings
import io.github.jsnjack.wallet.store.CardStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Keeps everything outside the app's own screen current: archive clean-up,
 * the "today" notification and reminders, widgets and the Quick Settings
 * tile. [refresh] runs after card changes, after sync and at each moment
 * the agenda changes (scheduled with WorkManager).
 */
class Surfaces(private val context: Context, private val store: CardStore, private val settings: AppSettings) {
    private val lock = Mutex()

    /** Brings every surface up to date and schedules the next run. */
    suspend fun refresh() = lock.withLock {
        store.load()
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val agenda = Agenda(zone)
        val expired = agenda.beyondRetention(store.cards.value, now, settings.retention.value)
        if (expired.isNotEmpty()) {
            val removed = store.deleteAll(expired.map { it.id }, System.currentTimeMillis())
            Log.i(LOG_TAG, "archive clean-up deleted $removed cards")
        }
        val cards = store.cards.value
        val upNext = agenda.upNext(cards, now)
        Notifier.update(context, agenda.today(cards, now).firstOrNull(), agenda.remindersDue(cards, now), settings)
        Widgets.update(context, upNext.firstOrNull(), favourites(cards, agenda, now))
        WalletTileService.requestUpdate(context)
        schedule(Duration.between(now, agenda.nextChange(cards, now)))
    }

    /** The card the Quick Settings tile opens: the first "Up next" card, else the first favourite or most used card. */
    suspend fun tileCard(): Card? {
        store.load()
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val agenda = Agenda(zone)
        return agenda.upNext(store.cards.value, now).firstOrNull()?.card ?: favourites(store.cards.value, agenda, now).firstOrNull()
    }

    private fun favourites(cards: List<Card>, agenda: Agenda, now: LocalDateTime): List<Card> =
        CardList.quickAccess(Trips.collapse(cards.filterNot { agenda.isArchived(it, now) }), store.usage.value, FAVOURITES)

    private fun schedule(delay: Duration) {
        val request = OneTimeWorkRequestBuilder<AgendaWorker>()
            .setInitialDelay(delay.toMillis() + SCHEDULE_SLACK_MS, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val WORK_NAME = "agenda"
        const val FAVOURITES = 4
        const val SCHEDULE_SLACK_MS = 30_000L
    }
}

/** Runs [Surfaces.refresh] at the next agenda change, such as midnight or a reminder time. */
class AgendaWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as WalletApplication).surfaces.refresh()
        return Result.success()
    }
}
