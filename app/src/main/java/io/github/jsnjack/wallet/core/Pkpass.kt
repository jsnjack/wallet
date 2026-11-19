package io.github.jsnjack.wallet.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.zip.ZipInputStream

/**
 * Reads Apple Wallet pass files (.pkpass): a zip whose `pass.json` holds the
 * barcode, colours, dates and labelled fields. Images are not imported.
 */
object Pkpass {
    private const val PASS_JSON = "pass.json"
    private const val MAX_PASS_JSON_BYTES = 1 shl 20
    private const val MAX_ENTRIES = 200
    private const val MAX_DETAILS = 8
    private val FIELD_GROUPS = listOf("headerFields", "primaryFields", "secondaryFields", "auxiliaryFields")
    private val STYLES = mapOf(
        "boardingPass" to CardKind.BOARDING_PASS,
        "eventTicket" to CardKind.TICKET,
        "coupon" to CardKind.VOUCHER,
        "storeCard" to CardKind.LOYALTY,
        "generic" to CardKind.OTHER,
    )
    private val FORMATS = mapOf(
        "PKBarcodeFormatQR" to CodeFormat.QR,
        "PKBarcodeFormatPDF417" to CodeFormat.PDF_417,
        "PKBarcodeFormatAztec" to CodeFormat.AZTEC,
        "PKBarcodeFormatCode128" to CodeFormat.CODE_128,
    )
    private val RGB = Regex("""rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3}).*\)""")

    /** True when [bytes] start like a zip file, which a .pkpass is. */
    fun looksLikePkpass(bytes: ByteArray): Boolean =
        bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() && bytes[2] == 3.toByte() && bytes[3] == 4.toByte()

    /**
     * A form draft for the pass in [bytes]. Throws [IOException] when it is not
     * a readable pass or has no barcode the app can draw. [now] marks a voided
     * pass as used; [today] places a boarding pass date in its year.
     */
    fun read(bytes: ByteArray, today: LocalDate, now: Long): CardDraft {
        val pass = try {
            Json.parseToJsonElement(passJson(bytes).decodeToString()) as? JsonObject
        } catch (e: SerializationException) {
            throw IOException("pass.json is not valid JSON", e)
        } ?: throw IOException("pass.json is not a JSON object")

        val (code, format) = barcode(pass) ?: throw IOException("pass has no barcode the app can draw")
        val styleKey = STYLES.keys.firstOrNull { it in pass } ?: "generic"
        val kind = STYLES.getValue(styleKey)
        val style = pass[styleKey] as? JsonObject
        val boarding = if (kind == CardKind.BOARDING_PASS) Bcbp.parse(code, today) else null
        val leg = boarding?.legs?.firstOrNull()
        val relevant = dayTime(pass.string("relevantDate"))
        val event = when {
            leg != null -> DayTime(leg.date, relevant?.takeIf { it.date == leg.date }?.time)
            kind in Agenda.DATED_KINDS -> relevant
            else -> null
        }
        val name = leg?.flight?.code
            ?: listOfNotNull(pass.string("logoText"), pass.string("organizationName"), pass.string("description"))
                .firstOrNull { it.isNotBlank() }
            ?: ""
        return CardDraft(
            name = name.trim(),
            code = code,
            format = format,
            formatChosen = true,
            kind = kind,
            color = color(pass.string("backgroundColor")),
            expires = dayTime(pass.string("expirationDate")),
            event = event,
            details = details(style),
            flight = leg?.flight,
            usedAt = if ((pass["voided"] as? JsonPrimitive)?.content == "true") now else null,
        )
    }

    private fun passJson(bytes: ByteArray): ByteArray {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entries = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > MAX_ENTRIES) throw IOException("pass has too many files")
                if (entry.name == PASS_JSON) {
                    val data = zip.readNBytes(MAX_PASS_JSON_BYTES + 1)
                    if (data.size > MAX_PASS_JSON_BYTES) throw IOException("pass.json is too large")
                    return data
                }
            }
        }
        throw IOException("file has no pass.json")
    }

    private fun barcode(pass: JsonObject): Pair<String, CodeFormat>? {
        val candidates = (pass["barcodes"] as? JsonArray).orEmpty() + listOfNotNull(pass["barcode"])
        return candidates.firstNotNullOfOrNull { element ->
            val barcode = element as? JsonObject ?: return@firstNotNullOfOrNull null
            val format = FORMATS[barcode.string("format")] ?: return@firstNotNullOfOrNull null
            barcode.string("message")?.takeIf { it.isNotEmpty() }?.let { it to format }
        }
    }

    private fun details(style: JsonObject?): List<Detail> = FIELD_GROUPS
        .flatMap { (style?.get(it) as? JsonArray).orEmpty() }
        .mapNotNull { element ->
            val field = element as? JsonObject ?: return@mapNotNull null
            val value = fieldValue(field["value"]) ?: return@mapNotNull null
            Detail(field.string("label").orEmpty().trim(), value)
        }
        .filter { it.value.isNotBlank() }
        .take(MAX_DETAILS)

    private fun fieldValue(value: JsonElement?): String? {
        val text = (value as? JsonPrimitive)?.content?.trim() ?: return null
        return dayTime(text)?.let { it.time?.let { time -> "${it.date} $time" } ?: it.date.toString() } ?: text
    }

    /** The colour for `rgb(r, g, b)` or `#RRGGBB`, or null. */
    internal fun color(text: String?): Int? {
        if (text == null) return null
        RGB.matchEntire(text.trim())?.let { m ->
            val (r, g, b) = m.destructured.toList().map { it.toInt().coerceIn(0, 255) }
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return CardLooks.parseHex(text.trim())
    }

    /** The wall-clock date and time in an ISO 8601 value, in the offset it was written with. */
    internal fun dayTime(text: String?): DayTime? {
        if (text.isNullOrBlank() || text.length < DATE_LENGTH || !text[0].isDigit()) return null
        val local: LocalDateTime = try {
            OffsetDateTime.parse(text).toLocalDateTime()
        } catch (e: DateTimeParseException) {
            try {
                LocalDateTime.parse(text)
            } catch (e: DateTimeParseException) {
                return try {
                    DayTime(LocalDate.parse(text))
                } catch (e: DateTimeParseException) {
                    null
                }
            }
        }
        return DayTime(local.toLocalDate(), local.toLocalTime().truncatedTo(ChronoUnit.MINUTES))
    }

    private const val DATE_LENGTH = 10

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
