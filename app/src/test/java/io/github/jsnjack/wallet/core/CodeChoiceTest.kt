package io.github.jsnjack.wallet.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CodeChoiceTest {
    @Test
    fun keepsOneCodePerTextPreferringASquareOne() {
        val codes = listOf("%TST/1234" to CodeFormat.CODE_39, "other" to CodeFormat.EAN_13, "%TST/1234" to CodeFormat.QR)
        assertEquals(
            listOf("%TST/1234" to CodeFormat.QR, "other" to CodeFormat.EAN_13),
            CodeChoice.onePerText(codes, { it.first }, { it.second }),
        )
        assertEquals(CodeFormat.CODE_39, CodeChoice.alternative(CodeFormat.QR, listOf(CodeFormat.CODE_39, CodeFormat.QR)))
        assertEquals(null, CodeChoice.alternative(CodeFormat.QR, listOf(CodeFormat.QR)))
    }
}
