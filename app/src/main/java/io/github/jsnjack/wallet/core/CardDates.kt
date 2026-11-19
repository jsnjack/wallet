package io.github.jsnjack.wallet.core

import java.time.LocalDate
import java.time.LocalDateTime

/** Which of a card's dates a chip is about. */
enum class DateKind { EVENT, STARTS, ENDS }

/** How a chip looks: [NOW] an event today, [WARN] ending within a week, [WAIT] not valid yet, [PLAIN] the rest. */
enum class DateTone { NOW, WARN, WAIT, PLAIN }

/** How near a date is, which decides its wording: "Today", "Tomorrow", a weekday, or a date with or without the year. */
enum class Nearness { TODAY, TOMORROW, WEEKDAY, DATE, DATE_WITH_YEAR }

/** The date a card's chip shows. */
data class CardDate(val kind: DateKind, val at: DayTime, val tone: DateTone)

/**
 * The dates that put a card in "Coming up" at the top of the grid, and the
 * one its chip shows: an event from today on, a start date still ahead, or
 * an end date within [HORIZON_DAYS].
 */
object CardDates {
    /** End and start dates further away than this are not news. */
    const val HORIZON_DAYS = 30L

    private const val WARN_DAYS = 7L
    private const val WEEKDAY_DAYS = 6L

    /** The date that matters next for [card] at [now], or null when none is worth a chip. */
    fun next(card: Card, now: LocalDateTime): CardDate? {
        val today = now.toLocalDate()
        val starts = card.validFrom?.takeIf { it.start.isAfter(now) }?.let { CardDate(DateKind.STARTS, it, DateTone.WAIT) }
        val event = card.event?.takeIf { card.kind in Agenda.DATED_KINDS && !it.date.isBefore(today) }
            ?.let { CardDate(DateKind.EVENT, it, if (it.date == today) DateTone.NOW else DateTone.PLAIN) }
        val ends = card.expires?.takeIf { it.end.isAfter(now) && !it.date.isAfter(today.plusDays(HORIZON_DAYS)) }
            ?.let { CardDate(DateKind.ENDS, it, if (it.date.isAfter(today.plusDays(WARN_DAYS))) DateTone.PLAIN else DateTone.WARN) }
        return listOfNotNull(starts, event, ends).minByOrNull { it.at.start }
    }

    /**
     * The cards for "Coming up", soonest first: those with an event ahead, or
     * a start or end date within [HORIZON_DAYS]. A start date further away
     * still gets its chip on the card's own tile.
     */
    fun comingUp(cards: List<Card>, now: LocalDateTime): List<Card> {
        val horizon = now.toLocalDate().plusDays(HORIZON_DAYS)
        return cards.mapNotNull { card -> next(card, now)?.let { card to it } }
            .filter { (_, date) -> date.kind == DateKind.EVENT || !date.at.date.isAfter(horizon) }
            .sortedWith(compareBy<Pair<Card, CardDate>> { it.second.at.start }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.first.name })
            .map { it.first }
    }

    /** How near [date] is to [today]. */
    fun nearness(date: LocalDate, today: LocalDate): Nearness = when {
        date == today -> Nearness.TODAY
        date == today.plusDays(1) -> Nearness.TOMORROW
        date.isAfter(today) && !date.isAfter(today.plusDays(WEEKDAY_DAYS)) -> Nearness.WEEKDAY
        date.year == today.year -> Nearness.DATE
        else -> Nearness.DATE_WITH_YEAR
    }
}
