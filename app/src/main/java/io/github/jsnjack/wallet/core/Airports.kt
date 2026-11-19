package io.github.jsnjack.wallet.core

/** IATA airport code → city, from the bundled table (`make airports`). */
class Airports(private val cities: Map<String, String>) {

    /** The city for [code], or null when the table does not list it. */
    fun city(code: String): String? = cities[code.uppercase()]

    companion object {
        /** Reads the table: one `CODE<tab>City` line per airport. */
        fun parse(tsv: String): Airports = Airports(
            tsv.lineSequence()
                .mapNotNull { line -> line.split('\t').takeIf { it.size == 2 && it[0].length == 3 }?.let { it[0] to it[1] } }
                .toMap(),
        )
    }
}
