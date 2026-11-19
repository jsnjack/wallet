package io.github.jsnjack.wallet.store

import android.content.Context
import androidx.core.content.edit
import io.github.jsnjack.wallet.core.Retention
import io.github.jsnjack.wallet.core.SyncedSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Archive and notification preferences, kept on this device, and the
 * flight-status key, which also syncs through Drive ([SyncedSettings]).
 * Android backup and phone transfer carry all of them.
 */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _retention = MutableStateFlow(readRetention())

    /** How long archived cards are kept. */
    val retention: StateFlow<Retention> = _retention.asStateFlow()

    private val _notifications = MutableStateFlow(prefs.getBoolean(KEY_NOTIFICATIONS, true))

    /** Whether the app posts the "today" notification and expiry reminders. */
    val notifications: StateFlow<Boolean> = _notifications.asStateFlow()

    private val _flightKey = MutableStateFlow(prefs.getString(KEY_FLIGHT_KEY, null).orEmpty())

    /** The user's RapidAPI key for AeroDataBox; empty when live flight info is off. Never logged. */
    val flightKey: StateFlow<String> = _flightKey.asStateFlow()

    /** Keys ("card id@expiry date") of reminders already posted, so each is posted once. */
    var postedReminders: Set<String>
        get() = prefs.getStringSet(KEY_POSTED_REMINDERS, emptySet()).orEmpty()
        set(value) = prefs.edit { putStringSet(KEY_POSTED_REMINDERS, value) }

    /** Changes how long archived cards are kept. */
    fun setRetention(value: Retention) {
        prefs.edit { putString(KEY_RETENTION, value.name) }
        _retention.value = value
    }

    /** Turns notifications on or off. */
    fun setNotifications(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_NOTIFICATIONS, enabled) }
        _notifications.value = enabled
    }

    /** Sets the flight-status key at [now]; the newer key wins on other devices. */
    fun setFlightKey(key: String, now: Long) = applySynced(SyncedSettings(flightApiKey = key.trim(), updatedAt = now))

    /** The settings that sync through Drive, with the time they last changed here (0 when never). */
    fun synced(): SyncedSettings = SyncedSettings(flightApiKey = _flightKey.value, updatedAt = prefs.getLong(KEY_SYNCED_AT, 0L))

    /** Takes settings that arrived through sync, or were changed here. */
    fun applySynced(settings: SyncedSettings) {
        prefs.edit {
            putString(KEY_FLIGHT_KEY, settings.flightApiKey)
            putLong(KEY_SYNCED_AT, settings.updatedAt)
        }
        _flightKey.value = settings.flightApiKey
    }

    private fun readRetention(): Retention =
        prefs.getString(KEY_RETENTION, null)?.let { name -> Retention.entries.firstOrNull { it.name == name } } ?: Retention.ONE_YEAR

    private companion object {
        const val PREFS = "settings"
        const val KEY_RETENTION = "archive_retention"
        const val KEY_NOTIFICATIONS = "notifications"
        const val KEY_POSTED_REMINDERS = "posted_reminders"
        const val KEY_FLIGHT_KEY = "flight_api_key"
        const val KEY_SYNCED_AT = "synced_settings_at"
    }
}
