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
        assertEquals("", decoded.pin)
        assertEquals(300L, decoded.updatedAt)
    }

    @Test
    fun unknownFormatIsSkipped() {
        val text = CardJson.encode(card).replace("\"EAN_13\"", "\"DOT_CODE\"")
        assertNull(CardJson.decode(text))
    }

    @Test
    fun unknownKindBecomesOtherAndUnknownFieldsAreIgnored() {
        val text = CardJson.encode(card).replace("\"LOYALTY\"", "\"PARKING_PERMIT\"").replace("{", "{\"seat\":\"14C\",")
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

    @Test
    fun version2FieldsRoundTrip() {
        val full = card.copy(
            kind = CardKind.BOARDING_PASS, pin = "1234", balanceCents = 2500, usedAt = 400,
            expires = DayTime(LocalDate.of(2026, 12, 31)), event = DayTime(LocalDate.of(2026, 11, 20), LocalTime.of(7, 5)),
            details = listOf(Detail("Gate", "D7")), flight = Flight("AMS", "LIS", "KL", "1691", "TRAVELLER/A", "X7K2QP", "14C", "Y"),
        )
        assertEquals(full, CardJson.decode(CardJson.encode(full)))
    }

    @Test
    fun version1FileReadsWithDefaults() {
        val v1 = """{"v":1,"id":"c1","name":"AH","code":"2620048511734","format":"EAN_13","kind":"LOYALTY",""" +
            """"color":"#00A0E2","note":"","pinned":false,"locked":false,"createdAt":1,"updatedAt":2,"deleted":false}"""
        val decoded = CardJson.decode(v1)!!
        assertEquals("", decoded.pin)
        assertNull(decoded.flight)
        assertEquals(emptyList<Detail>(), decoded.details)
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
