package io.github.jsnjack.wallet.surface

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.view.View
import android.widget.RemoteViews
import io.github.jsnjack.wallet.MainActivity
import io.github.jsnjack.wallet.R
import io.github.jsnjack.wallet.WalletApplication
import io.github.jsnjack.wallet.core.Card
import io.github.jsnjack.wallet.core.CardLooks
import io.github.jsnjack.wallet.core.UpNext
import io.github.jsnjack.wallet.core.UpNextReason
import io.github.jsnjack.wallet.platform.Shortcuts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Home screen widgets: "Up next" (4×1) and four favourite cards (4×2). */
object Widgets {
    private val SLOTS = listOf(
        Triple(R.id.fav_1, R.id.fav_1_monogram, R.id.fav_1_name),
        Triple(R.id.fav_2, R.id.fav_2_monogram, R.id.fav_2_name),
        Triple(R.id.fav_3, R.id.fav_3_monogram, R.id.fav_3_name),
        Triple(R.id.fav_4, R.id.fav_4_monogram, R.id.fav_4_name),
    )
    private const val OPEN_APP_REQUEST = 0
    private const val UP_NEXT_REQUEST = 1
    private const val FAVOURITE_REQUEST_BASE = 10

    /** Redraws every placed widget. */
    fun update(context: Context, upNext: UpNext?, favourites: List<Card>) {
        val manager = AppWidgetManager.getInstance(context)
        val upNextIds = manager.getAppWidgetIds(ComponentName(context, UpNextWidget::class.java))
        if (upNextIds.isNotEmpty()) manager.updateAppWidget(upNextIds, upNextViews(context, upNext))
        val favouriteIds = manager.getAppWidgetIds(ComponentName(context, FavouritesWidget::class.java))
        if (favouriteIds.isNotEmpty()) manager.updateAppWidget(favouriteIds, favouriteViews(context, favourites))
    }

    private fun upNextViews(context: Context, item: UpNext?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_up_next)
        if (item == null) {
            views.setTextViewText(R.id.up_next_badge, "")
            views.setColorStateList(R.id.up_next_badge, "setBackgroundTintList", ColorStateList.valueOf(context.getColor(R.color.surface_variant)))
            views.setTextViewText(R.id.up_next_label, context.getString(R.string.app_name))
            views.setTextViewText(R.id.up_next_title, context.getString(R.string.widget_nothing_today))
            views.setViewVisibility(R.id.up_next_detail, View.GONE)
            views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
            return views
        }
        val card = item.card
        views.setTextViewText(R.id.up_next_badge, CardLooks.monogram(card.name))
        views.setTextColor(R.id.up_next_badge, CardLooks.contentColor(card.color))
        views.setColorStateList(R.id.up_next_badge, "setBackgroundTintList", ColorStateList.valueOf(card.color))
        views.setTextViewText(R.id.up_next_label, context.getString(R.string.up_next_label))
        views.setTextViewText(R.id.up_next_title, card.flight?.let { "${it.code} · ${it.from} → ${it.to}" } ?: card.name)
        views.setViewVisibility(R.id.up_next_detail, View.VISIBLE)
        views.setTextViewText(
            R.id.up_next_detail,
            context.getString(
                when (item.reason) {
                    UpNextReason.FLIGHT -> R.string.up_next_flight
                    UpNextReason.EVENT_TODAY -> R.string.up_next_event
                    UpNextReason.STARTS_TODAY -> R.string.up_next_valid
                    UpNextReason.EXPIRES_SOON -> R.string.up_next_expires
                },
            ),
        )
        views.setOnClickPendingIntent(R.id.widget_root, openCard(context, card.id, UP_NEXT_REQUEST))
        return views
    }

    private fun favouriteViews(context: Context, cards: List<Card>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_favourites)
        views.setViewVisibility(R.id.fav_empty, if (cards.isEmpty()) View.VISIBLE else View.GONE)
        views.setOnClickPendingIntent(R.id.fav_empty, openApp(context))
        SLOTS.forEachIndexed { index, (slot, monogram, name) ->
            val card = cards.getOrNull(index)
            if (card == null) {
                views.setViewVisibility(slot, View.INVISIBLE)
                return@forEachIndexed
            }
            val text = CardLooks.contentColor(card.color)
            views.setViewVisibility(slot, View.VISIBLE)
            views.setColorStateList(slot, "setBackgroundTintList", ColorStateList.valueOf(card.color))
            views.setTextViewText(monogram, CardLooks.monogram(card.name))
            views.setTextColor(monogram, text)
            views.setTextViewText(name, card.name)
            views.setTextColor(name, text)
            views.setOnClickPendingIntent(slot, openCard(context, card.id, FAVOURITE_REQUEST_BASE + index))
        }
        return views
    }

    private fun openCard(context: Context, cardId: String, request: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Shortcuts.ACTION_SHOW_CARD)
            .putExtra(Shortcuts.EXTRA_CARD_ID, cardId)
        return PendingIntent.getActivity(context, request, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST,
            Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

/** Redraws all widgets through [io.github.jsnjack.wallet.surface.Surfaces.refresh]. */
abstract class WalletWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                (context.applicationContext as WalletApplication).surfaces.refresh()
            } finally {
                pending.finish()
            }
        }
    }
}

/** The 4×1 "Up next" widget. */
class UpNextWidget : WalletWidget()

/** The 4×2 favourite cards widget. */
class FavouritesWidget : WalletWidget()
