package io.github.jsnjack.wallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardListTest {
    private fun card(id: String, name: String, pinned: Boolean = false, code: String = "123", note: String = "") = Card(
        id = id, name = name, code = code, format = CodeFormat.CODE_128, kind = CardKind.LOYALTY,
        color = 0, note = note, pinned = pinned, locked = false, createdAt = 0, updatedAt = 0,
    )

    @Test
    fun mostUsedSortsPinnedThenUsageThenName() {
        val cards = listOf(card("a", "Zeta"), card("b", "Alpha"), card("c", "Mid", pinned = true), card("d", "Beta"))
        val usage = mapOf("a" to Usage(5, 10), "d" to Usage(5, 20))
        assertEquals(listOf("c", "d", "a", "b"), CardList.sorted(cards, usage, CardOrder.MOST_USED).map { it.id })
    }

    @Test
    fun nameOrderIgnoresCaseUsageAndFavourites() {
        val cards = listOf(card("a", "zeta"), card("b", "Alpha"), card("c", "Mid", pinned = true), card("d", "beta"))
        val usage = mapOf("a" to Usage(9, 10))
        assertEquals(listOf("b", "d", "c", "a"), CardList.sorted(cards, usage, CardOrder.NAME).map { it.id })
    }

    @Test
    fun matchesNameAndNoteIgnoringCase() {
        assertTrue(CardList.matches(card("a", "Kruidvat"), "kruid"))
        assertTrue(CardList.matches(card("a", "Gym", note = "Shared with family"), "family"))
        assertFalse(CardList.matches(card("a", "Gym"), "pool"))
    }

    @Test
    fun matchesDigitsInsideTheNumber() {
        val c = card("a", "AH", code = "2620048511734")
        assertTrue(CardList.matches(c, "4851"))
        assertTrue(CardList.matches(c, "0485 1173"))
        assertFalse(CardList.matches(c, "9"))
    }

    @Test
    fun emptyQueryMatchesEverything() {
        assertTrue(CardList.matches(card("a", "Any"), "  "))
    }

    @Test
    fun mostUsedSkipsUnusedAndLimits() {
        val cards = listOf(card("a", "A"), card("b", "B"), card("c", "C"), card("d", "D"), card("e", "E"))
        val usage = mapOf("a" to Usage(1, 1), "b" to Usage(9, 1), "c" to Usage(3, 1), "d" to Usage(3, 5))
        assertEquals(listOf("b", "d", "c"), CardList.mostUsed(cards, usage).map { it.id })
    }

    @Test
    fun favouritesAreTheStarredCardsAToZWhateverTheirUse() {
        val cards = listOf(card("c", "C", pinned = true), card("b", "B"), card("a", "A", pinned = true))
        assertEquals(listOf("a", "c"), CardList.favourites(cards).map { it.id })
    }

    @Test
    fun quickAccessPutsFavouritesFirstAndFillsWithMostUsed() {
        val cards = listOf(card("fav", "F", pinned = true), card("busy", "B"), card("quiet", "Q"), card("never", "N"))
        val usage = mapOf("busy" to Usage(9, 1), "quiet" to Usage(1, 1))
        assertEquals(listOf("fav", "busy", "quiet"), CardList.quickAccess(cards, usage, 3).map { it.id })
        assertEquals(listOf("fav"), CardList.quickAccess(cards, usage, 1).map { it.id })
    }
}
