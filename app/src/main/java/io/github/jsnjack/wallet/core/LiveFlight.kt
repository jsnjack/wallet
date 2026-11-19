package io.github.jsnjack.wallet.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.LocalDateTime

/** One flight on one day. Every boarding pass for it shares this, so one lookup serves all passengers. */
data class FlightRef(val carrier: String, val number: String, val from: String, val date: LocalDate) {
    /** The flight number a flight-status service looks up: "KL1691". */
    val lookupNumber: String get() = carrier + number

    /** A stable id for what a lookup found: "KL1691-AMS-2026-11-20". */
    val id: String get() = "$lookupNumber-$from-$date"

    companion object {
        /** The flight a boarding pass is for, or null when it has no flight number or date. */
        fun of(card: Card): FlightRef? {
            if (card.kind != CardKind.BOARDING_PASS) return null
            val flight = card.flight?.takeIf { it.number.isNotBlank() } ?: return null
            val date = card.event?.date ?: return null
            return FlightRef(flight.carrier.trim().uppercase(), flight.number.trim(), flight.from.trim().uppercase(), date)
        }
    }
}

/** Where a flight is in its day, as flight-status services report it. */
enum class FlightState {
    UNKNOWN, EXPECTED, CHECK_IN, BOARDING, GATE_CLOSED, DEPARTED, EN_ROUTE, DELAYED, APPROACHING, ARRIVED,
    CANCELED, CANCELED_UNCERTAIN, DIVERTED,
    ;

    /** True when nothing more will change, so lookups stop. */
    val final: Boolean get() = this == ARRIVED || this == CANCELED || this == DIVERTED
}

/**
 * Live information about one flight. Times are wall-clock times at the
 * airport, like the card's dates; null means the service did not say.
 */
@Serializable
data class LiveFlight(
    val state: FlightState,
    val departureGate: String? = null,
    val departureTerminal: String? = null,
    val checkInDesk: String? = null,
    @Serializable(with = LocalDateTimeText::class) val scheduledDeparture: LocalDateTime? = null,
    @Serializable(with = LocalDateTimeText::class) val expectedDeparture: LocalDateTime? = null,
    val arrivalGate: String? = null,
    val arrivalTerminal: String? = null,
    val baggageBelt: String? = null,
    @Serializable(with = LocalDateTimeText::class) val scheduledArrival: LocalDateTime? = null,
    @Serializable(with = LocalDateTimeText::class) val expectedArrival: LocalDateTime? = null,
) {
    /** The departure time to go by: the expected one, else the scheduled one. */
    val departure: LocalDateTime? get() = expectedDeparture ?: scheduledDeparture

    /** The arrival time to go by: the expected one, else the scheduled one. */
    val arrival: LocalDateTime? get() = expectedArrival ?: scheduledArrival
}

/** One lookup: when it ran ([checkedAt], epoch milliseconds) and what it found; null when the service knew no such flight. */
@Serializable
data class FlightCheck(val checkedAt: Long, val flight: LiveFlight?)

/** Why lookups stopped until something changes. */
enum class LookupProblem {
    /** The service refused the key; lookups wait for a new key. */
    KEY_REJECTED,

    /** This month's allowance is used up; lookups wait for next month. */
    LIMIT_REACHED,
}

/** Stores [LocalDateTime] as ISO text, such as "2026-11-20T14:10". */
object LocalDateTimeText : KSerializer<LocalDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDateTimeText", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDateTime = LocalDateTime.parse(decoder.decodeString())
}
