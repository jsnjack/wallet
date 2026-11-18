package io.github.jsnjack.wallet.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import io.github.jsnjack.wallet.core.DraftProblem
import io.github.jsnjack.wallet.platform.Biometrics

/** New-card and edit form. */
@Composable
fun CardFormScreen(vm: WalletViewModel) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val current = draft ?: return
    val editing = current.editingId != null
    val problem = current.problem()
    var showProblem by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close))
            }
            Text(
                stringResource(if (editing) R.string.form_edit_title else R.string.form_new_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { if (problem == null) vm.saveDraft() else showProblem = true }) {
                Text(stringResource(R.string.action_save), fontWeight = FontWeight.SemiBold)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = SCREEN_PADDING),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CardTile(
                name = current.name.ifBlank { stringResource(R.string.form_name_placeholder) },
                color = current.effectiveColor,
                large = true,
                modifier = Modifier.fillMaxWidth(0.62f).align(Alignment.CenterHorizontally),
            )
            OutlinedTextField(
                value = current.name,
                onValueChange = { name -> vm.updateDraft { it.copy(name = name) } },
                label = { Text(stringResource(R.string.form_name)) },
                singleLine = true,
                isError = showProblem && problem == DraftProblem.NAME_MISSING,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            val suggestions = Brands.suggest(current.name)
            if (suggestions.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { brand ->
                        FilterChip(
                            selected = false,
                            onClick = { vm.updateDraft { it.copy(name = brand.name, color = brand.color) } },
                            label = { Text(brand.name) },
                            leadingIcon = { Box(Modifier.size(14.dp).clip(CircleShape).background(Color(brand.color))) },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = current.code,
                onValueChange = { code -> vm.updateDraft { it.withCode(code) } },
                label = { Text(stringResource(R.string.form_number, current.format.label)) },
                singleLine = true,
                isError = showProblem && (problem == DraftProblem.CODE_MISSING || problem == DraftProblem.CODE_NOT_DRAWABLE),
                supportingText = { CodeStatus(current, problem) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            SectionLabel(stringResource(R.string.form_format))
            ChipRow(CodeFormat.entries, current.format, { it.label }) { format -> vm.updateDraft { it.copy(format = format, formatChosen = true) } }
            SectionLabel(stringResource(R.string.form_kind))
            val kindLabels = CardKind.entries.associateWith { stringResource(kindLabel(it)) }
            ChipRow(CardKind.entries, current.kind, { kindLabels.getValue(it) }) { kind -> vm.updateDraft { it.copy(kind = kind) } }
            SectionLabel(stringResource(R.string.form_color))
            Swatches(current) { color -> vm.updateDraft { it.copy(color = color) } }
            OutlinedTextField(
                value = current.note,
                onValueChange = { note -> vm.updateDraft { it.copy(note = note) } },
                label = { Text(stringResource(R.string.form_note)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                Column {
                    ToggleRow(stringResource(R.string.form_keep_at_top), current.pinned) { on -> vm.updateDraft { it.copy(pinned = on) } }
                    ToggleRow(stringResource(R.string.form_require_unlock), current.locked) { on ->
                        if (on && !Biometrics.available(context)) {
                            vm.message(R.string.message_no_screen_lock)
                        } else {
                            vm.updateDraft { it.copy(locked = on) }
                        }
                    }
                }
            }
            if (editing) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.form_delete), color = colorResource(R.color.danger))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        if (!editing) {
            Button(
                onClick = { if (problem == null) vm.saveDraft() else showProblem = true },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(SCREEN_PADDING).height(52.dp),
            ) {
                Text(stringResource(R.string.action_save), fontSize = 16.sp)
            }
        }
    }

    val editingId = current.editingId
    if (confirmDelete && editingId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title, current.name)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(editingId) }) {
                    Text(stringResource(R.string.action_delete), color = colorResource(R.color.danger))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun CodeStatus(draft: CardDraft, problem: DraftProblem?) {
    val digits = draft.cleanCode
    val text = when {
        problem == DraftProblem.CODE_NOT_DRAWABLE -> stringResource(R.string.form_code_not_drawable, draft.format.label)
        draft.format in CHECKED_FORMATS && CodeNumbers.hasValidCheckDigit(digits) -> stringResource(R.string.form_check_digit_ok)
        else -> return
    }
    val color = if (problem == null) colorResource(R.color.positive) else colorResource(R.color.danger)
    Text(text, color = color)
}

private val CHECKED_FORMATS = setOf(CodeFormat.EAN_13, CodeFormat.EAN_8, CodeFormat.UPC_A)

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

@Composable
private fun Swatches(draft: CardDraft, onPick: (Int) -> Unit) {
    val brand = Brands.exact(draft.name)?.color
    val colors = (listOfNotNull(brand) + CardLooks.PALETTE).distinct()
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
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 15.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
