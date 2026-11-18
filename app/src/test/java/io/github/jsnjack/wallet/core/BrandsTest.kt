package io.github.jsnjack.wallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandsTest {
    @Test
    fun suggestsByNameStart() {
        assertEquals(listOf("Albert Heijn"), Brands.suggest("alb").map { it.name })
    }

    @Test
    fun suggestsByLaterWord() {
        assertTrue(Brands.suggest("fam").any { it.name == "IKEA Family" })
    }

    @Test
    fun noSuggestionsForOneLetterOrExactMatch() {
        assertTrue(Brands.suggest("a").isEmpty())
        assertTrue(Brands.suggest("Jumbo").isEmpty())
    }

    @Test
    fun exactIgnoresCaseAndSpaces() {
        assertEquals("Albert Heijn", Brands.exact(" albert heijn ")?.name)
        assertNull(Brands.exact("Albert"))
    }
}
