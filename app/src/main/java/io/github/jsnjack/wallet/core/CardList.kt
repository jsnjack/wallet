package io.github.jsnjack.wallet.core

/** How the grid orders cards, chosen in Settings. */
enum class CardOrder {
    /** Alphabetical by name; favourites keep their place, so cards never move. */
    NAME,

    /** Favourites first, then the most used and most recently used on this phone. */
    MOST_USED,
}

/** Ordering and filtering of the card grid. */
object CardList {
    private const val MIN_SEARCH_DIGITS = 2

    private val byName = compareBy<Card, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id }

    /** The grid's cards in [order]; [usage] decides [CardOrder.MOST_USED]. */
    fun sorted(cards: List<Card>, usage: Map<String, Usage>, order: CardOrder): List<Card> = when (order) {
        CardOrder.NAME -> cards.sortedWith(byName)
        CardOrder.MOST_USED -> byUse(cards, usage)
    }

    // Favourites first, then most used, most recently used, then by name.
    private fun byUse(cards: List<Card>, usage: Map<String, Usage>): List<Card> = cards.sortedWith(
        compareByDescending<Card> { it.pinned }
            .thenByDescending { usage[it.id]?.count ?: 0 }
            .thenByDescending { usage[it.id]?.lastUsedAt ?: 0L }
            .then(byName),
    )

    /**
     * True when [query] appears in the card's name or note, or when a query of
     * digits appears in the card number, whatever spaces either one has.
     */
    fun matches(card: Card, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        if (card.name.contains(q, ignoreCase = true) || card.note.contains(q, ignoreCase = true)) return true
        val compact = q.filterNot(Char::isWhitespace)
        return compact.length >= MIN_SEARCH_DIGITS && compact.all(Char::isDigit) &&
            card.code.filterNot(Char::isWhitespace).contains(compact)
    }

    /** Favourites in grid order (most used first), for the favourites rail. */
    fun favourites(cards: List<Card>, usage: Map<String, Usage>): List<Card> = byUse(cards.filter { it.pinned }, usage)

    /**
     * Cards for surfaces outside the app (shortcuts, widget, tile): favourites
     * first, then the most used other cards, so they are never empty.
     */
    fun quickAccess(cards: List<Card>, usage: Map<String, Usage>, limit: Int): List<Card> =
        (favourites(cards, usage) + mostUsed(cards.filterNot { it.pinned }, usage, limit)).take(limit)

    /** The [limit] most used cards; cards never opened are left out. */
    fun mostUsed(cards: List<Card>, usage: Map<String, Usage>, limit: Int = 3): List<Card> =
        cards.filter { (usage[it.id]?.count ?: 0) > 0 }
            .sortedWith(
                compareByDescending<Card> { usage[it.id]?.count ?: 0 }
                    .thenByDescending { usage[it.id]?.lastUsedAt ?: 0L },
            )
            .take(limit)
}
