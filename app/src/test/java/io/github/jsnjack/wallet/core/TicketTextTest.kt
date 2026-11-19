package io.github.jsnjack.wallet.core

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TicketTextTest {
    private val today = LocalDate.of(2026, 11, 19)

    // The layout of a Ticketmaster e-ticket, with made-up buyer details.
    private val ticketmaster = """
        ticketmaster
        SYML & Metropole Orkest
        Date & time: Thursday 3 December 2026 at 20:30
        Doors open: 19:30
        TivoliVredenburg
        Vredenburgkade 11
        3511 WC  Utrecht
        Customer number:
        12345678
        Customer name:
        Jane Doe
        Organisatie: Stichting TivoliVredenburg
        Zaal: Grote Zaal
        Ticket price:
        € 51,00
        Price type:
        Regulier
        *%TST/1234*
    """.trimIndent()

    @Test
    fun readsTheEventFromATicketmasterTicket() {
        val facts = TicketText.read(ticketmaster, today, code = "%TST/1234")
        assertEquals("SYML & Metropole Orkest", facts.name)
        assertEquals(DayTime(LocalDate.of(2026, 12, 3), LocalTime.of(20, 30)), facts.event)
        assertEquals(
            listOf(Detail("Doors open", "19:30"), Detail("Zaal", "Grote Zaal"), Detail("Venue", "TivoliVredenburg, Utrecht")),
            facts.details,
        )
    }

    @Test
    fun termsAreNoName() {
        val terms = "The General Terms and Conditions of TICKETMASTER (please check: Ticketmaster.nl)\n" +
            "• Only the first holder who can show an admission ticket at the entrance to the"
        assertNull(TicketText.read(terms, today, code = "%TST/1234").name)
    }

    @Test
    fun aPageWithoutTheEventBorrowsItFromTheFile() {
        val facts = TicketText.readPage("Rij: 7\nStoel: 15", "Concert\nDatum: 3-12-2026 20:00\nRij: 7\nStoel: 14", today, "x")!!
        assertEquals("Concert", facts.name)
        assertEquals(DayTime(LocalDate.of(2026, 12, 3), LocalTime.of(20, 0)), facts.event)
        assertEquals(listOf(Detail("Rij", "7"), Detail("Stoel", "15")), facts.details)
        assertNull(TicketText.readPage(null, null, today, "x"))
    }

    @Test
    fun neverTakesTheBuyerOrderOrPrice() {
        val facts = TicketText.read(ticketmaster, today)
        val text = (listOfNotNull(facts.name) + facts.details.map { it.value }).joinToString()
        listOf("Jane", "12345678", "51", "Regulier", "Stichting").forEach { assert(!text.contains(it)) { "took $it" } }
    }

    @Test
    fun dutchTicketWithASeparateTimeAndAnOrderDate() {
        val text = """
            Toegangsbewijs
            Besteld op: 2 november 2026
            Het Nationale Theater
            Datum: zaterdag 12 dec. 2026
            Aanvang: 20.15 uur
            Rij: 7
            Stoel: 14
        """.trimIndent()
        val facts = TicketText.read(text, today)
        assertEquals("Het Nationale Theater", facts.name)
        assertEquals(DayTime(LocalDate.of(2026, 12, 12), LocalTime.of(20, 15)), facts.event)
        assertEquals(listOf(Detail("Rij", "7"), Detail("Stoel", "14")), facts.details)
    }

    @Test
    fun numericAndIsoDatesWithoutLabelsPreferTheComingOne() {
        assertEquals(
            DayTime(LocalDate.of(2027, 1, 5), LocalTime.of(19, 0)),
            TicketText.read("Jazz Night\n01-10-2026\n05-01-2027 19:00", today).event,
        )
        assertEquals(DayTime(LocalDate.of(2026, 12, 24)), TicketText.read("Kerstconcert\n2026-12-24", today).event)
        assertEquals(
            DayTime(LocalDate.of(2026, 12, 3), LocalTime.of(21, 0)),
            TicketText.read("Gig\nDecember 3, 2026 21:00", today).event,
        )
    }

    @Test
    fun aYearLineIsNoAddressAndNothingIsInventedFromNothing() {
        assertEquals("2026 Tour", TicketText.read("2026 Tour\nZiggo Dome", today).name)
        val empty = TicketText.read("", today)
        assertNull(empty.name)
        assertNull(empty.event)
        assertEquals(emptyList<Detail>(), empty.details)
    }
}
