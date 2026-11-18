package io.github.jsnjack.wallet.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.camera.core.ImageProxy
import io.github.jsnjack.wallet.core.CodeFormat
import zxingcpp.BarcodeReader
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** A code read from the camera or an image, in a format the app can draw again. */
data class ReadCode(val text: String, val format: CodeFormat)

/** Outcome of reading an image. */
sealed interface ImageReadResult {
    /** The first readable code. */
    data class Found(val code: ReadCode) : ImageReadResult

    /** A code was found, but in a format the app cannot draw. */
    data object Unsupported : ImageReadResult

    /** No code in the image. */
    data object Nothing : ImageReadResult
}

/** Reads codes with zxing-cpp; one instance per scanner screen. */
class CodeReader(liveCamera: Boolean) {
    private val reader = BarcodeReader(
        BarcodeReader.Options(
            formats = READABLE,
            tryHarder = !liveCamera,
            tryRotate = true,
            tryInvert = true,
            tryDownscale = true,
            // Raw content, so the code can be drawn again exactly as it was read.
            textMode = BarcodeReader.TextMode.PLAIN,
        ),
    )
    private val delivered = AtomicBoolean(false)

    /**
     * Reads one camera frame and closes it. [onCode] runs once, for the first
     * drawable code; [onUnsupported] runs for codes the app cannot draw.
     */
    fun analyze(frame: ImageProxy, onCode: (ReadCode) -> Unit, onUnsupported: () -> Unit) {
        frame.use {
            if (delivered.get()) return
            val results = reader.read(it).filter { r -> r.error == null && !r.text.isNullOrEmpty() }
            val first = results.firstOrNull() ?: return
            val format = first.format.toCodeFormat()
            if (format == null) {
                onUnsupported()
            } else if (delivered.compareAndSet(false, true)) {
                onCode(ReadCode(first.text!!, format))
            }
        }
    }

    /** Reads the first code in the image at [uri]. Throws [IOException] when the image cannot be opened. */
    fun readImage(context: Context, uri: Uri): ImageReadResult {
        val bitmap = decode(context, uri)
        val results = reader.read(bitmap).filter { it.error == null && !it.text.isNullOrEmpty() }
        if (results.isEmpty()) return ImageReadResult.Nothing
        val drawable = results.firstNotNullOfOrNull { r -> r.format.toCodeFormat()?.let { ReadCode(r.text!!, it) } }
        return drawable?.let { ImageReadResult.Found(it) } ?: ImageReadResult.Unsupported
    }

    private fun decode(context: Context, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return try {
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_IMAGE_SIDE) {
                    val scale = MAX_IMAGE_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
            }.let { if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false) }
        } catch (e: ImageDecoder.DecodeException) {
            throw IOException("shared image could not be decoded", e)
        }
    }

    private fun BarcodeReader.Format.toCodeFormat(): CodeFormat? = when (this) {
        BarcodeReader.Format.QR_CODE, BarcodeReader.Format.QR_CODE_MODEL_1, BarcodeReader.Format.QR_CODE_MODEL_2,
        BarcodeReader.Format.MICRO_QR_CODE, BarcodeReader.Format.RMQR_CODE,
        -> CodeFormat.QR
        BarcodeReader.Format.AZTEC, BarcodeReader.Format.AZTEC_CODE -> CodeFormat.AZTEC
        BarcodeReader.Format.PDF_417, BarcodeReader.Format.COMPACT_PDF_417 -> CodeFormat.PDF_417
        BarcodeReader.Format.DATA_MATRIX -> CodeFormat.DATA_MATRIX
        BarcodeReader.Format.EAN_13, BarcodeReader.Format.ISBN -> CodeFormat.EAN_13
        BarcodeReader.Format.EAN_8 -> CodeFormat.EAN_8
        BarcodeReader.Format.UPC_A -> CodeFormat.UPC_A
        BarcodeReader.Format.UPC_E -> CodeFormat.UPC_E
        BarcodeReader.Format.CODE_128 -> CodeFormat.CODE_128
        BarcodeReader.Format.CODE_39, BarcodeReader.Format.CODE_39_STD, BarcodeReader.Format.CODE_39_EXT -> CodeFormat.CODE_39
        BarcodeReader.Format.CODE_93 -> CodeFormat.CODE_93
        BarcodeReader.Format.ITF, BarcodeReader.Format.ITF_14 -> CodeFormat.ITF
        BarcodeReader.Format.CODABAR -> CodeFormat.CODABAR
        else -> null
    }

    private companion object {
        const val MAX_IMAGE_SIDE = 2400
        val READABLE = setOf(
            BarcodeReader.Format.QR_CODE, BarcodeReader.Format.MICRO_QR_CODE, BarcodeReader.Format.AZTEC,
            BarcodeReader.Format.PDF_417, BarcodeReader.Format.DATA_MATRIX, BarcodeReader.Format.EAN_13,
            BarcodeReader.Format.EAN_8, BarcodeReader.Format.UPC_A, BarcodeReader.Format.UPC_E,
            BarcodeReader.Format.CODE_128, BarcodeReader.Format.CODE_39, BarcodeReader.Format.CODE_93,
            BarcodeReader.Format.ITF, BarcodeReader.Format.CODABAR,
        )
    }
}
