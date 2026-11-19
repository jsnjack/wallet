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
 * landing for the baggage belt. Times are instants, because departure and
 * landing are local at different airports; [zone] reads the pass's date and
 * time, which are local at the departure airport, where the phone is too.
 */
class FlightLookups(private val zone: ZoneId) {

    /**
     * When [ref] should next be looked up, or null when no more lookups are
     * needed. [cardTime] is the departure time on the pass, if it has one;
     * [last] is the previous lookup.
     */
    fun next(ref: FlightRef, cardTime: LocalTime?, last: FlightCheck?): Instant? {
        val live = last?.flight
        if (live?.state?.final == true) return null
        val checked = last?.let { Instant.ofEpochMilli(it.checkedAt) }
        val onPass = cardTime?.let { at(ref.date.atTime(it)) }
        val known = live?.departureAt(zone)
        // Not found (the service knows flights about a day ahead), or found without a time: try once more,
        // early on the day or four hours before a departure printed on the pass, whichever comes first.
        if (checked != null && known == null) {
            val retry = listOfNotNull(at(ref.date.atTime(RETRY_TIME)), onPass?.minus(POLL_FROM)).min()
            return retry.takeIf { checked < it }
        }
        val departure = known ?: onPass ?: return at(ref.date.minusDays(1).atTime(FIRST_LOOKUP_TIME))
        if (checked == null) return departure.minus(DAY_AHEAD)
        return when {
            checked < departure.minus(POLL_FROM) -> departure.minus(POLL_FROM)
            checked < departure.minus(LAST_HOUR) -> checked.plus(SLOW_POLL)
            checked < departure.plus(FAST_POLL) -> checked.plus(FAST_POLL)
            else -> live?.arrivalAt(zone)?.plus(AFTER_LANDING)?.takeIf { checked < it }
        }
    }

    /**
     * True when opening the pass at [now] should look the flight up: a lookup
     * is due, or it is the day's polling window and the last result is more
     * than ten minutes old.
     */
    fun worthRefreshing(ref: FlightRef, cardTime: LocalTime?, last: FlightCheck?, now: Instant): Boolean {
        val next = next(ref, cardTime, last) ?: return false
        if (!now.isBefore(next)) return true
        val checked = last?.let { Instant.ofEpochMilli(it.checkedAt) } ?: return false
        val departure = last.flight?.departureAt(zone) ?: return false
        return !now.isBefore(departure.minus(POLL_FROM)) && Duration.between(checked, now) >= FRESH
    }

    private fun at(time: LocalDateTime): Instant = time.atZone(zone).toInstant()

    companion object {
        private val FIRST_LOOKUP_TIME: LocalTime = LocalTime.of(8, 0)
        private val RETRY_TIME: LocalTime = LocalTime.of(6, 0)
        private val DAY_AHEAD: Duration = Duration.ofHours(24)
        private val POLL_FROM: Duration = Duration.ofHours(4)
        private val LAST_HOUR: Duration = Duration.ofHours(1)
        private val SLOW_POLL: Duration = Duration.ofMinutes(30)
        private val FAST_POLL: Duration = Duration.ofMinutes(15)
        private val AFTER_LANDING: Duration = Duration.ofMinutes(10)
        private val FRESH: Duration = Duration.ofMinutes(10)
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
