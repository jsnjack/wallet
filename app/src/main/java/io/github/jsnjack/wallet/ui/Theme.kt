package io.github.jsnjack.wallet.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import io.github.jsnjack.wallet.R

/** One UI 9 corner radii: chips 18, tiles 22, groups and dialogs 26. */
private val OneUiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/** One UI 9 style theme from res/values(-night)/colors.xml; cards bring their own colours. */
@Composable
fun WalletTheme(content: @Composable () -> Unit) {
    val background = colorResource(R.color.background)
    val surface = colorResource(R.color.surface)
    val surfaceVariant = colorResource(R.color.surface_variant)
    val text = colorResource(R.color.on_background)
    val muted = colorResource(R.color.muted)
    val outline = colorResource(R.color.outline)
    val primary = colorResource(R.color.primary)
    val onPrimary = colorResource(R.color.on_primary)
    val container = colorResource(R.color.primary_container)
    val onContainer = colorResource(R.color.on_primary_container)
    val danger = colorResource(R.color.danger)
    val toast = colorResource(R.color.toast)
    val nowBarAccent = colorResource(R.color.now_bar_accent)
    // The colours come from resources that already differ by night mode; the base only fills the roles not set here.
    val base = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = primary, onPrimary = onPrimary, primaryContainer = container, onPrimaryContainer = onContainer,
        secondary = primary, onSecondary = onPrimary, secondaryContainer = container, onSecondaryContainer = onContainer,
        background = background, onBackground = text, surface = surface, onSurface = text,
        surfaceVariant = surfaceVariant, onSurfaceVariant = muted, surfaceContainerLowest = background,
        surfaceContainerLow = surface, surfaceContainer = surface, surfaceContainerHigh = surface,
        surfaceContainerHighest = surfaceVariant, outline = outline, outlineVariant = outline, error = danger,
        inverseSurface = toast, inverseOnSurface = Color.White, inversePrimary = nowBarAccent,
    )
    MaterialTheme(colorScheme = scheme, shapes = OneUiShapes, content = content)
}
