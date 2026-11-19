package io.github.jsnjack.wallet.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BcbpTest {
    companion object {
        private fun leg(pnr: String, from: String, to: String, carrier: String, flight: String, day: String, seat: String, variable: String = "") =
            pnr.padEnd(7) + from + to + carrier.padEnd(3) + flight.padEnd(5) + day + "Y" + seat + "0025 " + "1" +
                "%02X".format(variable.length) + variable

        /** SYNTH: one leg AMS → LIS on day 324 (20 November 2026), with conditional data to skip. */
        val ONE_LEG = "M1" + "TRAVELLER/A".padEnd(20) + "E" + leg("X7K2QP", "AMS", "LIS", "KL", "1691", "324", "014C", ">5180M") + "^164GIWVC5EH7JNT"

        /** SYNTH: two legs on one booking. */
        val TWO_LEGS = "M2" + "TRAVELLER/A".padEnd(20) + "E" + leg("X7K2QP", "AMS", "LIS", "KL", "1691", "324", "014C") +
            leg("X7K2QP", "LIS", "OPO", "TP", "0123", "325", "003A")
    }

    private val today = LocalDate.of(2026, 11, 19)

    @Test
    fun readsTheMandatoryItems() {
        val pass = Bcbp.parse(ONE_LEG, today)!!
        assertEquals("TRAVELLER/A", pass.passenger)
        val leg = pass.legs.single()
        assertEquals(Flight("AMS", "LIS", "KL", "1691", "TRAVELLER/A", "X7K2QP", "14C", "Y"), leg.flight)
        assertEquals(LocalDate.of(2026, 11, 20), leg.date)
    }

    @Test
    fun readsEveryLegAndStripsLeadingZeros() {
        val legs = Bcbp.parse(TWO_LEGS, today)!!.legs
        assertEquals(listOf("AMS", "LIS"), legs.map { it.flight.from })
        assertEquals("123", legs[1].flight.number)
        assertEquals("3A", legs[1].flight.seat)
        assertEquals(LocalDate.of(2026, 11, 21), legs[1].date)
    }

    @Test
    fun rejectsOtherText() {
        assertNull(Bcbp.parse("https://example.com", today))
        assertNull(Bcbp.parse("M1" + "X".repeat(60), today))
        assertNull(Bcbp.parse(ONE_LEG.take(50), today))
    }

    @Test
    fun placesDayOfYearNearToday() {
        assertEquals(LocalDate.of(2027, 1, 5), Bcbp.dateOf(5, today))
        assertEquals(LocalDate.of(2026, 11, 16), Bcbp.dateOf(320, today))
        assertEquals(LocalDate.of(2025, 12, 26), Bcbp.dateOf(360, LocalDate.of(2026, 1, 10)))
        assertNull(Bcbp.dateOf(366, LocalDate.of(2026, 6, 1))?.takeIf { it.year == 2026 })
    }

    @Test
    fun airportTableParsesTabSeparatedLines() {
        val airports = Airports.parse("AMS\tAmsterdam\nLIS\tLisbon\nbad line\n")
        assertEquals("Amsterdam", airports.city("ams"))
        assertNull(airports.city("XXX"))
    }
}
