package io.github.jsnjack.wallet

import android.app.Application
import androidx.work.Configuration
import io.github.jsnjack.wallet.drive.SyncController
import io.github.jsnjack.wallet.flight.FlightUpdater
import io.github.jsnjack.wallet.store.AppSettings
import io.github.jsnjack.wallet.store.CardStore
import io.github.jsnjack.wallet.store.FlightStore
import io.github.jsnjack.wallet.surface.Surfaces
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Logcat tag for every log line in the app (`make logs`). */
const val LOG_TAG = "Wallet"

/** Owns the card store, sync, flight lookups and home-screen surfaces, created on first use so app start stays light. */
class WalletApplication : Application(), Configuration.Provider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** All cards. */
    val store: CardStore by lazy { CardStore(this) { sync.onLocalChange() } }

    /** Archive and notification preferences, and the flight-status key. */
    val settings: AppSettings by lazy { AppSettings(this) }

    /** Drive sync for [store]; cards that arrive from sync refresh the surfaces. */
    val sync: SyncController by lazy { SyncController(this, store, settings) { scope.launch { surfaces.refresh() } } }

    /** Live flight results and this month's lookup count. */
    val flights: FlightStore by lazy { FlightStore(this) }

    /** Looks boarding passes' flights up; new results refresh the surfaces. */
    val flightUpdater: FlightUpdater by lazy { FlightUpdater(this, store, settings, flights) { surfaces.refresh() } }

    /** Notifications, widgets and the Quick Settings tile; each refresh also schedules the next flight lookup. */
    val surfaces: Surfaces by lazy { Surfaces(this, store, settings, flights) { flightUpdater.schedule() } }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
