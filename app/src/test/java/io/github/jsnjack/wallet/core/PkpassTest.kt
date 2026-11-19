package io.github.jsnjack.wallet.core

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PkpassTest {
    private val today = LocalDate.of(2026, 11, 19)

    private fun pkpass(json: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("icon.png"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.putNextEntry(ZipEntry("pass.json"))
            zip.write(json.encodeToByteArray())
        }
        return out.toByteArray()
    }

    @Test
    fun storeCardBecomesLoyaltyCard() {
        // SYNTH: shaped like a supermarket loyalty pass.
        val draft = Pkpass.read(
            pkpass(
                """{"formatVersion":1,"organizationName":"Example Market","logoText":"Market Bonus",
                "backgroundColor":"rgb(0, 160, 226)","barcodes":[{"format":"PKBarcodeFormatCode128","message":"2620048511734",
                "messageEncoding":"iso-8859-1"}],"storeCard":{"primaryFields":[{"key":"points","label":"Points","value":120}]}}""",
            ),
            today, 0,
        )
        assertEquals("Market Bonus", draft.name)
        assertEquals(CardKind.LOYALTY, draft.kind)
        assertEquals(CodeFormat.CODE_128, draft.format)
        assertEquals("2620048511734", draft.code)
        assertEquals(0xFF00A0E2.toInt(), draft.color)
        assertEquals(listOf(Detail("Points", "120")), draft.details)
        assertNull(draft.problem())
    }

    @Test
    fun boardingPassUsesTheBcbpBarcode() {
        val draft = Pkpass.read(
            pkpass(
                """{"description":"Boarding pass","relevantDate":"2026-11-20T07:05+01:00",
                "barcode":{"format":"PKBarcodeFormatAztec","message":"${BcbpTest.ONE_LEG}"},
                "boardingPass":{"transitType":"PKTransitTypeAir","auxiliaryFields":[{"key":"gate","label":"Gate","value":"D7"}]}}""",
            ),
            today, 0,
        )
        assertEquals(CardKind.BOARDING_PASS, draft.kind)
        assertEquals("KL 1691", draft.name)
        assertEquals(DayTime(LocalDate.of(2026, 11, 20), LocalTime.of(7, 5)), draft.event)
        assertEquals("LIS", draft.flight?.to)
        assertEquals(listOf(Detail("Gate", "D7")), draft.details)
    }

    @Test
    fun voidedCouponIsAlreadyUsedAndKeepsItsExpiry() {
        val draft = Pkpass.read(
            pkpass(
                """{"organizationName":"Shop","voided":true,"expirationDate":"2026-12-31T23:59:00Z",
                "barcodes":[{"format":"PKBarcodeFormatQR","message":"COUPON-1"}],"coupon":{}}""",
            ),
            today, 42,
        )
        assertEquals(CardKind.VOUCHER, draft.kind)
        assertEquals(42L, draft.usedAt)
        assertEquals(LocalDate.of(2026, 12, 31), draft.expires?.date)
    }

    @Test(expected = IOException::class)
    fun passWithoutDrawableBarcodeIsRejected() {
        Pkpass.read(pkpass("""{"organizationName":"Shop","generic":{}}"""), today, 0)
    }

    @Test(expected = IOException::class)
    fun zipWithoutPassJsonIsRejected() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { it.putNextEntry(ZipEntry("other.txt")); it.write(1) }
        Pkpass.read(out.toByteArray(), today, 0)
    }

    @Test
    fun recognisesZipHeaderAndColours() {
        assertTrue(Pkpass.looksLikePkpass(pkpass("{}")))
        assertFalse(Pkpass.looksLikePkpass("%PDF-1.7".encodeToByteArray()))
        assertEquals(0xFF0B1F3A.toInt(), Pkpass.color("#0B1F3A"))
        assertNull(Pkpass.color("blue"))
    }
}
