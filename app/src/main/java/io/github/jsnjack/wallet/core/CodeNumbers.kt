package io.github.jsnjack.wallet.core

/** Rules for the numbers and text printed on cards. */
object CodeNumbers {
    private val GTIN_LENGTHS = setOf(8, 12, 13)
    private val NUMERIC_FORMATS = setOf(
        CodeFormat.EAN_13, CodeFormat.EAN_8, CodeFormat.UPC_A, CodeFormat.UPC_E, CodeFormat.ITF,
    )
    private const val MIN_GROUPED_LENGTH = 8
    private const val GROUP_SIZE = 4
    private const val LONG_TEXT = 30
    private val SEPARATORS = Regex("[\\s-]")
    private val DIGIT_GROUPS = Regex("\\d+(?:[\\s-]+\\d+)+")

    /** True when [digits] is an EAN-8, UPC-A or EAN-13 number with a correct GS1 check digit. */
    fun hasValidCheckDigit(digits: String): Boolean {
        if (digits.length !in GTIN_LENGTHS || !digits.all(Char::isDigit)) return false
        val sum = digits.dropLast(1).reversed()
            .mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 3 else 1 }
            .sum()
        return (10 - sum % 10) % 10 == digits.last() - '0'
    }

    /**
     * The code as stored: numeric formats drop the spaces and dashes that
     * cards print between digit groups, and so does a number [typed] in
     * groups, such as "6012 3456 7890 1234", whatever its format: the
     * barcode on the card holds the digits only. Other codes keep their text
     * as read, because a scanned code must be drawn again exactly.
     */
    fun clean(code: String, format: CodeFormat, typed: Boolean = false): String =
        if (format in NUMERIC_FORMATS || typed && DIGIT_GROUPS.matches(code.trim())) code.replace(SEPARATORS, "") else code.trim()

    /** The format a typed code most likely uses. */
    fun guessFormat(typed: String): CodeFormat {
        val digits = typed.replace(SEPARATORS, "")
        if (hasValidCheckDigit(digits)) {
            return when (digits.length) {
                13 -> CodeFormat.EAN_13
                8 -> CodeFormat.EAN_8
                else -> CodeFormat.UPC_A
            }
        }
        val printableAscii = typed.all { it.code in 32..126 }
        return if (printableAscii && typed.length <= LONG_TEXT) CodeFormat.CODE_128 else CodeFormat.QR
    }

    /** A long all-digit code in groups of four, easier to read aloud; other codes unchanged. */
    fun display(code: String): String =
        if (code.length >= MIN_GROUPED_LENGTH && code.all(Char::isDigit)) {
            code.chunked(GROUP_SIZE).joinToString(" ")
        } else {
            code
        }
}
