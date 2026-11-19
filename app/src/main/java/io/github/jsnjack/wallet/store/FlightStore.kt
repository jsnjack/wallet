package io.github.jsnjack.wallet.store

import android.content.Context
import androidx.core.content.edit
import io.github.jsnjack.wallet.core.FlightCheck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Flight lookups on this device: the latest result per flight, by
 * [io.github.jsnjack.wallet.core.FlightRef.id]. Results are short-lived and
 * stay on the device.
 */
class FlightStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    init {
        // The monthly count and refusal of the earlier keyed service mean nothing now.
        if (OLD_KEYS.any(prefs::contains)) prefs.edit { OLD_KEYS.forEach(::remove) }
    }

    private val _checks = MutableStateFlow(readChecks())

    /** The latest lookup per flight id. */
    val checks: StateFlow<Map<String, FlightCheck>> = _checks.asStateFlow()

    /** Before this time (epoch milliseconds) background lookups wait, after a failed one. */
    var retryAfter: Long
        get() = prefs.getLong(KEY_RETRY_AFTER, 0L)
        set(value) = prefs.edit { putLong(KEY_RETRY_AFTER, value) }

    /** Saves the result for flight [flightId]. */
    fun save(flightId: String, check: FlightCheck) {
        prefs.edit { putString(CHECK_PREFIX + flightId, json.encodeToString(FlightCheck.serializer(), check)) }
        _checks.value = _checks.value + (flightId to check)
    }

    /** Drops results for flights that are no longer current. */
    fun keepOnly(flightIds: Set<String>) {
        val gone = _checks.value.keys - flightIds
        if (gone.isEmpty()) return
        prefs.edit { gone.forEach { remove(CHECK_PREFIX + it) } }
        _checks.value = _checks.value - gone
    }

    private fun readChecks(): Map<String, FlightCheck> = prefs.all.mapNotNull { (key, value) ->
        if (!key.startsWith(CHECK_PREFIX) || value !is String) return@mapNotNull null
        try {
            key.removePrefix(CHECK_PREFIX) to json.decodeFromString(FlightCheck.serializer(), value)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }.toMap()

    private companion object {
        const val PREFS = "flights"
        const val CHECK_PREFIX = "check:"
        const val KEY_RETRY_AFTER = "retry_after"
        val OLD_KEYS = listOf("lookup_month", "lookups_used", "lookup_problem")
    }
}
