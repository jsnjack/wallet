package io.github.jsnjack.wallet.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jsnjack.wallet.R

/** Shows the top screen of the stack, the snackbar, and Google's consent screen when sync asks for it. */
@Composable
fun WalletRoot(vm: WalletViewModel) {
    val stack by vm.stack.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        vm.onConsentResult(result.data)
    }
    LaunchedEffect(Unit) {
        vm.consentRequests.collect { consentLauncher.launch(IntentSenderRequest.Builder(it).build()) }
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
                is UiEvent.Message -> snackbar.showSnackbar(resources.getString(event.text))
                is UiEvent.Counted -> snackbar.showSnackbar(resources.getQuantityString(event.text, event.count, event.count))
            }
        }
    }
    BackHandler(enabled = stack.size > 1) { vm.back() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val screen = stack.last()) {
            Screen.Grid -> GridScreen(vm)
            is Screen.Code -> CodeScreen(vm, screen.cardId)
            Screen.Scan -> ScanScreen(vm)
            Screen.Form -> CardFormScreen(vm)
            Screen.Settings -> SettingsScreen(vm)
            Screen.FirstRun -> FirstRunScreen(vm)
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 72.dp),
        )
    }
}
