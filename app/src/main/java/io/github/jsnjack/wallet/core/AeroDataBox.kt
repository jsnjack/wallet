package io.github.jsnjack.wallet.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Reads AeroDataBox's flight status response: a JSON array of flights with
 * the same number around the requested date. Only the fields the app shows
 * are read.
 */
object AeroDataBox {
    private val json = Json { ignoreUnknownKeys = true }
    private val flights = ListSerializer(FlightDto.serializer())

    // "2026-11-20 14:10+01:00" or "2026-11-20T14:10:00+01:00": the local date and time come first.
    private val LOCAL_TIME = Regex("""^(\d{4}-\d{2}-\d{2})[ T](\d{2}):(\d{2})""")

    @Serializable
    private data class FlightDto(
        val status: String? = null,
        val isCargo: Boolean = false,
        val departure: MovementDto? = null,
        val arrival: MovementDto? = null,
    )

    @Serializable
    private data class MovementDto(
        val airport: AirportDto? = null,
        val scheduledTime: TimeDto? = null,
        val revisedTime: TimeDto? = null,
        val predictedTime: TimeDto? = null,
        val terminal: String? = null,
        val checkInDesk: String? = null,
        val gate: String? = null,
        val baggageBelt: String? = null,
    )

    @Serializable
    private data class AirportDto(val iata: String? = null)

    @Serializable
    private data class TimeDto(val local: String? = null)

    /**
     * The flight leaving [ref]'s airport on its date, or null when the
     * response holds none. Throws [kotlinx.serialization.SerializationException]
     * or [IllegalArgumentException] when the response is not the expected JSON.
     */
    fun parse(body: String, ref: FlightRef): LiveFlight? {
        val all = json.decodeFromString(flights, body).filterNot { it.isCargo }
        val fromHere = all.filter { it.departure?.airport?.iata.equals(ref.from, ignoreCase = true) }
        val flight = fromHere.firstOrNull { localTime(it.departure?.scheduledTime?.local)?.toLocalDate() == ref.date }
            ?: fromHere.firstOrNull()
            ?: all.firstOrNull()
            ?: return null
        val departure = flight.departure
        val arrival = flight.arrival
        return LiveFlight(
            state = state(flight.status),
            departureGate = departure?.gate.clean(),
            departureTerminal = departure?.terminal.clean(),
            checkInDesk = departure?.checkInDesk.clean(),
            scheduledDeparture = localTime(departure?.scheduledTime?.local),
            expectedDeparture = expected(departure),
            arrivalGate = arrival?.gate.clean(),
            arrivalTerminal = arrival?.terminal.clean(),
            baggageBelt = arrival?.baggageBelt.clean(),
            scheduledArrival = localTime(arrival?.scheduledTime?.local),
            expectedArrival = expected(arrival),
        )
    }

    /** The local wall-clock time in an AeroDataBox time, or null when it has none. */
    fun localTime(text: String?): LocalDateTime? {
        val match = text?.let { LOCAL_TIME.find(it.trim()) } ?: return null
        val (date, hour, minute) = match.destructured
        return LocalDateTime.of(LocalDate.parse(date), LocalTime.of(hour.toInt(), minute.toInt()))
    }

    // A revised time comes from the airline or airport; a predicted one is AeroDataBox's own estimate.
    private fun expected(movement: MovementDto?): LocalDateTime? =
        localTime(movement?.revisedTime?.local) ?: localTime(movement?.predictedTime?.local)

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun state(status: String?): FlightState = when (status) {
        "Expected" -> FlightState.EXPECTED
        "CheckIn" -> FlightState.CHECK_IN
        "Boarding" -> FlightState.BOARDING
        "GateClosed" -> FlightState.GATE_CLOSED
        "Departed" -> FlightState.DEPARTED
        "EnRoute" -> FlightState.EN_ROUTE
        "Delayed" -> FlightState.DELAYED
        "Approaching" -> FlightState.APPROACHING
        "Arrived" -> FlightState.ARRIVED
        "Canceled" -> FlightState.CANCELED
        "CanceledUncertain" -> FlightState.CANCELED_UNCERTAIN
        "Diverted" -> FlightState.DIVERTED
        else -> FlightState.UNKNOWN
    }
}
