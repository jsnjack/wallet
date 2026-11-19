package io.github.jsnjack.wallet.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jsnjack.wallet.R
import io.github.jsnjack.wallet.core.Agenda
import io.github.jsnjack.wallet.core.Airports
import io.github.jsnjack.wallet.core.Card
import io.github.jsnjack.wallet.core.CardKind
import io.github.jsnjack.wallet.core.CardLooks
import io.github.jsnjack.wallet.core.CodeNumbers
import io.github.jsnjack.wallet.core.Detail
import io.github.jsnjack.wallet.core.Flight
import io.github.jsnjack.wallet.core.Money
import io.github.jsnjack.wallet.platform.AirportTable
import io.github.jsnjack.wallet.platform.Biometrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId

private const val USE_AFTER_MS = 1_000L
private const val MAX_DOTS = 7
private val MARKABLE = setOf(CardKind.VOUCHER, CardKind.GIFT_CARD, CardKind.TICKET)

/** Full-screen codes; swipe sideways for the next card or the next leg of a trip. */
@Composable
fun CodeScreen(vm: WalletViewModel, startId: String) {
    val all by vm.allCards.collectAsStateWithLifecycle()
    val archived by vm.archived.collectAsStateWithLifecycle()
    // Order is fixed when the screen opens, so counting a use never moves the page.
    val order = remember(startId) { vm.pagesFor(startId) }
    val startPage = order.indexOf(startId)
    if (startPage < 0) {
        LaunchedEffect(startId) { vm.back() }
        return
    }
    val pager = rememberPagerState(initialPage = startPage) { order.size }
    var unlocked by remember { mutableStateOf(emptySet<String>()) }
    val activity = LocalActivity.current
    val context = LocalContext.current
    val unlockTitle = stringResource(R.string.unlock_prompt_title)
    val airports by produceState<Airports?>(null) { value = withContext(Dispatchers.IO) { AirportTable.get(context) } }
    val archivedIds = archived.map { it.first.id }.toSet()

    MaxBrightness()
    val current = vm.card(order[pager.settledPage])
    StatusBarIcons(dark = current != null && CardLooks.contentColor(current.color) == CardLooks.DARK_TEXT)
    LaunchedEffect(pager.settledPage) {
        delay(USE_AFTER_MS)
        vm.recordUse(order[pager.settledPage])
    }

    HorizontalPager(state = pager, key = { order[it] }, modifier = Modifier.fillMaxSize()) { page ->
        // Read through the cards flow so edits show without reopening.
        val card = all.firstOrNull { it.id == order[page] }
        if (card == null) {
            Box(Modifier.fillMaxSize().background(Color.Black))
            return@HorizontalPager
        }
        CodePage(
            card = card,
            page = page,
            pages = order.size,
            archived = card.id in archivedIds,
            airports = airports,
            showCode = !card.locked || card.id in unlocked,
            onClose = { vm.back() },
            onEdit = { vm.edit(card.id) },
            onMarkUsed = { vm.markUsed(card.id) },
            onRestore = { vm.restore(card.id) },
            onUnlock = {
                if (activity != null) {
                    Biometrics.authenticate(
                        activity,
                        unlockTitle,
                        onSuccess = { unlocked = unlocked + card.id },
                        onError = { vm.message(R.string.message_unlock_failed) },
                    )
                }
            },
        )
    }
}

@Composable
private fun CodePage(
    card: Card,
    page: Int,
    pages: Int,
    archived: Boolean,
    airports: Airports?,
    showCode: Boolean,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onMarkUsed: () -> Unit,
    onRestore: () -> Unit,
    onUnlock: () -> Unit,
) {
    val content = Color(CardLooks.contentColor(card.color))
    val context = LocalContext.current
    val flight = card.flight
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(card.color))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = onClose, shape = CircleShape, color = Color.Black.copy(alpha = 0.16f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close), tint = content)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!archived) {
                    Icon(painterResource(R.drawable.ic_brightness), contentDescription = null, tint = content, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(stringResource(if (archived) R.string.code_archived else R.string.code_brightness), color = content, fontSize = 12.sp)
            }
        }

        if (flight != null) {
            FlightHeader(card, flight, airports, content)
        } else {
            CardHeader(card, content)
        }
        DetailGrid(pageDetails(card), content, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp))

        Surface(
            shape = RoundedCornerShape(26.dp),
            color = colorResource(R.color.code_panel),
            contentColor = colorResource(R.color.code_ink),
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        ) {
            Box {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CodeImage(card.code, card.format, if (showCode) Modifier else Modifier.blur(18.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        when {
                            !showCode -> stringResource(R.string.code_hidden)
                            flight != null -> listOf(flight.passenger, flight.pnr.takeIf { it.isNotBlank() }?.let { stringResource(R.string.code_pnr, it) })
                                .filterNot { it.isNullOrBlank() }.joinToString(" · ")
                            else -> CodeNumbers.display(card.code)
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        letterSpacing = 1.5.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MetaLine(card, showCode)
                    if (card.note.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(card.note, fontSize = 13.sp, color = colorResource(R.color.muted), textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (showCode && flight == null) PanelAction(stringResource(R.string.action_copy_number), R.drawable.ic_copy) { copy(context, card) }
                        when {
                            archived && card.usedAt != null -> PanelAction(stringResource(R.string.action_restore), primary = true, onClick = onRestore)
                            !archived && card.kind in MARKABLE -> PanelAction(stringResource(R.string.action_mark_used), primary = true, onClick = onMarkUsed)
                        }
                        if (showCode) PanelAction(stringResource(R.string.menu_edit), R.drawable.ic_edit, onClick = onEdit)
                    }
                }
                if (!showCode) {
                    Button(
                        onClick = onUnlock,
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.code_ink), contentColor = colorResource(R.color.code_panel)),
                        modifier = Modifier.align(Alignment.Center).padding(bottom = 40.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_lock), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_unlock))
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (pages > 1) PageDots(page, pages, content)
    }
}

@Composable
private fun CardHeader(card: Card, content: Color) {
    Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(CardLooks.monogram(card.name), color = content, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(card.name, color = content, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            val subtitle = card.event?.let { dayTimeText(it) } ?: stringResource(kindLabel(card.kind))
            Text(subtitle, color = content.copy(alpha = 0.8f), fontSize = 13.sp)
        }
        card.balanceCents?.let {
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.code_balance), color = content.copy(alpha = 0.75f), fontSize = 11.sp)
                Text(Money.display(it, LocalConfiguration.current.locales[0]), color = content, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FlightHeader(card: Card, flight: Flight, airports: Airports?, content: Color) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(
            listOfNotNull(flight.code, card.event?.let { dayTimeText(it) }).joinToString(" · "),
            color = content.copy(alpha = 0.8f),
            fontSize = 13.sp,
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            Airport(flight.from, airports?.city(flight.from), content, Alignment.Start, Modifier.weight(1f))
            Icon(
                painterResource(R.drawable.ic_flight),
                contentDescription = null,
                tint = content.copy(alpha = 0.85f),
                modifier = Modifier.padding(bottom = 18.dp).size(26.dp).rotate(90f),
            )
            Airport(flight.to, airports?.city(flight.to), content, Alignment.End, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Airport(code: String, city: String?, content: Color, alignment: Alignment.Horizontal, modifier: Modifier) {
    Column(modifier, horizontalAlignment = alignment) {
        Text(code, color = content, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
        Text(city.orEmpty(), color = content.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun pageDetails(card: Card): List<Detail> {
    val flight = card.flight ?: return card.details
    val seat = flight.seat.takeIf { it.isNotBlank() && card.details.none { d -> d.label.equals(stringResource(R.string.detail_seat), true) } }
    return card.details + listOfNotNull(seat?.let { Detail(stringResource(R.string.detail_seat), it) })
}

@Composable
private fun MetaLine(card: Card, showCode: Boolean) {
    val parts = buildList {
        card.expires?.let {
            val ended = !it.end.isAfter(LocalDateTime.now())
            add(stringResource(if (ended) R.string.code_expired else R.string.code_expires, dayTimeText(it, inSentence = true)))
        }
        if (card.pin.isNotBlank()) add(if (showCode) stringResource(R.string.code_pin, card.pin) else stringResource(R.string.code_pin_hidden))
        if (card.usedAt != null) add(stringResource(R.string.code_used))
        if (card.kind in Agenda.DATED_KINDS && card.event != null && Agenda(ZoneId.systemDefault()).isArchived(card, LocalDateTime.now())) {
            add(stringResource(R.string.code_past))
        }
    }
    if (parts.isEmpty()) return
    Spacer(Modifier.height(6.dp))
    Text(parts.joinToString(" · "), fontSize = 13.sp, color = colorResource(R.color.muted), textAlign = TextAlign.Center)
}

@Composable
private fun PageDots(page: Int, pages: Int, content: Color) {
    Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.Center) {
        val shown = minOf(pages, MAX_DOTS)
        val first = (page - shown / 2).coerceIn(0, pages - shown)
        repeat(shown) { i ->
            val active = first + i == page
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (active) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(content.copy(alpha = if (active) 0.9f else 0.4f)),
            )
        }
    }
}

@Composable
private fun PanelAction(text: String, icon: Int? = null, primary: Boolean = false, onClick: () -> Unit) {
    val ink = colorResource(R.color.code_ink)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (primary) colorResource(R.color.launcher_teal) else ink.copy(alpha = 0.07f),
        contentColor = if (primary) Color.White else ink,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, fontSize = 13.sp, fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

private fun copy(context: Context, card: Card) {
    val clip = ClipData.newPlainText(card.name, card.code)
    if (card.locked) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}

/** Full screen brightness and no screen timeout while a code is shown. */
@Composable
private fun MaxBrightness() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val previous = window.attributes.screenBrightness
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = previous }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/** Dark status bar icons on light card colours; the theme's choice comes back on leaving. */
@Composable
private fun StatusBarIcons(dark: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity, dark) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = dark
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}
