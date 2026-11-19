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
            listOf(
                Detail("Doors open", "19:30"),
                Detail("Zaal", "Grote Zaal"),
                Detail("Venue", "TivoliVredenburg"),
                Detail("Address", "Vredenburgkade 11, 3511 WC Utrecht"),
            ),
            facts.details,
        )
    }

    // The text as stored in the real PDF: columns interleave, so the buyer's name shares a line with the postcode.
    private val storedOrder = """
        SYML & Metropole Orkest
        Date & time: Thursday 3 December 2026 at 20:30
        Doors open: 19:30
        Customer number:
        12345678
        Ticket price:
        € 51,00
        Price type:
        Regulier
        TivoliVredenburg
        Vredenburgkade 11
        Customer name:
        Jane Doe 3511 WC Utrecht
        Organisatie: Stichting TivoliVredenburg
        Zaal: Grote Zaal
        The General Terms and Conditions of TICKETMASTER (please check: Ticketmaster.nl)
        and of the organiser of this event apply to the agreement pertaining to this admission
        • Only the first holder who can show an admission ticket at the entrance to the
        10000000 ABC---100 00 00 X0000ABC00000 0000000 00
        *%TST/1234*
    """.trimIndent()

    @Test
    fun readsTheStoredOrderOfARealTicketWithoutTheBuyer() {
        val facts = TicketText.read(storedOrder, today, code = "%TST/1234")
        assertEquals("SYML & Metropole Orkest", facts.name)
        assertEquals(DayTime(LocalDate.of(2026, 12, 3), LocalTime.of(20, 30)), facts.event)
        assertEquals(Detail("Address", "Vredenburgkade 11, 3511 WC Utrecht"), facts.details.last())
        assertEquals(Detail("Venue", "TivoliVredenburg"), facts.details[facts.details.size - 2])
        assert(facts.details.none { it.value.contains("Jane") }) { "took the buyer's name" }
    }

    @Test
    fun termsAreNoName() {
        val terms = storedOrder.lines().drop(15).joinToString("\n")
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
        listOf("Jane", "12345678", "51,00", "Regulier", "Stichting").forEach { assert(!text.contains(it)) { "took $it" } }
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
    fun voucherValidityDatesAreNotTheEvent() {
        val facts = TicketText.read("Kerstbon\nGeldig vanaf: 1-12-2026\nGeldig t/m: 31-12-2026 23:59", today)
        assertNull(facts.event)
        assertEquals(DayTime(LocalDate.of(2026, 12, 1)), facts.validFrom)
        assertEquals(DayTime(LocalDate.of(2026, 12, 31), LocalTime.of(23, 59)), facts.expires)
        val draft = CardDraft(code = "1", format = CodeFormat.QR, formatChosen = true).withTicket(facts)
        assertEquals(CardKind.VOUCHER, draft.kind)
        assertEquals("Kerstbon", draft.name)
    }

    @Test
    fun aYearLineIsNoAddressAndNothingIsInventedFromNothing() {
        assertEquals("2026 Tour", TicketText.read("2026 Tour\nZiggo Dome", today).name)
        val empty = TicketText.read("", today)
        assertNull(empty.name)
        assertNull(empty.event)
        assertEquals(emptyList<Detail>(), empty.details)
    }

    @Test
    fun aTimeOnTheLineAfterALabelIsTheLabelsValue() {
        val ordered = TicketText.read("Besteld op:\n19-11-2026 14:32\nZaterdag 3 december 2026 20:00", today)
        assertEquals(DayTime(LocalDate.of(2026, 12, 3), LocalTime.of(20, 0)), ordered.event)
        val doors = TicketText.read("Zaterdag 3 december 2026 20:00\nDoors open:\n19:30", today)
        assertEquals(listOf(Detail("Doors open", "19:30")), doors.details.filter { it.label == "Doors open" })
    }
}
