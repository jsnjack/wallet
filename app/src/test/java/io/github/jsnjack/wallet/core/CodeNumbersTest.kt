package io.github.jsnjack.wallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeNumbersTest {
    @Test
    fun acceptsKnownValidCheckDigits() {
        assertTrue(CodeNumbers.hasValidCheckDigit("4006381333931"))
        assertTrue(CodeNumbers.hasValidCheckDigit("96385074"))
        assertTrue(CodeNumbers.hasValidCheckDigit("036000291452"))
    }

    @Test
    fun rejectsWrongCheckDigit() {
        assertFalse(CodeNumbers.hasValidCheckDigit("4006381333932"))
    }

    @Test
    fun rejectsOtherLengthsAndLetters() {
        assertFalse(CodeNumbers.hasValidCheckDigit("12345"))
        assertFalse(CodeNumbers.hasValidCheckDigit("40063813339A1"))
    }

    @Test
    fun guessesEan13ForValidThirteenDigitsWithSpaces() {
        assertEquals(CodeFormat.EAN_13, CodeNumbers.guessFormat("4006 3813 3393 1"))
    }

    @Test
    fun guessesEan8AndUpcA() {
        assertEquals(CodeFormat.EAN_8, CodeNumbers.guessFormat("96385074"))
        assertEquals(CodeFormat.UPC_A, CodeNumbers.guessFormat("036000291452"))
    }

    @Test
    fun guessesCode128ForShortTextAndInvalidNumbers() {
        assertEquals(CodeFormat.CODE_128, CodeNumbers.guessFormat("M-0042-118"))
        assertEquals(CodeFormat.CODE_128, CodeNumbers.guessFormat("4006381333932"))
    }

    @Test
    fun guessesQrForLongOrNonAsciiText() {
        assertEquals(CodeFormat.QR, CodeNumbers.guessFormat("https://example.com/pickup/7Q4K2291?locker=14"))
        assertEquals(CodeFormat.QR, CodeNumbers.guessFormat("Café"))
    }

    @Test
    fun cleanDropsSeparatorsOnlyForNumericFormats() {
        assertEquals("4006381333931", CodeNumbers.clean("4006 3813-3393 1", CodeFormat.EAN_13))
        assertEquals("BM 7Q4K", CodeNumbers.clean("  BM 7Q4K ", CodeFormat.QR))
    }

    @Test
    fun displayGroupsLongDigitCodesInFours() {
        assertEquals("4006 3813 3393 1", CodeNumbers.display("4006381333931"))
        assertEquals("1234567", CodeNumbers.display("1234567"))
        assertEquals("BM-7Q4K-2291", CodeNumbers.display("BM-7Q4K-2291"))
    }

    @Test
    fun aNumberTypedInGroupsIsStoredWithoutTheSpaces() {
        assertEquals("6012345678901234", CodeNumbers.clean("6012 3456 7890 1234", CodeFormat.CODE_128, typed = true))
        assertEquals("6012345678901234", CardDraft().withCode("6012 3456 7890 1234").cleanCode)
        // Scanned text is kept exactly, spaces and all; typed text with letters too.
        assertEquals("6012 3456", CodeNumbers.clean("6012 3456", CodeFormat.CODE_128))
        assertEquals("AB 12", CodeNumbers.clean("AB 12", CodeFormat.CODE_128, typed = true))
    }
}
