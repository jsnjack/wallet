package io.github.jsnjack.wallet.core

/** Colours and short labels that make a card recognisable on a tile. */
object CardLooks {
    private const val OPAQUE = 0xFF000000.toInt()
    private const val RGB_MASK = 0xFFFFFF
    private const val MAX_WORD_MONOGRAM = 4
    private val WORD_BREAKS = Regex("[\\s-]+")

    /** Near-black text colour for light card colours. */
    const val DARK_TEXT = 0xFF111111.toInt()

    /** White text colour for dark card colours. */
    const val LIGHT_TEXT = 0xFFFFFFFF.toInt()

    // WCAG AA contrast for large or bold text, which is what tiles and code headers use.
    private const val MIN_CONTRAST_FOR_WHITE = 3.0

    /** Colours offered in the card form, also used for names without a known brand. */
    val PALETTE: List<Int> = listOf(
        0x00A0E2, 0x0058A3, 0x3643BA, 0x7B3FE4, 0xC2185B, 0xE30613,
        0xFF7500, 0xFFD200, 0x1F7A4D, 0x00897B, 0x5D4037, 0x111318,
    ).map { it or OPAQUE }

    /** White when it has enough contrast on [background], otherwise near-black. */
    fun contentColor(background: Int): Int {
        val whiteContrast = 1.05 / (luminance(background) + 0.05)
        return if (whiteContrast >= MIN_CONTRAST_FOR_WHITE) LIGHT_TEXT else DARK_TEXT
    }

    /** WCAG relative luminance of an ARGB colour, from 0 (black) to 1 (white). */
    fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** A stable palette colour for a card name without a known brand. */
    fun forName(name: String): Int =
        PALETTE[Math.floorMod(name.trim().lowercase().hashCode(), PALETTE.size)]

    /** `#RRGGBB` for an ARGB colour. */
    fun toHex(argb: Int): String = "#%06X".format(argb and RGB_MASK)

    /** The opaque colour for `#RRGGBB`, or null when [hex] is not in that form. */
    fun parseHex(hex: String): Int? =
        hex.removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16)?.let { it or OPAQUE }

    /** A short tile label: a short first word as is ("AH", "IKEA"), otherwise up to two initials. */
    fun monogram(name: String): String {
        val words = name.trim().split(WORD_BREAKS).filter { it.isNotEmpty() }
        val first = words.firstOrNull() ?: return "?"
        if (first.length <= MAX_WORD_MONOGRAM) return first.uppercase()
        return words.take(2).joinToString("") { it.first().uppercaseChar().toString() }
    }
}
