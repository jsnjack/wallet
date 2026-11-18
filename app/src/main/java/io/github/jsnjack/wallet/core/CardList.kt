package io.github.jsnjack.wallet.core

/** Ordering and filtering of the card grid. */
object CardList {
    private const val MIN_SEARCH_DIGITS = 2

    /** Grid order: cards kept at the top first, then most used, most recently used, then by name. */
    fun sorted(cards: List<Card>, usage: Map<String, Usage>): List<Card> = cards.sortedWith(
        compareByDescending<Card> { it.pinned }
            .thenByDescending { usage[it.id]?.count ?: 0 }
            .thenByDescending { usage[it.id]?.lastUsedAt ?: 0L }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
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

    /** The [limit] most used cards for launcher shortcuts; cards never opened are left out. */
    fun mostUsed(cards: List<Card>, usage: Map<String, Usage>, limit: Int = 3): List<Card> =
        cards.filter { (usage[it.id]?.count ?: 0) > 0 }
            .sortedWith(
                compareByDescending<Card> { usage[it.id]?.count ?: 0 }
                    .thenByDescending { usage[it.id]?.lastUsedAt ?: 0L },
            )
            .take(limit)
}
