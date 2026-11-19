package io.github.jsnjack.wallet.platform

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import io.github.jsnjack.wallet.R
import io.github.jsnjack.wallet.core.Card
import io.github.jsnjack.wallet.core.CardKind
import io.github.jsnjack.wallet.core.CodeFormat
import io.github.jsnjack.wallet.core.CodeNumbers
import java.io.File

/**
 * Shares a card as a picture: its name above its code, drawn at whole pixels
 * per module with a quiet zone, so a till can scan the picture and this app
 * imports it like any screenshot. The name also goes as the share text,
 * which this app uses to fill in the name.
 */
object CardShare {
    private const val WIDTH = 1080
    private const val MARGIN = 72
    private const val NAME_SIZE = 60f
    private const val NUMBER_SIZE = 40f
    private const val GAP = 48
    private const val LINEAR_HEIGHT = 320
    private const val MAX_CODE_HEIGHT = 900
    private const val LINEAR_QUIET_MODULES = 11
    private const val MATRIX_QUIET_MODULES = 4
    private const val MAX_NUMBER_CHARS = 40
    private const val DIR = "shared"
    private const val AUTHORITY_SUFFIX = ".files"
    private val INK = Color.rgb(0x20, 0x21, 0x24)

    /** The share sheet for [card], or null when its code cannot be drawn. Writes a file: call off the main thread. */
    fun chooser(context: Context, card: Card): Intent? {
        val picture = render(card) ?: return null
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        // Only the latest picture is kept; earlier shares have been handed over by now.
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName(card.name))
        file.outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, card.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // The clip carries the read grant through the chooser to the app picked.
        send.clipData = ClipData.newRawUri(card.name, uri)
        return Intent.createChooser(send, context.getString(R.string.share_title, card.name))
    }

    private fun render(card: Card): Bitmap? {
        val code = CodeBitmaps.get(card.code, card.format) ?: return null
        val quiet = if (card.format.linear) LINEAR_QUIET_MODULES else MATRIX_QUIET_MODULES
        val scale = minOf(
            (WIDTH - 2 * MARGIN) / (code.width + 2 * quiet),
            if (card.format.linear) Int.MAX_VALUE else MAX_CODE_HEIGHT / (code.height + 2 * quiet),
        ).coerceAtLeast(1)
        val codeWidth = code.width * scale
        val codeHeight = if (card.format.linear) LINEAR_HEIGHT else code.height * scale

        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            textSize = NAME_SIZE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val numberPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            textSize = NUMBER_SIZE
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
        // A boarding pass's code is airline data, not a number anyone reads out.
        val number = CodeNumbers.display(card.code)
            .takeIf { card.kind != CardKind.BOARDING_PASS && card.code.length <= MAX_NUMBER_CHARS && card.format != CodeFormat.PDF_417 }

        val nameBaseline = MARGIN - namePaint.ascent()
        val codeTop = (nameBaseline + namePaint.descent()).toInt() + GAP
        val numberBaseline = codeTop + codeHeight + GAP - numberPaint.ascent()
        val height = (if (number != null) numberBaseline + numberPaint.descent() else (codeTop + codeHeight).toFloat()).toInt() + MARGIN

        val picture = createBitmap(WIDTH, height)
        val canvas = Canvas(picture)
        canvas.drawColor(Color.WHITE)
        val name = TextUtils.ellipsize(card.name, namePaint, (WIDTH - 2 * MARGIN).toFloat(), TextUtils.TruncateAt.END)
        canvas.drawText(name, 0, name.length, WIDTH / 2f, nameBaseline, namePaint)
        val left = (WIDTH - codeWidth) / 2
        // No filtering: every module keeps sharp edges at a whole number of pixels.
        canvas.drawBitmap(code, null, Rect(left, codeTop, left + codeWidth, codeTop + codeHeight), Paint())
        if (number != null) canvas.drawText(number, WIDTH / 2f, numberBaseline, numberPaint)
        return picture
    }

    private fun fileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9 ._-]"), "").trim().take(MAX_NUMBER_CHARS).ifEmpty { "card" } + ".png"
}
