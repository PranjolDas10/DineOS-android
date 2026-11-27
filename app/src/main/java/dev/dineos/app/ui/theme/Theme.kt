package dev.dineos.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The design tokens Material has no slot for (ok, warn and their backgrounds). */
val LocalTokens = staticCompositionLocalOf { LightTokens }

/** Every Material color role, named, so no component can fall back to Material's purple.
 *
 * The "ledger" world has one neutral brand color (near-black on light, near-white on dark) and
 * one emerald accent that only ever means money kept, so the accent is deliberately absent here:
 * screens reach for [LocalTokens] when they mean money. Secondary and tertiary are the same
 * neutral as primary, containers are the sunken surface, and surfaces separate with lines rather
 * than tonal elevation, so surfaceTint is the surface itself. Both themes pass through the same
 * mapping; `lightColorScheme` is only a constructor here, every argument is given.
 */
private fun scheme(t: Tokens) = lightColorScheme(
    primary = t.primary,
    onPrimary = t.onPrimary,
    primaryContainer = t.surfaceSunken,
    onPrimaryContainer = t.fg,
    inversePrimary = t.onPrimary, // the other theme's primary
    secondary = t.primary,
    onSecondary = t.onPrimary,
    secondaryContainer = t.surfaceSunken,
    onSecondaryContainer = t.fg,
    tertiary = t.primary,
    onTertiary = t.onPrimary,
    tertiaryContainer = t.surfaceSunken,
    onTertiaryContainer = t.fg,
    background = t.bg,
    onBackground = t.fg,
    surface = t.surface,
    onSurface = t.fg,
    surfaceVariant = t.surfaceSunken,
    onSurfaceVariant = t.fgMuted,
    surfaceTint = t.surface,
    inverseSurface = t.fg,
    inverseOnSurface = t.bg,
    error = t.danger,
    // The token set has no on-danger (it is generated from the backend repo), and on-primary is
    // exactly the right pair in both themes: white on red-700, near-black on red-400.
    onError = t.onPrimary,
    errorContainer = t.dangerBg,
    onErrorContainer = t.danger,
    outline = t.lineStrong,
    outlineVariant = t.line,
    scrim = Color.Black, // what a dialog dims the screen with: black in both themes
    surfaceBright = t.surface,
    surfaceDim = t.bg,
    surfaceContainer = t.surface,
    surfaceContainerHigh = t.surfaceSunken,
    surfaceContainerHighest = t.surfaceSunken,
    surfaceContainerLow = t.surface,
    surfaceContainerLowest = t.surface,
    // Material Expressive's "fixed" roles: nothing in the app reads them, they are mapped so
    // that nothing can.
    primaryFixed = t.primary,
    primaryFixedDim = t.primary,
    onPrimaryFixed = t.onPrimary,
    onPrimaryFixedVariant = t.onPrimary,
    secondaryFixed = t.primary,
    secondaryFixedDim = t.primary,
    onSecondaryFixed = t.onPrimary,
    onSecondaryFixedVariant = t.onPrimary,
    tertiaryFixed = t.primary,
    tertiaryFixedDim = t.primary,
    onTertiaryFixed = t.onPrimary,
    onTertiaryFixedVariant = t.onPrimary,
)

private val Light = scheme(LightTokens)
private val Dark = scheme(DarkTokens)

// Dynamic color is off on purpose: the app looks like the dashboard, not like the phone's
// wallpaper.
@Composable
fun DineTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalTokens provides if (dark) DarkTokens else LightTokens) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = DineTypography, content = content)
    }
}
