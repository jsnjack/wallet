package io.github.jsnjack.wallet.store

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import io.github.jsnjack.wallet.core.Card
import io.github.jsnjack.wallet.core.CardVersions
import io.github.jsnjack.wallet.core.Usage

/** All cards, held in memory and written through to SQLite. */
class CardStore(context: Context) {
    private val db = CardDatabase(context)
    private val mutex = Mutex()
    private val all = mutableMapOf<String, Card>()

    private val _cards = MutableStateFlow<List<Card>>(emptyList())

    /** Cards that are not deleted, in storage order. */
    val cards: StateFlow<List<Card>> = _cards.asStateFlow()

    private val _usage = MutableStateFlow<Map<String, Usage>>(emptyMap())

    /** How often each card was shown on this device. */
    val usage: StateFlow<Map<String, Usage>> = _usage.asStateFlow()

    private var loaded = false

    /** Reads the database once; later calls return at once. */
    suspend fun load() = mutex.withLock {
        if (loaded) return@withLock
        val (stored, usage) = withContext(Dispatchers.IO) { db.readCards() to db.readUsage() }
        stored.forEach { all[it.id] = it }
        _usage.value = usage
        loaded = true
        publish()
    }

    /** The card with [id], unless it is deleted. */
    fun get(id: String): Card? = _cards.value.firstOrNull { it.id == id }

    /** Saves a card the user added or changed; its "last changed" time moves forward. */
    suspend fun save(card: Card): Card = mutex.withLock {
        val stamped = CardVersions.saved(card, all[card.id])
        write(listOf(stamped))
        stamped
    }

    /**
     * Changes the stored card [id] under the lock, so a delete or an edit made
     * since a copy was taken is not overwritten. [change] returns null to
     * leave the card alone. Returns the saved card, or null when the card is
     * deleted or unchanged.
     */
    suspend fun update(id: String, now: Long, change: (Card) -> Card?): Card? = mutex.withLock {
        val current = all[id]?.takeUnless { it.deleted } ?: return@withLock null
        val changed = change(current)?.takeIf { it != current } ?: return@withLock null
        val stamped = CardVersions.changed(changed, current, now)
        write(listOf(stamped))
        stamped
    }

    /** Replaces the card with a tombstone and returns the card as it was, for undo. */
    suspend fun delete(id: String, now: Long): Card? = mutex.withLock {
        val previous = all[id]?.takeUnless { it.deleted } ?: return@withLock null
        write(listOf(CardVersions.deleted(previous, now)))
        withContext(Dispatchers.IO) { db.deleteUsage(id) }
        _usage.value = _usage.value - id
        previous
    }

    /**
     * Replaces several cards with tombstones without undo, for archive
     * clean-up. Returns how many were deleted.
     */
    suspend fun deleteAll(ids: Collection<String>, now: Long): Int = mutex.withLock {
        val current = ids.mapNotNull { all[it]?.takeUnless { card -> card.deleted } }
        if (current.isEmpty()) return@withLock 0
        write(current.map { CardVersions.deleted(it, now) })
        withContext(Dispatchers.IO) { current.forEach { db.deleteUsage(it.id) } }
        _usage.value = _usage.value - current.map { it.id }.toSet()
        current.size
    }

    /** Counts one showing of the card. */
    suspend fun recordUse(id: String, now: Long) = mutex.withLock {
        val usage = _usage.value[id]?.let { it.copy(count = it.count + 1, lastUsedAt = now) } ?: Usage(1, now)
        withContext(Dispatchers.IO) { db.writeUsage(id, usage) }
        _usage.value = _usage.value + (id to usage)
    }

    /** Every card including deleted ones, for export. */
    suspend fun snapshot(): List<Card> = mutex.withLock { all.values.toList() }

    /**
     * Stores cards from an import where they are newer than the local copy,
     * keeping their "last changed" time. Returns how many were taken.
     */
    suspend fun applyIncoming(incoming: List<Card>): Int = mutex.withLock {
        val newer = CardVersions.newer(incoming) { all[it] }
        if (newer.isNotEmpty()) write(newer)
        newer.size
    }

    private suspend fun write(cards: List<Card>) {
        withContext(Dispatchers.IO) { db.writeCards(cards) }
        cards.forEach { all[it.id] = it }
        publish()
    }

    private fun publish() {
        _cards.value = all.values.filterNot { it.deleted }
    }
}
