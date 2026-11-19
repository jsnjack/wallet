package io.github.jsnjack.wallet.core

import java.time.Duration
import java.time.LocalDateTime

/**
 * Where a flight is on its day, as a progress bar from 0 to [MAX]: the four
 * hours before boarding fill the first part, boarding the next, the flight
 * itself the rest. Time moves the bar; live status ([LiveFlight.state]) can
 * only move it forward, so boarding called early shows at once.
 */
object FlightProgress {
    /** The whole bar. */
    const val MAX = 100

    /** Where boarding starts on the bar. */
    const val BOARDING = 40

    /** Where the plane leaves on the bar. */
    const val DEPARTURE = 50

    private const val SHOW_FROM_HOURS = 4L
    private const val BOARDING_MINUTES = 40L
    private const val TICK_MINUTES = 10L

    /**
     * When the bar for [departure] should next be redrawn after [now], given
     * its current [progress]: when it appears, every ten minutes while it
     * moves, and never once it is full or the flight has no time.
     */
    fun nextUpdate(departure: LocalDateTime?, progress: Int?, now: LocalDateTime): LocalDateTime? = when {
        departure == null -> null
        progress == null -> departure.minusHours(SHOW_FROM_HOURS).takeIf { now.isBefore(it) }
        progress < MAX -> now.plusMinutes(TICK_MINUTES)
        else -> null
    }

    /**
     * The progress at [now] for a flight leaving at [departure] and landing at
     * [arrival] (null when unknown), or null outside the bar's time span, for a
     * cancelled or diverted flight, without a departure time, or an hour after
     * take-off when the landing time is unknown.
     */
    fun of(departure: LocalDateTime?, arrival: LocalDateTime?, state: FlightState?, now: LocalDateTime): Int? {
        if (departure == null || state == FlightState.CANCELED || state == FlightState.DIVERTED || state == FlightState.CANCELED_UNCERTAIN) return null
        if (state == FlightState.ARRIVED) return MAX
        val start = departure.minusHours(SHOW_FROM_HOURS)
        if (now.isBefore(start)) return null
        val boarding = departure.minusMinutes(BOARDING_MINUTES)
        val byTime = when {
            now.isBefore(boarding) -> share(start, boarding, now, 0, BOARDING)
            now.isBefore(departure) -> share(boarding, departure, now, BOARDING, DEPARTURE)
            // Without a landing time the bar holds at take-off for an hour, then goes.
            arrival == null || !arrival.isAfter(departure) -> if (now.isAfter(departure.plusHours(1))) return null else DEPARTURE
            now.isBefore(arrival) -> share(departure, arrival, now, DEPARTURE, MAX)
            else -> MAX
        }
        // Without a status the clock decides; with one, the bar waits at the gate until the plane has left.
        val airborne = state == FlightState.DEPARTED || state == FlightState.EN_ROUTE || state == FlightState.APPROACHING
        val boardingNow = state == FlightState.BOARDING || state == FlightState.GATE_CLOSED
        return when {
            state == null || state == FlightState.UNKNOWN -> byTime
            airborne -> maxOf(byTime, DEPARTURE)
            boardingNow -> byTime.coerceIn(BOARDING, DEPARTURE)
            else -> minOf(byTime, DEPARTURE)
        }
    }

    // The point between [from] and [to] that [now] is at, mapped onto the bar between [low] and [high].
    private fun share(from: LocalDateTime, to: LocalDateTime, now: LocalDateTime, low: Int, high: Int): Int {
        val total = Duration.between(from, to).toSeconds().coerceAtLeast(1)
        val done = Duration.between(from, now).toSeconds().coerceIn(0, total)
        return low + ((high - low) * done / total).toInt()
    }
}
