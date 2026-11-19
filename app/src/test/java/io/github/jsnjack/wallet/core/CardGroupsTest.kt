package io.github.jsnjack.wallet.core

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class CardGroupsTest {
    private val day = LocalDate.of(2026, 11, 20)

    private fun card(
        id: String,
        kind: CardKind = CardKind.LOYALTY,
        name: String = id,
        event: DayTime? = DayTime(day),
        flight: Flight? = null,
        createdAt: Long = 0,
    ) = Card(
        id = id, name = name, code = "1", format = CodeFormat.QR, kind = kind, color = 0, note = "", pinned = false,
        locked = false, createdAt = createdAt, updatedAt = 0, event = event, flight = flight,
    )

    private fun pass(id: String, passenger: String, pnr: String, createdAt: Long, number: String = "1691", date: LocalDate = day) =
        card(id, CardKind.BOARDING_PASS, event = DayTime(date), flight = Flight("AMS", "BCN", "KL", number, passenger, pnr), createdAt = createdAt)

    @Test
    fun passengersOnOneFlightGroupWhateverTheBooking() {
        val mine = pass("mine", "DOE/JOHN", "ABC123", createdAt = 1)
        val hers = pass("hers", "ROE/JANE", "XYZ789", createdAt = 2)
        val loyalty = card("loyalty")
        assertEquals(listOf("loyalty", "mine"), CardGroups.collapse(listOf(loyalty, hers, mine)).map { it.id })
        assertEquals(listOf("mine", "hers"), CardGroups.members(hers, listOf(hers, loyalty, mine)).map { it.id })
        assertEquals(mapOf("mine" to 2), CardGroups.sizes(listOf(hers, loyalty, mine)))
    }

    @Test
    fun otherFlightsOfOneBookingStayApart() {
        val out = pass("out", "DOE/JOHN", "ABC123", createdAt = 1)
        val back = pass("back", "DOE/JOHN", "ABC123", createdAt = 1, number = "1692")
        val nextWeek = pass("next", "DOE/JOHN", "DEF456", createdAt = 1, date = day.plusDays(7))
        val all = listOf(out, back, nextWeek)
        assertEquals(listOf("out", "back", "next"), CardGroups.collapse(all).map { it.id })
        assertEquals(listOf("back"), CardGroups.members(back, all).map { it.id })
        assertEquals(emptyMap<String, Int>(), CardGroups.sizes(all))
    }

    @Test
    fun ticketsForOneShowGroupByNameAndTime() {
        val eight = DayTime(day, LocalTime.of(20, 0))
        val first = card("a", CardKind.TICKET, name = "Concert", event = eight, createdAt = 1)
        val second = card("b", CardKind.TICKET, name = " concert ", event = eight, createdAt = 2)
        val matinee = card("c", CardKind.TICKET, name = "Concert", event = DayTime(day, LocalTime.of(14, 0)))
        val all = listOf(second, matinee, first)
        assertEquals(listOf("c", "a"), CardGroups.collapse(all).map { it.id })
        assertEquals(listOf("a", "b"), CardGroups.members(second, all).map { it.id })
    }

    @Test
    fun cardsWithoutAGroupStandAlone() {
        val voucherA = card("a", CardKind.VOUCHER, name = "Same")
        val voucherB = card("b", CardKind.VOUCHER, name = "Same")
        val undated = card("t", CardKind.TICKET, name = "Same", event = null)
        val all = listOf(voucherA, voucherB, undated)
        assertEquals(all, CardGroups.collapse(all))
        assertEquals(listOf(undated), CardGroups.members(undated, all + card("u", CardKind.TICKET, name = "Same", event = null)))
    }
}
