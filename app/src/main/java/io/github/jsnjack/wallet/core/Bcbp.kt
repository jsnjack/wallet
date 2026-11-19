package io.github.jsnjack.wallet.core

import java.time.LocalDate

/** One leg read from a boarding pass barcode. */
data class BcbpLeg(val flight: Flight, val date: LocalDate)

/** A boarding pass barcode: the passenger and one or more flight legs. */
data class BoardingPass(val passenger: String, val legs: List<BcbpLeg>)

/**
 * Reads IATA BCBP (Resolution 792), the text inside airline boarding pass
 * barcodes. Only the mandatory items are read; conditional and airline
 * data are skipped by their declared length.
 */
object Bcbp {
    private const val FORMAT_CODE = 'M'
    private const val NAME_START = 2
    private const val NAME_END = 22
    private const val FIRST_LEG = 23
    private const val LEG_LENGTH = 37
    private const val MAX_LEGS = 4
    private const val PAST_TOLERANCE_DAYS = 30L
    private val AIRPORT = Regex("[A-Z]{3}")

    /** The boarding pass in [text], or null when it is not BCBP. [today] places the day-of-year date in a year. */
    fun parse(text: String, today: LocalDate): BoardingPass? {
        if (text.length < FIRST_LEG + LEG_LENGTH || text[0] != FORMAT_CODE) return null
        val legCount = text[1].digitToIntOrNull()?.takeIf { it in 1..MAX_LEGS } ?: return null
        val passenger = text.substring(NAME_START, NAME_END).trim()
        val legs = mutableListOf<BcbpLeg>()
        var pos = FIRST_LEG
        repeat(legCount) {
            if (pos + LEG_LENGTH > text.length) return null
            val leg = text.substring(pos, pos + LEG_LENGTH)
            val from = leg.substring(7, 10)
            val to = leg.substring(10, 13)
            if (!AIRPORT.matches(from) || !AIRPORT.matches(to)) return null
            val dayOfYear = leg.substring(21, 24).trim().toIntOrNull()?.takeIf { it in 1..366 } ?: return null
            val variable = leg.substring(35, 37).toIntOrNull(16) ?: return null
            val flight = Flight(
                from = from,
                to = to,
                carrier = leg.substring(13, 16).trim(),
                number = leg.substring(16, 21).trim().trimStart('0').ifEmpty { "0" },
                passenger = passenger,
                pnr = leg.substring(0, 7).trim(),
                seat = leg.substring(25, 29).trim().trimStart('0'),
                cabin = leg.substring(24, 25).trim(),
            )
            legs += BcbpLeg(flight, dateOf(dayOfYear, today) ?: return null)
            pos += LEG_LENGTH + variable
        }
        return BoardingPass(passenger, legs)
    }

    /**
     * The date for a day of the year: the earliest such date from 30 days
     * ago onwards, because passes are scanned before a flight or soon after.
     */
    fun dateOf(dayOfYear: Int, today: LocalDate): LocalDate? =
        (today.year - 1..today.year + 1)
            .mapNotNull { year -> if (dayOfYear <= LocalDate.of(year, 1, 1).lengthOfYear()) LocalDate.ofYearDay(year, dayOfYear) else null }
            .firstOrNull { !it.isBefore(today.minusDays(PAST_TOLERANCE_DAYS)) }
}
