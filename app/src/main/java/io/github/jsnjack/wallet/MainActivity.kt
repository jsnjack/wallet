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

/** The only activity: hosts the Compose UI and routes shortcuts, widgets, notifications and shared files. */
class MainActivity : ComponentActivity() {
    private val vm: WalletViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        holdFirstFrameUntilReady()
        setContent { WalletTheme { WalletRoot(vm) } }
        if (savedInstanceState == null) route(intent)
    }

    override fun onStart() {
        super.onStart()
        // The date, permissions or cards from sync may have changed while the app was away.
        if (vm.ready.value) vm.refreshSurfaces()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        route(intent)
    }

    private fun route(intent: Intent) {
        when (intent.action) {
            Shortcuts.ACTION_SHOW_CARD -> intent.getStringExtra(Shortcuts.EXTRA_CARD_ID)?.let(vm::openFromShortcut)
            Shortcuts.ACTION_SCAN -> vm.openScanFromShortcut()
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                ?.takeIf { it.scheme == SCHEME_CONTENT }
                ?.let { vm.importFile(it, fromShare = true) }
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                ?.filter { it.scheme == SCHEME_CONTENT }
                ?.takeIf { it.isNotEmpty() }
                ?.let { vm.importScreenshots(it, fromShare = true) }
            Intent.ACTION_VIEW -> intent.data?.takeIf { it.scheme == SCHEME_CONTENT }?.let { vm.importFile(it, fromShare = true) }
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

    private companion object {
        const val SCHEME_CONTENT = "content"
    }
}
