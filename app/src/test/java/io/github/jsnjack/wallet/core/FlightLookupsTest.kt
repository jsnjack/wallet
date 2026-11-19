package io.github.jsnjack.wallet.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightLookupsTest {
    private val lookups = FlightLookups(ZoneOffset.UTC)
    private val day = LocalDate.of(2026, 11, 20)
    private val ref = FlightRef("KL", "1691", "AMS", day)
    private val departs = day.atTime(14, 0)

    private fun at(time: LocalDateTime) = time.toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun i(time: LocalDateTime): Instant = time.toInstant(ZoneOffset.UTC)

    private fun check(time: LocalDateTime, live: LiveFlight? = LiveFlight(FlightState.EXPECTED, scheduledDeparture = departs)) =
        FlightCheck(at(time), live)

    @Test
    fun withoutATimeTheFirstLookupIsTheMorningBefore() {
        assertEquals(i(day.minusDays(1).atTime(8, 0)), lookups.next(ref, null, null))
    }

    @Test
    fun aKnownTimeLooksUpADayAheadThenPollsCloserToDeparture() {
        assertEquals(i(departs.minusHours(24)), lookups.next(ref, LocalTime.of(14, 0), null))
        assertEquals(i(departs.minusHours(4)), lookups.next(ref, null, check(departs.minusHours(24))))
        assertEquals(i(departs.minusHours(3).minusMinutes(30)), lookups.next(ref, null, check(departs.minusHours(4))))
        assertEquals(i(departs.minusMinutes(45)), lookups.next(ref, null, check(departs.minusHours(1))))
    }

    @Test
    fun afterDepartureOneMoreLookupAfterLandingThenNone() {
        val landed = LiveFlight(FlightState.EN_ROUTE, scheduledDeparture = departs, scheduledArrival = departs.plusHours(2))
        assertEquals(i(departs.plusHours(2).plusMinutes(10)), lookups.next(ref, null, check(departs.plusMinutes(20), landed)))
        assertNull(lookups.next(ref, null, check(departs.plusHours(3), landed)))
        assertNull(lookups.next(ref, null, check(departs, LiveFlight(FlightState.CANCELED))))
    }

    @Test
    fun aDelayMovesTheSchedule() {
        val delayed = LiveFlight(FlightState.DELAYED, scheduledDeparture = departs, expectedDeparture = departs.plusHours(2))
        assertEquals(i(departs.plusMinutes(30)), lookups.next(ref, null, check(departs, delayed)))
    }

    @Test
    fun anEarlyFlightUnknownADayAheadIsTriedAgainBeforeItLeaves() {
        assertEquals(i(day.atTime(1, 30)), lookups.next(ref, LocalTime.of(5, 30), check(day.minusDays(1).atTime(5, 30), null)))
        assertNull(lookups.next(ref, LocalTime.of(5, 30), check(day.atTime(1, 30), null)))
    }

    @Test
    fun aLandingInAnotherTimeZoneIsLookedUpAfterItLands() {
        // AMS 10:00 (09:00 UTC) to JFK 12:30 local (17:30 UTC): the local times alone would say 3.5 hours, and look up too early.
        val transatlantic = LiveFlight(
            FlightState.EN_ROUTE,
            scheduledDeparture = day.atTime(10, 0),
            scheduledArrival = day.atTime(12, 30),
            scheduledDepartureUtc = i(day.atTime(9, 0)),
            scheduledArrivalUtc = i(day.atTime(17, 30)),
        )
        assertEquals(i(day.atTime(17, 40)), lookups.next(ref, null, check(day.atTime(10, 0), transatlantic)))
    }

    @Test
    fun anUnknownFlightIsTriedOnceMoreOnTheDay() {
        assertEquals(i(day.atTime(6, 0)), lookups.next(ref, LocalTime.of(14, 0), check(day.minusDays(1).atTime(8, 0), null)))
        assertNull(lookups.next(ref, LocalTime.of(14, 0), check(day.atTime(6, 0), null)))
    }

    @Test
    fun openingThePassRefreshesOnlyOnTheDayWhenStale() {
        val last = check(departs.minusHours(3))
        assertTrue(lookups.worthRefreshing(ref, null, last, i(departs.minusHours(3).plusMinutes(11))))
        assertFalse(lookups.worthRefreshing(ref, null, last, i(departs.minusHours(3).plusMinutes(5))))
        assertFalse(lookups.worthRefreshing(ref, null, check(departs.minusHours(24)), i(departs.minusHours(20))))
        assertTrue(lookups.worthRefreshing(ref, LocalTime.of(14, 0), null, i(departs.minusHours(23))))
    }

    @Test
    fun changesWorthAnAlert() {
        val before = LiveFlight(FlightState.EXPECTED, departureGate = "D7", scheduledDeparture = departs)
        assertEquals(emptySet<FlightChange>(), FlightLookups.changes(before, before.copy(expectedDeparture = departs.plusMinutes(10))))
        assertEquals(setOf(FlightChange.GATE), FlightLookups.changes(before, before.copy(departureGate = "D9")))
        assertEquals(setOf(FlightChange.TIME), FlightLookups.changes(before, before.copy(expectedDeparture = departs.plusMinutes(15))))
        assertEquals(setOf(FlightChange.CANCELED), FlightLookups.changes(before, before.copy(state = FlightState.CANCELED)))
        assertEquals(emptySet<FlightChange>(), FlightLookups.changes(null, before))
    }
}
