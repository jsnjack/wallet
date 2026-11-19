package io.github.jsnjack.wallet.flight

import io.github.jsnjack.wallet.core.FlightRef
import io.github.jsnjack.wallet.core.LookupProblem
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/** A lookup the service refused for a reason that waiting will not fix. */
class LookupRefused(val problem: LookupProblem, message: String) : IOException(message)

/**
 * AeroDataBox through RapidAPI, with the user's own [key]. Blocking: call
 * from an IO thread. Requests carry only the flight number and date.
 */
class AeroDataBoxClient(private val key: String) {

    /** The status response for [ref], or null when the service has no such flight. */
    fun flight(ref: FlightRef): String? =
        get("$BASE/flights/number/${URLEncoder.encode(ref.lookupNumber, Charsets.UTF_8)}/${ref.date}?withAircraftImage=false&withLocation=false")

    /** Checks the key with the cheapest request there is, an airport by code. */
    fun checkKey() {
        get("$BASE/airports/iata/AMS")
    }

    private fun get(url: String): String? {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("X-RapidAPI-Key", key)
            conn.setRequestProperty("X-RapidAPI-Host", HOST)
            conn.setRequestProperty("Accept", "application/json")
            return when (val status = conn.responseCode) {
                HttpURLConnection.HTTP_OK -> conn.inputStream.use { it.readBytes().decodeToString() }
                HttpURLConnection.HTTP_NO_CONTENT, HttpURLConnection.HTTP_NOT_FOUND -> null
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                    throw LookupRefused(LookupProblem.KEY_REJECTED, "flight lookup refused: HTTP $status")
                TOO_MANY_REQUESTS -> {
                    // RapidAPI answers 429 both for the monthly quota and for more than one request a second.
                    val detail = conn.errorStream?.use { it.readBytes().decodeToString() }.orEmpty()
                    if (detail.contains("quota", ignoreCase = true)) {
                        throw LookupRefused(LookupProblem.LIMIT_REACHED, "flight lookup quota used up")
                    }
                    throw IOException("flight lookup rate-limited")
                }
                else -> throw IOException("flight lookup failed: HTTP $status")
            }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val HOST = "aerodatabox.p.rapidapi.com"
        const val BASE = "https://$HOST"
        const val TIMEOUT_MS = 15_000
        const val TOO_MANY_REQUESTS = 429
    }
}
