package io.github.jsnjack.wallet.core

/** What a card is used for; decides its label, form fields and code screen. */
enum class CardKind { LOYALTY, MEMBERSHIP, VOUCHER, GIFT_CARD, TICKET, BOARDING_PASS, OTHER }

/**
 * Barcode symbologies the app can read and draw. A [linear] code is one row
 * of bars, drawn stretched to a fixed height; the others are 2D matrices.
 */
enum class CodeFormat(val label: String, val linear: Boolean) {
    QR("QR", false),
    AZTEC("Aztec", false),
    PDF_417("PDF417", false),
    DATA_MATRIX("Data Matrix", false),
    EAN_13("EAN-13", true),
    EAN_8("EAN-8", true),
    UPC_A("UPC-A", true),
    UPC_E("UPC-E", true),
    CODE_128("Code 128", true),
    CODE_39("Code 39", true),
    CODE_93("Code 93", true),
    ITF("ITF", true),
    CODABAR("Codabar", true),
    ;

    /** QR, Aztec and Data Matrix: drawn as a square, unlike barcodes and the wide PDF417. */
    val square: Boolean get() = !linear && this != PDF_417
}

/** A labelled line shown on the code screen, such as "Gate · D7" or "Row · 8". */
data class Detail(val label: String, val value: String)

/** One flight leg of a boarding pass; codes are as printed (IATA airport and airline codes). */
data class Flight(
    val from: String,
    val to: String,
    val carrier: String,
    val number: String,
    val passenger: String = "",
    val pnr: String = "",
    val seat: String = "",
    val cabin: String = "",
) {
    /** "KL 1691". */
    val code: String get() = listOf(carrier, number).filter { it.isNotBlank() }.joinToString(" ")
}

/**
 * One stored card. [code] is the exact text inside the barcode and [color]
 * an ARGB brand colour. [validFrom] starts a voucher's, membership's or
 * ticket's validity and [expires] ends it; [event] is a ticket's or flight's
 * date. A card with [usedAt] set is in the
 * archive. [altFormat] is a second format the same code was printed in,
 * such as a ticket's barcode beside its QR code; the code screen can switch
 * to it. A deleted card stays as a blank tombstone, hidden everywhere; undo
 * saves the card again.
 */
data class Card(
    val id: String,
    val name: String,
    val code: String,
    val format: CodeFormat,
    val kind: CardKind,
    val color: Int,
    val note: String,
    /** True for a favourite (stored as "pinned" in the file format and database). */
    val pinned: Boolean,
    val locked: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val pin: String = "",
    val expires: DayTime? = null,
    val event: DayTime? = null,
    val balanceCents: Long? = null,
    val usedAt: Long? = null,
    val details: List<Detail> = emptyList(),
    val flight: Flight? = null,
    val altFormat: CodeFormat? = null,
    val validFrom: DayTime? = null,
) {
    /** The tombstone that replaces this card when it is deleted at [time]. */
    fun tombstone(time: Long) = Card(
        id = id, name = "", code = "", format = format, kind = kind, color = color, note = "",
        pinned = false, locked = false, createdAt = createdAt, updatedAt = time, deleted = true,
    )
}

/** How often and how recently a card was shown on this device. */
data class Usage(val count: Int, val lastUsedAt: Long)
