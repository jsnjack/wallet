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

class AgendaTest {
    private val agenda = Agenda(ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 11, 19)
    private val now = today.atTime(10, 0)

    private fun card(
        id: String,
        kind: CardKind = CardKind.LOYALTY,
        expires: DayTime? = null,
        event: DayTime? = null,
        usedAt: Long? = null,
        flight: Flight? = null,
    ) = Card(
        id = id, name = id, code = "1", format = CodeFormat.QR, kind = kind, color = 0, note = "", pinned = false,
        locked = false, createdAt = 0, updatedAt = 0, expires = expires, event = event, usedAt = usedAt, flight = flight,
    )

    @Test
    fun usedExpiredAndPastCardsAreArchived() {
        assertTrue(agenda.isArchived(card("used", usedAt = 0), now))
        assertTrue(agenda.isArchived(card("expired", expires = DayTime(today.minusDays(1))), now))
        assertFalse(agenda.isArchived(card("ends today", expires = DayTime(today)), now))
        assertTrue(agenda.isArchived(card("ticket", CardKind.TICKET, event = DayTime(today.minusDays(1))), now))
        assertFalse(agenda.isArchived(card("ticket today", CardKind.TICKET, event = DayTime(today, LocalTime.of(8, 0))), now))
        assertFalse(agenda.isArchived(card("loyalty with old event", event = DayTime(today.minusDays(9))), now))
    }

    @Test
    fun archivedAtIsWhenItEnded() {
        assertEquals(today.atStartOfDay(), agenda.archivedAt(card("ticket", CardKind.TICKET, event = DayTime(today.minusDays(1))), now))
        assertNull(agenda.archivedAt(card("current"), now))
    }

    @Test
    fun upNextOrdersFlightThenEventThenExpiry() {
        val cards = listOf(
            card("voucher", CardKind.VOUCHER, expires = DayTime(today.plusDays(1))),
            card("concert", CardKind.TICKET, event = DayTime(today, LocalTime.of(20, 0))),
            card("flight", CardKind.BOARDING_PASS, event = DayTime(today.plusDays(1))),
            card("later", CardKind.VOUCHER, expires = DayTime(today.plusDays(5))),
        )
        assertEquals(listOf("flight", "concert", "voucher"), agenda.upNext(cards, now).map { it.card.id })
    }

    @Test
    fun flightWithTimeShowsFrom24HoursBefore() {
        val flight = card("f", CardKind.BOARDING_PASS, event = DayTime(today.plusDays(1), LocalTime.of(11, 0)))
        assertTrue(agenda.upNext(listOf(flight), now).isEmpty())
        assertEquals(1, agenda.upNext(listOf(flight), today.atTime(11, 0)).size)
    }

    @Test
    fun todayLeavesOutVouchersEndingTomorrow() {
        val cards = listOf(
            card("tomorrow", CardKind.VOUCHER, expires = DayTime(today.plusDays(1))),
            card("tonight", CardKind.VOUCHER, expires = DayTime(today, LocalTime.of(21, 0))),
        )
        assertEquals(listOf("tonight"), agenda.today(cards, now).map { it.card.id })
    }

    @Test
    fun remindersAreDueFromNineTheDayBefore() {
        val cards = listOf(card("v", CardKind.VOUCHER, expires = DayTime(today.plusDays(1))))
        assertTrue(agenda.remindersDue(cards, today.atTime(8, 59)).isEmpty())
        assertEquals(1, agenda.remindersDue(cards, today.atTime(9, 0)).size)
    }

    @Test
    fun retentionDeletesOnlyOldArchivedCards() {
        val old = card("old", usedAt = LocalDateTime.of(2025, 1, 1, 0, 0).toEpochSecond(ZoneOffset.UTC) * 1000)
        val recent = card("recent", usedAt = now.minusDays(3).toEpochSecond(ZoneOffset.UTC) * 1000)
        val current = card("current")
        assertEquals(listOf("old"), agenda.beyondRetention(listOf(old, recent, current), now, Retention.ONE_YEAR).map { it.id })
        assertTrue(agenda.beyondRetention(listOf(old), now, Retention.FOREVER).isEmpty())
    }

    @Test
    fun nextChangeIsTheNearestMomentOrMidnight() {
        val voucher = card("v", CardKind.VOUCHER, expires = DayTime(today, LocalTime.of(21, 0)))
        assertEquals(today.atTime(21, 0), agenda.nextChange(listOf(voucher), now))
        assertEquals(today.plusDays(1).atStartOfDay(), agenda.nextChange(emptyList(), now))
    }
}
