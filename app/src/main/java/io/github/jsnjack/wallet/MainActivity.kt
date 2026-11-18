package io.github.jsnjack.wallet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.jsnjack.wallet.platform.Shortcuts
import io.github.jsnjack.wallet.ui.WalletRoot
import io.github.jsnjack.wallet.ui.WalletTheme
import io.github.jsnjack.wallet.ui.WalletViewModel

/** The only activity: hosts the Compose UI and routes launcher shortcuts and shared images. */
class MainActivity : ComponentActivity() {
    private val vm: WalletViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        holdFirstFrameUntilReady()
        setContent { WalletTheme { WalletRoot(vm) } }
        if (savedInstanceState == null) route(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        route(intent)
    }

    private fun route(intent: Intent) {
        when (intent.action) {
            Shortcuts.ACTION_SHOW_CARD -> intent.getStringExtra(Shortcuts.EXTRA_CARD_ID)?.let(vm::openFromShortcut)
            Shortcuts.ACTION_SCAN -> vm.openScanFromShortcut()
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                if (intent.type?.startsWith("image/") == true && uri?.scheme == "content") vm.readImage(uri, fromShare = true)
            }
        }
    }

    // Cards load in a few milliseconds; waiting avoids drawing an empty grid first.
    private fun holdFirstFrameUntilReady() {
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!vm.ready.value) return false
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            },
        )
    }
}
