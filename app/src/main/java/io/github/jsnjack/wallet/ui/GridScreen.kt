package io.github.jsnjack.wallet.ui

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jsnjack.wallet.R
import io.github.jsnjack.wallet.core.Card
import io.github.jsnjack.wallet.drive.SyncProblem
import io.github.jsnjack.wallet.drive.SyncStatus
import io.github.jsnjack.wallet.platform.Biometrics

/** The card grid with search and the add button at the bottom. */
@Composable
fun GridScreen(vm: WalletViewModel) {
    val all by vm.cards.collectAsStateWithLifecycle()
    val visible by vm.visible.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sync by vm.syncStatus.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<Card?>(null) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header(count = all.size, sync = sync, onSettings = vm::openSettings)
            when {
                all.isEmpty() -> Hint(stringResource(R.string.grid_empty_title), stringResource(R.string.grid_empty_body))
                visible.isEmpty() -> Hint(stringResource(R.string.grid_no_match), null)
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(visible, key = { it.id }) { card ->
                        GridTile(card, vm, onDelete = { confirmDelete = card })
                    }
                }
            }
        }
        BottomBar(
            query = query,
            onQuery = { vm.query.value = it },
            onOpenFirst = { visible.firstOrNull()?.let { vm.openCard(it.id) } },
            onAdd = vm::startScan,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    confirmDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete_title, card.name)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(card.id); confirmDelete = null }) {
                    Text(stringResource(R.string.action_delete), color = colorResource(R.color.danger))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Header(count: Int, sync: SyncStatus, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(pluralStringResource(R.plurals.card_count, count, count), syncSummary(sync)).joinToString(" · "),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSettings) {
            Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings_title))
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
    Column(Modifier.fillMaxWidth().padding(top = 96.dp, start = 32.dp, end = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridTile(card: Card, vm: WalletViewModel, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box {
        CardTile(
            name = card.name,
            color = card.color,
            badge = listOfNotNull("★".takeIf { card.pinned }, "🔒".takeIf { card.locked }).joinToString(" ").ifEmpty { null },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(onClick = { vm.openCard(card.id) }, onLongClick = { menu = true }),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            MenuItem(if (card.pinned) R.string.menu_unpin else R.string.menu_keep_at_top) { menu = false; vm.togglePinned(card.id) }
            MenuItem(R.string.menu_pin_home) { menu = false; vm.pinToHomeScreen(card.id) }
            MenuItem(R.string.menu_edit) { menu = false; vm.edit(card.id) }
            MenuItem(if (card.locked) R.string.menu_unlock else R.string.menu_lock) {
                menu = false
                if (card.locked || Biometrics.available(context)) vm.toggleLocked(card.id) else vm.message(R.string.message_no_screen_lock)
            }
            MenuItem(R.string.menu_delete, danger = true) { menu = false; onDelete() }
        }
    }
}

@Composable
private fun MenuItem(text: Int, danger: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(text), color = if (danger) colorResource(R.color.danger) else MaterialTheme.colorScheme.onSurface) },
        onClick = onClick,
    )
}

@Composable
private fun BottomBar(query: String, onQuery: (String) -> Unit, onOpenFirst: () -> Unit, onAdd: () -> Unit, modifier: Modifier) {
    val background = MaterialTheme.colorScheme.background
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to background.copy(alpha = 0f), 0.35f to background))
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp, modifier = Modifier.weight(1f).height(48.dp)) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(stringResource(R.string.search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
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
        Surface(
            onClick = onAdd,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.action_add_card), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

/** "5 min ago" style text for a past time. */
fun relativeTime(time: Long): String =
    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
