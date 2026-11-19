package io.github.jsnjack.wallet.core

import java.time.LocalDate
import java.util.Locale

/** Why a card in the form cannot be saved yet. */
enum class DraftProblem { NAME_MISSING, CODE_MISSING, CODE_NOT_DRAWABLE, BALANCE_INVALID }

/**
 * A card being added or edited. [formatChosen] is true once the format came
 * from a scan or a tap, so typing no longer re-guesses it; a null [color]
 * follows the brand list or the name. [balanceText] is the balance as typed.
 */
data class CardDraft(
    val editingId: String? = null,
    val name: String = "",
    val code: String = "",
    val format: CodeFormat = CodeFormat.CODE_128,
    val formatChosen: Boolean = false,
    val kind: CardKind = CardKind.LOYALTY,
    val color: Int? = null,
    val note: String = "",
    val pinned: Boolean = false,
    val locked: Boolean = false,
    val createdAt: Long? = null,
    val pin: String = "",
    val expires: DayTime? = null,
    val event: DayTime? = null,
    val balanceText: String = "",
    val details: List<Detail> = emptyList(),
    val flight: Flight? = null,
    val usedAt: Long? = null,
) {
    /** The colour the card gets: the chosen one, the brand's, or one derived from the name. */
    val effectiveColor: Int
        get() = color ?: if (kind == CardKind.BOARDING_PASS) BOARDING_COLOR else Brands.exact(name)?.color ?: CardLooks.forName(name)

    /** The code as it will be stored. */
    val cleanCode: String get() = CodeNumbers.clean(code, format)

    /** The first reason the draft cannot be saved, or null when it can. */
    fun problem(): DraftProblem? = when {
        name.isBlank() -> DraftProblem.NAME_MISSING
        cleanCode.isEmpty() -> DraftProblem.CODE_MISSING
        !CodeMatrix.canEncode(cleanCode, format) -> DraftProblem.CODE_NOT_DRAWABLE
        balanceText.isNotBlank() && Money.parseCents(balanceText) == null -> DraftProblem.BALANCE_INVALID
        else -> null
    }

    /** The draft after the user typed [typed] as the code. */
    fun withCode(typed: String): CardDraft =
        copy(code = typed, format = if (formatChosen) format else CodeNumbers.guessFormat(typed))

    /** The card to store; [id] and [now] are used for a new card. */
    fun toCard(id: String, now: Long): Card = Card(
        id = editingId ?: id,
        name = name.trim(),
        code = cleanCode,
        format = format,
        kind = kind,
        color = effectiveColor,
        note = note.trim(),
        pinned = pinned,
        locked = locked,
        createdAt = createdAt ?: now,
        updatedAt = now,
        pin = pin.trim(),
        expires = expires,
        event = event,
        balanceCents = Money.parseCents(balanceText),
        usedAt = usedAt,
        details = details.map { Detail(it.label.trim(), it.value.trim()) }.filter { it.label.isNotEmpty() || it.value.isNotEmpty() },
        flight = flight,
    )

    companion object {
        /** Navy used for boarding passes, which rarely have a brand colour of their own. */
        const val BOARDING_COLOR = 0xFF0B1F3A.toInt()

        /**
         * A draft for a code read by the camera, an image or a PDF. A boarding
         * pass barcode fills in the flight; [today] places its date in a year.
         */
        fun scanned(code: String, format: CodeFormat, today: LocalDate): CardDraft {
            val leg = Bcbp.parse(code, today)?.legs?.firstOrNull()
                ?: return CardDraft(code = code, format = format, formatChosen = true)
            return CardDraft(
                name = leg.flight.code,
                code = code,
                format = format,
                formatChosen = true,
                kind = CardKind.BOARDING_PASS,
                event = DayTime(leg.date),
                flight = leg.flight,
            )
        }

        /** A draft that edits [card]; [locale] formats the balance for typing. */
        fun editing(card: Card, locale: Locale) = CardDraft(
            editingId = card.id,
            name = card.name,
            code = card.code,
            format = card.format,
            formatChosen = true,
            kind = card.kind,
            color = card.color,
            note = card.note,
            pinned = card.pinned,
            locked = card.locked,
            createdAt = card.createdAt,
            pin = card.pin,
            expires = card.expires,
            event = card.event,
            balanceText = card.balanceCents?.let { Money.editable(it, locale) }.orEmpty(),
            details = card.details,
            flight = card.flight,
            usedAt = card.usedAt,
        )
    }
}
