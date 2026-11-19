package io.github.jsnjack.wallet.store

import android.content.Context
import androidx.core.content.edit
import io.github.jsnjack.wallet.core.FlightCheck
import io.github.jsnjack.wallet.core.LookupProblem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** This month's lookups: how many ran and, if lookups stopped, why. */
data class LookupUsage(val month: String, val used: Int, val problem: LookupProblem?)

/**
 * Flight lookups on this device: the latest result per flight (by
 * [io.github.jsnjack.wallet.core.FlightRef.id]) and the monthly count.
 * Nothing here syncs; results are short-lived and each device counts its own.
 */
class FlightStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _checks = MutableStateFlow(readChecks())

    /** The latest lookup per flight id. */
    val checks: StateFlow<Map<String, FlightCheck>> = _checks.asStateFlow()

    private val _usage = MutableStateFlow(readUsage())

    /** Lookups counted for the month they ran in. */
    val usage: StateFlow<LookupUsage> = _usage.asStateFlow()

    /** Before this time (epoch milliseconds) background lookups wait, after a failed one. */
    var retryAfter: Long
        get() = prefs.getLong(KEY_RETRY_AFTER, 0L)
        set(value) = prefs.edit { putLong(KEY_RETRY_AFTER, value) }

    /** This month's usage as of [month]: a new month starts at zero and forgets the used-up allowance. */
    fun usageIn(month: String): LookupUsage = _usage.value.let { usage ->
        if (usage.month == month) usage else LookupUsage(month, 0, usage.problem.takeIf { it == LookupProblem.KEY_REJECTED })
    }

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

    /** Counts one request against [month]. */
    fun countLookup(month: String) = writeUsage(usageIn(month).let { it.copy(used = it.used + 1) })

    /** Records why lookups stop in [month], or clears it with null. */
    fun setProblem(month: String, problem: LookupProblem?) = writeUsage(usageIn(month).copy(problem = problem))

    private fun writeUsage(usage: LookupUsage) {
        prefs.edit {
            putString(KEY_MONTH, usage.month)
            putInt(KEY_USED, usage.used)
            putString(KEY_PROBLEM, usage.problem?.name)
        }
        _usage.value = usage
    }

    private fun readUsage() = LookupUsage(
        month = prefs.getString(KEY_MONTH, null).orEmpty(),
        used = prefs.getInt(KEY_USED, 0),
        problem = prefs.getString(KEY_PROBLEM, null)?.let { name -> LookupProblem.entries.firstOrNull { it.name == name } },
    )

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
        const val KEY_MONTH = "lookup_month"
        const val KEY_USED = "lookups_used"
        const val KEY_PROBLEM = "lookup_problem"
        const val KEY_RETRY_AFTER = "retry_after"
    }
}
