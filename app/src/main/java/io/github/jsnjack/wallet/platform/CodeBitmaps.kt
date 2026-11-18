package io.github.jsnjack.wallet.platform

import android.graphics.Bitmap
import android.util.LruCache
import io.github.jsnjack.wallet.core.CodeFormat
import io.github.jsnjack.wallet.core.CodeMatrix

/** Code images at one pixel per module, cached; the UI scales them by whole pixels. */
object CodeBitmaps {
    private const val CACHE_SIZE = 32
    private const val INK = 0xFF000000.toInt()
    private const val PAPER = 0xFFFFFFFF.toInt()
    private val cache = LruCache<String, Bitmap>(CACHE_SIZE)

    /** The image for [code] in [format], or null when it cannot be drawn. */
    fun get(code: String, format: CodeFormat): Bitmap? {
        val key = "${format.name}:$code"
        cache.get(key)?.let { return it }
        val matrix = try {
            CodeMatrix.encode(code, format)
        } catch (e: IllegalArgumentException) {
            return null
        }
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix.get(i % matrix.width, i / matrix.width)) INK else PAPER
        }
        val bitmap = Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        cache.put(key, bitmap)
        return bitmap
    }
}
