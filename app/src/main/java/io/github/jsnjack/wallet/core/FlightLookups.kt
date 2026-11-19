package io.github.jsnjack.wallet.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** What a new lookup changed compared with the one before, worth an alert. */
enum class FlightChange { GATE, TIME, CANCELED }

/**
 * When to look a flight up, about a dozen times per flight so the service
 * that answers is not burdened: once the day before, every 30 minutes from four
 * hours before departure, every 15 minutes in the last hour, and once after
 * landing for the baggage belt. [zone] converts the stored lookup instants.
 */
class FlightLookups(private val zone: ZoneId) {

    /**
     * When [ref] should next be looked up, or null when no more lookups are
     * needed. [cardTime] is the departure time on the pass, if it has one;
     * [last] is the previous lookup.
     */
    fun next(ref: FlightRef, cardTime: LocalTime?, last: FlightCheck?): LocalDateTime? {
        val live = last?.flight
        if (live?.state?.final == true) return null
        val checked = last?.let { local(it.checkedAt) }
        // Not found, or found without a time: try once more early on the day itself.
        if (checked != null && live?.departure == null) return ref.date.atTime(RETRY_TIME).takeIf { checked < it }
        val departure = live?.departure ?: cardTime?.let { ref.date.atTime(it) }
            ?: return ref.date.minusDays(1).atTime(FIRST_LOOKUP_TIME)
        if (checked == null) return departure.minusHours(DAY_AHEAD_HOURS)
        return when {
            checked < departure.minusHours(POLL_FROM_HOURS) -> departure.minusHours(POLL_FROM_HOURS)
            checked < departure.minusHours(1) -> checked.plusMinutes(SLOW_POLL_MINUTES)
            checked < departure.plusMinutes(FAST_POLL_MINUTES) -> checked.plusMinutes(FAST_POLL_MINUTES)
            else -> live?.arrival?.plusMinutes(AFTER_LANDING_MINUTES)?.takeIf { checked < it }
        }
    }

    /**
     * True when opening the pass at [now] should look the flight up: a lookup
     * is due, or it is the day's polling window and the last result is more
     * than ten minutes old.
     */
    fun worthRefreshing(ref: FlightRef, cardTime: LocalTime?, last: FlightCheck?, now: LocalDateTime): Boolean {
        val next = next(ref, cardTime, last) ?: return false
        if (!now.isBefore(next)) return true
        val checked = last?.let { local(it.checkedAt) } ?: return false
        val departure = last.flight?.departure ?: return false
        return !now.isBefore(departure.minusHours(POLL_FROM_HOURS)) && Duration.between(checked, now).toMinutes() >= FRESH_MINUTES
    }

    private fun local(epochMillis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone)

    companion object {
        private val FIRST_LOOKUP_TIME: LocalTime = LocalTime.of(8, 0)
        private val RETRY_TIME: LocalTime = LocalTime.of(6, 0)
        private const val DAY_AHEAD_HOURS = 24L
        private const val POLL_FROM_HOURS = 4L
        private const val SLOW_POLL_MINUTES = 30L
        private const val FAST_POLL_MINUTES = 15L
        private const val AFTER_LANDING_MINUTES = 10L
        private const val FRESH_MINUTES = 10L
        private const val TIME_CHANGE_MINUTES = 15L

        /** What changed between two lookups: a gate set or moved, departure moved by 15 minutes or more, or a cancellation. */
        fun changes(old: LiveFlight?, new: LiveFlight?): Set<FlightChange> {
            if (old == null || new == null) return emptySet()
            return buildSet {
                if (new.departureGate != null && new.departureGate != old.departureGate) add(FlightChange.GATE)
                val before = old.departure
                val after = new.departure
                if (before != null && after != null && Duration.between(before, after).abs().toMinutes() >= TIME_CHANGE_MINUTES) {
                    add(FlightChange.TIME)
                }
                if ((new.state == FlightState.CANCELED || new.state == FlightState.DIVERTED) && new.state != old.state) add(FlightChange.CANCELED)
            }
        }
    }
}
