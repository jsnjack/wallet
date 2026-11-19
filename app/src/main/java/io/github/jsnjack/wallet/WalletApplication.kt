package io.github.jsnjack.wallet

import android.app.Application
import androidx.work.Configuration
import io.github.jsnjack.wallet.flight.FlightUpdater
import io.github.jsnjack.wallet.store.AppSettings
import io.github.jsnjack.wallet.store.CardStore
import io.github.jsnjack.wallet.store.FlightStore
import io.github.jsnjack.wallet.surface.Surfaces

/** Logcat tag for every log line in the app (`make logs`). */
const val LOG_TAG = "Wallet"

/** Owns the card store, flight lookups and home-screen surfaces, created on first use so app start stays light. */
class WalletApplication : Application(), Configuration.Provider {
    /** All cards. */
    val store: CardStore by lazy { CardStore(this) }

    /** Card order, archive, notification and live flight preferences. */
    val settings: AppSettings by lazy { AppSettings(this) }

    /** The latest flight lookup per flight, and when to retry after a failure. */
    val flights: FlightStore by lazy { FlightStore(this) }

    /** Looks boarding passes' flights up; new results refresh the surfaces. */
    val flightUpdater: FlightUpdater by lazy { FlightUpdater(this, store, settings, flights) { surfaces.refresh() } }

    /** Notifications, widgets and the Quick Settings tile; each refresh also schedules the next flight lookup. */
    val surfaces: Surfaces by lazy { Surfaces(this, store, settings, flights) { flightUpdater.schedule() } }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
