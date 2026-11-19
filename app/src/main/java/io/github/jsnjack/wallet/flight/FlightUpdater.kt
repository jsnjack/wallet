package io.github.jsnjack.wallet.flight

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.jsnjack.wallet.LOG_TAG
import io.github.jsnjack.wallet.WalletApplication
import io.github.jsnjack.wallet.core.Agenda
import io.github.jsnjack.wallet.core.Card
import io.github.jsnjack.wallet.core.DayTime
import io.github.jsnjack.wallet.core.FlightCheck
import io.github.jsnjack.wallet.core.FlightLookups
import io.github.jsnjack.wallet.core.FlightRef
import io.github.jsnjack.wallet.core.FlightStats
import io.github.jsnjack.wallet.core.LiveFlight
import io.github.jsnjack.wallet.store.AppSettings
import io.github.jsnjack.wallet.store.CardStore
import io.github.jsnjack.wallet.store.FlightStore
import io.github.jsnjack.wallet.surface.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Looks boarding passes' flights up on flightstats.com when [FlightLookups]
 * says a lookup is due ([FlightWorker]) and when a pass opens, unless live
 * flight info is off in Settings. One lookup serves every passenger on a
 * flight. Results go to [FlightStore]; the first one fills in a pass's
 * missing departure time, and a gate change, a new time or a cancellation
 * posts an alert. [onUpdate] then refreshes the notification, widgets and
 * tile, which calls [schedule] for the next run.
 */
class FlightUpdater(
    private val context: Context,
    private val store: CardStore,
    private val settings: AppSettings,
    private val flights: FlightStore,
    private val onUpdate: suspend () -> Unit,
) {
    private val lock = Mutex()
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val client by lazy {
        FlightStatsClient(context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty())
    }

    /** Looks up every flight whose lookup is due. */
    suspend fun runDue() {
        val looked = lock.withLock {
            if (!settings.liveFlights.value || System.currentTimeMillis() < flights.retryAfter) return@withLock false
            val now = Instant.now()
            val lookups = FlightLookups(zone)
            val upcoming = upcoming()
            flights.keepOnly(upcoming.keys.map { it.id }.toSet())
            var any = false
            for ((ref, cards) in upcoming) {
                val next = lookups.next(ref, departureOnPass(cards), flights.checks.value[ref.id]) ?: continue
                if (now.isBefore(next)) continue
                // A pause between flights keeps a run from looking like a burst of traffic.
                if (any) delay(REQUEST_GAP_MS)
                if (!lookUp(ref, cards)) break
                any = true
            }
            any
        }
        if (looked) onUpdate() else schedule()
    }

    /** Looks the flight of [cardId] up when its pass opens and the last result is stale. */
    suspend fun refreshOnOpen(cardId: String) {
        val looked = lock.withLock {
            if (!settings.liveFlights.value) return@withLock false
            val ref = store.get(cardId)?.let(FlightRef::of) ?: return@withLock false
            val now = Instant.now()
            val cards = upcoming()[ref] ?: return@withLock false
            if (!FlightLookups(zone).worthRefreshing(ref, departureOnPass(cards), flights.checks.value[ref.id], now)) return@withLock false
            lookUp(ref, cards)
        }
        if (looked) onUpdate()
    }

    /**
     * Schedules [FlightWorker] for the next due lookup, or cancels it when
     * there is none or live flight info is off. Skipped while a lookup runs:
     * replacing the work would cancel a running [FlightWorker] mid-request,
     * and the lookup schedules again when it is done.
     */
    suspend fun schedule() {
        if (lock.isLocked) return
        val work = WorkManager.getInstance(context)
        val now = Instant.now()
        val lookups = FlightLookups(zone)
        val next = if (settings.liveFlights.value) {
            upcoming().mapNotNull { (ref, cards) -> lookups.next(ref, departureOnPass(cards), flights.checks.value[ref.id]) }.minOrNull()
        } else {
            null
        }
        if (next == null) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val retry = Instant.ofEpochMilli(flights.retryAfter)
        val delayMs = Duration.between(now, maxOf(next, retry)).toMillis().coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<FlightWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    // Returns false when the lookup failed, so a run stops instead of trying the next flight.
    private suspend fun lookUp(ref: FlightRef, cards: List<Card>): Boolean {
        val previous = flights.checks.value[ref.id]
        val body = try {
            withContext(Dispatchers.IO) { client.flight(ref) }
        } catch (e: IOException) {
            Log.w(LOG_TAG, "flight lookup failed: ${e.message}")
            flights.retryAfter = System.currentTimeMillis() + RETRY_AFTER_FAILURE_MS
            return false
        }
        val live = try {
            FlightStats.parse(body, ref)
        } catch (e: SerializationException) {
            return failed(e)
        } catch (e: IllegalArgumentException) {
            return failed(e)
        }
        // The state only: flight numbers and dates are the user's travel plans and stay out of logs.
        Log.i(LOG_TAG, "flight lookup: ${live?.state ?: "no such flight"}")
        flights.save(ref.id, FlightCheck(System.currentTimeMillis(), live))
        fillDepartureTime(ref, cards, live)
        val changes = FlightLookups.changes(previous?.flight, live)
        if (live != null && changes.isNotEmpty()) Notifier.flightChanged(context, cards.first(), live, changes, settings)
        return true
    }

    // A response that is not the expected JSON (an error page, a changed format) is a failure like a network error:
    // the last good result stays and lookups pause. Parser messages quote the response, which names the flight,
    // so only the error type is logged.
    private fun failed(e: Exception): Boolean {
        Log.w(LOG_TAG, "unexpected flight lookup response (${e::class.simpleName})")
        flights.retryAfter = System.currentTimeMillis() + RETRY_AFTER_FAILURE_MS
        return false
    }

    // A boarding pass barcode has the date only; the first lookup supplies the time.
    // Changes the stored card, not the copy taken before the request: a pass deleted or renamed meanwhile stays so.
    private suspend fun fillDepartureTime(ref: FlightRef, cards: List<Card>, live: LiveFlight?) {
        val time = live?.scheduledDeparture?.takeIf { it.toLocalDate() == ref.date }?.toLocalTime() ?: return
        cards.forEach { card ->
            store.update(card.id, System.currentTimeMillis()) { stored ->
                stored.takeIf { it.event?.time == null && FlightRef.of(it) == ref }?.copy(event = DayTime(ref.date, time))
            }
        }
    }

    // Current passes by flight; a late flight stays until it has landed.
    private suspend fun upcoming(): Map<FlightRef, List<Card>> {
        store.load()
        val agenda = Agenda(zone) { card -> FlightRef.of(card)?.let { flights.checks.value[it.id]?.flight } }
        val now = LocalDateTime.now(zone)
        return store.cards.value.filterNot { agenda.isArchived(it, now) }
            .mapNotNull { card -> FlightRef.of(card)?.let { it to card } }
            .groupBy({ it.first }, { it.second })
    }

    private fun departureOnPass(cards: List<Card>): LocalTime? = cards.firstNotNullOfOrNull { it.event?.time }

    private companion object {
        const val WORK_NAME = "flights"
        const val REQUEST_GAP_MS = 1_100L
        const val RETRY_AFTER_FAILURE_MS = 10 * 60 * 1000L
    }
}

/** Runs [FlightUpdater.runDue] when the next lookup is due and the phone is online. */
class FlightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as WalletApplication).flightUpdater.runDue()
        return Result.success()
    }
}
