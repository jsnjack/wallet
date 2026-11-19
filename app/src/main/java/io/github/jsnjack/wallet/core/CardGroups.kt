package io.github.jsnjack.wallet.core

import java.time.LocalDate

/**
 * Cards that belong together: boarding passes for one flight (one per
 * passenger, whatever the booking) and tickets for one show. The grid shows
 * one tile per group, and the code screen swipes only within a group.
 */
object CardGroups {
    /** One card per group, the one added first; cards outside a group pass through in their place. */
    fun collapse(cards: List<Card>): List<Card> {
        val shown = firstOfEach(cards)
        return cards.filter { card -> key(card)?.let { shown[it] == card.id } ?: true }
    }

    /** Every card in [card]'s group in the order they were added, or just [card] when it has no group. */
    fun members(card: Card, cards: List<Card>): List<Card> {
        val key = key(card) ?: return listOf(card)
        return cards.filter { key(it) == key }.sortedWith(ORDER)
    }

    /** How many cards each group of two or more holds, by the id of the card [collapse] shows for it. */
    fun sizes(cards: List<Card>): Map<String, Int> = cards.groupBy(::key)
        .filter { (key, members) -> key != null && members.size > 1 }
        .values.associate { members -> members.minWith(ORDER).id to members.size }

    private fun firstOfEach(cards: List<Card>): Map<Key, String> = cards
        .mapNotNull { card -> key(card)?.let { it to card } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, members) -> members.minWith(ORDER).id }

    private fun key(card: Card): Key? {
        val event = card.event ?: return null
        return when (card.kind) {
            CardKind.BOARDING_PASS -> card.flight?.takeIf { it.number.isNotBlank() }?.let {
                Key.Flight(it.carrier.uppercase(), it.number, it.from.uppercase(), event.date)
            }
            CardKind.TICKET -> card.name.trim().lowercase().takeIf { it.isNotEmpty() }?.let { Key.Show(it, event) }
            else -> null
        }
    }

    private sealed interface Key {
        data class Flight(val carrier: String, val number: String, val from: String, val date: LocalDate) : Key

        data class Show(val name: String, val event: DayTime) : Key
    }

    private val ORDER = compareBy<Card>({ it.createdAt }, { it.id })
}
