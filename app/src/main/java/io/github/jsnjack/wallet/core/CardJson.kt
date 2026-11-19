package io.github.jsnjack.wallet.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The card format inside export files. Version 2 added the fields after
 * `deleted`, version 3 `altFormat`; older cards read with their defaults.
 */
@Serializable
data class CardDto(
    @SerialName("v") val version: Int = CardJson.FORMAT_VERSION,
    val id: String,
    val name: String,
    val code: String,
    val format: String,
    val kind: String,
    val color: String,
    val note: String = "",
    val pinned: Boolean = false,
    val locked: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val pin: String = "",
    val expires: String? = null,
    val event: String? = null,
    val balanceCents: Long? = null,
    val usedAt: Long? = null,
    val details: List<DetailDto> = emptyList(),
    val flight: FlightDto? = null,
    val altFormat: String? = null,
)

/** A [Detail] in the file format. */
@Serializable
data class DetailDto(val label: String, val value: String)

/** A [Flight] in the file format. */
@Serializable
data class FlightDto(
    val from: String,
    val to: String,
    val carrier: String,
    val number: String,
    val passenger: String = "",
    val pnr: String = "",
    val seat: String = "",
    val cabin: String = "",
)

@Serializable
private data class ExportBundle(
    @SerialName("v") val version: Int = CardJson.FORMAT_VERSION,
    val exportedAt: Long,
    val cards: List<CardDto>,
)

/** Converts cards to and from the JSON in export files and in database columns. */
object CardJson {
    /** Format version written into every card and export file. */
    const val FORMAT_VERSION = 3

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val detailList = ListSerializer(DetailDto.serializer())

    /** The JSON inside an export file. */
    fun encodeExport(cards: List<Card>, exportedAt: Long): String =
        json.encodeToString(ExportBundle.serializer(), ExportBundle(exportedAt = exportedAt, cards = cards.map { it.toDto() }))

    /**
     * The cards in an export file's JSON, skipping any this version cannot draw.
     * Throws [IllegalArgumentException] when the text is not an export.
     */
    fun decodeExport(text: String): List<Card> = try {
        json.decodeFromString(ExportBundle.serializer(), text).cards.mapNotNull { it.toCard() }
    } catch (e: SerializationException) {
        throw IllegalArgumentException("export file content is not a card list", e)
    }

    /** Details as stored in a database column. */
    fun encodeDetails(details: List<Detail>): String =
        json.encodeToString(detailList, details.map { DetailDto(it.label, it.value) })

    /** Details from a database column; empty when unreadable. */
    fun decodeDetails(text: String?): List<Detail> = try {
        text?.let { json.decodeFromString(detailList, it).map { d -> Detail(d.label, d.value) } }.orEmpty()
    } catch (e: SerializationException) {
        emptyList()
    }

    /** A flight as stored in a database column. */
    fun encodeFlight(flight: Flight?): String? = flight?.let { json.encodeToString(FlightDto.serializer(), it.toDto()) }

    /** A flight from a database column; null when absent or unreadable. */
    fun decodeFlight(text: String?): Flight? = try {
        text?.let { json.decodeFromString(FlightDto.serializer(), it).toFlight() }
    } catch (e: SerializationException) {
        null
    }

    private fun Card.toDto() = CardDto(
        id = id,
        name = name,
        code = code,
        format = format.name,
        kind = kind.name,
        color = CardLooks.toHex(color),
        note = note,
        pinned = pinned,
        locked = locked,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deleted = deleted,
        pin = pin,
        expires = expires?.toString(),
        event = event?.toString(),
        balanceCents = balanceCents,
        usedAt = usedAt,
        details = details.map { DetailDto(it.label, it.value) },
        flight = flight?.toDto(),
        altFormat = altFormat?.name,
    )

    private fun CardDto.toCard(): Card? {
        val format = CodeFormat.entries.firstOrNull { it.name == format } ?: return null
        return Card(
            id = id,
            name = name,
            code = code,
            format = format,
            kind = CardKind.entries.firstOrNull { it.name == kind } ?: CardKind.OTHER,
            color = CardLooks.parseHex(color) ?: CardLooks.forName(name),
            note = note,
            pinned = pinned,
            locked = locked,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deleted = deleted,
            pin = pin,
            expires = DayTime.parse(expires),
            event = DayTime.parse(event),
            balanceCents = balanceCents,
            usedAt = usedAt,
            details = details.map { Detail(it.label, it.value) },
            flight = flight?.toFlight(),
            altFormat = CodeFormat.entries.firstOrNull { it.name == altFormat },
        )
    }

    private fun Flight.toDto() = FlightDto(from, to, carrier, number, passenger, pnr, seat, cabin)

    private fun FlightDto.toFlight() = Flight(from, to, carrier, number, passenger, pnr, seat, cabin)
}
