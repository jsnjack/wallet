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
import io.github.jsnjack.wallet.core.AeroDataBox
import io.github.jsnjack.wallet.core.Agenda
import io.github.jsnjack.wallet.core.Card
import io.github.jsnjack.wallet.core.DayTime
import io.github.jsnjack.wallet.core.FlightCheck
import io.github.jsnjack.wallet.core.FlightLookups
import io.github.jsnjack.wallet.core.FlightRef
import io.github.jsnjack.wallet.core.LiveFlight
import io.github.jsnjack.wallet.core.LookupProblem
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

/** The outcome of "Test key" in Settings. */
enum class KeyCheck { WORKS, REJECTED, LIMIT_REACHED, UNREACHABLE }

/**
 * Looks boarding passes' flights up in AeroDataBox when [FlightLookups] says
 * a lookup is due ([FlightWorker]) and when a pass opens. One lookup serves
 * every passenger on a flight. Results go to [FlightStore]; the first one
 * fills in a pass's missing departure time, and a gate change, a new time or
 * a cancellation posts an alert. [onUpdate] then refreshes the notification,
 * widgets and tile, which calls [schedule] for the next run.
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

    /** Looks up every flight whose lookup is due, within the background share of the allowance. */
    suspend fun runDue() {
        val looked = lock.withLock {
            val key = usableKey() ?: return@withLock false
            if (System.currentTimeMillis() < flights.retryAfter) return@withLock false
            val now = LocalDateTime.now(zone)
            val lookups = FlightLookups(zone)
            val upcoming = upcoming(now)
            flights.keepOnly(upcoming.keys.map { it.id }.toSet())
            var any = false
            for ((ref, cards) in upcoming) {
                val next = lookups.next(ref, departureOnPass(cards), flights.checks.value[ref.id]) ?: continue
                if (now.isBefore(next)) continue
                if (!FlightLookups.allows(flights.usageIn(FlightLookups.month(now)).used, background = true)) break
                // AeroDataBox's free plan takes one request a second.
                if (any) delay(REQUEST_GAP_MS)
                if (!lookUp(key, ref, cards)) break
                any = true
            }
            any
        }
        if (looked) onUpdate() else schedule()
    }

    /** Looks the flight of [cardId] up when its pass opens and the last result is stale. */
    suspend fun refreshOnOpen(cardId: String) {
        val looked = lock.withLock {
            val key = usableKey() ?: return@withLock false
            val ref = store.get(cardId)?.let(FlightRef::of) ?: return@withLock false
            val now = LocalDateTime.now(zone)
            val cards = upcoming(now)[ref] ?: return@withLock false
            if (!FlightLookups.allows(flights.usageIn(FlightLookups.month(now)).used, background = false)) return@withLock false
            if (!FlightLookups(zone).worthRefreshing(ref, departureOnPass(cards), flights.checks.value[ref.id], now)) return@withLock false
            lookUp(key, ref, cards)
        }
        if (looked) onUpdate()
    }

    /** Tries the saved key with one cheap request, so Settings can say whether it works. */
    suspend fun checkKey(): KeyCheck {
        val key = settings.flightKey.value.ifBlank { return KeyCheck.REJECTED }
        val month = FlightLookups.month(LocalDateTime.now(zone))
        val outcome = try {
            withContext(Dispatchers.IO) { AeroDataBoxClient(key).checkKey() }
            flights.countLookup(month)
            flights.setProblem(month, null)
            KeyCheck.WORKS
        } catch (e: LookupRefused) {
            Log.w(LOG_TAG, e.message.orEmpty())
            flights.setProblem(month, e.problem)
            if (e.problem == LookupProblem.KEY_REJECTED) KeyCheck.REJECTED else KeyCheck.LIMIT_REACHED
        } catch (e: IOException) {
            Log.w(LOG_TAG, "flight key check failed: ${e.message}")
            KeyCheck.UNREACHABLE
        }
        schedule()
        return outcome
    }

    /** Forgets a refused key's problem after the user changed the key. */
    fun keyChanged() {
        val month = FlightLookups.month(LocalDateTime.now(zone))
        if (flights.usageIn(month).problem == LookupProblem.KEY_REJECTED) flights.setProblem(month, null)
    }

    /** Schedules [FlightWorker] for the next due lookup, or cancels it when there is none or lookups cannot run. */
    suspend fun schedule() {
        val work = WorkManager.getInstance(context)
        val now = LocalDateTime.now(zone)
        val lookups = FlightLookups(zone)
        val next = usableKey()
            ?.takeIf { FlightLookups.allows(flights.usageIn(FlightLookups.month(now)).used, background = true) }
            ?.let { upcoming(now).mapNotNull { (ref, cards) -> lookups.next(ref, departureOnPass(cards), flights.checks.value[ref.id]) }.minOrNull() }
        if (next == null) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val retry = LocalDateTime.ofInstant(Instant.ofEpochMilli(flights.retryAfter), zone)
        val delayMs = Duration.between(now, maxOf(next, retry)).toMillis().coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<FlightWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    // Returns false when the lookup failed, so a run stops instead of trying the next flight.
    private suspend fun lookUp(key: String, ref: FlightRef, cards: List<Card>): Boolean {
        val month = FlightLookups.month(LocalDateTime.now(zone))
        val previous = flights.checks.value[ref.id]
        val body = try {
            withContext(Dispatchers.IO) { AeroDataBoxClient(key).flight(ref) }
        } catch (e: LookupRefused) {
            Log.w(LOG_TAG, e.message.orEmpty())
            flights.setProblem(month, e.problem)
            return false
        } catch (e: IOException) {
            Log.w(LOG_TAG, "flight lookup failed: ${e.message}")
            flights.retryAfter = System.currentTimeMillis() + RETRY_AFTER_FAILURE_MS
            return false
        }
        flights.countLookup(month)
        val live = body?.let { parse(it, ref) }
        // The state only: flight numbers and dates are the user's travel plans and stay out of logs.
        Log.i(LOG_TAG, "flight lookup: ${live?.state ?: "no such flight"}")
        flights.save(ref.id, FlightCheck(System.currentTimeMillis(), live))
        fillDepartureTime(ref, cards, live)
        val changes = FlightLookups.changes(previous?.flight, live)
        if (live != null && changes.isNotEmpty()) Notifier.flightChanged(context, cards.first(), live, changes, settings)
        return true
    }

    // Parser messages quote the response, which names the flight, so only the error type is logged.
    private fun parse(body: String, ref: FlightRef): LiveFlight? = try {
        AeroDataBox.parse(body, ref)
    } catch (e: SerializationException) {
        Log.w(LOG_TAG, "unexpected flight lookup response (${e::class.simpleName})")
        null
    } catch (e: IllegalArgumentException) {
        Log.w(LOG_TAG, "unexpected flight lookup response (${e::class.simpleName})")
        null
    }

    // A boarding pass barcode has the date only; the first lookup supplies the time.
    private suspend fun fillDepartureTime(ref: FlightRef, cards: List<Card>, live: LiveFlight?) {
        val time = live?.scheduledDeparture?.takeIf { it.toLocalDate() == ref.date }?.toLocalTime() ?: return
        cards.filter { it.event?.time == null }.forEach { card ->
            store.save(card.copy(event = DayTime(ref.date, time), updatedAt = System.currentTimeMillis()))
        }
    }

    private fun usableKey(): String? = settings.flightKey.value.takeIf { it.isNotBlank() }
        ?.takeIf { flights.usageIn(FlightLookups.month(LocalDateTime.now(zone))).problem == null }

    private suspend fun upcoming(now: LocalDateTime): Map<FlightRef, List<Card>> {
        store.load()
        val agenda = Agenda(zone)
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
