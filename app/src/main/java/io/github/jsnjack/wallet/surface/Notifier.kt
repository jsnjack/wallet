package io.github.jsnjack.wallet.surface

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.jsnjack.wallet.MainActivity
import io.github.jsnjack.wallet.R
import io.github.jsnjack.wallet.core.Card
import io.github.jsnjack.wallet.core.FlightChange
import io.github.jsnjack.wallet.core.FlightRef
import io.github.jsnjack.wallet.core.FlightState
import io.github.jsnjack.wallet.core.LiveFlight
import io.github.jsnjack.wallet.core.UpNext
import io.github.jsnjack.wallet.core.UpNextReason
import io.github.jsnjack.wallet.flight.FlightText
import io.github.jsnjack.wallet.platform.Shortcuts
import io.github.jsnjack.wallet.store.AppSettings
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The ongoing "today" notification (a Live Update on Android 16 and newer,
 * shown in One UI's Now Bar), one-off expiry reminders and alerts when a
 * flight's gate, time or status changes. Notifications never contain card
 * numbers or PINs.
 */
object Notifier {
    private const val CHANNEL_TODAY = "today"
    private const val CHANNEL_REMINDERS = "reminders"
    private const val CHANNEL_FLIGHTS = "flights"
    private const val TODAY_ID = 1
    private const val REMINDER_ID_BASE = 1000
    private const val REMINDER_ID_RANGE = 100_000
    private const val FLIGHT_ID_BASE = 200_000

    /**
     * Shows [today] (or removes the notification), with [live] information
     * when it is a flight, and posts reminders not posted before.
     */
    fun update(context: Context, today: UpNext?, live: LiveFlight?, reminders: List<Card>, settings: AppSettings) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!settings.notifications.value || !manager.areNotificationsEnabled()) {
            manager.cancel(TODAY_ID)
            return
        }
        createChannels(context, manager)
        if (today == null) manager.cancel(TODAY_ID) else manager.notify(TODAY_ID, todayNotification(context, today, live))

        val due = reminders.associateBy { "${it.id}@${it.expires?.date}" }
        val posted = settings.postedReminders
        due.filterKeys { it !in posted }.values.forEach { card ->
            manager.notify(REMINDER_ID_BASE + Math.floorMod(card.id.hashCode(), REMINDER_ID_RANGE), reminder(context, card))
        }
        settings.postedReminders = due.keys
    }

    private fun createChannels(context: Context, manager: NotificationManager) {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_TODAY, context.getString(R.string.channel_today), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_today_description) },
                NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_reminders_description) },
                NotificationChannel(CHANNEL_FLIGHTS, context.getString(R.string.channel_flights), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = context.getString(R.string.channel_flights_description) },
            ),
        )
    }

    /** Alerts that [card]'s flight changed: a gate set or moved, a new departure time, or a cancellation. */
    fun flightChanged(context: Context, card: Card, live: LiveFlight, changes: Set<FlightChange>, settings: AppSettings) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!settings.notifications.value || !manager.areNotificationsEnabled()) return
        createChannels(context, manager)
        val text = listOfNotNull(
            FlightText.state(live.state)?.takeIf { FlightChange.CANCELED in changes }?.let(context::getString),
            live.departureGate?.takeIf { FlightChange.GATE in changes }?.let { context.getString(R.string.flight_gate_value, it) },
            live.departure?.takeIf { FlightChange.TIME in changes }?.let { context.getString(R.string.notify_departs_now, shortTime(it.toLocalTime())) },
        ).joinToString(" · ")
        val notification = Notification.Builder(context, CHANNEL_FLIGHTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(flightTitle(card))
            .setContentText(text)
            .setContentIntent(openCard(context, card.id))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        val key = FlightRef.of(card)?.id ?: card.id
        manager.notify(FLIGHT_ID_BASE + Math.floorMod(key.hashCode(), REMINDER_ID_RANGE), notification)
    }

    private fun todayNotification(context: Context, item: UpNext, live: LiveFlight?): Notification {
        val card = item.card
        val time = when (item.reason) {
            UpNextReason.FLIGHT -> live?.departure?.toLocalTime() ?: card.event?.time
            UpNextReason.EVENT_TODAY -> card.event?.time
            UpNextReason.EXPIRES_SOON -> card.expires?.time
        }
        val timeText = time?.let(::shortTime)
        val title = flightTitle(card)
        val text = when (item.reason) {
            UpNextReason.FLIGHT -> live?.let { liveText(context, it, timeText) }
                ?: timeText?.let { context.getString(R.string.notify_departs, it) }
                ?: context.getString(R.string.notify_flight_today)
            UpNextReason.EVENT_TODAY -> timeText?.let { context.getString(R.string.notify_event_at, it) }
                ?: context.getString(R.string.notify_event_today)
            UpNextReason.EXPIRES_SOON -> timeText?.let { context.getString(R.string.notify_expires_today_at, it) }
                ?: context.getString(R.string.notify_expires_today)
        }
        val builder = Notification.Builder(context, CHANNEL_TODAY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openCard(context, card.id))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(if (item.reason == UpNextReason.FLIGHT) Notification.CATEGORY_TRANSPORT else Notification.CATEGORY_EVENT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        // Live Updates arrived in Android 16 QPR2 (36.1).
        if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
            builder.setRequestPromotedOngoing(true)
            timeText?.let(builder::setShortCriticalText)
        }
        return builder.build()
    }

    // "Boarding · Gate D7 · Departs 14:35"; a cancelled flight says only that.
    private fun liveText(context: Context, live: LiveFlight, timeText: String?): String? {
        val state = FlightText.state(live.state)?.let(context::getString)
        if (live.state == FlightState.CANCELED || live.state == FlightState.DIVERTED) return state
        return listOfNotNull(
            state,
            live.departureGate?.let { context.getString(R.string.flight_gate_value, it) },
            timeText?.let { context.getString(R.string.up_next_departs, it) },
        ).joinToString(" · ").ifEmpty { null }
    }

    private fun flightTitle(card: Card): String = card.flight?.let { "${it.code} · ${it.from} → ${it.to}" } ?: card.name

    private fun shortTime(time: LocalTime): String = time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    private fun reminder(context: Context, card: Card): Notification =
        Notification.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notify_reminder_title, card.name))
            .setContentText(context.getString(R.string.notify_reminder_text))
            .setContentIntent(openCard(context, card.id))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()

    private fun openCard(context: Context, cardId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Shortcuts.ACTION_SHOW_CARD)
            .putExtra(Shortcuts.EXTRA_CARD_ID, cardId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            cardId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
