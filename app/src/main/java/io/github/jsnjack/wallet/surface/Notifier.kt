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
import io.github.jsnjack.wallet.core.UpNext
import io.github.jsnjack.wallet.core.UpNextReason
import io.github.jsnjack.wallet.platform.Shortcuts
import io.github.jsnjack.wallet.store.AppSettings
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The ongoing "today" notification (a Live Update on Android 16 and newer,
 * shown in One UI's Now Bar) and one-off expiry reminders. Notifications
 * never contain card numbers or PINs.
 */
object Notifier {
    private const val CHANNEL_TODAY = "today"
    private const val CHANNEL_REMINDERS = "reminders"
    private const val TODAY_ID = 1
    private const val REMINDER_ID_BASE = 1000
    private const val REMINDER_ID_RANGE = 100_000

    /** Shows [today] (or removes the notification) and posts reminders not posted before. */
    fun update(context: Context, today: UpNext?, reminders: List<Card>, settings: AppSettings) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!settings.notifications.value || !manager.areNotificationsEnabled()) {
            manager.cancel(TODAY_ID)
            return
        }
        createChannels(context, manager)
        if (today == null) manager.cancel(TODAY_ID) else manager.notify(TODAY_ID, todayNotification(context, today))

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
            ),
        )
    }

    private fun todayNotification(context: Context, item: UpNext): Notification {
        val card = item.card
        val time = when (item.reason) {
            UpNextReason.FLIGHT, UpNextReason.EVENT_TODAY -> card.event?.time
            UpNextReason.EXPIRES_SOON -> card.expires?.time
        }
        val timeText = time?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        val title = card.flight?.let { "${it.code} · ${it.from} → ${it.to}" } ?: card.name
        val text = when (item.reason) {
            UpNextReason.FLIGHT -> timeText?.let { context.getString(R.string.notify_departs, it) }
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
