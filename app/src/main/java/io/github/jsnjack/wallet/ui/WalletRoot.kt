package io.github.jsnjack.wallet.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import io.github.jsnjack.wallet.R
import io.github.jsnjack.wallet.core.Bcbp
import io.github.jsnjack.wallet.platform.ReadCode
import java.time.LocalDate

/**
 * Shows the top screen of the stack inside a themed [Surface] (so text
 * follows light and dark mode), plus the snackbar, the code chooser and the
 * permission and consent screens the view model asks for.
 */
@Composable
fun WalletRoot(vm: WalletViewModel) {
    val stack by vm.stack.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val choices by vm.choices.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        vm.onConsentResult(result.data)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.refreshSurfaces()
    }
    LaunchedEffect(Unit) {
        vm.consentRequests.collect { consentLauncher.launch(IntentSenderRequest.Builder(it).build()) }
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
                    val result = snackbar.showSnackbar(
                        resources.getString(R.string.message_deleted, event.card.name),
                        actionLabel = resources.getString(R.string.action_undo),
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.undoDelete(event.card)
                }
                is UiEvent.MarkedUsed -> {
                    val result = snackbar.showSnackbar(
                        resources.getString(R.string.message_marked_used, event.card.name),
                        actionLabel = resources.getString(R.string.action_undo),
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.restore(event.card.id)
                }
                is UiEvent.Message -> snackbar.showSnackbar(resources.getString(event.text))
                is UiEvent.Counted -> snackbar.showSnackbar(resources.getQuantityString(event.text, event.count, event.count))
            }
        }
    }
    BackHandler(enabled = stack.size > 1) { vm.back() }

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
                Screen.FirstRun -> FirstRunScreen(vm)
                Screen.Archive, Screen.Settings -> GridScreen(vm)
            }
            if (top == Screen.Settings) SettingsSheet(vm)
            if (top == Screen.Archive) ArchiveSheet(vm)
            if (busy && stack.last() != Screen.FirstRun) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 68.dp),
            ) { ToastSnackbar(it) }
        }
    }

    choices?.let { codes -> CodeChooser(codes, onPick = vm::onScanned, onDismiss = vm::dismissChoices) }
}

private val SHEET_SCREENS = setOf(Screen.Settings, Screen.Archive)

/** Several codes in one file: pick one from a sheet. */
@Composable
private fun CodeChooser(codes: List<ReadCode>, onPick: (ReadCode) -> Unit, onDismiss: () -> Unit) {
    val today = LocalDate.now()
    WalletSheet(onDismiss) {
        Text(stringResource(R.string.choose_code_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            codes.forEach { code ->
                val leg = Bcbp.parse(code.text, today)?.legs?.firstOrNull()
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(code) }.padding(horizontal = 12.dp, vertical = 12.dp)) {
                    Text(
                        leg?.let { "${it.flight.code} · ${it.flight.from} → ${it.flight.to} · ${it.flight.passenger}" } ?: code.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 15.sp,
                    )
                    Text(code.format.label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
