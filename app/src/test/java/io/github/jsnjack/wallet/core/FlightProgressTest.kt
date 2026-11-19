package io.github.jsnjack.wallet.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlightProgressTest {
    private val departs = LocalDate.of(2026, 11, 20).atTime(14, 0).toInstant(ZoneOffset.UTC)
    private val lands = departs.plusHours(2)

    private fun Instant.plusHours(hours: Long) = plus(Duration.ofHours(hours))
    private fun Instant.minusHours(hours: Long) = minus(Duration.ofHours(hours))
    private fun Instant.plusMinutes(minutes: Long) = plus(Duration.ofMinutes(minutes))
    private fun Instant.minusMinutes(minutes: Long) = minus(Duration.ofMinutes(minutes))

    @Test
    fun aFlightAcrossTimeZonesFillsTheBarByItsRealDuration() {
        // AMS 10:00 to JFK 12:30 local is 8.5 hours in the air; three hours in, the flight part of the bar is about a third full.
        val leaves = LocalDate.of(2026, 11, 20).atTime(9, 0).toInstant(ZoneOffset.UTC)
        val arrives = leaves.plus(Duration.ofMinutes(510))
        assertEquals(67, FlightProgress.of(leaves, arrives, FlightState.EN_ROUTE, leaves.plusHours(3)))
    }

    @Test
    fun timeAloneFillsTheBarFromFourHoursBeforeToLanding() {
        assertNull(FlightProgress.of(departs, lands, null, departs.minusHours(5)))
        assertEquals(0, FlightProgress.of(departs, lands, null, departs.minusHours(4)))
        assertEquals(FlightProgress.BOARDING, FlightProgress.of(departs, lands, null, departs.minusMinutes(40)))
        assertEquals(FlightProgress.DEPARTURE, FlightProgress.of(departs, lands, null, departs))
        assertEquals(75, FlightProgress.of(departs, lands, null, departs.plusHours(1)))
        assertEquals(FlightProgress.MAX, FlightProgress.of(departs, lands, null, lands.plusMinutes(5)))
    }

    @Test
    fun statusMovesTheBarForwardButWaitsAtTheGate() {
        assertEquals(FlightProgress.BOARDING, FlightProgress.of(departs, lands, FlightState.BOARDING, departs.minusHours(2)))
        assertEquals(FlightProgress.DEPARTURE, FlightProgress.of(departs, lands, FlightState.EXPECTED, departs.plusMinutes(30)))
        assertEquals(FlightProgress.DEPARTURE, FlightProgress.of(departs, lands, FlightState.DEPARTED, departs.minusMinutes(5)))
        assertEquals(FlightProgress.MAX, FlightProgress.of(departs, lands, FlightState.ARRIVED, departs.minusHours(6)))
    }

    @Test
    fun theBarIsRedrawnWhenItAppearsAndWhileItMoves() {
        assertEquals(departs.minusHours(4), FlightProgress.nextUpdate(departs, null, departs.minusHours(6)))
        assertEquals(departs.plusMinutes(10), FlightProgress.nextUpdate(departs, 50, departs))
        assertNull(FlightProgress.nextUpdate(departs, FlightProgress.MAX, lands))
        assertNull(FlightProgress.nextUpdate(departs, null, lands))
        assertNull(FlightProgress.nextUpdate(null, null, departs))
    }

    @Test
    fun noBarWithoutATimeOrForACancelledFlight() {
        assertNull(FlightProgress.of(null, lands, null, departs))
        assertNull(FlightProgress.of(departs, lands, FlightState.CANCELED, departs))
        assertEquals(FlightProgress.DEPARTURE, FlightProgress.of(departs, null, FlightState.EN_ROUTE, departs.plusHours(1)))
        assertNull(FlightProgress.of(departs, null, null, departs.plusHours(2)))
    }
}
