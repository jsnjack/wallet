package io.github.jsnjack.wallet.store

import android.content.Context
import androidx.core.content.edit
import io.github.jsnjack.wallet.core.CardOrder
import io.github.jsnjack.wallet.core.Retention
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Card order, archive, notification and live flight preferences, kept on
 * this device; Android backup and phone transfer carry them.
 */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _retention = MutableStateFlow(readRetention())

    /** How long archived cards are kept. */
    val retention: StateFlow<Retention> = _retention.asStateFlow()

    private val _notifications = MutableStateFlow(prefs.getBoolean(KEY_NOTIFICATIONS, true))

    /** Whether the app posts the "today" notification and expiry reminders. */
    val notifications: StateFlow<Boolean> = _notifications.asStateFlow()

    init {
        // A key for the earlier AeroDataBox service is a secret nothing uses any more; it leaves the phone and its backup.
        if (prefs.contains(KEY_OLD_FLIGHT_KEY)) prefs.edit { remove(KEY_OLD_FLIGHT_KEY) }
    }

    private val _liveFlights = MutableStateFlow(prefs.getBoolean(KEY_LIVE_FLIGHTS, true))

    /** Whether boarding passes look their flight up on flightstats.com; on unless turned off. */
    val liveFlights: StateFlow<Boolean> = _liveFlights.asStateFlow()

    /** Keys ("card id@expiry date") of reminders already posted, so each is posted once. */
    var postedReminders: Set<String>
        get() = prefs.getStringSet(KEY_POSTED_REMINDERS, emptySet()).orEmpty()
        set(value) = prefs.edit { putStringSet(KEY_POSTED_REMINDERS, value) }

    private val _order = MutableStateFlow(
        prefs.getString(KEY_ORDER, null)?.let { name -> CardOrder.entries.firstOrNull { it.name == name } } ?: CardOrder.NAME,
    )

    /** How the grid orders cards; A–Z unless the user chose most used. */
    val order: StateFlow<CardOrder> = _order.asStateFlow()

    private val _favouriteOrder = MutableStateFlow(prefs.getString(KEY_FAVOURITE_ORDER, null)?.split(',')?.filter { it.isNotEmpty() }.orEmpty())

    /** Card ids of favourites in the order the user dragged them into; favourites not in it follow A–Z. */
    val favouriteOrder: StateFlow<List<String>> = _favouriteOrder.asStateFlow()

    /** Keeps [ids] as the favourites' order. */
    fun setFavouriteOrder(ids: List<String>) {
        prefs.edit { putString(KEY_FAVOURITE_ORDER, ids.joinToString(",")) }
        _favouriteOrder.value = ids
    }

    /** Changes how the grid orders cards. */
    fun setOrder(value: CardOrder) {
        prefs.edit { putString(KEY_ORDER, value.name) }
        _order.value = value
    }

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

    /** Turns live flight info on or off. */
    fun setLiveFlights(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_LIVE_FLIGHTS, enabled) }
        _liveFlights.value = enabled
    }

    private fun readRetention(): Retention =
        prefs.getString(KEY_RETENTION, null)?.let { name -> Retention.entries.firstOrNull { it.name == name } } ?: Retention.ONE_YEAR

    private companion object {
        const val PREFS = "settings"
        const val KEY_RETENTION = "archive_retention"
        const val KEY_ORDER = "card_order"
        const val KEY_NOTIFICATIONS = "notifications"
        const val KEY_POSTED_REMINDERS = "posted_reminders"
        const val KEY_LIVE_FLIGHTS = "live_flights"
        const val KEY_FAVOURITE_ORDER = "favourite_order"
        const val KEY_OLD_FLIGHT_KEY = "flight_api_key"
    }
}
