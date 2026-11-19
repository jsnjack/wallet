package io.github.jsnjack.wallet.platform

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.util.Log
import androidx.core.graphics.createBitmap
import io.github.jsnjack.wallet.LOG_TAG
import io.github.jsnjack.wallet.MainActivity
import io.github.jsnjack.wallet.core.Card
import io.github.jsnjack.wallet.core.CardLooks

/** Launcher shortcuts: the most used cards on a long press, and cards pinned to the home screen. */
object Shortcuts {
    /** Intent action that opens one card's code screen. */
    const val ACTION_SHOW_CARD = "io.github.jsnjack.wallet.action.SHOW_CARD"

    /** Intent action that opens the scanner (static shortcut in res/xml/shortcuts.xml). */
    const val ACTION_SCAN = "io.github.jsnjack.wallet.action.SCAN"

    /** Card id extra for [ACTION_SHOW_CARD]. */
    const val EXTRA_CARD_ID = "card_id"

    private const val ID_PREFIX = "card-"
    private const val ICON_PX = 216
    private const val TEXT_SHARE = 0.26f

    /**
     * Shows [top] as the long-press shortcuts and refreshes pinned shortcuts
     * from [all]: renamed cards update, deleted ones are disabled.
     */
    fun update(context: Context, top: List<Card>, all: List<Card>) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        try {
            val byId = all.associateBy { ID_PREFIX + it.id }
            val pinned = manager.pinnedShortcuts.filter { it.id.startsWith(ID_PREFIX) }
            val (live, gone) = pinned.partition { it.id in byId }
            // A card restored after a delete gets its greyed-out home-screen icon back.
            val restored = live.filterNot { it.isEnabled }.map { it.id }
            if (restored.isNotEmpty()) manager.enableShortcuts(restored)
            // Both return false when the launcher's rate limit for background apps is hit; the next change retries.
            if (!manager.setDynamicShortcuts(top.mapIndexed { rank, card -> info(context, card, rank) })) {
                Log.i(LOG_TAG, "shortcut update skipped: rate limited")
            }
            if (live.isNotEmpty() && !manager.updateShortcuts(live.map { info(context, byId.getValue(it.id), rank = null) })) {
                Log.i(LOG_TAG, "pinned shortcut update skipped: rate limited")
            }
            if (gone.isNotEmpty()) manager.disableShortcuts(gone.map { it.id })
        } catch (e: IllegalStateException) {
            // Thrown while the phone is locked after a restart.
            Log.w(LOG_TAG, "shortcut update skipped: ${e.message}")
        } catch (e: IllegalArgumentException) {
            Log.w(LOG_TAG, "shortcut update refused: ${e.message}")
        }
    }

    /** Asks the launcher to pin [card] to the home screen; false when it does not support pinning or refuses. */
    fun requestPin(context: Context, card: Card): Boolean {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!manager.isRequestPinShortcutSupported) return false
        return try {
            // The launcher refuses to pin a shortcut that was disabled when the card was deleted, so enable it first.
            manager.enableShortcuts(listOf(ID_PREFIX + card.id))
            manager.requestPinShortcut(info(context, card, rank = null), null)
        } catch (e: IllegalArgumentException) {
            Log.w(LOG_TAG, "pin request refused: ${e.message}")
            false
        } catch (e: IllegalStateException) {
            Log.w(LOG_TAG, "pin request refused: ${e.message}")
            false
        }
    }

    /** Tells the launcher [cardId] was used, which improves its own suggestions. */
    fun reportUsed(context: Context, cardId: String) {
        context.getSystemService(ShortcutManager::class.java)?.reportShortcutUsed(ID_PREFIX + cardId)
    }

    // A null [rank] leaves a pinned shortcut where it is in the launcher's long-press menu.
    private fun info(context: Context, card: Card, rank: Int?): ShortcutInfo {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_SHOW_CARD)
            .putExtra(EXTRA_CARD_ID, card.id)
        val builder = ShortcutInfo.Builder(context, ID_PREFIX + card.id)
            .setShortLabel(card.name)
            .setLongLabel(card.name)
            .setIcon(Icon.createWithAdaptiveBitmap(icon(card)))
            .setIntent(intent)
        if (rank != null) builder.setRank(rank)
        return builder.build()
    }

    private fun icon(card: Card): Bitmap {
        val bitmap = createBitmap(ICON_PX, ICON_PX)
        val canvas = Canvas(bitmap)
        canvas.drawColor(card.color)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = CardLooks.contentColor(card.color)
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = ICON_PX * TEXT_SHARE
        }
        val label = CardLooks.monogram(card.name)
        // Adaptive icons show the middle two thirds; shrink long labels to fit it.
        val maxWidth = ICON_PX * 0.55f
        val width = paint.measureText(label)
        if (width > maxWidth) paint.textSize *= maxWidth / width
        val baseline = ICON_PX / 2f - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(label, ICON_PX / 2f, baseline, paint)
        return bitmap
    }
}
