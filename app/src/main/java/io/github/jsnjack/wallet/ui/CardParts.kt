package io.github.jsnjack.wallet.ui

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.jsnjack.wallet.core.Usage
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jsnjack.wallet.core.CardDate
import io.github.jsnjack.wallet.core.CardDates
import io.github.jsnjack.wallet.core.DateKind
import io.github.jsnjack.wallet.core.DateTone
import io.github.jsnjack.wallet.core.Nearness
import android.text.format.DateFormat
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import io.github.jsnjack.wallet.R
import io.github.jsnjack.wallet.core.CardKind
import io.github.jsnjack.wallet.core.CardLooks
import io.github.jsnjack.wallet.core.CodeFormat
import io.github.jsnjack.wallet.core.DayTime
import io.github.jsnjack.wallet.core.Detail
import io.github.jsnjack.wallet.platform.CodeBitmaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Width to height of a bank card, used for every tile. */
const val CARD_RATIO = 1.6f

/**
 * A "big type" tile: the brand colour with the card name at the top and a
 * giant monogram (or a plane for flights) bleeding off the bottom-right
 * corner in a lighter tone. Small icons mark cards kept at the top or locked.
 */
@Composable
fun CardTile(
    name: String,
    color: Int,
    modifier: Modifier = Modifier,
    kind: CardKind = CardKind.LOYALTY,
    pinned: Boolean = false,
    locked: Boolean = false,
    large: Boolean = false,
    count: Int = 1,
    date: CardDate? = null,
) {
    val content = Color(CardLooks.contentColor(color))
    val ghost = if (content == Color.White) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.16f)
    val shape = RoundedCornerShape(if (large) 24.dp else 20.dp)
    val shadow = if (isSystemInDarkTheme()) 0.dp else 3.dp
    Box(
        modifier
            .aspectRatio(CARD_RATIO)
            .shadow(shadow, shape)
            .clip(shape)
            .background(Color(color)),
    ) {
        if (kind == CardKind.BOARDING_PASS) {
            Icon(
                painterResource(R.drawable.ic_flight),
                contentDescription = null,
                tint = ghost,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 14.dp, y = 18.dp).size(if (large) 130.dp else 96.dp),
            )
        } else {
            Text(
                CardLooks.monogram(name),
                color = ghost,
                fontSize = if (large) 92.sp else 68.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-3).sp,
                maxLines = 1,
                softWrap = false,
                // Decoration: the name above already says it.
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 8.dp, y = if (large) 26.dp else 20.dp).clearAndSetSemantics {},
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = if (large) 16.dp else 12.dp, vertical = if (large) 14.dp else 10.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    color = content,
                    fontSize = if (large) 17.sp else 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = if (large) 20.sp else 16.sp,
                )
                if (count > 1) {
                    val members = if (kind == CardKind.BOARDING_PASS) R.plurals.tile_passengers else R.plurals.tile_tickets
                    Text(pluralStringResource(members, count, count), color = content.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1)
                }
            }
            if (pinned) Badge(R.drawable.ic_star_filled, stringResource(R.string.badge_favourite), content)
            if (locked) Badge(R.drawable.ic_lock_filled, stringResource(R.string.badge_locked), content)
        }
        date?.let { DateChip(it, Modifier.align(Alignment.BottomStart).padding(start = if (large) 14.dp else 10.dp, bottom = if (large) 12.dp else 9.dp)) }
    }
}

/**
 * When a card's date is, as a small pill: white for an event today, amber
 * for an end within a week, faint for a card not valid yet, dark otherwise.
 */
@Composable
fun DateChip(date: CardDate, modifier: Modifier = Modifier) {
    val (background, text) = when (date.tone) {
        DateTone.NOW -> Color.White to colorResource(R.color.accent_on_white)
        DateTone.WARN -> colorResource(R.color.chip_warn) to colorResource(R.color.chip_warn_text)
        DateTone.WAIT -> Color.White.copy(alpha = 0.28f) to Color.White
        DateTone.PLAIN -> Color.Black.copy(alpha = 0.30f) to Color.White
    }
    Text(
        whenText(date),
        color = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** "Today 20:30", "Fri 07:05", "3 Dec", "Ends Sun", "From 1 Dec": short while close, a plain date further away. */
@Composable
fun whenText(date: CardDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val inSentence = date.kind != DateKind.EVENT
    val day = when (CardDates.nearness(date.at.date, LocalDate.now())) {
        Nearness.TODAY -> stringResource(if (inSentence) R.string.day_today_in_sentence else R.string.day_today)
        Nearness.TOMORROW -> stringResource(if (inSentence) R.string.day_tomorrow_in_sentence else R.string.day_tomorrow)
        Nearness.WEEKDAY -> date.at.date.format(DateTimeFormatter.ofPattern("EEE", locale))
        Nearness.DATE -> date.at.date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale))
        Nearness.DATE_WITH_YEAR -> date.at.date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMMyyyy"), locale))
    }
    val time = date.at.time?.takeIf { date.kind == DateKind.EVENT }?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    return when (date.kind) {
        DateKind.EVENT -> listOfNotNull(day, time).joinToString(" ")
        DateKind.ENDS -> stringResource(R.string.when_ends, day)
        DateKind.STARTS -> stringResource(R.string.when_from, day)
    }
}

/** A small big-type tile for the favourites rail: name at the top, monogram bleeding off the corner. */
@Composable
fun MiniTile(name: String, color: Int, flight: Boolean, modifier: Modifier = Modifier) {
    val content = Color(CardLooks.contentColor(color))
    val ghost = if (content == Color.White) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.16f)
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Color(color))) {
        if (flight) {
            Icon(painterResource(R.drawable.ic_flight), contentDescription = null, tint = ghost, modifier = Modifier.align(Alignment.BottomEnd).offset(x = 8.dp, y = 10.dp).size(52.dp))
        } else {
            Text(
                CardLooks.monogram(name),
                color = ghost,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-2).sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 12.dp).clearAndSetSemantics {},
            )
        }
        Text(
            name,
            color = content,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

/** "Used 3×, last 2 days ago" for a card's code screen; null before the first use. */
@Composable
fun usageText(usage: Usage?): String? {
    if (usage == null || usage.count == 0) return null
    val count = pluralStringResource(R.plurals.used_count, usage.count, usage.count)
    val now = System.currentTimeMillis()
    val last = if (now - usage.lastUsedAt < DateUtils.MINUTE_IN_MILLIS) {
        stringResource(R.string.used_just_now)
    } else {
        DateUtils.getRelativeTimeSpanString(usage.lastUsedAt, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
    return stringResource(R.string.used_last, count, last)
}

@Composable
private fun Badge(icon: Int, description: String, tint: Color) {
    Icon(painterResource(icon), contentDescription = description, tint = tint.copy(alpha = 0.85f), modifier = Modifier.padding(start = 3.dp, top = 2.dp).size(14.dp))
}

/**
 * The code drawn pixel-exact: each module is a whole number of screen
 * pixels, so bars keep equal widths and scanners read them on the first try.
 * Barcodes use the full width. Square codes are about as big as Google
 * Wallet draws them, unless [large] makes them full width: a scanner held
 * close to the phone must see the whole code. The scale leaves room for the
 * white margin each format needs around it.
 */
@Composable
fun CodeImage(code: String, format: CodeFormat, modifier: Modifier = Modifier, large: Boolean = false) {
    val bitmap by produceState<ImageBitmap?>(null, code, format) {
        value = withContext(Dispatchers.Default) { CodeBitmaps.get(code, format)?.asImageBitmap() }
    }
    val box = when {
        format.linear -> Modifier.fillMaxWidth().height(LINEAR_HEIGHT)
        format == CodeFormat.PDF_417 -> Modifier.fillMaxWidth().height(PDF417_HEIGHT)
        large -> Modifier.fillMaxWidth().aspectRatio(1f)
        else -> Modifier.fillMaxWidth().height(SQUARE_HEIGHT)
    }
    BoxWithConstraints(modifier.then(box), contentAlignment = Alignment.Center) {
        val image = bitmap ?: return@BoxWithConstraints
        val density = LocalDensity.current
        val quiet = if (format.linear) LINEAR_QUIET_MODULES else MATRIX_QUIET_MODULES
        val widthScale = constraints.maxWidth / (image.width + 2 * quiet)
        val scale = when {
            format.linear -> widthScale
            // A Data Matrix code can be a rectangle; its longer side gets the square's size.
            format.square -> minOf(widthScale, constraints.maxHeight / (maxOf(image.width, image.height) + 2 * quiet))
            else -> minOf(widthScale, constraints.maxHeight / (image.height + 2 * quiet))
        }
        if (scale < 1) return@BoxWithConstraints
        with(density) {
            val width = (image.width * scale).toDp()
            val height = if (format.linear) LINEAR_HEIGHT else (image.height * scale).toDp()
            Image(
                image,
                contentDescription = null,
                modifier = Modifier.size(width, height),
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.None,
            )
        }
    }
}

private val LINEAR_HEIGHT = 120.dp
private val PDF417_HEIGHT = 150.dp

// Google Wallet draws a 29-module QR code 145 dp wide; with its margin that fits in 168 dp.
private val SQUARE_HEIGHT = 168.dp

// EAN asks for 11 modules on the left; QR for 4, though phones and shop scanners read 2.
private const val LINEAR_QUIET_MODULES = 11
private const val MATRIX_QUIET_MODULES = 2

/** The label for a kind of card. */
fun kindLabel(kind: CardKind): Int = when (kind) {
    CardKind.LOYALTY -> R.string.kind_loyalty
    CardKind.MEMBERSHIP -> R.string.kind_membership
    CardKind.VOUCHER -> R.string.kind_voucher
    CardKind.GIFT_CARD -> R.string.kind_gift_card
    CardKind.TICKET -> R.string.kind_ticket
    CardKind.BOARDING_PASS -> R.string.kind_boarding_pass
    CardKind.OTHER -> R.string.kind_other
}

/** Space around the edges of screens. */
val SCREEN_PADDING = 12.dp

/**
 * "Today 21:00", "Tomorrow", or "Fri, 20 Nov 07:05" in the phone's language;
 * [inSentence] gives "today" and "tomorrow" in lower case for use mid-sentence.
 */
@Composable
fun dayTimeText(value: DayTime, inSentence: Boolean = false): String {
    val context = LocalContext.current
    val today = LocalDate.now()
    val day = when (value.date) {
        today -> stringResource(if (inSentence) R.string.day_today_in_sentence else R.string.day_today)
        today.plusDays(1) -> stringResource(if (inSentence) R.string.day_tomorrow_in_sentence else R.string.day_tomorrow)
        else -> DateUtils.formatDateTime(
            context,
            value.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL or
                if (value.date.year == today.year) DateUtils.FORMAT_NO_YEAR else 0,
        )
    }
    return value.time?.let { "$day ${it.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))}" } ?: day
}

/** Up to four labelled values in a row, as on a printed ticket. */
@Composable
fun DetailGrid(details: List<Detail>, color: Color, modifier: Modifier = Modifier) {
    if (details.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        details.chunked(DETAILS_PER_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { detail ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(detail.value, color = color, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(detail.label.uppercase(), color = color.copy(alpha = 0.7f), fontSize = 10.sp, letterSpacing = 0.5.sp, maxLines = 1)
                    }
                }
                repeat(DETAILS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val DETAILS_PER_ROW = 4
