package io.github.jsnjack.wallet.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * A date with an optional time of day, as printed on a ticket or voucher.
 * It has no time zone: a flight's "07:05" is the time at its airport.
 */
data class DayTime(val date: LocalDate, val time: LocalTime? = null) : Comparable<DayTime> {

    /** The moment it starts: the time, or the start of the day. */
    val start: LocalDateTime get() = date.atTime(time ?: LocalTime.MIDNIGHT)

    /** The moment it ends: the time, or the start of the next day. */
    val end: LocalDateTime get() = time?.let { date.atTime(it) } ?: date.plusDays(1).atStartOfDay()

    override fun compareTo(other: DayTime): Int = start.compareTo(other.start)

    /** `2026-11-20` or `2026-11-20T07:05`, the stored form. */
    override fun toString(): String = time?.let { "${date}T${it.truncatedTo(ChronoUnit.MINUTES)}" } ?: date.toString()

    companion object {
        /** Parses the stored form; null for anything else. */
        fun parse(text: String?): DayTime? {
            if (text.isNullOrBlank()) return null
            return try {
                if ('T' in text) {
                    val dt = LocalDateTime.parse(text)
                    DayTime(dt.toLocalDate(), dt.toLocalTime().truncatedTo(ChronoUnit.MINUTES))
                } else {
                    DayTime(LocalDate.parse(text))
                }
            } catch (e: DateTimeParseException) {
                null
            }
        }
    }
}
