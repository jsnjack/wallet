package io.github.jsnjack.wallet.core

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AeroDataBoxTest {
    private val ref = FlightRef("KL", "1691", "AMS", LocalDate.of(2026, 11, 20))

    private fun movement(iata: String, scheduled: String, extra: String = "") =
        """{"airport":{"iata":"$iata","name":"x"},"scheduledTime":{"utc":"u","local":"$scheduled"}$extra,"quality":["Basic"]}"""

    @Test
    fun readsGateTimesAndStatusFromTheDepartureAirport() {
        val body = """[{
            "number":"KL 1691","status":"Boarding","codeshareStatus":"IsOperator","isCargo":false,"lastUpdatedUtc":"2026-11-20 12:58Z",
            "departure":${movement("AMS", "2026-11-20 14:10+01:00", ""","revisedTime":{"utc":"u","local":"2026-11-20 14:35+01:00"},"terminal":"2","gate":" D7 ","checkInDesk":" ","runway":null""")},
            "arrival":${movement("BCN", "2026-11-20T16:20:00+01:00", ""","predictedTime":{"utc":"u","local":"2026-11-20T16:41:00+01:00"},"baggageBelt":"12"""")},
            "airline":{"name":"KLM","iata":"KL","icao":"KLM"},"someNewField":1
        }]"""
        val live = AeroDataBox.parse(body, ref)!!
        assertEquals(FlightState.BOARDING, live.state)
        assertEquals("D7", live.departureGate)
        assertEquals("2", live.departureTerminal)
        assertNull(live.checkInDesk)
        assertEquals(LocalDateTime.of(2026, 11, 20, 14, 10), live.scheduledDeparture)
        assertEquals(LocalDateTime.of(2026, 11, 20, 14, 35), live.departure)
        assertEquals(LocalDateTime.of(2026, 11, 20, 16, 41), live.arrival)
        assertEquals("12", live.baggageBelt)
    }

    @Test
    fun picksTheLegThatLeavesThePassAirportOnItsDate() {
        val body = """[
            {"number":"KL1691","status":"Expected","isCargo":false,"departure":${movement("RTM", "2026-11-20 06:00+01:00")},"arrival":${movement("AMS", "2026-11-20 06:40+01:00")}},
            {"number":"KL1691","status":"Expected","isCargo":false,"departure":${movement("AMS", "2026-11-19 14:10+01:00")},"arrival":${movement("BCN", "2026-11-19 16:20+01:00")}},
            {"number":"KL1691","status":"Delayed","isCargo":false,"departure":${movement("AMS", "2026-11-20 14:10+01:00")},"arrival":${movement("BCN", "2026-11-20 16:20+01:00")}}
        ]"""
        assertEquals(FlightState.DELAYED, AeroDataBox.parse(body, ref)?.state)
    }

    @Test
    fun emptyCargoOnlyAndUnknownStatuses() {
        assertNull(AeroDataBox.parse("[]", ref))
        assertNull(AeroDataBox.parse("""[{"status":"Expected","isCargo":true}]""", ref))
        assertEquals(FlightState.UNKNOWN, AeroDataBox.parse("""[{"status":"SomethingNew"}]""", ref)?.state)
        assertEquals(FlightState.CANCELED_UNCERTAIN, AeroDataBox.parse("""[{"status":"CanceledUncertain"}]""", ref)?.state)
    }

    @Test
    fun localTimeTakesTheWallClockPart() {
        assertEquals(LocalDateTime.of(2026, 11, 20, 23, 5), AeroDataBox.localTime("2026-11-20 23:05-05:00"))
        assertEquals(LocalDateTime.of(2026, 11, 20, 23, 5), AeroDataBox.localTime("2026-11-20T23:05:00Z"))
        assertNull(AeroDataBox.localTime("soon"))
        assertNull(AeroDataBox.localTime(null))
    }

    @Test
    fun flightRefComesFromABoardingPass() {
        val pass = Card(
            id = "p", name = "KL 1691", code = "M1", format = CodeFormat.AZTEC, kind = CardKind.BOARDING_PASS, color = 0, note = "",
            pinned = false, locked = false, createdAt = 0, updatedAt = 0, event = DayTime(ref.date), flight = Flight("ams", "BCN", "kl", "1691"),
        )
        assertEquals(ref, FlightRef.of(pass))
        assertEquals("KL1691-AMS-2026-11-20", FlightRef.of(pass)?.id)
        assertNull(FlightRef.of(pass.copy(event = null)))
        assertNull(FlightRef.of(pass.copy(kind = CardKind.TICKET)))
    }
}
