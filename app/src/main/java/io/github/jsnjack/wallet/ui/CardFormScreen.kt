package io.github.jsnjack.wallet.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jsnjack.wallet.R
import io.github.jsnjack.wallet.core.Brands
import io.github.jsnjack.wallet.core.CardDraft
import io.github.jsnjack.wallet.core.CardKind
import io.github.jsnjack.wallet.core.CardLooks
import io.github.jsnjack.wallet.core.CodeFormat
import io.github.jsnjack.wallet.core.CodeNumbers
import io.github.jsnjack.wallet.core.DayTime
import io.github.jsnjack.wallet.core.Detail
import io.github.jsnjack.wallet.core.DraftProblem
import io.github.jsnjack.wallet.core.Flight
import io.github.jsnjack.wallet.platform.Biometrics
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private val EXPIRING_KINDS = setOf(CardKind.VOUCHER, CardKind.GIFT_CARD, CardKind.MEMBERSHIP)
private val PIN_KINDS = setOf(CardKind.VOUCHER, CardKind.GIFT_CARD)
private val DETAIL_KINDS = setOf(CardKind.TICKET, CardKind.BOARDING_PASS, CardKind.OTHER)
private val CHECKED_FORMATS = setOf(CodeFormat.EAN_13, CodeFormat.EAN_8, CodeFormat.UPC_A)

/** New-card and edit form; its fields follow the kind of card. */
@Composable
fun CardFormScreen(vm: WalletViewModel) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val queued by vm.queued.collectAsStateWithLifecycle()
    val current = draft ?: return
    val editing = current.editingId != null
    val problem = current.problem()
    var showProblem by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val update: ((CardDraft) -> CardDraft) -> Unit = vm::updateDraft
    val save = { if (problem == null) vm.saveDraft() else showProblem = true }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (editing) R.string.form_edit_title else R.string.form_new_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (queued > 0) Text(pluralStringResource(R.plurals.form_queue, queued, queued), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = SCREEN_PADDING),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CardTile(
                name = current.name.ifBlank { stringResource(R.string.form_name_placeholder) },
                color = current.effectiveColor,
                kind = current.kind,
                large = true,
                modifier = Modifier.fillMaxWidth(0.5f).align(Alignment.CenterHorizontally),
            )
            WalletField(
                value = current.name,
                onValueChange = { name -> update { it.copy(name = name) } },
                label = stringResource(R.string.form_name),
                singleLine = true,
                isError = showProblem && problem == DraftProblem.NAME_MISSING,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            val suggestions = Brands.suggest(current.name)
            if (suggestions.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { brand ->
                        WalletChip(
                            label = brand.name,
                            selected = false,
                            onClick = { update { it.copy(name = brand.name, color = brand.color) } },
                            leading = { Box(Modifier.size(14.dp).clip(CircleShape).background(Color(brand.color))) },
                        )
                    }
                }
            }
            WalletField(
                value = current.code,
                onValueChange = { code -> update { it.withCode(code) } },
                label = stringResource(R.string.form_number, current.format.label),
                singleLine = current.format.linear,
                maxLines = 3,
                isError = showProblem && (problem == DraftProblem.CODE_MISSING || problem == DraftProblem.CODE_NOT_DRAWABLE),
                supportingText = { CodeStatus(current, problem) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            SectionLabel(stringResource(R.string.form_format))
            ChipRow(CodeFormat.entries, current.format, { it.label }) { format -> update { it.copy(format = format, formatChosen = true) } }
            SectionLabel(stringResource(R.string.form_kind))
            val kindLabels = CardKind.entries.associateWith { stringResource(kindLabel(it)) }
            ChipRow(CardKind.entries, current.kind, { kindLabels.getValue(it) }) { kind -> update { it.copy(kind = kind) } }

            KindFields(current, showProblem && problem == DraftProblem.BALANCE_INVALID, update)

            SectionLabel(stringResource(R.string.form_color))
            Swatches(current) { color -> update { it.copy(color = color) } }
            WalletField(
                value = current.note,
                onValueChange = { note -> update { it.copy(note = note) } },
                label = stringResource(R.string.form_note),
                modifier = Modifier.fillMaxWidth(),
            )
            Group {
                ToggleRow(stringResource(R.string.form_keep_at_top), current.pinned) { on -> update { it.copy(pinned = on) } }
                GroupDivider()
                ToggleRow(stringResource(R.string.form_require_unlock), current.locked) { on ->
                        if (on && !Biometrics.available(context)) {
                            vm.message(R.string.message_no_screen_lock)
                        } else {
                            update { it.copy(locked = on) }
                        }
                }
            }
            if (editing) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.form_delete), color = colorResource(R.color.danger), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Box(Modifier.padding(horizontal = SCREEN_PADDING, vertical = 8.dp)) {
            if (queued > 0) {
                BottomButtons(stringResource(R.string.action_skip), vm::skipDraft, stringResource(R.string.action_save_next), save)
            } else {
                BottomButtons(stringResource(R.string.action_cancel), { vm.back() }, stringResource(R.string.action_save), save)
            }
        }
    }

    val editingId = current.editingId
    if (confirmDelete && editingId != null) {
        ConfirmSheet(
            title = stringResource(R.string.delete_title, current.name),
            body = stringResource(R.string.delete_body),
            confirm = stringResource(R.string.action_delete),
            onConfirm = { confirmDelete = false; vm.delete(editingId) },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Fields that only some kinds of card have: expiry, PIN, balance, event date, flight and details. */
@Composable
private fun KindFields(draft: CardDraft, balanceError: Boolean, update: ((CardDraft) -> CardDraft) -> Unit) {
    when (draft.kind) {
        CardKind.TICKET -> DayTimeField(stringResource(R.string.form_event), draft.event) { value -> update { it.copy(event = value) } }
        CardKind.BOARDING_PASS -> FlightFields(draft, update)
        else -> Unit
    }
    if (draft.kind in EXPIRING_KINDS) {
        DayTimeField(stringResource(R.string.form_expires), draft.expires) { value -> update { it.copy(expires = value) } }
    }
    if (draft.kind == CardKind.GIFT_CARD) {
        WalletField(
            value = draft.balanceText,
            onValueChange = { text -> update { it.copy(balanceText = text) } },
            label = stringResource(R.string.form_balance),
            prefix = { Text("€ ") },
            singleLine = true,
            isError = balanceError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (draft.kind in PIN_KINDS || draft.pin.isNotEmpty()) {
        WalletField(
            value = draft.pin,
            onValueChange = { pin -> update { it.copy(pin = pin) } },
            label = stringResource(R.string.form_pin),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (draft.kind in DETAIL_KINDS || draft.details.isNotEmpty()) DetailsEditor(draft.details) { list -> update { it.copy(details = list) } }
}

@Composable
private fun FlightFields(draft: CardDraft, update: ((CardDraft) -> CardDraft) -> Unit) {
    val flight = draft.flight ?: Flight(from = "", to = "", carrier = "", number = "")
    fun change(edit: (Flight) -> Flight) = update { it.copy(flight = edit(flight)) }
    SectionLabel(stringResource(R.string.form_flight))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallField(stringResource(R.string.form_from), flight.from, Modifier.weight(1f), caps = true) { v -> change { it.copy(from = v.uppercase().take(3)) } }
        SmallField(stringResource(R.string.form_to), flight.to, Modifier.weight(1f), caps = true) { v -> change { it.copy(to = v.uppercase().take(3)) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallField(stringResource(R.string.form_airline), flight.carrier, Modifier.weight(1f), caps = true) { v -> change { it.copy(carrier = v.uppercase().take(3)) } }
        SmallField(stringResource(R.string.form_flight_number), flight.number, Modifier.weight(1f)) { v -> change { it.copy(number = v) } }
        SmallField(stringResource(R.string.detail_seat), flight.seat, Modifier.weight(1f), caps = true) { v -> change { it.copy(seat = v.uppercase()) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallField(stringResource(R.string.form_passenger), flight.passenger, Modifier.weight(2f), caps = true) { v -> change { it.copy(passenger = v) } }
        SmallField(stringResource(R.string.form_pnr), flight.pnr, Modifier.weight(1f), caps = true) { v -> change { it.copy(pnr = v.uppercase()) } }
    }
    DayTimeField(stringResource(R.string.form_departure), draft.event) { value -> update { it.copy(event = value) } }
}

@Composable
private fun SmallField(label: String, value: String, modifier: Modifier, caps: Boolean = false, onChange: (String) -> Unit) {
    WalletField(
        value = value,
        onValueChange = onChange,
        label = label,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = if (caps) KeyboardCapitalization.Characters else KeyboardCapitalization.Words),
        modifier = modifier,
    )
}

@Composable
private fun DetailsEditor(details: List<Detail>, onChange: (List<Detail>) -> Unit) {
    SectionLabel(stringResource(R.string.form_details))
    details.forEachIndexed { index, detail ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallField(stringResource(R.string.form_detail_label), detail.label, Modifier.weight(1f)) { v ->
                onChange(details.toMutableList().also { it[index] = detail.copy(label = v) })
            }
            SmallField(stringResource(R.string.form_detail_value), detail.value, Modifier.weight(1f)) { v ->
                onChange(details.toMutableList().also { it[index] = detail.copy(value = v) })
            }
            IconButton(onClick = { onChange(details.filterIndexed { i, _ -> i != index }) }) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_remove))
            }
        }
    }
    TextButton(onClick = { onChange(details + Detail("", "")) }) { Text(stringResource(R.string.form_add_detail)) }
}

/** A date with an optional time: tap to pick the date, then optionally a time; ✕ clears it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayTimeField(label: String, value: DayTime?, onChange: (DayTime?) -> Unit) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { pickDate = true }.padding(vertical = 8.dp)) {
                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value?.let { dayTimeText(it) } ?: stringResource(R.string.form_pick_date), fontSize = 15.sp)
            }
            if (value != null) {
                TextButton(onClick = { pickTime = true }) {
                    Text(stringResource(if (value.time == null) R.string.form_add_time else R.string.form_change_time))
                }
                IconButton(onClick = { onChange(null) }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_clear))
                }
            }
        }
    }
    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (value?.date ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(DayTime(date, value?.time))
                    }
                    pickDate = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state) }
    }
    if (pickTime && value != null) {
        val context = LocalContext.current
        val initial = value.time ?: LocalTime.of(DEFAULT_HOUR, 0)
        val state = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(value.copy(time = LocalTime.of(state.hour, state.minute)))
                    pickTime = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.action_cancel)) } },
            text = { TimePicker(state) },
        )
    }
}

private const val DEFAULT_HOUR = 9

@Composable
private fun CodeStatus(draft: CardDraft, problem: DraftProblem?) {
    val text = when {
        problem == DraftProblem.CODE_NOT_DRAWABLE -> stringResource(R.string.form_code_not_drawable, draft.format.label)
        draft.flight != null && draft.kind == CardKind.BOARDING_PASS -> stringResource(R.string.form_boarding_pass_read)
        draft.format in CHECKED_FORMATS && CodeNumbers.hasValidCheckDigit(draft.cleanCode) -> stringResource(R.string.form_check_digit_ok)
        else -> return
    }
    // Other problems (a missing name) must not turn a correct check digit red.
    val color = if (problem == DraftProblem.CODE_NOT_DRAWABLE) colorResource(R.color.danger) else colorResource(R.color.positive)
    Text(text, color = color)
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

/** Chips in one scrolling row that keeps the selected chip in view. */
@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val state = rememberLazyListState()
    val index = options.indexOf(selected)
    LaunchedEffect(index) { if (index >= 0) state.animateScrollToItem(maxOf(0, index - 1)) }
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(options) { _, option ->
            WalletChip(label = label(option), selected = option == selected, onClick = { onSelect(option) })
        }
    }
}

@Composable
private fun Swatches(draft: CardDraft, onPick: (Int) -> Unit) {
    val brand = Brands.exact(draft.name)?.color
    val colors = (listOfNotNull(brand, draft.color, CardDraft.BOARDING_COLOR.takeIf { draft.kind == CardKind.BOARDING_PASS }) + CardLooks.PALETTE).distinct()
    val ring = MaterialTheme.colorScheme.onBackground
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { color ->
            val selected = color == draft.effectiveColor
            Box(
                Modifier
                    .size(34.dp)
                    .then(if (selected) Modifier.border(2.dp, ring, CircleShape) else Modifier)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .clickable { onPick(color) },
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 16.sp)
        WalletSwitch(checked = checked, onCheckedChange = onChange)
    }
}
