package io.github.jsnjack.wallet.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlightProgressTest {
    private val departs = LocalDate.of(2026, 11, 20).atTime(14, 0)
    private val lands = departs.plusHours(2)

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
