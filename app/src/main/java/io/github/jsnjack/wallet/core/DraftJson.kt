package io.github.jsnjack.wallet.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Form drafts as JSON, so a half-filled form and the import queue behind it
 * survive Android stopping the app in the background. Only kept for the
 * app's saved state, never in files, so the format can change freely.
 */
object DraftJson {
    @Serializable
    private data class DraftDto(
        val editingId: String? = null,
        val name: String = "",
        val code: String = "",
        val format: String,
        val formatChosen: Boolean = false,
        val kind: String,
        val color: Int? = null,
        val note: String = "",
        val pinned: Boolean = false,
        val locked: Boolean = false,
        val createdAt: Long? = null,
        val pin: String = "",
        val expires: String? = null,
        val event: String? = null,
        val balanceText: String = "",
        val details: List<DetailDto> = emptyList(),
        val flight: FlightDto? = null,
        val altFormat: String? = null,
        val validFrom: String? = null,
        val usedAt: Long? = null,
        val codeTyped: Boolean = false,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val list = ListSerializer(DraftDto.serializer())

    /** [drafts] as JSON. */
    fun encode(drafts: List<CardDraft>): String = json.encodeToString(list, drafts.map { it.toDto() })

    /** The drafts in [text]; empty when there is none or it cannot be read. */
    fun decode(text: String?): List<CardDraft> = try {
        text?.let { json.decodeFromString(list, it).mapNotNull { dto -> dto.toDraft() } }.orEmpty()
    } catch (e: SerializationException) {
        emptyList()
    } catch (e: IllegalArgumentException) {
        emptyList()
    }

    private fun CardDraft.toDto() = DraftDto(
        editingId = editingId,
        name = name,
        code = code,
        format = format.name,
        formatChosen = formatChosen,
        kind = kind.name,
        color = color,
        note = note,
        pinned = pinned,
        locked = locked,
        createdAt = createdAt,
        pin = pin,
        expires = expires?.toString(),
        event = event?.toString(),
        balanceText = balanceText,
        details = details.map { DetailDto(it.label, it.value) },
        flight = flight?.let { FlightDto(it.from, it.to, it.carrier, it.number, it.passenger, it.pnr, it.seat, it.cabin) },
        altFormat = altFormat?.name,
        validFrom = validFrom?.toString(),
        usedAt = usedAt,
        codeTyped = codeTyped,
    )

    private fun DraftDto.toDraft(): CardDraft? {
        val format = CodeFormat.entries.firstOrNull { it.name == format } ?: return null
        return CardDraft(
            editingId = editingId,
            name = name,
            code = code,
            format = format,
            formatChosen = formatChosen,
            kind = CardKind.entries.firstOrNull { it.name == kind } ?: CardKind.OTHER,
            color = color,
            note = note,
            pinned = pinned,
            locked = locked,
            createdAt = createdAt,
            pin = pin,
            expires = DayTime.parse(expires),
            event = DayTime.parse(event),
            balanceText = balanceText,
            details = details.map { Detail(it.label, it.value) },
            flight = flight?.let { Flight(it.from, it.to, it.carrier, it.number, it.passenger, it.pnr, it.seat, it.cabin) },
            altFormat = CodeFormat.entries.firstOrNull { it.name == altFormat },
            validFrom = DayTime.parse(validFrom),
            usedAt = usedAt,
            codeTyped = codeTyped,
        )
    }
}
