package io.github.jsnjack.wallet.core

/** Why a card in the form cannot be saved yet. */
enum class DraftProblem { NAME_MISSING, CODE_MISSING, CODE_NOT_DRAWABLE }

/**
 * A card being added or edited. [formatChosen] is true once the format came
 * from a scan or a tap, so typing no longer re-guesses it; a null [color]
 * follows the brand list or the name.
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
) {
    /** The colour the card gets: the chosen one, the brand's, or one derived from the name. */
    val effectiveColor: Int get() = color ?: Brands.exact(name)?.color ?: CardLooks.forName(name)

    /** The code as it will be stored. */
    val cleanCode: String get() = CodeNumbers.clean(code, format)

    /** The first reason the draft cannot be saved, or null when it can. */
    fun problem(): DraftProblem? = when {
        name.isBlank() -> DraftProblem.NAME_MISSING
        cleanCode.isEmpty() -> DraftProblem.CODE_MISSING
        !CodeMatrix.canEncode(cleanCode, format) -> DraftProblem.CODE_NOT_DRAWABLE
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
    )

    companion object {
        /** A draft for a code read by the camera or from an image. */
        fun scanned(code: String, format: CodeFormat) = CardDraft(code = code, format = format, formatChosen = true)

        /** A draft that edits [card]. */
        fun editing(card: Card) = CardDraft(
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
        )
    }
}
