package io.github.jsnjack.wallet.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import io.github.jsnjack.wallet.core.CardOrder
import io.github.jsnjack.wallet.core.Retention
import java.time.LocalDate

private const val EXPORT_MIME = "application/octet-stream"
private const val MIN_PASSWORD = 8

private enum class PasswordFor { EXPORT, IMPORT }

/** Imports, notifications, archive, live flight info and backup, as a full-height sheet that closes with a swipe down. */
@Composable
fun SettingsSheet(vm: WalletViewModel) {
    var passwordFor by remember { mutableStateOf<PasswordFor?>(null) }
    var target by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val resources = LocalResources.current
    // A system call: once per sheet, not on every redraw.
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }

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
    val pickers = rememberImportPickers(vm)
    val retention by vm.retention.collectAsStateWithLifecycle()
    val order by vm.order.collectAsStateWithLifecycle()
    val notifications by vm.notifications.collectAsStateWithLifecycle()

    WalletSheet(onDismiss = { vm.back() }) {
        Text(stringResource(R.string.settings_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(GROUP_GAP),
        ) {
            Group {
                GroupRow(
                    stringResource(R.string.settings_google_wallet),
                    stringResource(R.string.settings_google_wallet_detail),
                    icon = R.drawable.ic_image,
                    onClick = pickers::pickScreenshots,
                )
                GroupDivider()
                GroupRow(
                    stringResource(R.string.settings_pass_files),
                    stringResource(R.string.settings_pass_files_detail),
                    icon = R.drawable.ic_file,
                    onClick = pickers::pickFile,
                )
            }
            Group {
                SwitchRow(
                    stringResource(R.string.settings_notifications),
                    stringResource(R.string.settings_notifications_detail),
                    icon = R.drawable.ic_brightness,
                    checked = notifications,
                    onCheckedChange = vm::setNotifications,
                )
                GroupDivider()
                Column(Modifier.padding(start = 54.dp, end = 18.dp, top = 12.dp, bottom = 14.dp)) {
                    Text(stringResource(R.string.settings_order), fontSize = 16.sp)
                    Row(Modifier.padding(top = 8.dp, bottom = 14.dp), horizontalArrangement = CHIP_GAP) {
                        CardOrder.entries.forEach { option ->
                            WalletChip(stringResource(orderLabel(option)), selected = option == order, onClick = { vm.setOrder(option) })
                        }
                    }
                    Text(stringResource(R.string.settings_retention), fontSize = 16.sp)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = CHIP_GAP) {
                        Retention.entries.forEach { option ->
                            WalletChip(stringResource(retentionLabel(option)), selected = option == retention, onClick = { vm.setRetention(option) })
                        }
                    }
                }
            }
            FlightGroup(vm)
            Group {
                GroupRow(stringResource(R.string.settings_backup), stringResource(R.string.settings_backup_detail))
                GroupDivider()
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
                stringResource(R.string.settings_version, version),
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

/** The label for a card order choice. */
fun orderLabel(value: CardOrder): Int = when (value) {
    CardOrder.NAME -> R.string.order_name
    CardOrder.MOST_USED -> R.string.order_most_used
}

/** The label for an archive retention choice. */
fun retentionLabel(value: Retention): Int = when (value) {
    Retention.THREE_MONTHS -> R.string.retention_three_months
    Retention.ONE_YEAR -> R.string.retention_one_year
    Retention.FOREVER -> R.string.retention_forever
}

/** Live flight info on or off, with where the information comes from. */
@Composable
private fun FlightGroup(vm: WalletViewModel) {
    val enabled by vm.liveFlights.collectAsStateWithLifecycle()
    Group {
        SwitchRow(
            stringResource(R.string.flights_title),
            stringResource(R.string.flights_body),
            icon = R.drawable.ic_flight,
            checked = enabled,
            onCheckedChange = vm::setLiveFlights,
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
