package io.github.jsnjack.wallet.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Version 1 of the card file format, shared by Drive sync and the export file. */
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
)

@Serializable
private data class ExportBundle(
    @SerialName("v") val version: Int = CardJson.FORMAT_VERSION,
    val exportedAt: Long,
    val cards: List<CardDto>,
)

/** Converts cards to and from the JSON stored in Drive and in export files. */
object CardJson {
    /** Format version written into every file. */
    const val FORMAT_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** The JSON for one synced card file. */
    fun encode(card: Card): String = json.encodeToString(CardDto.serializer(), card.toDto())

    /** One synced card file, or null when it is unreadable or uses a format this version cannot draw. */
    fun decode(text: String): Card? = try {
        json.decodeFromString(CardDto.serializer(), text).toCard()
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

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
        )
    }
}
