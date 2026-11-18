package io.github.jsnjack.wallet.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jsnjack.wallet.R
import io.github.jsnjack.wallet.drive.SyncProblem
import io.github.jsnjack.wallet.drive.SyncStatus
import java.time.LocalDate

private const val EXPORT_MIME = "application/octet-stream"
private const val MIN_PASSWORD = 8

private enum class PasswordFor { EXPORT, IMPORT }

/** Sync with Google Drive, export and import. */
@Composable
fun SettingsScreen(vm: WalletViewModel) {
    val sync by vm.syncStatus.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var passwordFor by remember { mutableStateOf<PasswordFor?>(null) }
    var target by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val resources = LocalResources.current

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(EXPORT_MIME)) { uri ->
        if (uri != null) {
            target = uri
            passwordFor = PasswordFor.EXPORT
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            target = uri
            passwordFor = PasswordFor.IMPORT
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close))
            }
            Text(stringResource(R.string.settings_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(horizontal = SCREEN_PADDING), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SyncCard(sync, busy, vm)
            Group {
                ClickRow(stringResource(R.string.settings_export), stringResource(R.string.settings_export_detail)) {
                    exportPicker.launch(resources.getString(R.string.export_file_name, LocalDate.now().toString()))
                }
                ClickRow(stringResource(R.string.settings_import), stringResource(R.string.settings_import_detail)) {
                    importPicker.launch(arrayOf("*/*"))
                }
            }
            Text(
                stringResource(R.string.settings_version, context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp),
            )
        }
    }

    val purpose = passwordFor
    val uri = target
    if (purpose != null && uri != null) {
        PasswordDialog(
            confirmTwice = purpose == PasswordFor.EXPORT,
            onDismiss = { passwordFor = null },
            onDone = { password ->
                passwordFor = null
                if (purpose == PasswordFor.EXPORT) vm.export(uri, password) else vm.import(uri, password)
            },
        )
    }
}

@Composable
private fun SyncCard(sync: SyncStatus, busy: Boolean, vm: WalletViewModel) {
    if (!sync.enabled) {
        Group {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.sync_title), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(stringResource(R.string.sync_off_body), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Button(onClick = vm::connectDrive, enabled = !busy) { Text(stringResource(R.string.sync_turn_on)) }
            }
        }
        return
    }
    val (title, tint) = when {
        sync.running || busy -> stringResource(R.string.sync_running) to colorResource(R.color.muted)
        sync.problem == SyncProblem.NEEDS_SIGN_IN -> stringResource(R.string.sync_needs_sign_in) to colorResource(R.color.warning)
        sync.problem == SyncProblem.FAILED -> stringResource(R.string.sync_failed) to colorResource(R.color.danger)
        sync.lastSyncAt > 0 -> stringResource(R.string.sync_done, relativeTime(sync.lastSyncAt)) to colorResource(R.color.positive)
        else -> stringResource(R.string.sync_waiting) to colorResource(R.color.muted)
    }
    Group {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    sync.account ?: stringResource(R.string.sync_account_unknown),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            if (sync.running || busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(4.dp))
        }
        if (sync.problem == SyncProblem.NEEDS_SIGN_IN) {
            ClickRow(stringResource(R.string.sync_sign_in_again), null, onClick = vm::connectDrive)
        } else {
            ClickRow(stringResource(R.string.sync_now), null, onClick = vm::syncNow)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sync_mobile_data), fontSize = 15.sp)
                Text(stringResource(R.string.sync_mobile_data_detail), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = sync.useMobileData, onCheckedChange = vm::setUseMobileData)
        }
        ClickRow(stringResource(R.string.sync_turn_off), stringResource(R.string.sync_turn_off_detail), danger = true, onClick = vm::disableSync)
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun ClickRow(title: String, detail: String?, danger: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, fontSize = 15.sp, color = if (danger) colorResource(R.color.danger) else Color.Unspecified)
        if (detail != null) Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PasswordDialog(confirmTwice: Boolean, onDismiss: () -> Unit, onDone: (CharArray) -> Unit) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val tooShort = confirmTwice && password.length < MIN_PASSWORD
    val mismatch = confirmTwice && repeat != password
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (confirmTwice) R.string.password_export_title else R.string.password_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (confirmTwice) Text(stringResource(R.string.password_export_body), fontSize = 14.sp)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = if (tooShort && password.isNotEmpty()) {
                        { Text(pluralStringResource(R.plurals.password_too_short, MIN_PASSWORD, MIN_PASSWORD)) }
                    } else {
                        null
                    },
                )
                if (confirmTwice) {
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        label = { Text(stringResource(R.string.password_repeat)) },
                        singleLine = true,
                        isError = mismatch && repeat.isNotEmpty(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onDone(password.toCharArray()) },
                enabled = password.isNotEmpty() && !tooShort && !mismatch,
            ) { Text(stringResource(R.string.action_continue)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
