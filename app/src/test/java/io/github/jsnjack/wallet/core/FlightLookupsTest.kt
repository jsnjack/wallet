package io.github.jsnjack.wallet.core

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

    private fun check(time: LocalDateTime, live: LiveFlight? = LiveFlight(FlightState.EXPECTED, scheduledDeparture = departs)) =
        FlightCheck(at(time), live)

    @Test
    fun withoutATimeTheFirstLookupIsTheMorningBefore() {
        assertEquals(day.minusDays(1).atTime(8, 0), lookups.next(ref, null, null))
    }

    @Test
    fun aKnownTimeLooksUpADayAheadThenPollsCloserToDeparture() {
        assertEquals(departs.minusHours(24), lookups.next(ref, LocalTime.of(14, 0), null))
        assertEquals(departs.minusHours(4), lookups.next(ref, null, check(departs.minusHours(24))))
        assertEquals(departs.minusHours(3).minusMinutes(30), lookups.next(ref, null, check(departs.minusHours(4))))
        assertEquals(departs.minusMinutes(45), lookups.next(ref, null, check(departs.minusHours(1))))
    }

    @Test
    fun afterDepartureOneMoreLookupAfterLandingThenNone() {
        val landed = LiveFlight(FlightState.EN_ROUTE, scheduledDeparture = departs, scheduledArrival = departs.plusHours(2))
        assertEquals(departs.plusHours(2).plusMinutes(10), lookups.next(ref, null, check(departs.plusMinutes(20), landed)))
        assertNull(lookups.next(ref, null, check(departs.plusHours(3), landed)))
        assertNull(lookups.next(ref, null, check(departs, LiveFlight(FlightState.CANCELED))))
    }

    @Test
    fun aDelayMovesTheSchedule() {
        val delayed = LiveFlight(FlightState.DELAYED, scheduledDeparture = departs, expectedDeparture = departs.plusHours(2))
        assertEquals(departs.plusMinutes(30), lookups.next(ref, null, check(departs, delayed)))
    }

    @Test
    fun anUnknownFlightIsTriedOnceMoreOnTheDay() {
        assertEquals(day.atTime(6, 0), lookups.next(ref, LocalTime.of(14, 0), check(day.minusDays(1).atTime(8, 0), null)))
        assertNull(lookups.next(ref, LocalTime.of(14, 0), check(day.atTime(6, 0), null)))
    }

    @Test
    fun openingThePassRefreshesOnlyOnTheDayWhenStale() {
        val last = check(departs.minusHours(3))
        assertTrue(lookups.worthRefreshing(ref, null, last, departs.minusHours(3).plusMinutes(11)))
        assertFalse(lookups.worthRefreshing(ref, null, last, departs.minusHours(3).plusMinutes(5)))
        assertFalse(lookups.worthRefreshing(ref, null, check(departs.minusHours(24)), departs.minusHours(20)))
        assertTrue(lookups.worthRefreshing(ref, LocalTime.of(14, 0), null, departs.minusHours(23)))
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

    @Test
    fun theAllowanceKeepsAReserveForOpeningAPass() {
        assertTrue(FlightLookups.allows(189, background = true))
        assertFalse(FlightLookups.allows(190, background = true))
        assertTrue(FlightLookups.allows(199, background = false))
        assertFalse(FlightLookups.allows(200, background = false))
        assertEquals("2026-11", FlightLookups.month(departs))
    }
}
