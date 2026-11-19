package io.github.jsnjack.wallet.core

/**
 * One file can hold the same text in several codes, like a ticket's QR code
 * and the barcode beside it. Wallet keeps one code per text: a square one
 * when there is one, because it scans best from a phone screen.
 */
object CodeChoice {
    private val PREFERRED = listOf(CodeFormat.QR, CodeFormat.AZTEC, CodeFormat.DATA_MATRIX, CodeFormat.PDF_417)

    /** [codes] with one per distinct [text], in the order the texts first appear. */
    fun <T> onePerText(codes: List<T>, text: (T) -> String, format: (T) -> CodeFormat): List<T> =
        codes.groupBy(text).values.map { same -> same.minBy { rank(format(it)) } }

    /** Another format among [printed] than the [kept] one, for switching to on the code screen; null when there is none. */
    fun alternative(kept: CodeFormat, printed: List<CodeFormat>): CodeFormat? = printed.firstOrNull { it != kept }

    private fun rank(format: CodeFormat): Int = PREFERRED.indexOf(format).let { if (it < 0) PREFERRED.size else it }
}
