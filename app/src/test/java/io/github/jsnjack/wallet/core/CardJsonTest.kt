package io.github.jsnjack.wallet.core

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardJsonTest {
    private val card = Card(
        id = "c1", name = "AH Bonus", code = "2620048511734", format = CodeFormat.EAN_13, kind = CardKind.LOYALTY,
        color = 0xFF00A0E2.toInt(), note = "family", pinned = true, locked = false, createdAt = 100, updatedAt = 200,
    )

    private fun roundTrip(card: Card): Card? = CardJson.decodeExport(CardJson.encodeExport(listOf(card), 1)).singleOrNull()

    private fun export(cardJson: String) = """{"v":2,"exportedAt":1,"cards":[$cardJson]}"""

    @Test
    fun cardRoundTrips() {
        assertEquals(card, roundTrip(card))
    }

    @Test
    fun tombstoneKeepsNoContent() {
        val tombstone = card.tombstone(300)
        assertEquals(true, tombstone.deleted)
        assertEquals("", tombstone.code)
        assertEquals("", tombstone.pin)
        assertEquals(300L, tombstone.updatedAt)
    }

    @Test
    fun unknownFormatIsSkipped() {
        val text = CardJson.encodeExport(listOf(card), 1).replace("\"EAN_13\"", "\"DOT_CODE\"")
        assertEquals(emptyList<Card>(), CardJson.decodeExport(text))
    }

    @Test
    fun unknownKindBecomesOtherAndUnknownFieldsAreIgnored() {
        val text = CardJson.encodeExport(listOf(card), 1)
            .replace("\"LOYALTY\"", "\"PARKING_PERMIT\"")
            .replace("\"id\":\"c1\"", "\"seat\":\"14C\",\"id\":\"c1\"")
        assertEquals(CardKind.OTHER, CardJson.decodeExport(text).single().kind)
    }

    @Test(expected = IllegalArgumentException::class)
    fun garbageIsRejected() {
        CardJson.decodeExport("not json")
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

    @Test
    fun version2FieldsRoundTrip() {
        val full = card.copy(
            kind = CardKind.BOARDING_PASS, pin = "1234", balanceCents = 2500, usedAt = 400,
            expires = DayTime(LocalDate.of(2026, 12, 31)), event = DayTime(LocalDate.of(2026, 11, 20), LocalTime.of(7, 5)),
            details = listOf(Detail("Gate", "D7")), flight = Flight("AMS", "LIS", "KL", "1691", "TRAVELLER/A", "X7K2QP", "14C", "Y"),
        )
        assertEquals(full, roundTrip(full))
    }

    @Test
    fun version3KeepsTheSecondFormat() {
        val ticket = card.copy(format = CodeFormat.QR, code = "%TST/1234", altFormat = CodeFormat.CODE_39)
        assertEquals(ticket, roundTrip(ticket))
    }

    @Test
    fun version4KeepsTheStartDate() {
        val voucher = card.copy(kind = CardKind.VOUCHER, validFrom = DayTime(LocalDate.of(2026, 12, 1)))
        assertEquals(voucher, roundTrip(voucher))
    }

    @Test
    fun version1CardReadsWithDefaults() {
        val v1 = """{"v":1,"id":"c1","name":"AH","code":"2620048511734","format":"EAN_13","kind":"LOYALTY",""" +
            """"color":"#00A0E2","note":"","pinned":false,"locked":false,"createdAt":1,"updatedAt":2,"deleted":false}"""
        val decoded = CardJson.decodeExport(export(v1)).single()
        assertEquals("", decoded.pin)
        assertNull(decoded.flight)
        assertEquals(emptyList<Detail>(), decoded.details)
        assertNull(decoded.altFormat)
    }

    @Test
    fun detailAndFlightColumnsRoundTrip() {
        val details = listOf(Detail("Row", "8"), Detail("Seat", "14"))
        assertEquals(details, CardJson.decodeDetails(CardJson.encodeDetails(details)))
        assertEquals(emptyList<Detail>(), CardJson.decodeDetails("broken"))
        val flight = Flight("AMS", "LIS", "KL", "1691")
        assertEquals(flight, CardJson.decodeFlight(CardJson.encodeFlight(flight)))
        assertNull(CardJson.decodeFlight(null))
    }
}
