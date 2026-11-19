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
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.clickable
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
import io.github.jsnjack.wallet.core.FlightCheck
import io.github.jsnjack.wallet.core.FlightRef
import io.github.jsnjack.wallet.core.LiveFlight
import io.github.jsnjack.wallet.core.Money
import io.github.jsnjack.wallet.flight.FlightText
import io.github.jsnjack.wallet.platform.AirportTable
import io.github.jsnjack.wallet.platform.Biometrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val USE_AFTER_MS = 1_000L
private const val MAX_DOTS = 7

// Smaller differences from the schedule are noise, not news.
private const val MINUTES_WORTH_SAYING = 5L
private val MARKABLE = setOf(CardKind.VOUCHER, CardKind.GIFT_CARD, CardKind.TICKET)

/**
 * Full-screen codes; swipe sideways between the passengers on one flight or
 * the tickets for one show. A boarding pass shows live flight information and
 * asks for a fresh lookup when it opens.
 */
@Composable
fun CodeScreen(vm: WalletViewModel, startId: String) {
    val all by vm.allCards.collectAsStateWithLifecycle()
    // Usage as it was before this visit, so the line describes the previous use.
    val usage = remember(startId) { vm.usage.value }
    val archived by vm.archived.collectAsStateWithLifecycle()
    val flightChecks by vm.flightChecks.collectAsStateWithLifecycle()
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
        vm.refreshFlight(order[pager.settledPage])
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
            used = usageText(usage[card.id]),
            page = page,
            pages = order.size,
            archived = card.id in archivedIds,
            airports = airports,
            live = FlightRef.of(card)?.let { flightChecks[it.id] },
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

/**
 * One card's code. Built for one hand: swipe down anywhere to close, and
 * every action sits in the bar at the bottom of the screen.
 */
@Composable
private fun CodePage(
    card: Card,
    used: String?,
    page: Int,
    pages: Int,
    archived: Boolean,
    airports: Airports?,
    live: FlightCheck?,
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
    val density = LocalDensity.current
    val closeAfter = with(density) { SWIPE_TO_CLOSE.toPx() }
    var pulled by remember { mutableFloatStateOf(0f) }
    val swipeToClose = remember(closeAfter) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) pulled = (pulled + available.y).coerceAtLeast(0f)
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pulled > closeAfter || (pulled > 0f && available.y > FLING_TO_CLOSE)) onClose()
                pulled = 0f
                return Velocity.Zero
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Color(card.color))) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = pulled / 2 }
                .nestedScroll(swipeToClose)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(bottom = 96.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(content.copy(alpha = 0.45f)))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.code_swipe_hint), color = content.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.weight(1f))
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
                FlightHeader(card, flight, airports, live, content)
            } else {
                CardHeader(card, used, content)
            }
            DetailGrid(pageDetails(card, live?.flight), content, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp))

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
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.16f))
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showCode) BarAction(R.drawable.ic_edit, stringResource(R.string.menu_edit), content, onEdit)
            if (showCode && flight == null) BarAction(R.drawable.ic_copy, stringResource(R.string.action_copy_number), content) { copy(context, card) }
            Spacer(Modifier.weight(1f))
            when {
                archived && card.usedAt != null -> BarButton(stringResource(R.string.action_restore), filled = false, onClick = onRestore)
                !archived && card.kind in MARKABLE -> BarButton(stringResource(R.string.action_mark_used), filled = false, onClick = onMarkUsed)
            }
            Spacer(Modifier.width(8.dp))
            BarButton(stringResource(R.string.action_done), filled = true, onClick = onClose)
        }
    }
}

private val SWIPE_TO_CLOSE = 120.dp
private const val FLING_TO_CLOSE = 2_000f

@Composable
private fun BarAction(icon: Int, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, color = tint, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun BarButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (filled) colorResource(R.color.code_panel) else Color.Black.copy(alpha = 0.22f),
        contentColor = if (filled) colorResource(R.color.code_ink) else Color.White,
        modifier = Modifier.height(48.dp),
    ) {
        Box(Modifier.padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
            Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun CardHeader(card: Card, used: String?, content: Color) {
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
            Text(listOfNotNull(subtitle, used).joinToString(" · "), color = content.copy(alpha = 0.8f), fontSize = 13.sp)
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
private fun FlightHeader(card: Card, flight: Flight, airports: Airports?, live: FlightCheck?, content: Color) {
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
        if (live != null) LiveLine(live, content)
    }
}

/** The flight's status, how late it is and how fresh the information is; or that the service does not know it. */
@Composable
private fun LiveLine(check: FlightCheck, content: Color) {
    val live = check.flight
    if (live == null) {
        Text(stringResource(R.string.flight_not_found), color = content.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        return
    }
    val late = live.scheduledDeparture?.let { scheduled -> live.departure?.let { Duration.between(scheduled, it).toMinutes() } }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        FlightText.state(live.state)?.let { label ->
            Text(
                stringResource(label),
                color = content,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        when {
            late == null || FlightText.airborne(live.state) -> Unit
            late >= MINUTES_WORTH_SAYING -> Text(stringResource(R.string.flight_late, late), color = content, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            late <= -MINUTES_WORTH_SAYING -> Text(stringResource(R.string.flight_early, -late), color = content, fontSize = 12.sp)
        }
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.flight_updated, relativeTime(check.checkedAt)), color = content.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1)
    }
}

/** Gate, terminal and departure time before take-off; arrival time, gate and belt after it. */
@Composable
private fun liveDetails(live: LiveFlight?): List<Detail> {
    if (live == null) return emptyList()
    val format = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    return if (FlightText.airborne(live.state)) {
        listOfNotNull(
            live.arrival?.let { Detail(stringResource(R.string.detail_arrives), it.toLocalTime().format(format)) },
            live.arrivalGate?.let { Detail(stringResource(R.string.detail_arrival_gate), it) },
            live.baggageBelt?.let { Detail(stringResource(R.string.detail_belt), it) },
        )
    } else {
        listOfNotNull(
            live.departureGate?.let { Detail(stringResource(R.string.detail_gate), it) },
            live.departureTerminal?.let { Detail(stringResource(R.string.detail_terminal), it) },
            live.departure?.let { Detail(stringResource(R.string.detail_departs), it.toLocalTime().format(format)) },
        )
    }
}

@Composable
private fun Airport(code: String, city: String?, content: Color, alignment: Alignment.Horizontal, modifier: Modifier) {
    Column(modifier, horizontalAlignment = alignment) {
        Text(code, color = content, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
        Text(city.orEmpty(), color = content.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// Live facts come first and replace a pass's own field with the same label, such as a gate printed at check-in.
@Composable
private fun pageDetails(card: Card, live: LiveFlight?): List<Detail> {
    val flight = card.flight ?: return card.details
    val facts = liveDetails(live)
    val replaced = facts.map { it.label.lowercase() }.toSet()
    val details = card.details.filterNot { it.label.lowercase() in replaced }
    val seat = flight.seat.takeIf { it.isNotBlank() && details.none { d -> d.label.equals(stringResource(R.string.detail_seat), true) } }
    return facts + details + listOfNotNull(seat?.let { Detail(stringResource(R.string.detail_seat), it) })
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
