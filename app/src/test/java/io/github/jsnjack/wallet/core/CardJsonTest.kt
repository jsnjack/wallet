package io.github.jsnjack.wallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardJsonTest {
    private val card = Card(
        id = "c1", name = "AH Bonus", code = "2620048511734", format = CodeFormat.EAN_13, kind = CardKind.LOYALTY,
        color = 0xFF00A0E2.toInt(), note = "family", pinned = true, locked = false, createdAt = 100, updatedAt = 200,
    )

    @Test
    fun cardRoundTrips() {
        assertEquals(card, CardJson.decode(CardJson.encode(card)))
    }

    @Test
    fun tombstoneRoundTripsWithoutItsContent() {
        val tombstone = card.tombstone(300)
        val decoded = CardJson.decode(CardJson.encode(tombstone))!!
        assertEquals(true, decoded.deleted)
        assertEquals("", decoded.code)
        assertEquals(300L, decoded.updatedAt)
    }

    @Test
    fun unknownFormatIsSkipped() {
        val text = CardJson.encode(card).replace("\"EAN_13\"", "\"DOT_CODE\"")
        assertNull(CardJson.decode(text))
    }

    @Test
    fun unknownKindBecomesOtherAndUnknownFieldsAreIgnored() {
        val text = CardJson.encode(card).replace("\"LOYALTY\"", "\"BOARDING_PASS\"").replace("{", "{\"seat\":\"14C\",")
        assertEquals(CardKind.OTHER, CardJson.decode(text)?.kind)
    }

    @Test
    fun garbageIsNull() {
        assertNull(CardJson.decode("not json"))
    }

    @Test
    fun exportRoundTrips() {
        val other = card.copy(id = "c2", name = "Gym", format = CodeFormat.QR, code = "M-1")
        assertEquals(listOf(card, other), CardJson.decodeExport(CardJson.encodeExport(listOf(card, other), 1)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun exportRejectsOtherJson() {
        CardJson.decodeExport("{\"hello\":1}")
    }
}
