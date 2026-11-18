package io.github.jsnjack.wallet.core

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Turns a card's code into the smallest grid of modules; the UI scales it up pixel-exact. */
object CodeMatrix {
    private const val LATIN_1_MAX = 0xFF

    /**
     * The code's modules, one row for linear formats, with no quiet zone.
     * Throws [IllegalArgumentException] when [code] cannot be drawn in [format];
     * the message names the format but never the code.
     */
    fun encode(code: String, format: CodeFormat): BitMatrix {
        require(code.isNotEmpty()) { "${format.label}: code is empty" }
        val hints = mutableMapOf<EncodeHintType, Any>(EncodeHintType.MARGIN to 0)
        if (format == CodeFormat.QR) {
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
            // A charset hint adds an ECI marker that some shop scanners reject, so only when needed.
            if (code.any { it.code > LATIN_1_MAX }) hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        }
        return try {
            MultiFormatWriter().encode(code, format.toZxing(), 0, 0, hints)
        } catch (e: WriterException) {
            throw IllegalArgumentException("${format.label}: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("${format.label}: ${e.message}", e)
        }
    }

    /** True when [code] can be drawn in [format]. */
    fun canEncode(code: String, format: CodeFormat): Boolean = try {
        encode(code, format)
        true
    } catch (e: IllegalArgumentException) {
        false
    }

    private fun CodeFormat.toZxing(): BarcodeFormat = when (this) {
        CodeFormat.QR -> BarcodeFormat.QR_CODE
        CodeFormat.AZTEC -> BarcodeFormat.AZTEC
        CodeFormat.PDF_417 -> BarcodeFormat.PDF_417
        CodeFormat.DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
        CodeFormat.EAN_13 -> BarcodeFormat.EAN_13
        CodeFormat.EAN_8 -> BarcodeFormat.EAN_8
        CodeFormat.UPC_A -> BarcodeFormat.UPC_A
        CodeFormat.UPC_E -> BarcodeFormat.UPC_E
        CodeFormat.CODE_128 -> BarcodeFormat.CODE_128
        CodeFormat.CODE_39 -> BarcodeFormat.CODE_39
        CodeFormat.CODE_93 -> BarcodeFormat.CODE_93
        CodeFormat.ITF -> BarcodeFormat.ITF
        CodeFormat.CODABAR -> BarcodeFormat.CODABAR
    }
}
