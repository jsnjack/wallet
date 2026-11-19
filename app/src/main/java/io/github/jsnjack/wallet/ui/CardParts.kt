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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

// One UI tiles catch the light from the top left.
private val GLOSS = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.26f),
    0.46f to Color.White.copy(alpha = 0f),
)

/**
 * A brand-coloured tile with the card's monogram and name, with One UI's
 * gloss and, in light mode, a soft shadow. Flights show a plane instead of
 * a monogram; small icons mark cards kept at the top or locked.
 */
@Composable
fun CardTile(
    name: String,
    color: Int,
    modifier: Modifier = Modifier,
    flight: Boolean = false,
    pinned: Boolean = false,
    locked: Boolean = false,
    large: Boolean = false,
) {
    val content = Color(CardLooks.contentColor(color))
    val shape = RoundedCornerShape(if (large) 24.dp else 20.dp)
    val shadow = if (isSystemInDarkTheme()) 0.dp else 3.dp
    Box(
        modifier
            .aspectRatio(CARD_RATIO)
            .shadow(shadow, shape)
            .clip(shape)
            .background(Color(color))
            .background(GLOSS),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (large) 16.dp else 12.dp, vertical = if (large) 14.dp else 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            if (flight) {
                Icon(painterResource(R.drawable.ic_flight), contentDescription = null, tint = content, modifier = Modifier.size(if (large) 28.dp else 22.dp))
            } else {
                Text(
                    CardLooks.monogram(name),
                    color = content,
                    fontSize = if (large) 26.sp else 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    color = content,
                    fontSize = if (large) 14.sp else 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (pinned) Badge(R.drawable.ic_star_filled, content)
                if (locked) Badge(R.drawable.ic_lock_filled, content)
            }
        }
    }
}

@Composable
private fun Badge(icon: Int, tint: Color) {
    Spacer(Modifier.width(3.dp))
    Icon(painterResource(icon), contentDescription = null, tint = tint.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
}

/**
 * The code drawn pixel-exact: each module is a whole number of screen
 * pixels, so bars keep equal widths and scanners read them on the first try.
 * Square codes use the full width; the scale leaves room for the white
 * margin each format needs around it.
 */
@Composable
fun CodeImage(code: String, format: CodeFormat, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, code, format) {
        value = withContext(Dispatchers.Default) { CodeBitmaps.get(code, format)?.asImageBitmap() }
    }
    val box = when {
        format.linear -> Modifier.fillMaxWidth().height(LINEAR_HEIGHT)
        format == CodeFormat.PDF_417 -> Modifier.fillMaxWidth().height(PDF417_HEIGHT)
        else -> Modifier.fillMaxWidth().aspectRatio(1f)
    }
    BoxWithConstraints(modifier.then(box), contentAlignment = Alignment.Center) {
        val image = bitmap ?: return@BoxWithConstraints
        val density = LocalDensity.current
        val quiet = if (format.linear) LINEAR_QUIET_MODULES else MATRIX_QUIET_MODULES
        val widthScale = constraints.maxWidth / (image.width + 2 * quiet)
        val scale = if (format.linear) widthScale else minOf(widthScale, constraints.maxHeight / (image.height + 2 * quiet))
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
