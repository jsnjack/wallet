package io.github.jsnjack.wallet.store

import android.content.Context
import androidx.core.content.edit
import io.github.jsnjack.wallet.core.Retention
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Archive and notification preferences, kept on this device. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _retention = MutableStateFlow(readRetention())

    /** How long archived cards are kept. */
    val retention: StateFlow<Retention> = _retention.asStateFlow()

    private val _notifications = MutableStateFlow(prefs.getBoolean(KEY_NOTIFICATIONS, true))

    /** Whether the app posts the "today" notification and expiry reminders. */
    val notifications: StateFlow<Boolean> = _notifications.asStateFlow()

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

    private fun readRetention(): Retention =
        prefs.getString(KEY_RETENTION, null)?.let { name -> Retention.entries.firstOrNull { it.name == name } } ?: Retention.ONE_YEAR

    private companion object {
        const val PREFS = "settings"
        const val KEY_RETENTION = "archive_retention"
        const val KEY_NOTIFICATIONS = "notifications"
        const val KEY_POSTED_REMINDERS = "posted_reminders"
    }
}
