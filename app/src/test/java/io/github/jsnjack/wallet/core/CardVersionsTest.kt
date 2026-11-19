package io.github.jsnjack.wallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardVersionsTest {
    private val card = Card(
        id = "ah", name = "Albert Heijn", code = "2621951356855", format = CodeFormat.EAN_13, kind = CardKind.LOYALTY,
        color = 0, note = "", pinned = false, locked = false, createdAt = 0, updatedAt = 100,
    )

    @Test
    fun undoAfterADeleteWinsEvenWithTheClockBehind() {
        // The phone's clock says 50, earlier than the card's last change.
        val tombstone = CardVersions.deleted(card, now = 50)
        assertTrue(tombstone.deleted)
        assertTrue(tombstone.updatedAt > card.updatedAt)
        val restored = CardVersions.saved(card.copy(updatedAt = 60), previous = tombstone)
        assertTrue(restored.updatedAt > tombstone.updatedAt)
        assertFalse(restored.deleted)
    }

    @Test
    fun aChangeIsAlwaysNewerThanTheStoredCopy() {
        assertEquals(101L, CardVersions.changed(card.copy(name = "AH"), card, now = 50).updatedAt)
        assertEquals(500L, CardVersions.changed(card.copy(name = "AH"), card, now = 500).updatedAt)
    }

    @Test
    fun anImportTakesOnlyNewCardsAndNewerCopies() {
        val local = mapOf(card.id to card)
        val older = card.copy(name = "Old", updatedAt = 90)
        val newer = card.copy(name = "New", updatedAt = 110)
        val unknown = card.copy(id = "jumbo", updatedAt = 1)
        assertEquals(listOf(newer, unknown), CardVersions.newer(listOf(older, newer, unknown)) { local[it] })
    }

    @Test
    fun anOlderImportDoesNotBringADeletedCardBack() {
        val tombstone = CardVersions.deleted(card, now = 200)
        assertTrue(CardVersions.newer(listOf(card)) { tombstone.takeIf { t -> t.id == it } }.isEmpty())
    }
}
