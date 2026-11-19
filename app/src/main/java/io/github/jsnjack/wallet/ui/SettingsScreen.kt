package io.github.jsnjack.wallet.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jsnjack.wallet.R
import io.github.jsnjack.wallet.core.ConnectProblem
import io.github.jsnjack.wallet.core.Retention
import io.github.jsnjack.wallet.drive.SyncProblem
import io.github.jsnjack.wallet.drive.SyncStatus
import java.time.LocalDate

private const val EXPORT_MIME = "application/octet-stream"
private const val MIN_PASSWORD = 8

private enum class PasswordFor { EXPORT, IMPORT }

/** Sync, imports, notifications, archive and backup, as a full-height sheet that closes with a swipe down. */
@Composable
fun SettingsSheet(vm: WalletViewModel) {
    val sync by vm.syncStatus.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val connect by vm.connect.collectAsStateWithLifecycle()
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
    val screenshots = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_SCREENSHOTS)) { uris ->
        if (uris.isNotEmpty()) vm.importScreenshots(uris, fromShare = false)
    }
    val passFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(uri, fromShare = false)
    }
    val retention by vm.retention.collectAsStateWithLifecycle()
    val notifications by vm.notifications.collectAsStateWithLifecycle()

    WalletSheet(onDismiss = { vm.back() }) {
        Text(stringResource(R.string.settings_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(GROUP_GAP),
        ) {
            SyncGroup(sync, busy, connect, vm)
            Group {
                GroupRow(
                    stringResource(R.string.settings_google_wallet),
                    stringResource(R.string.settings_google_wallet_detail),
                    icon = R.drawable.ic_image,
                    onClick = { screenshots.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                )
                GroupDivider()
                GroupRow(
                    stringResource(R.string.settings_pass_files),
                    stringResource(R.string.settings_pass_files_detail),
                    icon = R.drawable.ic_file,
                    onClick = { passFiles.launch(IMPORT_TYPES) },
                )
            }
            Group {
                GroupRow(
                    stringResource(R.string.settings_notifications),
                    stringResource(R.string.settings_notifications_detail),
                    icon = R.drawable.ic_brightness,
                    onClick = { vm.setNotifications(!notifications) },
                ) { WalletSwitch(checked = notifications, onCheckedChange = vm::setNotifications) }
                GroupDivider()
                Column(Modifier.padding(start = 54.dp, end = 18.dp, top = 12.dp, bottom = 14.dp)) {
                    Text(stringResource(R.string.settings_retention), fontSize = 16.sp)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = CHIP_GAP) {
                        Retention.entries.forEach { option ->
                            WalletChip(stringResource(retentionLabel(option)), selected = option == retention, onClick = { vm.setRetention(option) })
                        }
                    }
                }
            }
            Group {
                GroupRow(
                    stringResource(R.string.settings_export),
                    stringResource(R.string.settings_export_detail),
                    onClick = { exportPicker.launch(resources.getString(R.string.export_file_name, LocalDate.now().toString())) },
                )
                GroupDivider()
                GroupRow(
                    stringResource(R.string.settings_import),
                    stringResource(R.string.settings_import_detail),
                    onClick = { importPicker.launch(arrayOf("*/*")) },
                )
            }
            Text(
                stringResource(R.string.settings_version, context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
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

/**
 * What happened after "Turn on sync": a spinner while Google is asked, or a
 * plain explanation when it refused. Nothing when idle or cancelled.
 */
@Composable
fun ConnectStatus(state: ConnectState) {
    when (state) {
        ConnectState.Idle -> Unit
        ConnectState.Connecting -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.sync_connecting), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is ConnectState.Failed -> Column {
            Text(
                stringResource(
                    when (state.problem) {
                        ConnectProblem.NOT_REGISTERED -> R.string.sync_problem_not_registered
                        ConnectProblem.API_DISABLED -> R.string.sync_problem_api_disabled
                        ConnectProblem.NETWORK -> R.string.sync_problem_network
                        ConnectProblem.OTHER -> R.string.sync_problem_other
                    },
                ),
                fontSize = 14.sp,
                color = colorResource(R.color.danger),
            )
            state.code?.let {
                Text(stringResource(R.string.sync_problem_code, it), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The label for an archive retention choice. */
fun retentionLabel(value: Retention): Int = when (value) {
    Retention.THREE_MONTHS -> R.string.retention_three_months
    Retention.ONE_YEAR -> R.string.retention_one_year
    Retention.FOREVER -> R.string.retention_forever
}

@Composable
private fun SyncGroup(sync: SyncStatus, busy: Boolean, connect: ConnectState, vm: WalletViewModel) {
    if (!sync.enabled) {
        Group {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.sync_title), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(stringResource(R.string.sync_off_body), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Button(onClick = vm::connectDrive, enabled = !busy && connect != ConnectState.Connecting) { Text(stringResource(R.string.sync_turn_on)) }
                ConnectStatus(connect)
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
        if (connect is ConnectState.Failed) {
            Box(Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp)) { ConnectStatus(connect) }
        }
        GroupRow(title, sync.account ?: stringResource(R.string.sync_account_unknown), titleColor = tint) {
            if (sync.running || busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        }
        GroupDivider()
        if (sync.problem == SyncProblem.NEEDS_SIGN_IN) {
            GroupRow(stringResource(R.string.sync_sign_in_again), titleColor = MaterialTheme.colorScheme.primary, onClick = vm::connectDrive)
        } else {
            GroupRow(stringResource(R.string.sync_now), titleColor = MaterialTheme.colorScheme.primary, onClick = vm::syncNow)
        }
        GroupDivider()
        GroupRow(
            stringResource(R.string.sync_mobile_data),
            stringResource(R.string.sync_mobile_data_detail),
            onClick = { vm.setUseMobileData(!sync.useMobileData) },
        ) { WalletSwitch(checked = sync.useMobileData, onCheckedChange = vm::setUseMobileData) }
        GroupDivider()
        GroupRow(
            stringResource(R.string.sync_turn_off),
            stringResource(R.string.sync_turn_off_detail),
            titleColor = colorResource(R.color.danger),
            onClick = vm::disableSync,
        )
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
                TextField(
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
                    TextField(
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
            ) { Text(stringResource(R.string.action_continue), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold) } },
    )
}
