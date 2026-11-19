package io.github.jsnjack.wallet.flight

import androidx.annotation.StringRes
import io.github.jsnjack.wallet.R
import io.github.jsnjack.wallet.core.FlightState

/** Words for live flight information, shared by the code screen, the Now Bar and notifications. */
object FlightText {
    /** The label for [state], or null when the service did not say. */
    @StringRes
    fun state(state: FlightState): Int? = when (state) {
        FlightState.UNKNOWN -> null
        FlightState.EXPECTED -> R.string.flight_state_expected
        FlightState.CHECK_IN -> R.string.flight_state_check_in
        FlightState.BOARDING -> R.string.flight_state_boarding
        FlightState.GATE_CLOSED -> R.string.flight_state_gate_closed
        FlightState.DEPARTED -> R.string.flight_state_departed
        FlightState.EN_ROUTE -> R.string.flight_state_en_route
        FlightState.DELAYED -> R.string.flight_state_delayed
        FlightState.APPROACHING -> R.string.flight_state_approaching
        FlightState.ARRIVED -> R.string.flight_state_arrived
        FlightState.CANCELED -> R.string.flight_state_canceled
        FlightState.CANCELED_UNCERTAIN -> R.string.flight_state_canceled_uncertain
        FlightState.DIVERTED -> R.string.flight_state_diverted
    }

    /** True once the plane has left, so arrival details matter more than departure ones. */
    fun airborne(state: FlightState): Boolean =
        state == FlightState.DEPARTED || state == FlightState.EN_ROUTE || state == FlightState.APPROACHING || state == FlightState.ARRIVED
}
