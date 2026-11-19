package io.github.jsnjack.wallet.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import io.github.jsnjack.wallet.core.CodeChoice
import io.github.jsnjack.wallet.core.CodeFormat
import zxingcpp.BarcodeReader
import zxingcpp.BarcodeReader.Result as Barcode
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A code read from the camera, an image or a PDF, in a format the app can
 * draw again. [page] is where in the file it was found (0 for an image);
 * [altFormat] is another format the same text was printed in there.
 */
data class ReadCode(val text: String, val format: CodeFormat, val page: Int = 0, val altFormat: CodeFormat? = null)

/** Outcome of reading an image or a PDF. */
sealed interface FileReadResult {
    /**
     * Every drawable code found, in page order, one per text (a ticket's QR
     * code and barcode often hold the same text). [pages] holds each PDF
     * page's printed text, on Android 15 and newer, for filling in the form;
     * it is empty for images.
     */
    data class Found(val codes: List<ReadCode>, val pages: List<String?> = emptyList()) : FileReadResult

    /** Codes were found, but only in formats the app cannot draw. */
    data object Unsupported : FileReadResult

    /** No code at all. */
    data object Nothing : FileReadResult
}

/** Reads codes with zxing-cpp; one instance per scanner screen or file. */
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

    /** Reads every code in the image at [uri]. Throws [IOException] when it cannot be opened. */
    fun readImage(context: Context, uri: Uri): FileReadResult {
        val bitmap = decode(context, uri)
        return summarize(read(bitmap).map { 0 to it }).also { bitmap.recycle() }
    }

    /**
     * Reads every code on the first pages of a PDF, and on Android 15 and
     * newer their text. [bytes] were read from the file already: PdfRenderer
     * needs a seekable file, which a shared stream is not, so they go through
     * a file in the cache. Throws [IOException] when the PDF cannot be read.
     */
    fun readPdf(context: Context, bytes: ByteArray): FileReadResult {
        val file = File.createTempFile("import", ".pdf", context.cacheDir)
        try {
            file.writeBytes(bytes)
            val found = mutableListOf<Pair<Int, Barcode>>()
            val texts = mutableListOf<String?>()
            // Closing the descriptor as well keeps it from leaking when PdfRenderer refuses the file on Android 13 and 14.
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { pdf ->
                    for (index in 0 until minOf(pdf.pageCount, MAX_PDF_PAGES)) {
                        pdf.openPage(index).use { page ->
                            // One page in memory at a time: render, read, let go.
                            val bitmap = render(page)
                            found += read(bitmap).map { index to it }
                            bitmap.recycle()
                            texts += pageText(page)
                        }
                    }
                }
            }
            return summarize(found, texts)
        } catch (e: SecurityException) {
            throw IOException("PDF is password protected", e)
        } catch (e: IllegalArgumentException) {
            // A damaged file, or a page without a size.
            throw IOException("PDF could not be read", e)
        } finally {
            file.delete()
        }
    }

    private fun render(page: PdfRenderer.Page): Bitmap {
        val scale = PDF_PAGE_SIDE.toFloat() / maxOf(page.width, page.height)
        val bitmap = createBitmap((page.width * scale).toInt(), (page.height * scale).toInt())
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    // The page's printed text; PdfRenderer offers it from Android 15 (API 35).
    private fun pageText(page: PdfRenderer.Page): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) page.textContents.joinToString("\n") { it.text } else null

    private fun read(bitmap: Bitmap): List<Barcode> = reader.read(bitmap).filter { it.error == null && !it.text.isNullOrEmpty() }

    // [results] pairs each code with the page it was found on.
    private fun summarize(results: List<Pair<Int, Barcode>>, pages: List<String?> = emptyList()): FileReadResult {
        if (results.isEmpty()) return FileReadResult.Nothing
        val codes = results.mapNotNull { (page, r) -> r.format.toCodeFormat()?.let { ReadCode(r.text!!, it, page) } }.distinct()
        if (codes.isEmpty()) return FileReadResult.Unsupported
        // A ticket prints its code as a QR code and a barcode: keep one, and remember the other format for the switch.
        val formats = codes.groupBy({ it.text }, { it.format })
        val kept = CodeChoice.onePerText(codes, { it.text }, { it.format })
            .map { it.copy(altFormat = CodeChoice.alternative(it.format, formats.getValue(it.text))) }
        return FileReadResult.Found(kept, pages)
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
            throw IOException("image could not be decoded", e)
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
        const val MAX_PDF_PAGES = 10
        const val PDF_PAGE_SIDE = 2000
        val READABLE = setOf(
            BarcodeReader.Format.QR_CODE, BarcodeReader.Format.MICRO_QR_CODE, BarcodeReader.Format.AZTEC,
            BarcodeReader.Format.PDF_417, BarcodeReader.Format.DATA_MATRIX, BarcodeReader.Format.EAN_13,
            BarcodeReader.Format.EAN_8, BarcodeReader.Format.UPC_A, BarcodeReader.Format.UPC_E,
            BarcodeReader.Format.CODE_128, BarcodeReader.Format.CODE_39, BarcodeReader.Format.CODE_93,
            BarcodeReader.Format.ITF, BarcodeReader.Format.CODABAR,
        )
    }
}
