package io.github.jsnjack.wallet.platform

import android.content.Context
import android.util.Log
import io.github.jsnjack.wallet.LOG_TAG
import io.github.jsnjack.wallet.core.Airports
import java.io.IOException

/** The bundled airport table, read from assets on first use. */
object AirportTable {
    private const val ASSET = "airports.tsv"

    @Volatile
    private var loaded: Airports? = null

    /** The table; reads about 60 kB the first time, so call it off the main thread once. */
    fun get(context: Context): Airports = loaded ?: synchronized(this) {
        loaded ?: read(context).also { loaded = it }
    }

    private fun read(context: Context): Airports = try {
        Airports.parse(context.assets.open(ASSET).use { it.readBytes().decodeToString() })
    } catch (e: IOException) {
        Log.w(LOG_TAG, "airport table could not be read: ${e.message}")
        Airports(emptyMap())
    }
}
