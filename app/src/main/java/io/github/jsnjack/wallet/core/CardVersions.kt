package io.github.jsnjack.wallet.core

/**
 * How a card's "last changed" time moves. Every change makes the stored
 * copy newer than the one before, even when the phone's clock is behind,
 * so an import takes only newer copies and an older copy never undoes a
 * delete, an undo or an edit.
 */
object CardVersions {
    /** [card] as saved over [previous], the stored copy: no older than the card says, and newer than the stored copy. */
    fun saved(card: Card, previous: Card?): Card = card.copy(updatedAt = maxOf(card.updatedAt, (previous?.updatedAt ?: 0L) + 1))

    /** [changed] replacing [current] at [now]. */
    fun changed(changed: Card, current: Card, now: Long): Card = changed.copy(updatedAt = maxOf(now, current.updatedAt + 1))

    /** [card] deleted at [now]: a blank tombstone newer than the card. */
    fun deleted(card: Card, now: Long): Card = card.tombstone(maxOf(now, card.updatedAt + 1))

    /** The cards in [incoming] that are new here or newer than the copy [local] holds, tombstones included. */
    fun newer(incoming: List<Card>, local: (String) -> Card?): List<Card> =
        incoming.filter { card -> local(card.id)?.let { card.updatedAt > it.updatedAt } ?: true }
}
