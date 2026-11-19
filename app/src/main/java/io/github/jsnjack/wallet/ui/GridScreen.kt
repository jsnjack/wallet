package io.github.jsnjack.wallet.ui

import android.text.format.DateUtils
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jsnjack.wallet.R
import io.github.jsnjack.wallet.core.Card
import io.github.jsnjack.wallet.core.CardKind
import io.github.jsnjack.wallet.core.CardLooks
import io.github.jsnjack.wallet.core.FlightCheck
import io.github.jsnjack.wallet.core.FlightRef
import io.github.jsnjack.wallet.core.LiveFlight
import io.github.jsnjack.wallet.core.UpNext
import io.github.jsnjack.wallet.core.UpNextReason
import io.github.jsnjack.wallet.drive.SyncProblem
import io.github.jsnjack.wallet.drive.SyncStatus
import io.github.jsnjack.wallet.flight.FlightText
import io.github.jsnjack.wallet.platform.Biometrics
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val TILE_GAP = 8.dp

/**
 * The card grid, built for one hand: it fills from the bottom, so the most
 * used card sits bottom-right next to the thumb and "Up next" sits just above
 * the bottom bar. Every action is in the bottom bar or a bottom sheet.
 */
@Composable
fun GridScreen(vm: WalletViewModel) {
    val all by vm.cards.collectAsStateWithLifecycle()
    val visible by vm.visible.collectAsStateWithLifecycle()
    val archivedMatches by vm.archivedMatches.collectAsStateWithLifecycle()
    val upNext by vm.upNext.collectAsStateWithLifecycle()
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val groupSizes by vm.groupSizes.collectAsStateWithLifecycle()
    val flightChecks by vm.flightChecks.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sync by vm.syncStatus.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<Card?>(null) }
    var confirmDelete by remember { mutableStateOf<Card?>(null) }
    var more by remember { mutableStateOf(false) }
    val ltr = LocalLayoutDirection.current
    val showRail = favourites.isNotEmpty() && query.isBlank()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // The grid fills from the bottom, so it must end above the keyboard or search results hide behind it.
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            TitleBar(
                title = stringResource(R.string.app_name),
                subtitle = listOfNotNull(pluralStringResource(R.plurals.card_count, all.size, all.size), syncSummary(sync)).joinToString(" · "),
            )
            when {
                all.isEmpty() && archivedMatches.isEmpty() ->
                    Hint(stringResource(R.string.grid_empty_title), stringResource(R.string.grid_empty_body))
                query.isNotBlank() && visible.isEmpty() && archivedMatches.isEmpty() -> Hint(stringResource(R.string.grid_no_match), null)
                // Right-to-left and bottom-up: the first card lands bottom-right, under a right thumb.
                else -> CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        reverseLayout = true,
                        contentPadding = PaddingValues(start = SCREEN_PADDING, end = SCREEN_PADDING, top = 8.dp, bottom = if (showRail) 86.dp + RAIL_HEIGHT else 76.dp),
                        horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
                        verticalArrangement = Arrangement.spacedBy(TILE_GAP, Alignment.Bottom),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (query.isBlank() && upNext.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "up-next") {
                                CompositionLocalProvider(LocalLayoutDirection provides ltr) { NowBar(upNext, flightChecks, onOpen = vm::openCard) }
                            }
                        }
                        items(visible, key = { it.id }) { card ->
                            CompositionLocalProvider(LocalLayoutDirection provides ltr) {
                                GridTile(card, groupSizes[card.id] ?: 1, vm, onMenu = { menuFor = card })
                            }
                        }
                        if (archivedMatches.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "archive-matches") {
                                CompositionLocalProvider(LocalLayoutDirection provides ltr) { ArchivedMatches(archivedMatches, vm) }
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter)) {
            if (showRail) FavouritesRail(favourites, onOpen = vm::openCard, onMenu = { menuFor = it })
            BottomBar(
                query = query,
                onQuery = { vm.query.value = it },
                onOpenFirst = { (visible.firstOrNull() ?: archivedMatches.firstOrNull())?.let { vm.openCard(it.id) } },
                onMore = { more = true },
                onAdd = vm::startScan,
                modifier = Modifier,
            )
        }
    }

    if (more) MoreSheet(vm, onDismiss = { more = false })
    menuFor?.let { card -> CardMenuSheet(card, vm, onDelete = { confirmDelete = card }, onDismiss = { menuFor = null }) }
    confirmDelete?.let { card ->
        val count = groupSizes[card.id] ?: 1
        ConfirmSheet(
            title = stringResource(R.string.delete_title, card.name),
            body = if (count > 1) pluralStringResource(R.plurals.delete_group_body, count, count) else stringResource(R.string.delete_body),
            confirm = stringResource(R.string.action_delete),
            onConfirm = { vm.deleteTile(card.id); confirmDelete = null },
            onDismiss = { confirmDelete = null },
        )
    }
}

private val RAIL_HEIGHT = 82.dp

/**
 * Favourites in a fixed strip just above the bottom bar: always one tap
 * away, even when the grid above is scrolled. Fills from the right and
 * scrolls sideways past four.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavouritesRail(cards: List<Card>, onOpen: (String) -> Unit, onMenu: (Card) -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = SCREEN_PADDING).height(RAIL_HEIGHT - 8.dp),
    ) {
        // Right to left like the grid: the first favourite sits by the right thumb.
        LazyRow(
            reverseLayout = true,
            contentPadding = PaddingValues(7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        ) {
            items(cards, key = { it.id }) { card ->
                MiniTile(
                    card.name,
                    card.color,
                    flight = card.kind == CardKind.BOARDING_PASS,
                    modifier = Modifier
                        .width(RAIL_TILE_WIDTH)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .combinedClickable(onClick = { onOpen(card.id) }, onLongClick = { onMenu(card) }),
                )
            }
        }
    }
}

private val RAIL_TILE_WIDTH = 86.dp

@Composable
private fun ArchivedMatches(cards: List<Card>, vm: WalletViewModel) {
    Column {
        Text(
            stringResource(R.string.archive_title),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        )
        Group {
            cards.forEachIndexed { i, card ->
                if (i > 0) GroupDivider()
                CardRow(card, stringResource(kindLabel(card.kind))) { vm.openCard(card.id) }
            }
        }
    }
}

@Composable
private fun syncSummary(sync: SyncStatus): String? = when {
    !sync.enabled -> null
    sync.running -> stringResource(R.string.sync_running_short)
    sync.problem == SyncProblem.NEEDS_SIGN_IN -> stringResource(R.string.sync_needs_sign_in_short)
    sync.problem == SyncProblem.FAILED -> stringResource(R.string.sync_failed_short)
    sync.lastSyncAt > 0 -> stringResource(R.string.sync_done_short)
    else -> null
}

@Composable
private fun Hint(title: String, body: String?) {
    Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 120.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }
}

/**
 * "Up next" as a dark capsule, like One UI's Now Bar on the lock screen.
 * Swipe sideways when several cards are due. A flight with live information
 * shows its status, gate and expected departure.
 */
@Composable
private fun NowBar(items: List<UpNext>, flightChecks: Map<String, FlightCheck>, onOpen: (String) -> Unit) {
    val pager = rememberPagerState { items.size }
    val accent = colorResource(R.color.now_bar_accent)
    HorizontalPager(state = pager, pageSpacing = 8.dp, modifier = Modifier.fillMaxWidth()) { page ->
        val item = items[page]
        val card = item.card
        val live = FlightRef.of(card)?.takeIf { item.reason == UpNextReason.FLIGHT }?.let { flightChecks[it.id]?.flight }
        Surface(onClick = { onOpen(card.id) }, shape = CircleShape, color = colorResource(R.color.now_bar), contentColor = Color.White) {
            Row(Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    if (card.kind == CardKind.BOARDING_PASS) {
                        Icon(painterResource(R.drawable.ic_flight), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                    } else {
                        Text(CardLooks.monogram(card.name), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, maxLines = 1)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text((live?.let { liveLabel(it) } ?: upNextLabel(item)).uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = accent, maxLines = 1)
                    Text(
                        card.flight?.let { "${it.code} · ${it.from} → ${it.to}" } ?: card.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val time = live?.departure?.toLocalTime() ?: (if (item.reason == UpNextReason.EXPIRES_SOON) card.expires else card.event)?.time
                val trailing = time?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                    ?: if (items.size > 1) "${page + 1}/${items.size}" else null
                if (trailing != null) Text(trailing, color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

// "Boarding · Gate D7"; null when the service gave neither, so the plain label shows.
@Composable
private fun liveLabel(live: LiveFlight): String? = listOfNotNull(
    FlightText.state(live.state)?.let { stringResource(it) },
    live.departureGate?.let { stringResource(R.string.flight_gate_value, it) },
).joinToString(" · ").ifEmpty { null }

@Composable
private fun upNextLabel(item: UpNext): String = when (item.reason) {
    UpNextReason.FLIGHT -> item.card.event?.let { stringResource(R.string.up_next_departs, dayTimeText(it)) }
        ?: stringResource(R.string.up_next_flight)
    UpNextReason.EVENT_TODAY -> item.card.event?.let { stringResource(R.string.up_next_event_at, dayTimeText(it)) }
        ?: stringResource(R.string.up_next_event)
    UpNextReason.EXPIRES_SOON -> item.card.expires?.let { stringResource(R.string.up_next_expires_at, dayTimeText(it)) }
        ?: stringResource(R.string.up_next_expires)
}

/** A small brand-coloured square with the card's monogram, or a plane for flights. */
@Composable
fun Monogram(card: Card, modifier: Modifier = Modifier) {
    val content = Color(CardLooks.contentColor(card.color))
    Box(modifier.clip(RoundedCornerShape(11.dp)).background(Color(card.color)), contentAlignment = Alignment.Center) {
        if (card.kind == CardKind.BOARDING_PASS) {
            Icon(painterResource(R.drawable.ic_flight), contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        } else {
            Text(CardLooks.monogram(card.name), color = content, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, maxLines = 1)
        }
    }
}

/** A one-line card entry inside a [Group], used for archived cards. */
@Composable
fun CardRow(card: Card, detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(card, Modifier.size(36.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(card.flight?.let { "${it.code} · ${it.from} → ${it.to}" } ?: card.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridTile(card: Card, count: Int, vm: WalletViewModel, onMenu: () -> Unit) {
    CardTile(
        name = card.name,
        count = count,
        color = card.color,
        kind = card.kind,
        pinned = card.pinned,
        locked = card.locked,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = { vm.openCard(card.id) }, onLongClick = onMenu),
    )
}

/** The long-press menu as a bottom sheet with full-width rows. */
@Composable
private fun CardMenuSheet(card: Card, vm: WalletViewModel, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val unlockTitle = stringResource(R.string.unlock_prompt_title)
    // Edit shows the number, so a locked card needs the same check as its code.
    fun afterUnlock(action: () -> Unit) {
        if (!card.locked || activity == null) return action()
        Biometrics.authenticate(activity, unlockTitle, onSuccess = action, onError = { vm.message(R.string.message_unlock_failed) })
    }
    WalletSheet(onDismiss) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Monogram(card, Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Text(card.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        SheetRow(stringResource(if (card.pinned) R.string.menu_unpin else R.string.menu_keep_at_top), R.drawable.ic_star_filled) {
            onDismiss(); vm.togglePinned(card.id)
        }
        SheetRow(stringResource(R.string.menu_pin_home), R.drawable.ic_add) { onDismiss(); vm.pinToHomeScreen(card.id) }
        SheetRow(stringResource(R.string.menu_edit), R.drawable.ic_edit) { onDismiss(); afterUnlock { vm.edit(card.id) } }
        SheetRow(stringResource(if (card.locked) R.string.menu_unlock else R.string.menu_lock), R.drawable.ic_lock) {
            onDismiss()
            when {
                card.locked -> afterUnlock { vm.toggleLocked(card.id) }
                Biometrics.available(context) -> vm.toggleLocked(card.id)
                else -> vm.message(R.string.message_no_screen_lock)
            }
        }
        SheetRow(stringResource(R.string.menu_delete), R.drawable.ic_close, color = colorResource(R.color.danger)) { onDismiss(); onDelete() }
    }
}

/** Everything that used to sit in the top bar, now one thumb-tap away. */
@Composable
private fun MoreSheet(vm: WalletViewModel, onDismiss: () -> Unit) {
    val screenshots = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_SCREENSHOTS)) { uris ->
        if (uris.isNotEmpty()) vm.importScreenshots(uris, fromShare = false)
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(uri, fromShare = false)
    }
    WalletSheet(onDismiss) {
        SheetRow(stringResource(R.string.settings_google_wallet), R.drawable.ic_image) {
            onDismiss(); screenshots.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        SheetRow(stringResource(R.string.settings_pass_files), R.drawable.ic_file) { onDismiss(); files.launch(IMPORT_TYPES) }
        SheetRow(stringResource(R.string.archive_title), R.drawable.ic_archive) { onDismiss(); vm.openArchive() }
        SheetRow(stringResource(R.string.settings_title), R.drawable.ic_settings) { onDismiss(); vm.openSettings() }
    }
}

@Composable
private fun BottomBar(query: String, onQuery: (String) -> Unit, onOpenFirst: () -> Unit, onMore: () -> Unit, onAdd: () -> Unit, modifier: Modifier) {
    val background = MaterialTheme.colorScheme.background
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to background.copy(alpha = 0f), 0.3f to background))
            .navigationBarsPadding()
            .imePadding()
            .padding(start = SCREEN_PADDING, end = SCREEN_PADDING, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(onClick = onMore, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_more), contentDescription = stringResource(R.string.action_more))
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.weight(1f).height(48.dp)) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(stringResource(R.string.search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { onOpenFirst() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }, modifier = Modifier.size(32.dp)) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_clear_search), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(onClick = onAdd, shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.action_add_card), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(26.dp))
            }
        }
    }
}

/** "5 min ago" style text for a past time. */
fun relativeTime(time: Long): String =
    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
