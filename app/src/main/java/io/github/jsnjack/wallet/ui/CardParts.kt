package io.github.jsnjack.wallet.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.jsnjack.wallet.R
import io.github.jsnjack.wallet.core.CardKind
import io.github.jsnjack.wallet.core.CardLooks
import io.github.jsnjack.wallet.core.CodeFormat
import io.github.jsnjack.wallet.platform.CodeBitmaps

/** Width to height of a bank card, used for every tile. */
const val CARD_RATIO = 1.6f

/** A brand-coloured tile with the card's monogram and name. */
@Composable
fun CardTile(name: String, color: Int, modifier: Modifier = Modifier, badge: String? = null, large: Boolean = false) {
    val content = Color(CardLooks.contentColor(color))
    Column(
        modifier
            .aspectRatio(CARD_RATIO)
            .clip(RoundedCornerShape(if (large) 20.dp else 14.dp))
            .background(Color(color))
            .padding(horizontal = if (large) 16.dp else 11.dp, vertical = if (large) 14.dp else 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            CardLooks.monogram(name),
            color = content,
            fontSize = if (large) 26.sp else 19.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                color = content,
                fontSize = if (large) 14.sp else 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (badge != null) Text(badge, color = content.copy(alpha = 0.8f), fontSize = 11.sp)
        }
    }
}

/**
 * The code drawn pixel-exact: each module is a whole number of screen
 * pixels, so bars keep equal widths and scanners read them on the first try.
 */
@Composable
fun CodeImage(code: String, format: CodeFormat, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, code, format) {
        value = withContext(Dispatchers.Default) { CodeBitmaps.get(code, format)?.asImageBitmap() }
    }
    val boxHeight: Dp = when {
        format.linear -> 112.dp
        format == CodeFormat.PDF_417 -> 140.dp
        else -> 240.dp
    }
    BoxWithConstraints(modifier.fillMaxWidth().height(boxHeight), contentAlignment = Alignment.Center) {
        val image = bitmap ?: return@BoxWithConstraints
        val density = LocalDensity.current
        val widthScale = constraints.maxWidth / image.width
        val scale = if (format.linear) widthScale else minOf(widthScale, constraints.maxHeight / image.height)
        if (scale < 1) return@BoxWithConstraints
        with(density) {
            val width = (image.width * scale).toDp()
            val height = if (format.linear) boxHeight else (image.height * scale).toDp()
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

/** The label for a kind of card. */
fun kindLabel(kind: CardKind): Int = when (kind) {
    CardKind.LOYALTY -> R.string.kind_loyalty
    CardKind.MEMBERSHIP -> R.string.kind_membership
    CardKind.VOUCHER -> R.string.kind_voucher
    CardKind.GIFT_CARD -> R.string.kind_gift_card
    CardKind.TICKET -> R.string.kind_ticket
    CardKind.OTHER -> R.string.kind_other
}

/** A round icon button area of the size One UI uses for top-bar actions. */
val ICON_BUTTON_SIZE = 44.dp

/** Space around the edges of screens. */
val SCREEN_PADDING = 16.dp
