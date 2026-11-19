package io.github.jsnjack.wallet.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import io.github.jsnjack.wallet.core.FlightLookups
import io.github.jsnjack.wallet.core.LookupProblem
import io.github.jsnjack.wallet.core.Retention
import io.github.jsnjack.wallet.flight.KeyCheck
import java.time.LocalDate
import java.time.YearMonth

private const val EXPORT_MIME = "application/octet-stream"
private const val MIN_PASSWORD = 8
private const val KEY_SHOWN_CHARS = 4

private enum class PasswordFor { EXPORT, IMPORT }

/** Imports, notifications, archive, live flight info and backup, as a full-height sheet that closes with a swipe down. */
@Composable
fun SettingsSheet(vm: WalletViewModel) {
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

/** The RapidAPI key for live flight info: paste and save it, then test or remove it; with this month's lookups. */
@Composable
private fun FlightGroup(vm: WalletViewModel) {
    val key by vm.flightKey.collectAsStateWithLifecycle()
    val usage by vm.lookupUsage.collectAsStateWithLifecycle()
    val check by vm.keyCheck.collectAsStateWithLifecycle()
    val checking by vm.checkingKey.collectAsStateWithLifecycle()
    var typed by remember { mutableStateOf("") }
    val month = YearMonth.now().toString()
    val used = if (usage.month == month) usage.used else 0
    // A used-up allowance belongs to its month; a refused key stays refused until it changes.
    val problem = usage.problem?.takeIf { usage.month == month || it == LookupProblem.KEY_REJECTED }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Group {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.flights_title), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(stringResource(R.string.flights_body), color = muted, fontSize = 14.sp)
            if (key.isBlank()) {
                WalletField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = stringResource(R.string.flights_key_label),
                    // A password keyboard does not learn or suggest the key.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { vm.setFlightKey(typed); typed = "" }, enabled = typed.isNotBlank()) { Text(stringResource(R.string.flights_save)) }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.flights_key_saved, key.takeLast(KEY_SHOWN_CHARS)), fontSize = 15.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::checkFlightKey, enabled = !checking) { Text(stringResource(R.string.flights_test)) }
                    TextButton(onClick = { vm.setFlightKey("") }) { Text(stringResource(R.string.flights_remove), color = colorResource(R.color.danger)) }
                }
                Text(pluralStringResource(R.plurals.flights_usage, FlightLookups.MONTHLY_LOOKUPS, used, FlightLookups.MONTHLY_LOOKUPS), color = muted, fontSize = 13.sp)
            }
            val status = when {
                checking -> stringResource(R.string.flights_checking) to colorResource(R.color.muted)
                check == KeyCheck.WORKS && problem == null -> stringResource(R.string.flights_key_works) to colorResource(R.color.positive)
                check == KeyCheck.REJECTED || problem == LookupProblem.KEY_REJECTED -> stringResource(R.string.flights_key_rejected) to colorResource(R.color.danger)
                check == KeyCheck.LIMIT_REACHED || problem == LookupProblem.LIMIT_REACHED -> stringResource(R.string.flights_limit_reached) to colorResource(R.color.warning)
                check == KeyCheck.UNREACHABLE -> stringResource(R.string.flights_unreachable) to colorResource(R.color.danger)
                else -> null
            }
            if (key.isNotBlank() && status != null) Text(status.first, color = status.second, fontSize = 14.sp)
        }
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
