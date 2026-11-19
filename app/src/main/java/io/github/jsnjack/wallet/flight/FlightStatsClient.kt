package io.github.jsnjack.wallet.flight

import io.github.jsnjack.wallet.core.FlightRef
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/**
 * Fetches one flight from the data behind flightstats.com's flight tracker
 * pages: free and without a key, but not an official API, so it can change or
 * stop. Blocking: call from an IO thread. Requests carry only the airline,
 * the flight number and the date, under the app's own name and [version].
 */
class FlightStatsClient(private val version: String) {

    /** The tracker response for [ref]; an unknown flight comes back with empty data. */
    fun flight(ref: FlightRef): String {
        val date = ref.date
        val path = listOf(ref.carrier, ref.number).joinToString("/") { URLEncoder.encode(it, Charsets.UTF_8) }
        val url = "$BASE/$path/${date.year}/${date.monthValue}/${date.dayOfMonth}"
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "Wallet/$version (Android)")
            val status = conn.responseCode
            if (status != HttpURLConnection.HTTP_OK) throw IOException("flight lookup failed: HTTP $status")
            return conn.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val BASE = "https://www.flightstats.com/v2/api-next/flight-tracker"
        const val TIMEOUT_MS = 15_000
    }
}
