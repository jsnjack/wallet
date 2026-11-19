package io.github.jsnjack.wallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeMatrixTest {
    @Test
    fun ean13IsOneRowOf95Modules() {
        val matrix = CodeMatrix.encode("4006381333931", CodeFormat.EAN_13)
        assertEquals(95, matrix.width)
        assertEquals(1, matrix.height)
    }

    @Test
    fun qrIsSquareWithoutQuietZone() {
        val matrix = CodeMatrix.encode("BM-7Q4K-2291", CodeFormat.QR)
        assertEquals(matrix.width, matrix.height)
        assertTrue(matrix.get(0, 0))
    }

    @Test
    fun everyFormatDrawsATypicalCode() {
        val samples = mapOf(
            CodeFormat.QR to "https://example.com",
            CodeFormat.AZTEC to "M1TRAVELLER/A",
            CodeFormat.PDF_417 to "M1TRAVELLER/A",
            CodeFormat.DATA_MATRIX to "PARCEL-1",
            CodeFormat.EAN_13 to "4006381333931",
            CodeFormat.EAN_8 to "96385074",
            CodeFormat.UPC_A to "036000291452",
            CodeFormat.UPC_E to "01234565",
            CodeFormat.CODE_128 to "M-0042-118",
            CodeFormat.CODE_39 to "LIB12345",
            CodeFormat.CODE_93 to "LIB12345",
            CodeFormat.ITF to "12345678",
            CodeFormat.CODABAR to "A12345B",
        )
        assertEquals(CodeFormat.entries.toSet(), samples.keys)
        samples.forEach { (format, code) -> assertTrue("$format", CodeMatrix.canEncode(code, format)) }
    }

    @Test
    fun onlyQrAztecAndDataMatrixAreSquare() {
        assertEquals(setOf(CodeFormat.QR, CodeFormat.AZTEC, CodeFormat.DATA_MATRIX), CodeFormat.entries.filter { it.square }.toSet())
    }

    @Test
    fun rejectsLettersInNumericFormatsAndEmptyCodes() {
        assertFalse(CodeMatrix.canEncode("ABC", CodeFormat.EAN_13))
        assertFalse(CodeMatrix.canEncode("", CodeFormat.QR))
    }

    @Test
    fun errorMessageNamesTheFormatButNotTheCode() {
        val message = try {
            CodeMatrix.encode("SECRET", CodeFormat.EAN_8)
            ""
        } catch (e: IllegalArgumentException) {
            e.message.orEmpty()
        }
        assertTrue(message.startsWith("EAN-8"))
        assertFalse(message.contains("SECRET"))
    }
}
