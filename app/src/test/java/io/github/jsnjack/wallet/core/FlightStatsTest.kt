package io.github.jsnjack.wallet.core

import java.time.LocalDate
import java.time.Instant
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlightStatsTest {
    private val ref = FlightRef("KL", "1001", "AMS", LocalDate.of(2026, 11, 19))

    // Shaped like a real flightstats.com tracker response, trimmed to the fields Wallet reads plus a few it ignores.
    private val landed = """{"data":{"flightId":1,"isLanded":true,
        "status":{"statusCode":"L","status":"Arrived","statusDescription":"On time","delay":{"departure":{"minutes":6}}},
        "schedule":{"scheduledDeparture":"2026-11-19T07:20:00.000","scheduledDepartureUTC":"2026-11-19T06:20:00.000Z",
          "estimatedActualDeparture":"2026-11-19T07:26:00.000","scheduledArrival":"2026-11-19T07:40:00.000",
          "estimatedActualArrival":"2026-11-19T07:39:00.000"},
        "departureAirport":{"iata":"AMS","name":"Amsterdam Schiphol Airport","gate":"D6","terminal":"2"},
        "arrivalAirport":{"iata":"LHR","gate":null,"terminal":"4","baggage":"8"},
        "positional":{"flexTrack":{}}}}"""

    @Test
    fun readsGatesTimesBeltAndStatus() {
        val live = FlightStats.parse(landed, ref)!!
        assertEquals(FlightState.ARRIVED, live.state)
        assertEquals("D6", live.departureGate)
        assertEquals("2", live.departureTerminal)
        assertEquals(LocalDateTime.of(2026, 11, 19, 7, 20), live.scheduledDeparture)
        assertEquals(Instant.parse("2026-11-19T06:20:00Z"), live.scheduledDepartureUtc)
        assertEquals(LocalDateTime.of(2026, 11, 19, 7, 26), live.departure)
        assertEquals(LocalDateTime.of(2026, 11, 19, 7, 39), live.arrival)
        assertNull(live.arrivalGate)
        assertEquals("4", live.arrivalTerminal)
        assertEquals("8", live.baggageBelt)
    }

    @Test
    fun emptyDataOrAnotherLegIsNoFlight() {
        assertNull(FlightStats.parse("""{"data":{}}""", ref))
        assertNull(FlightStats.parse("""{"data":null}""", ref))
        assertNull(FlightStats.parse(landed.replace("\"iata\":\"AMS\"", "\"iata\":\"RTM\""), ref))
    }

    @Test
    fun statusCodesMapToStates() {
        fun stateOf(code: String) = FlightStats.parse(landed.replace("\"statusCode\":\"L\"", "\"statusCode\":\"$code\""), ref)?.state
        assertEquals(FlightState.EXPECTED, stateOf("S"))
        assertEquals(FlightState.EN_ROUTE, stateOf("A"))
        assertEquals(FlightState.CANCELED, stateOf("C"))
        assertEquals(FlightState.DIVERTED, stateOf("D"))
        assertEquals(FlightState.UNKNOWN, stateOf("DN"))
    }

    @Test
    fun timesWithoutMillisecondsOrBrokenAreHandled() {
        assertEquals(LocalDateTime.of(2026, 11, 20, 7, 20), FlightStats.time("2026-11-20T07:20:00"))
        assertNull(FlightStats.time("soon"))
        assertNull(FlightStats.time(null))
    }
}
