package dev.dineos.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/** The design tokens Material has no slot for (ok, warn and their backgrounds). */
val LocalTokens = staticCompositionLocalOf { LightTokens }

// Material 3 names for the "ledger" tokens. Dynamic color is off on purpose: the app looks
// like the dashboard, not like the phone's wallpaper.
private fun scheme(t: Tokens, base: ColorScheme) = base.copy(
    primary = t.primary,
    onPrimary = t.onPrimary,
    secondary = t.primary,
    onSecondary = t.onPrimary,
    background = t.bg,
    onBackground = t.fg,
    surface = t.surface,
    onSurface = t.fg,
    surfaceVariant = t.surfaceSunken,
    onSurfaceVariant = t.fgMuted,
    surfaceContainerLowest = t.surface,
    surfaceContainerLow = t.surface,
    surfaceContainer = t.surface,
    surfaceContainerHigh = t.surfaceSunken,
    surfaceContainerHighest = t.surfaceSunken,
    outline = t.lineStrong,
    outlineVariant = t.line,
    error = t.danger,
    onError = t.surface,
    errorContainer = t.dangerBg,
    onErrorContainer = t.danger,
)

@Composable
fun DineTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val tokens = if (dark) DarkTokens else LightTokens
    CompositionLocalProvider(LocalTokens provides tokens) {
        // ponytail: system font. Bundle Geist (the dashboard's typeface) with the first design pass.
        MaterialTheme(colorScheme = scheme(tokens, if (dark) darkColorScheme() else lightColorScheme()), content = content)
    }
}
