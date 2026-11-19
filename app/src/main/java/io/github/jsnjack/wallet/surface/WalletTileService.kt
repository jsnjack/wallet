package io.github.jsnjack.wallet.surface

import android.annotation.SuppressLint
import android.app.PendingIntent
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

/** Quick Settings tile: opens the "Up next" card, else the first favourite, else the grid. */
class WalletTileService : TileService() {
    private val scope = MainScope()

    override fun onStartListening() {
        scope.launch {
            val card = (application as WalletApplication).surfaces.tileCard()
            val tile = qsTile ?: return@launch
            tile.label = getString(R.string.app_name)
            tile.subtitle = card?.name ?: getString(R.string.tile_open)
            tile.state = if (card != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    override fun onClick() {
        scope.launch {
            val card = (application as WalletApplication).surfaces.tileCard()
            val intent = Intent(this@WalletTileService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (card != null) intent.setAction(Shortcuts.ACTION_SHOW_CARD).putExtra(Shortcuts.EXTRA_CARD_ID, card.id)
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
}
