package io.github.jsnjack.wallet.core

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardDatesTest {
    private val today = LocalDate.of(2026, 11, 19)
    private val now = today.atTime(12, 0)

    private fun card(
        id: String,
        kind: CardKind = CardKind.VOUCHER,
        event: DayTime? = null,
        expires: DayTime? = null,
        validFrom: DayTime? = null,
    ) = Card(
        id = id, name = id, code = "1", format = CodeFormat.QR, kind = kind, color = 0, note = "", pinned = false,
        locked = false, createdAt = 0, updatedAt = 0, event = event, expires = expires, validFrom = validFrom,
    )

    @Test
    fun eventsTodayAreNowAndLaterOnesPlain() {
        val tonight = DayTime(today, LocalTime.of(20, 30))
        assertEquals(CardDate(DateKind.EVENT, tonight, DateTone.NOW), CardDates.next(card("t", CardKind.TICKET, event = tonight), now))
        assertEquals(DateTone.PLAIN, CardDates.next(card("t", CardKind.TICKET, event = DayTime(today.plusDays(3))), now)?.tone)
        assertNull(CardDates.next(card("t", CardKind.TICKET, event = DayTime(today.minusDays(1))), now))
    }

    @Test
    fun endsWarnWithinAWeekAndVanishAfterThirtyDays() {
        assertEquals(DateTone.WARN, CardDates.next(card("v", expires = DayTime(today.plusDays(4))), now)?.tone)
        assertEquals(DateTone.PLAIN, CardDates.next(card("v", expires = DayTime(today.plusDays(20))), now)?.tone)
        assertNull(CardDates.next(card("v", expires = DayTime(today.plusDays(40))), now))
        assertNull(CardDates.next(card("loyalty", CardKind.LOYALTY), now))
    }

    @Test
    fun aStartAheadComesBeforeTheEnd() {
        val voucher = card("v", validFrom = DayTime(today.plusDays(12)), expires = DayTime(today.plusDays(26)))
        assertEquals(CardDate(DateKind.STARTS, DayTime(today.plusDays(12)), DateTone.WAIT), CardDates.next(voucher, now))
        assertEquals(DateKind.ENDS, CardDates.next(voucher, today.plusDays(13).atStartOfDay())?.kind)
    }

    @Test
    fun comingUpHoldsEventsAndNearDatesSoonestFirst() {
        val concert = card("concert", CardKind.TICKET, event = DayTime(today.plusDays(200)))
        val ending = card("ending", expires = DayTime(today.plusDays(20)))
        val starting = card("starting", validFrom = DayTime(today.plusDays(5)))
        val farStart = card("far", validFrom = DayTime(today.plusDays(60)))
        val farEnd = card("membership", CardKind.MEMBERSHIP, expires = DayTime(today.plusDays(300)))
        val list = CardDates.comingUp(listOf(concert, farStart, ending, farEnd, starting), now)
        assertEquals(listOf("starting", "ending", "concert"), list.map { it.id })
        assertEquals(DateKind.STARTS, CardDates.next(farStart, now)?.kind)
    }

    @Test
    fun nearnessPicksTheWording() {
        assertEquals(Nearness.TODAY, CardDates.nearness(today, today))
        assertEquals(Nearness.TOMORROW, CardDates.nearness(today.plusDays(1), today))
        assertEquals(Nearness.WEEKDAY, CardDates.nearness(today.plusDays(6), today))
        assertEquals(Nearness.DATE, CardDates.nearness(today.plusDays(7), today))
        assertEquals(Nearness.DATE_WITH_YEAR, CardDates.nearness(LocalDate.of(2027, 1, 5), today))
    }
}
