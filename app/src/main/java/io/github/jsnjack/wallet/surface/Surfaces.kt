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
import io.github.jsnjack.wallet.core.CardGroups
import io.github.jsnjack.wallet.core.CardList
import io.github.jsnjack.wallet.core.FlightProgress
import io.github.jsnjack.wallet.core.FlightRef
import io.github.jsnjack.wallet.core.UpNextReason
import io.github.jsnjack.wallet.store.AppSettings
import io.github.jsnjack.wallet.store.CardStore
import io.github.jsnjack.wallet.store.FlightStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Keeps everything outside the app's own screen current: archive clean-up,
 * the "today" notification and reminders, widgets and the Quick Settings
 * tile. [refresh] runs after card changes, after flight lookups and at each
 * moment the agenda changes (scheduled with WorkManager).
 */
class Surfaces(
    private val context: Context,
    private val store: CardStore,
    private val settings: AppSettings,
    private val flights: FlightStore,
    private val afterRefresh: suspend () -> Unit,
) {
    private val lock = Mutex()

    /** Brings every surface up to date and schedules the next run. */
    suspend fun refresh() = lock.withLock {
        store.load()
        val zone = ZoneId.systemDefault()
        val instant = Instant.now()
        val now = LocalDateTime.ofInstant(instant, zone)
        val agenda = agenda(zone)
        val expired = agenda.beyondRetention(store.cards.value, now, settings.retention.value)
        if (expired.isNotEmpty()) {
            val removed = store.deleteAll(expired.map { it.id }, System.currentTimeMillis())
            Log.i(LOG_TAG, "archive clean-up deleted $removed cards")
        }
        val cards = store.cards.value
        val shown = tiles(cards, agenda, now)
        val today = agenda.today(shown, now).firstOrNull()
        val live = today?.card?.let(FlightRef::of)?.let { flights.checks.value[it.id]?.flight }
        val departure = today?.takeIf { it.reason == UpNextReason.FLIGHT }?.card?.event
            ?.let { event -> live?.departureAt(zone) ?: event.time?.let { event.date.atTime(it).atZone(zone).toInstant() } }
        val progress = FlightProgress.of(departure, live?.arrivalAt(zone), live?.state, instant)
        Notifier.update(context, today, live, progress, agenda.remindersDue(cards, now), settings)
        Widgets.update(context, agenda.upNext(shown, now).firstOrNull(), CardList.quickAccess(shown, store.usage.value, FAVOURITES, settings.favouriteOrder.value))
        afterRefresh()
        // Last: when AgendaWorker runs this, replacing its own work cancels it.
        // Measured between instants, so a daylight saving change in between does not move the run by an hour.
        val next = listOfNotNull(agenda.nextChange(cards, now).atZone(zone).toInstant(), FlightProgress.nextUpdate(departure, progress, instant)).min()
        schedule(Duration.between(instant, next))
    }

    /** The card the Quick Settings tile opens: the first "Up next" card, else the first favourite or most used card. */
    suspend fun tileCard(): Card? {
        store.load()
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val agenda = agenda(zone)
        val shown = tiles(store.cards.value, agenda, now)
        return agenda.upNext(shown, now).firstOrNull()?.card ?: CardList.quickAccess(shown, store.usage.value, FAVOURITES, settings.favouriteOrder.value).firstOrNull()
    }

    // The agenda with the latest flight lookups, so a late flight stays until it has landed.
    private fun agenda(zone: ZoneId) = Agenda(zone) { card -> FlightRef.of(card)?.let { flights.checks.value[it.id]?.flight } }

    /** Current cards as the grid shows them: one card per group, such as all passengers on one flight. */
    private fun tiles(cards: List<Card>, agenda: Agenda, now: LocalDateTime): List<Card> =
        CardGroups.collapse(cards.filterNot { agenda.isArchived(it, now) })

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
