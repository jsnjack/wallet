package io.github.jsnjack.wallet.core

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Gift card balances, kept in euro cents. */
object Money {
    private val AMOUNT = Regex("""(\d{1,9})(?:[.,](\d{1,2}))?""")
    private const val ARABIC_DECIMAL_SIGN = '\u066B'
    private val CURRENCY: Currency = Currency.getInstance("EUR")
    private const val CENTS_PER_UNIT = 100

    /** Cents for a typed amount such as `25`, `25,5` or `25.50`; null when it is not an amount. */
    fun parseCents(text: String): Long? {
        // Digits in any script and the Arabic decimal sign, as editable() writes them in Arabic or Persian.
        val plain = text.map { c ->
            when {
                c.isDigit() -> '0' + Character.digit(c, 10)
                c == ARABIC_DECIMAL_SIGN -> '.'
                else -> c
            }
        }.joinToString("")
        val match = AMOUNT.matchEntire(plain.trim().removePrefix("€").trim()) ?: return null
        val whole = match.groupValues[1].toLong()
        val fraction = match.groupValues[2].padEnd(2, '0').toLong()
        return whole * CENTS_PER_UNIT + fraction
    }

    /** The amount as typed in the form: `25,50` style with the locale's decimal separator. */
    fun editable(cents: Long, locale: Locale): String {
        val format = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
            isGroupingUsed = false
        }
        return format.format(cents / CENTS_PER_UNIT.toDouble())
    }

    /** The amount for display, such as `€ 25,00` in Dutch. */
    fun display(cents: Long, locale: Locale): String =
        NumberFormat.getCurrencyInstance(locale).apply { currency = CURRENCY }.format(cents / CENTS_PER_UNIT.toDouble())
}
