package io.github.jsnjack.wallet.surface

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.jsnjack.wallet.MainActivity
import io.github.jsnjack.wallet.R
import io.github.jsnjack.wallet.WalletApplication
import io.github.jsnjack.wallet.platform.Shortcuts
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick Settings tile: opens the "Up next" card, or the grid when there is none. */
class WalletTileService : TileService() {
    private val scope = MainScope()

    override fun onStartListening() {
        scope.launch {
            val next = (application as WalletApplication).surfaces.upNextNow()
            val tile = qsTile ?: return@launch
            tile.label = getString(R.string.app_name)
            tile.subtitle = next?.card?.name ?: getString(R.string.tile_open)
            tile.state = if (next != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    override fun onClick() {
        scope.launch {
            val next = (application as WalletApplication).surfaces.upNextNow()
            val intent = Intent(this@WalletTileService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (next != null) intent.setAction(Shortcuts.ACTION_SHOW_CARD).putExtra(Shortcuts.EXTRA_CARD_ID, next.card.id)
            val pending = PendingIntent.getActivity(
                this@WalletTileService,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val open = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) startActivityAndCollapse(pending) else openBeforeAndroid14(intent) }
            if (isLocked) unlockAndRun(open) else open()
        }
    }

    // Android 13 has only the Intent form, deprecated from Android 14 on.
    @Suppress("DEPRECATION")
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openBeforeAndroid14(intent: Intent) = startActivityAndCollapse(intent)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Asks the system to refresh the tile, for example after the agenda changed. */
        fun requestUpdate(context: Context) {
            requestListeningState(context, ComponentName(context, WalletTileService::class.java))
        }
    }
}
