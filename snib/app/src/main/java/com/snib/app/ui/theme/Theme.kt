package com.snib.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Dark by default, and only. This is used at 11pm to stop you opening Instagram; a white screen
 * is the wrong instrument. There is no light theme and no dynamic colour — the palette is the
 * identity, not a preference.
 */
private val scheme = darkColorScheme(
    primary = Snib.brass,
    onPrimary = Snib.slate,
    secondary = Snib.verdigris,
    onSecondary = Snib.harl,
    error = Snib.rust,
    onError = Snib.harl,
    background = Snib.slate,
    onBackground = Snib.harl,
    surface = Snib.slate,
    onSurface = Snib.harl,
    surfaceVariant = Snib.slateLift,
    onSurfaceVariant = Snib.harl,
    outline = Snib.harlFaint,
)

@Composable
fun SnibTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = SnibType, content = content)
}
