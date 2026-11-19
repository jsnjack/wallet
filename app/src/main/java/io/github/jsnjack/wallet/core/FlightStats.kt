package io.github.jsnjack.wallet.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/**
 * Reads the flight tracker data that flightstats.com's own pages load: one
 * flight by airline, number and date, with gates, terminals, the baggage
 * belt, scheduled and estimated or actual times, and a status. It is not an
 * official API, so every field is optional and anything unexpected reads as
 * "no information".
 */
object FlightStats {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Response(val data: FlightDto? = null)

    @Serializable
    private data class FlightDto(
        val status: StatusDto? = null,
        val schedule: ScheduleDto? = null,
        val departureAirport: AirportDto? = null,
        val arrivalAirport: AirportDto? = null,
    )

    @Serializable
    private data class StatusDto(val statusCode: String? = null)

    // Times are local at each airport, like "2026-11-19T07:20:00.000"; the UTC ones end in "Z".
    @Serializable
    private data class ScheduleDto(
        val scheduledDeparture: String? = null,
        val scheduledDepartureUTC: String? = null,
        val estimatedActualDeparture: String? = null,
        val estimatedActualDepartureUTC: String? = null,
        val scheduledArrival: String? = null,
        val scheduledArrivalUTC: String? = null,
        val estimatedActualArrival: String? = null,
        val estimatedActualArrivalUTC: String? = null,
    )

    @Serializable
    private data class AirportDto(
        val iata: String? = null,
        val gate: String? = null,
        val terminal: String? = null,
        val baggage: String? = null,
    )

    /**
     * The flight in a tracker response, or null when it holds none (the site
     * answers an unknown flight, or one too far ahead, with empty data).
     * Throws [kotlinx.serialization.SerializationException] or
     * [IllegalArgumentException] when the response is not the expected JSON.
     */
    fun parse(body: String, ref: FlightRef): LiveFlight? {
        val flight = json.decodeFromString(Response.serializer(), body).data ?: return null
        val departure = flight.departureAirport
        // A different departure airport is another leg of the same flight number.
        if (departure?.iata != null && !departure.iata.equals(ref.from, ignoreCase = true)) return null
        val schedule = flight.schedule
        if (schedule?.scheduledDeparture == null && departure == null) return null
        val arrival = flight.arrivalAirport
        return LiveFlight(
            state = state(flight.status?.statusCode),
            departureGate = departure?.gate.clean(),
            departureTerminal = departure?.terminal.clean(),
            scheduledDeparture = time(schedule?.scheduledDeparture),
            expectedDeparture = time(schedule?.estimatedActualDeparture),
            arrivalGate = arrival?.gate.clean(),
            arrivalTerminal = arrival?.terminal.clean(),
            baggageBelt = arrival?.baggage.clean(),
            scheduledArrival = time(schedule?.scheduledArrival),
            expectedArrival = time(schedule?.estimatedActualArrival),
            scheduledDepartureUtc = instant(schedule?.scheduledDepartureUTC),
            expectedDepartureUtc = instant(schedule?.estimatedActualDepartureUTC),
            scheduledArrivalUtc = instant(schedule?.scheduledArrivalUTC),
            expectedArrivalUtc = instant(schedule?.estimatedActualArrivalUTC),
        )
    }

    /** A UTC time such as "2026-11-19T06:20:00.000Z", or null when missing or unreadable. */
    fun instant(text: String?): Instant? = text?.let {
        try {
            Instant.parse(it)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /** A local time such as "2026-11-19T07:20:00.000", or null when missing or unreadable. */
    fun time(text: String?): LocalDateTime? = text?.let {
        try {
            LocalDateTime.parse(it.take(LOCAL_DATE_TIME_LENGTH))
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    // FlightStats' status codes: scheduled, active (in the air), landed, cancelled, diverted, redirected, not operating.
    private fun state(code: String?): FlightState = when (code) {
        "S" -> FlightState.EXPECTED
        "A" -> FlightState.EN_ROUTE
        "L" -> FlightState.ARRIVED
        "C", "NO" -> FlightState.CANCELED
        "D", "R" -> FlightState.DIVERTED
        else -> FlightState.UNKNOWN
    }

    // "2026-11-19T07:20:00": the milliseconds are dropped.
    private const val LOCAL_DATE_TIME_LENGTH = 19
}
