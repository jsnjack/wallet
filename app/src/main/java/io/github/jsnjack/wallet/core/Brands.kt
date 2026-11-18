package io.github.jsnjack.wallet.core

/** A shop in the bundled list: its usual card name and an approximate brand colour. */
data class Brand(val name: String, val color: Int)

/** Shops suggested while naming a card; colours are approximations the user can change. */
object Brands {
    private const val MIN_QUERY = 2
    private val WORD_BREAKS = Regex("[\\s-]+")

    private fun brand(name: String, rgb: Int) = Brand(name, rgb or 0xFF000000.toInt())

    /** Common Dutch and European shops and services with loyalty or membership cards. */
    val ALL: List<Brand> = listOf(
        brand("Albert Heijn", 0x00A0E2),
        brand("ANWB", 0xFFCC00),
        brand("Basic-Fit", 0xFF7900),
        brand("de Bijenkorf", 0x000000),
        brand("bol", 0x0000A4),
        brand("Decathlon", 0x3643BA),
        brand("DHL", 0xFFCC00),
        brand("H&M", 0xE50010),
        brand("HEMA", 0xE3001B),
        brand("Hornbach", 0xF7941D),
        brand("IKEA Family", 0x0058A3),
        brand("Jumbo", 0xFFD200),
        brand("Kruidvat", 0xE3051B),
        brand("Lidl Plus", 0x0050AA),
        brand("MediaMarkt", 0xDF0000),
        brand("NS", 0xFFC917),
        brand("PostNL", 0xFF6200),
        brand("Shell", 0xFFD500),
        brand("Starbucks", 0x00704A),
        brand("Zalando", 0xFF6900),
    )

    /** Up to [limit] brands whose name, or a word in it, starts with [query]; none for one letter. */
    fun suggest(query: String, limit: Int = 3): List<Brand> {
        val q = query.trim()
        if (q.length < MIN_QUERY) return emptyList()
        return ALL.filter { brand ->
            !brand.name.equals(q, ignoreCase = true) &&
                (brand.name.startsWith(q, ignoreCase = true) ||
                    brand.name.split(WORD_BREAKS).any { it.startsWith(q, ignoreCase = true) })
        }.take(limit)
    }

    /** The brand named exactly [name], ignoring case and surrounding spaces. */
    fun exact(name: String): Brand? = ALL.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
}
