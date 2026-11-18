package io.github.jsnjack.wallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardDraftTest {
    @Test
    fun typingANumberGuessesItsFormat() {
        assertEquals(CodeFormat.EAN_13, CardDraft().withCode("4006381333931").format)
    }

    @Test
    fun typingKeepsAChosenFormat() {
        val draft = CardDraft.scanned("ABC", CodeFormat.QR).withCode("4006381333931")
        assertEquals(CodeFormat.QR, draft.format)
    }

    @Test
    fun reportsMissingNameThenMissingCode() {
        assertEquals(DraftProblem.NAME_MISSING, CardDraft(code = "1").problem())
        assertEquals(DraftProblem.CODE_MISSING, CardDraft(name = "Gym").problem())
    }

    @Test
    fun reportsCodesTheFormatCannotDraw() {
        val draft = CardDraft(name = "AH", code = "4006381333932", format = CodeFormat.EAN_13, formatChosen = true)
        assertEquals(DraftProblem.CODE_NOT_DRAWABLE, draft.problem())
    }

    @Test
    fun validDraftHasNoProblem() {
        assertNull(CardDraft(name = "AH", code = "4006 3813 3393 1", format = CodeFormat.EAN_13).problem())
    }

    @Test
    fun colourFollowsBrandUntilChosen() {
        val brand = Brands.exact("Jumbo")!!.color
        assertEquals(brand, CardDraft(name = "jumbo").effectiveColor)
        assertEquals(7, CardDraft(name = "jumbo", color = 7).effectiveColor)
    }

    @Test
    fun newCardGetsIdTimesAndCleanCode() {
        val card = CardDraft(name = " AH ", code = "4006 3813 3393 1", format = CodeFormat.EAN_13).toCard("id1", 50)
        assertEquals("id1", card.id)
        assertEquals("AH", card.name)
        assertEquals("4006381333931", card.code)
        assertEquals(50L, card.createdAt)
    }

    @Test
    fun editedCardKeepsIdAndCreationTime() {
        val original = CardDraft(name = "Gym", code = "M-1").toCard("orig", 10)
        val edited = CardDraft.editing(original).copy(name = "Gym 2").toCard("ignored", 99)
        assertEquals("orig", edited.id)
        assertEquals(10L, edited.createdAt)
        assertEquals(99L, edited.updatedAt)
        assertTrue(edited.name == "Gym 2")
    }
}
