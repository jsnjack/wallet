package io.github.jsnjack.wallet.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import io.github.jsnjack.wallet.R

/** Neutral light and dark theme from res/values(-night)/colors.xml; cards bring their own colours. */
@Composable
fun WalletTheme(content: @Composable () -> Unit) {
    val background = colorResource(R.color.background)
    val surface = colorResource(R.color.surface)
    val surfaceVariant = colorResource(R.color.surface_variant)
    val text = colorResource(R.color.on_background)
    val muted = colorResource(R.color.muted)
    val outline = colorResource(R.color.outline)
    val danger = colorResource(R.color.danger)
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = text, onPrimary = background, secondary = text, onSecondary = background,
            background = background, onBackground = text, surface = surface, onSurface = text,
            surfaceVariant = surfaceVariant, onSurfaceVariant = muted, surfaceContainer = surface,
            surfaceContainerHigh = surface, outline = outline, outlineVariant = outline, error = danger,
            secondaryContainer = surfaceVariant, onSecondaryContainer = text,
        )
    } else {
        lightColorScheme(
            primary = text, onPrimary = background, secondary = text, onSecondary = background,
            background = background, onBackground = text, surface = surface, onSurface = text,
            surfaceVariant = surfaceVariant, onSurfaceVariant = muted, surfaceContainer = surface,
            surfaceContainerHigh = surface, outline = outline, outlineVariant = outline, error = danger,
            secondaryContainer = surfaceVariant, onSecondaryContainer = text,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
