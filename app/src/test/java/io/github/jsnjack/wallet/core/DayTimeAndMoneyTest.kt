package io.github.jsnjack.wallet.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayTimeAndMoneyTest {
    @Test
    fun dayTimeStoredFormRoundTrips() {
        val dated = DayTime(LocalDate.of(2026, 11, 20))
        val timed = DayTime(LocalDate.of(2026, 11, 20), LocalTime.of(7, 5))
        assertEquals("2026-11-20", dated.toString())
        assertEquals("2026-11-20T07:05", timed.toString())
        assertEquals(dated, DayTime.parse(dated.toString()))
        assertEquals(timed, DayTime.parse(timed.toString()))
        assertNull(DayTime.parse("tomorrow"))
        assertNull(DayTime.parse(null))
    }

    @Test
    fun dateWithoutTimeLastsTheWholeDay() {
        val day = DayTime(LocalDate.of(2026, 11, 20))
        assertEquals(LocalDateTime.of(2026, 11, 20, 0, 0), day.start)
        assertEquals(LocalDateTime.of(2026, 11, 21, 0, 0), day.end)
        assertTrue(day < DayTime(LocalDate.of(2026, 11, 20), LocalTime.of(9, 0)))
    }

    @Test
    fun parsesTypedAmounts() {
        assertEquals(2500L, Money.parseCents("25"))
        assertEquals(2550L, Money.parseCents("25,5"))
        assertEquals(2505L, Money.parseCents(" € 25.05 "))
        assertNull(Money.parseCents("25,555"))
        assertNull(Money.parseCents("1.234,56"))
        assertNull(Money.parseCents("abc"))
    }

    @Test
    fun formatsForTypingAndDisplay() {
        val dutch = Locale.forLanguageTag("nl-NL")
        assertEquals("25,50", Money.editable(2550, dutch))
        assertTrue(Money.display(2550, dutch).contains("25,50"))
        assertTrue(Money.display(2550, dutch).contains("€"))
    }

    @Test
    fun readsBackBalancesWrittenWithArabicDigits() {
        val arabic = Locale.forLanguageTag("ar-EG")
        assertEquals(2550L, Money.parseCents(Money.editable(2550, arabic)))
        assertEquals(2550L, Money.parseCents("٢٥٫٥٠"))
    }
}
