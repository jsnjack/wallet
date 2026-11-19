package io.github.jsnjack.wallet.core

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime

/** What a ticket's printed text says: the event, when it starts, and details worth showing at the door. */
data class TicketFacts(val name: String?, val event: DayTime?, val details: List<Detail>)

/**
 * Reads the text of an e-ticket, such as the text inside a PDF, in English
 * or Dutch: the event name, its date and time, and doors, hall, row, seat,
 * section, entrance and venue. Lines about the buyer, the order or the price
 * are skipped, so no customer name or number ends up on the card.
 */
object TicketText {
    private const val MAX_LABEL = 30
    private const val MAX_NAME = 80
    private const val MIN_LETTERS = 3
    private const val MAX_NAME_WORDS = 10
    private const val VENUE_LABEL = "Venue"

    private val LABEL = Regex("""^([^:]{2,$MAX_LABEL}):\s*(.*)$""")
    private val DAY_MONTH_YEAR = Regex("""\b(\d{1,2})(?:st|nd|rd|th|e)?\.?\s+(\p{L}{3,9})\.?,?\s+(\d{4})\b""")
    private val MONTH_DAY_YEAR = Regex("""\b(\p{L}{3,9})\.?\s+(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{4})\b""")
    private val NUMERIC_DMY = Regex("""\b(\d{1,2})[-./](\d{1,2})[-./](\d{4})\b""")
    private val ISO_DATE = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val TIME = Regex("""(?<![\d€$£.,])([01]?\d|2[0-3])[:.]([0-5]\d)(?![\d.,])""")
    private val POSTCODE_LINE = Regex("""^\d{4,5}\s?(?:[A-Z]{2}\s+)?(\p{Lu}[\p{L} '-]+)$""")
    private val HOUSE_NUMBER = Regex("""\s\d+\s?[A-Za-z]?$""")
    private val NOT_EVENT_DATE = Regex("""(?i)\b(order|besteld|bestel|gekocht|purchase|aankoop|printed|geprint|issued|uitgegeven|created|aangemaakt|geboren|born)""")
    private val BOILERPLATE = Regex("""(?i)(www\.|https?:|@|\*|e-?ticket|toegangsbewijs|print (this|dit)|mobile ticket|\bterms\b|conditions|voorwaarden)""")
    private val ISSUERS = setOf("ticketmaster", "eventim", "see tickets", "ticketswap", "paylogic", "eventbrite", "ticketcounter", "ticketland")

    private val MONTHS = mapOf(
        "january" to 1, "januari" to 1, "jan" to 1,
        "february" to 2, "februari" to 2, "feb" to 2,
        "march" to 3, "maart" to 3, "mar" to 3, "mrt" to 3,
        "april" to 4, "apr" to 4,
        "may" to 5, "mei" to 5,
        "june" to 6, "juni" to 6, "jun" to 6,
        "july" to 7, "juli" to 7, "jul" to 7,
        "august" to 8, "augustus" to 8, "aug" to 8,
        "september" to 9, "sept" to 9, "sep" to 9,
        "october" to 10, "oktober" to 10, "oct" to 10, "okt" to 10,
        "november" to 11, "nov" to 11,
        "december" to 12, "dec" to 12,
    )

    private val DATE_LABELS = setOf("date", "date & time", "date and time", "datum", "datum & tijd", "datum en tijd", "when", "wanneer", "event date")
    private val TIME_LABELS = setOf("time", "tijd", "start", "starts", "aanvang", "begin", "begintijd", "show")
    private val DETAIL_LABELS = setOf(
        "doors", "doors open", "deuren", "deuren open", "zaal open", "entree vanaf",
        "zaal", "hall", "room", "stage", "podium",
        "row", "rij",
        "seat", "stoel", "plaats", "seat number", "stoelnummer",
        "section", "vak", "block", "blok", "tribune", "area",
        "entrance", "ingang", "gate",
    )

    private data class Line(val text: String, val label: String?, val value: String?)

    /**
     * The facts for a ticket printed on one [page] of a file: from that page,
     * with the name and date taken from the whole [file] when the page lacks
     * them. Null when there is no text at all.
     */
    fun readPage(page: String?, file: String?, today: LocalDate, code: String): TicketFacts? {
        val own = page?.let { read(it, today, code) }
        val all = file?.takeIf { it != page }?.let { read(it, today, code) }
        if (own == null) return all
        return own.copy(name = own.name ?: all?.name, event = own.event ?: all?.event)
    }

    /** The facts in [text]; [code] is the ticket's code, whose printed copy is not a name. [today] picks the event date over others. */
    fun read(text: String, today: LocalDate, code: String = ""): TicketFacts {
        val raw = text.lines().map { it.trim().replace(Regex("""\s+"""), " ") }.filter { it.isNotEmpty() }
        val lines = labelled(raw)
        val used = BooleanArray(lines.size)
        lines.forEachIndexed { i, line -> if (line.label != null) used[i] = true }

        val event = eventDate(lines, today)
        val details = mutableListOf<Detail>()
        lines.forEach { line ->
            val label = line.label ?: return@forEach
            val value = line.value?.takeIf { it.isNotBlank() } ?: return@forEach
            if (label.lowercase() in DETAIL_LABELS && details.none { it.label.equals(label, ignoreCase = true) }) details += Detail(label, value)
        }
        venue(lines, used)?.let { details += Detail(VENUE_LABEL, it) }

        val name = lines.indices.firstOrNull { i -> !used[i] && isName(lines[i].text, code) }?.let { lines[it].text }
        return TicketFacts(name, event, details)
    }

    // Pairs "Label:" lines with their value, inline or on the next line; the value line then counts as part of the label.
    private fun labelled(raw: List<String>): List<Line> {
        val out = mutableListOf<Line>()
        var i = 0
        while (i < raw.size) {
            val match = LABEL.find(raw[i])
            if (match == null || match.groupValues[1].any(Char::isDigit)) {
                out += Line(raw[i], null, null)
                i++
                continue
            }
            val label = match.groupValues[1].trim()
            val inline = match.groupValues[2].trim()
            val next = raw.getOrNull(i + 1)
            if (inline.isEmpty() && next != null && LABEL.find(next) == null) {
                out += Line("$label: $next", label, next)
                i += 2
            } else {
                out += Line(raw[i], label, inline)
                i++
            }
        }
        return out
    }

    // A date under a date label wins; otherwise the first one from today on, so an order date is passed over.
    private fun eventDate(lines: List<Line>, today: LocalDate): DayTime? {
        val found = lines.mapNotNull { line ->
            if (NOT_EVENT_DATE.containsMatchIn(line.label ?: line.text)) return@mapNotNull null
            val text = line.value ?: line.text
            date(text)?.let { (date, end) -> Triple(line, date, TIME.find(text, end)?.let(::time)) }
        }
        val (line, date, time) = found.firstOrNull { it.first.label?.lowercase() in DATE_LABELS }
            ?: found.firstOrNull { !it.second.isBefore(today) }
            ?: found.firstOrNull()
            ?: return null
        val labelledTime = time ?: lines.firstNotNullOfOrNull { other ->
            other.takeIf { it !== line && it.label?.lowercase() in TIME_LABELS }?.value?.let { TIME.find(it) }?.let(::time)
        }
        return DayTime(date, labelledTime)
    }

    // The date in [text] and where it ends, so the time is looked for after it.
    private fun date(text: String): Pair<LocalDate, Int>? {
        ISO_DATE.find(text)?.let { m -> of(m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3])?.let { return it to m.range.last + 1 } }
        DAY_MONTH_YEAR.find(text)?.let { m ->
            MONTHS[m.groupValues[2].lowercase()]?.let { month -> of(m.groupValues[3], month, m.groupValues[1])?.let { return it to m.range.last + 1 } }
        }
        MONTH_DAY_YEAR.find(text)?.let { m ->
            MONTHS[m.groupValues[1].lowercase()]?.let { month -> of(m.groupValues[3], month, m.groupValues[2])?.let { return it to m.range.last + 1 } }
        }
        NUMERIC_DMY.find(text)?.let { m -> of(m.groupValues[3], m.groupValues[2].toInt(), m.groupValues[1])?.let { return it to m.range.last + 1 } }
        return null
    }

    private fun of(year: String, month: Int, day: String): LocalDate? = try {
        LocalDate.of(year.toInt(), month, day.toInt())
    } catch (e: DateTimeException) {
        null
    }

    private fun time(match: MatchResult): LocalTime = LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt())

    // A postcode line ("3511 WC Utrecht") follows the street ("Vredenburgkade 11"), which follows the venue.
    private fun venue(lines: List<Line>, used: BooleanArray): String? {
        // A line like "2026 Tour" also looks like a postcode line; only one with a street above it counts.
        val index = lines.indices.firstOrNull { i ->
            !used[i] && POSTCODE_LINE.matches(lines[i].text) && i > 0 && !used[i - 1] && HOUSE_NUMBER.containsMatchIn(lines[i - 1].text)
        } ?: return null
        val city = POSTCODE_LINE.find(lines[index].text)!!.groupValues[1].trim()
        val street = index - 1
        used[index] = true
        used[street] = true
        val place = street - 1
        if (place < 0 || used[place] || date(lines[place].text) != null) return city
        used[place] = true
        return "${lines[place].text}, $city"
    }

    private fun isName(text: String, code: String): Boolean {
        if (text.length > MAX_NAME || text.count(Char::isLetter) < MIN_LETTERS) return false
        // Sentences and bullet points are terms and conditions, not an event's name.
        if (text.endsWith('.') || text.startsWith('•') || text.split(' ').size > MAX_NAME_WORDS) return false
        if (text.count(Char::isDigit) * 2 >= text.length) return false
        if (text.lowercase() in ISSUERS || BOILERPLATE.containsMatchIn(text)) return false
        if (code.isNotEmpty() && text.contains(code)) return false
        return date(text) == null
    }
}
