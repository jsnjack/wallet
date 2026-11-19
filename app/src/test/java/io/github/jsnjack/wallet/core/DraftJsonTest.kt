package io.github.jsnjack.wallet.core

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftJsonTest {
    @Test
    fun halfFilledFormsComeBackUnchanged() {
        val drafts = listOf(
            CardDraft(
                editingId = "card-1",
                name = "Pathé",
                code = "TICKET-42",
                format = CodeFormat.QR,
                formatChosen = true,
                kind = CardKind.TICKET,
                color = 0xFF112233.toInt(),
                note = "Row 8",
                pin = "1234",
                event = DayTime(LocalDate.of(2026, 12, 3), LocalTime.of(20, 0)),
                expires = DayTime(LocalDate.of(2026, 12, 4)),
                validFrom = DayTime(LocalDate.of(2026, 11, 20)),
                balanceText = "12,5",
                details = listOf(Detail("Seat", "14")),
                altFormat = CodeFormat.CODE_128,
            ),
            CardDraft(
                name = "KLM",
                code = "M1TRAVELLER/A",
                format = CodeFormat.AZTEC,
                kind = CardKind.BOARDING_PASS,
                flight = Flight("AMS", "LHR", "KL", "1001", passenger = "TRAVELLER/A", pnr = "ABC123", seat = "12A"),
            ),
        )
        assertEquals(drafts, DraftJson.decode(DraftJson.encode(drafts)))
    }

    @Test
    fun unreadableTextGivesNoDrafts() {
        assertTrue(DraftJson.decode(null).isEmpty())
        assertTrue(DraftJson.decode("not json").isEmpty())
        assertTrue(DraftJson.decode("""[{"format":"NO_SUCH_FORMAT","kind":"LOYALTY"}]""").isEmpty())
    }
}
