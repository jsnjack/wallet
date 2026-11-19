package io.github.jsnjack.wallet.core

import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
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
    fun ticketTextFillsWhatTheCodeLeftOpen() {
        val facts = TicketFacts("Concert", DayTime(LocalDate.of(2026, 12, 3), LocalTime.of(20, 30)), listOf(Detail("Zaal", "Grote Zaal")))
        val draft = CardDraft.scanned("%TST/1234", CodeFormat.QR, LocalDate.of(2026, 11, 19)).withTicket(facts)
        assertEquals("Concert", draft.name)
        assertEquals(CardKind.TICKET, draft.kind)
        assertEquals(facts.event, draft.event)
        assertEquals(listOf(Detail("Zaal", "Grote Zaal")), draft.details)
        val named = CardDraft(name = "Mine").withTicket(facts.copy(event = null))
        assertEquals("Mine", named.name)
        assertEquals(CardKind.LOYALTY, named.kind)
    }

    @Test
    fun theSecondFormatGoesWhenItCanNoLongerDrawTheCode() {
        val ticket = CardDraft(name = "Concert", code = "%TST/1234", format = CodeFormat.QR, formatChosen = true, altFormat = CodeFormat.CODE_39)
        assertEquals(CodeFormat.CODE_39, ticket.toCard("t", 1).altFormat)
        assertNull(ticket.copy(code = "Prijs €5").toCard("t", 1).altFormat)
        assertNull(ticket.copy(format = CodeFormat.CODE_39).toCard("t", 1).altFormat)
    }

    @Test
    fun typingKeepsAChosenFormat() {
        val draft = CardDraft.scanned("ABC", CodeFormat.QR, LocalDate.of(2026, 11, 19)).withCode("4006381333931")
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
        val edited = CardDraft.editing(original, Locale.ROOT).copy(name = "Gym 2").toCard("ignored", 99)
        assertEquals("orig", edited.id)
        assertEquals(10L, edited.createdAt)
        assertEquals(99L, edited.updatedAt)
        assertTrue(edited.name == "Gym 2")
    }

    @Test
    fun scannedBoardingPassFillsTheFlight() {
        val draft = CardDraft.scanned(BcbpTest.ONE_LEG, CodeFormat.PDF_417, LocalDate.of(2026, 11, 19))
        assertEquals(CardKind.BOARDING_PASS, draft.kind)
        assertEquals("KL 1691", draft.name)
        assertEquals(LocalDate.of(2026, 11, 20), draft.event?.date)
        assertEquals("X7K2QP", draft.flight?.pnr)
        assertEquals(CardDraft.BOARDING_COLOR, draft.effectiveColor)
    }

    @Test
    fun invalidBalanceBlocksSaving() {
        val draft = CardDraft(name = "Gift", code = "M-1", kind = CardKind.GIFT_CARD, balanceText = "lots")
        assertEquals(DraftProblem.BALANCE_INVALID, draft.problem())
        assertEquals(2550L, draft.copy(balanceText = "25,50").toCard("g", 1).balanceCents)
    }

    @Test
    fun editingKeepsTypeSpecificFields() {
        val card = CardDraft(
            name = "Voucher", code = "V-1", kind = CardKind.VOUCHER, pin = "4821",
            expires = DayTime(LocalDate.of(2026, 11, 20)), details = listOf(Detail("Locker", "14")),
        ).toCard("v", 1)
        val again = CardDraft.editing(card, Locale.ROOT).toCard("other", 2)
        assertEquals(card.copy(updatedAt = 2), again)
    }
}
