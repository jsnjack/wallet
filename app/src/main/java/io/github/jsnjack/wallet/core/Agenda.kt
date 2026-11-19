package io.github.jsnjack.wallet.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Why a card is suggested in the "Up next" row, highest priority first. */
enum class UpNextReason { FLIGHT, EVENT_TODAY, STARTS_TODAY, EXPIRES_SOON }

/** A suggested card and the reason for it. */
data class UpNext(val card: Card, val reason: UpNextReason)

/** How long archived cards are kept before they are deleted. */
enum class Retention(val days: Long?) { THREE_MONTHS(91), ONE_YEAR(365), FOREVER(null) }

/**
 * Time-based rules: what is archived, what is "Up next", which reminders are
 * due and when any of that next changes. All times are local wall-clock
 * times; [zone] converts the stored "used at" instants. [flights] gives a
 * boarding pass's latest flight lookup, so a late flight stays current
 * until it has landed.
 */
class Agenda(private val zone: ZoneId, private val flights: (Card) -> LiveFlight? = { null }) {

    /** When the card moved to the archive, or null while it is current. */
    fun archivedAt(card: Card, now: LocalDateTime): LocalDateTime? {
        card.usedAt?.let { return local(it) }
        card.expires?.end?.takeIf { !it.isAfter(now) }?.let { return it }
        if (card.kind in DATED_KINDS) datedEnd(card)?.takeIf { !it.isAfter(now) }?.let { return it }
        return null
    }

    // The day after the event; a flight delayed past midnight, or landing after it, two hours after it lands.
    private fun datedEnd(card: Card): LocalDateTime? {
        val dayAfter = card.event?.date?.plusDays(1)?.atStartOfDay() ?: return null
        if (card.kind != CardKind.BOARDING_PASS) return dayAfter
        val live = flights(card) ?: return dayAfter
        val landed = (live.arrivalAt(zone) ?: live.departureAt(zone))?.plus(AFTER_FLIGHT) ?: return dayAfter
        return maxOf(dayAfter, LocalDateTime.ofInstant(landed, zone))
    }

    /** True when the card belongs in the archive at [now]. */
    fun isArchived(card: Card, now: LocalDateTime): Boolean = archivedAt(card, now) != null

    /** Archived cards older than [retention] allows; they are deleted. */
    fun beyondRetention(cards: List<Card>, now: LocalDateTime, retention: Retention): List<Card> {
        val days = retention.days ?: return emptyList()
        val cutoff = now.minusDays(days)
        return cards.filter { card -> archivedAt(card, now)?.isBefore(cutoff) == true }
    }

    /** Current cards to suggest, most urgent first. */
    fun upNext(cards: List<Card>, now: LocalDateTime): List<UpNext> = cards
        .filterNot { isArchived(it, now) }
        .mapNotNull { card -> reason(card, now)?.let { UpNext(card, it) } }
        .sortedWith(compareBy<UpNext> { it.reason.ordinal }.thenBy { sortTime(it.card) })

    /** Suggestions that concern today, shown as an ongoing notification: travel, an event or a voucher ending today. */
    fun today(cards: List<Card>, now: LocalDateTime): List<UpNext> = upNext(cards, now).filter {
        it.reason != UpNextReason.EXPIRES_SOON || it.card.expires?.date == now.toLocalDate()
    }

    /** Cards whose expiry reminder is due: they end tomorrow and it is past the reminder hour today. */
    fun remindersDue(cards: List<Card>, now: LocalDateTime): List<Card> = cards.filter { card ->
        val expires = card.expires ?: return@filter false
        !isArchived(card, now) && card.kind !in DATED_KINDS &&
            expires.date == now.toLocalDate().plusDays(1) && !now.toLocalTime().isBefore(REMINDER_TIME)
    }

    /** The next moment after [now] when archive, suggestions or reminders change; at least the next midnight. */
    fun nextChange(cards: List<Card>, now: LocalDateTime): LocalDateTime {
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
        val moments = cards.filterNot { isArchived(it, now) }.flatMap { card ->
            buildList {
                card.expires?.let { add(it.end); add(it.date.minusDays(1).atTime(REMINDER_TIME)) }
                card.validFrom?.let { add(it.start) }
                if (card.kind == CardKind.BOARDING_PASS) card.event?.let { add(flightWindowStart(it)) }
                if (card.kind in DATED_KINDS) datedEnd(card)?.let(::add)
            }
        }
        return (moments.filter { it.isAfter(now) } + midnight).min()
    }

    private fun reason(card: Card, now: LocalDateTime): UpNextReason? {
        val today = now.toLocalDate()
        val event = card.event
        return when {
            card.kind == CardKind.BOARDING_PASS && event != null ->
                UpNextReason.FLIGHT.takeIf { !now.isBefore(flightWindowStart(event)) }
            card.kind == CardKind.TICKET && event != null && event.date == today -> UpNextReason.EVENT_TODAY
            card.validFrom?.date == today -> UpNextReason.STARTS_TODAY
            card.expires != null && card.expires.date.let { it == today || it == today.plusDays(1) } ->
                UpNextReason.EXPIRES_SOON
            else -> null
        }
    }

    // A known departure time shows the pass 24 hours ahead; a date alone shows it from the day before.
    private fun flightWindowStart(event: DayTime): LocalDateTime =
        event.time?.let { event.start.minusHours(FLIGHT_LEAD_HOURS) } ?: event.date.minusDays(1).atStartOfDay()

    private fun sortTime(card: Card): LocalDateTime = (card.event ?: card.expires)?.start ?: LocalDateTime.MAX

    private fun local(epochMillis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone)

    companion object {
        /** Kinds that belong to one date and move to the archive the day after it. */
        val DATED_KINDS = setOf(CardKind.TICKET, CardKind.BOARDING_PASS)

        /** Expiry reminders are posted from this time on the day before. */
        val REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

        private const val FLIGHT_LEAD_HOURS = 24L
        private val AFTER_FLIGHT: Duration = Duration.ofHours(2)
    }
}
