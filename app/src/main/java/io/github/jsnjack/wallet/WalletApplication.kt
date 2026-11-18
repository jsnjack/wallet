package io.github.jsnjack.wallet

import android.app.Application
import androidx.work.Configuration
import io.github.jsnjack.wallet.drive.SyncController
import io.github.jsnjack.wallet.store.CardStore

/** Logcat tag for every log line in the app (`make logs`). */
const val LOG_TAG = "Wallet"

/** Owns the card store and sync, created on first use so app start stays light. */
class WalletApplication : Application(), Configuration.Provider {
    /** All cards. */
    val store: CardStore by lazy { CardStore(this) { sync.onLocalChange() } }

    /** Drive sync for [store]. */
    val sync: SyncController by lazy { SyncController(this, store) }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
