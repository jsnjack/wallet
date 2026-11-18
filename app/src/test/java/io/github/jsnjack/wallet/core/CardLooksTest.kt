package io.github.jsnjack.wallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardLooksTest {
    @Test
    fun whiteTextOnBlackAndDarkBlue() {
        assertEquals(CardLooks.LIGHT_TEXT, CardLooks.contentColor(0xFF000000.toInt()))
        assertEquals(CardLooks.LIGHT_TEXT, CardLooks.contentColor(0xFF0058A3.toInt()))
    }

    @Test
    fun darkTextOnWhiteYellowAndLightBlue() {
        assertEquals(CardLooks.DARK_TEXT, CardLooks.contentColor(0xFF00A0E2.toInt()))
        assertEquals(CardLooks.DARK_TEXT, CardLooks.contentColor(0xFFFFFFFF.toInt()))
        assertEquals(CardLooks.DARK_TEXT, CardLooks.contentColor(0xFFFFD200.toInt()))
    }

    @Test
    fun forNameIsStableAndIgnoresCaseAndSpaces() {
        assertEquals(CardLooks.forName("Gym"), CardLooks.forName("  gym "))
    }

    @Test
    fun hexRoundTrips() {
        val color = 0xFF3643BA.toInt()
        assertEquals("#3643BA", CardLooks.toHex(color))
        assertEquals(color, CardLooks.parseHex("#3643BA"))
    }

    @Test
    fun parseHexRejectsOtherForms() {
        assertNull(CardLooks.parseHex("#FFF"))
        assertNull(CardLooks.parseHex("blue"))
    }

    @Test
    fun monogramKeepsShortFirstWord() {
        assertEquals("AH", CardLooks.monogram("AH Bonus"))
        assertEquals("IKEA", CardLooks.monogram("IKEA Family"))
        assertEquals("OBA", CardLooks.monogram("oba"))
    }

    @Test
    fun monogramUsesInitialsForLongWords() {
        assertEquals("BF", CardLooks.monogram("Basic-Fit"))
        assertEquals("K", CardLooks.monogram("Kruidvat"))
        assertEquals("?", CardLooks.monogram("   "))
    }
}
