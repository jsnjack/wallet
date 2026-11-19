package io.github.jsnjack.wallet.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jsnjack.wallet.R

/**
 * Compact One UI title bar: a bold title and optional subtitle, information
 * only. Actions live at the bottom of the screen, within reach of the thumb.
 */
@Composable
fun TitleBar(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 6.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** A One UI rounded container that groups related rows. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

/** A row inside a [Group]: optional icon, title, optional subtitle and trailing content. */
@Composable
fun GroupRow(
    title: String,
    subtitle: String? = null,
    icon: Int? = null,
    titleColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = titleColor)
            if (subtitle != null) Text(subtitle, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/**
 * A [GroupRow] with a switch. The whole row toggles, and a screen reader
 * hears one switch named by the row instead of a row and an unnamed switch.
 */
@Composable
fun SwitchRow(title: String, subtitle: String? = null, icon: Int? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    GroupRow(title, subtitle, icon, modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)) {
        WalletSwitch(checked = checked, onCheckedChange = null)
    }
}

/** The inset divider between rows of a [Group]. */
@Composable
fun GroupDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 18.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
}

/** One UI switch: teal track when on, grey when off, white thumb; with a null [onCheckedChange] its row handles taps. */
@Composable
fun WalletSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = colorResource(R.color.switch_off_track),
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** One UI filled chip: grey when off, teal when selected. */
@Composable
fun WalletChip(label: String, selected: Boolean, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(6.dp))
            }
            Text(label, fontSize = 13.5.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

/** One UI filled text field: white surface with a teal underline when focused. */
@Composable
fun WalletField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 3,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val surface = MaterialTheme.colorScheme.surface
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1) },
        singleLine = singleLine,
        maxLines = maxLines,
        isError = isError,
        supportingText = supportingText,
        prefix = prefix,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = surface,
            unfocusedContainerColor = surface,
            errorContainerColor = surface,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** One UI toast-style snackbar: a dark pill with an optional teal action. */
@Composable
fun ToastSnackbar(data: SnackbarData) {
    Snackbar(
        shape = CircleShape,
        containerColor = colorResource(R.color.toast),
        contentColor = Color.White,
        action = data.visuals.actionLabel?.let { label ->
            {
                TextButton(onClick = data::performAction) {
                    Text(label, color = colorResource(R.color.now_bar_accent), fontWeight = FontWeight.Bold)
                }
            }
        },
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        Box(Modifier.padding(horizontal = 4.dp)) { Text(data.visuals.message) }
    }
}

/** Vertical gap between groups on settings-like screens. */
val GROUP_GAP = 10.dp

/** Spacing used in rows of chips. */
val CHIP_GAP = Arrangement.spacedBy(8.dp)

/**
 * A One UI bottom sheet: rises from the bottom, closes with a swipe down,
 * a tap outside or Back, so it never needs the top of the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = SCREEN_PADDING).padding(bottom = 8.dp)) {
            content()
            // A sheet is its own window and covers the screen's snackbar, so it shows messages itself.
            SnackbarHost(LocalSnackbar.current) { ToastSnackbar(it) }
        }
    }
}

/** The app's snackbar, so sheets can show messages above themselves. */
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

/** A full-width row in a sheet: an easy target for the thumb. */
@Composable
fun SheetRow(title: String, icon: Int? = null, color: Color = Color.Unspecified, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
        }
        Text(title, fontSize = 16.sp, color = color)
    }
}

/** A confirmation that rises from the bottom, with both buttons under the thumb. */
@Composable
fun ConfirmSheet(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    WalletSheet(onDismiss) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
        Spacer(Modifier.height(18.dp))
        BottomButtons(stringResource(R.string.action_cancel), onDismiss, confirm, onConfirm, danger = true)
    }
}

/** Two equal pill buttons side by side, the secondary one first. */
@Composable
fun BottomButtons(secondary: String, onSecondary: () -> Unit, primary: String, onPrimary: () -> Unit, danger: Boolean = false, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(onClick = onSecondary, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.weight(1f).height(50.dp)) {
            Box(contentAlignment = Alignment.Center) { Text(secondary, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }
        Surface(
            onClick = onPrimary,
            enabled = enabled,
            shape = CircleShape,
            color = if (danger) colorResource(R.color.danger) else MaterialTheme.colorScheme.primary,
            contentColor = if (danger) Color.White else MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.weight(1f).height(50.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Text(primary, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }
    }
}
