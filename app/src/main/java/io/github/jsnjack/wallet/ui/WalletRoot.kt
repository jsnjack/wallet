package io.github.jsnjack.wallet.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import io.github.jsnjack.wallet.platform.CodeBitmaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.jsnjack.wallet.R
import io.github.jsnjack.wallet.core.Bcbp
import io.github.jsnjack.wallet.platform.ReadCode
import java.time.LocalDate

/**
 * Shows the top screen of the stack inside a themed [Surface] (so text
 * follows light and dark mode), plus the snackbar, the code chooser and the
 * notification permission request the view model asks for.
 */
@Composable
fun WalletRoot(vm: WalletViewModel) {
    val stack by vm.stack.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val choices by vm.choices.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.refreshSurfaces()
    }
    LaunchedEffect(Unit) {
        vm.notificationPermissionRequests.collect {
            if (vm.notifications.value &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { event ->
            when (event) {
                is UiEvent.Deleted -> {
                    // With an action Material keeps a snackbar until dismissed; Long lets it go after about 10 s.
                    val result = snackbar.showSnackbar(
                        resources.getString(R.string.message_deleted, event.cards.first().name),
                        actionLabel = resources.getString(R.string.action_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.undoDelete(event.cards)
                }
                is UiEvent.MarkedUsed -> {
                    val result = snackbar.showSnackbar(
                        resources.getString(R.string.message_marked_used, event.card.name),
                        actionLabel = resources.getString(R.string.action_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.restore(event.card.id)
                }
                is UiEvent.Message -> snackbar.showSnackbar(resources.getString(event.text))
                is UiEvent.Counted -> snackbar.showSnackbar(resources.getQuantityString(event.text, event.count, event.count))
            }
        }
    }
    BackHandler(enabled = stack.size > 1) { vm.back() }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                // Settings and the archive are sheets: the screen below stays visible under them.
                val top = stack.last()
                val base = if (top in SHEET_SCREENS) stack.dropLast(1).lastOrNull() ?: Screen.Grid else top
                when (base) {
                    Screen.Grid -> GridScreen(vm)
                    is Screen.Code -> CodeScreen(vm, base.cardId)
                    Screen.Scan -> ScanScreen(vm)
                    Screen.Form -> CardFormScreen(vm)
                    Screen.Archive, Screen.Settings -> GridScreen(vm)
                }
                if (top == Screen.Settings) SettingsSheet(vm)
                if (top == Screen.Archive) ArchiveSheet(vm)
                if (busy) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                SnackbarHost(
                    snackbar,
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 68.dp),
                ) { ToastSnackbar(it) }
            }
        }

        choices?.let { codes -> CodeChooser(codes, onPick = vm::onChosen, onAddAll = vm::addAllChoices, onDismiss = vm::dismissChoices) }
    }
}

private val SHEET_SCREENS = setOf(Screen.Settings, Screen.Archive)

/**
 * The codes found in one file, each with a small picture to match against
 * it. When every code sits on its own page they are separate tickets, such
 * as two people's tickets in one PDF: the sheet says so and adds them all,
 * one form each, filled from its own page.
 */
@Composable
private fun CodeChooser(codes: List<ReadCode>, onPick: (ReadCode) -> Unit, onAddAll: () -> Unit, onDismiss: () -> Unit) {
    val today = LocalDate.now()
    val tickets = codes.size > 1 && codes.map { it.page }.distinct().size == codes.size
    WalletSheet(onDismiss) {
        Text(
            if (tickets) pluralStringResource(R.plurals.choose_tickets_title, codes.size, codes.size) else stringResource(R.string.choose_code_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
        )
        if (tickets) {
            Button(onClick = onAddAll, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(if (codes.size == 2) stringResource(R.string.choose_add_both) else stringResource(R.string.choose_add_all, codes.size))
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            codes.forEachIndexed { index, code ->
                val leg = Bcbp.parse(code.text, today)?.legs?.firstOrNull()
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(code) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CodeThumbnail(code)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                leg != null -> "${leg.flight.code} · ${leg.flight.from} → ${leg.flight.to} · ${leg.flight.passenger}"
                                tickets -> stringResource(R.string.choose_ticket_row, index + 1, code.page + 1)
                                else -> code.text
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 15.sp,
                        )
                        Text(
                            listOfNotNull(code.format.label, code.text.takeIf { tickets && leg == null }).joinToString(" · "),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** A small picture of [code] on white, drawn without smoothing like the printed one. */
@Composable
private fun CodeThumbnail(code: ReadCode) {
    val bitmap by produceState<ImageBitmap?>(null, code) {
        value = withContext(Dispatchers.Default) { CodeBitmaps.get(code.text, code.format)?.asImageBitmap() }
    }
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(5.dp), contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(
                it,
                contentDescription = null,
                contentScale = if (code.format.linear) ContentScale.FillBounds else ContentScale.Fit,
                filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
