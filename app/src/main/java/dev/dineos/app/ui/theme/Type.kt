package dev.dineos.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import dev.dineos.app.R

/** Geist, the dashboard's typeface (bundled: SIL Open Font License, see third_party/geist). */
private val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
)

// Digits of equal width, so "7 / 9" doesn't shift when it becomes "8 / 9", and a column of
// money lines up. design.md: tabular figures everywhere, so every role carries it.
private const val TABULAR = "tnum"

/** Headings are tracked tight (design.md: -0.02em); body and label text keep Material's. */
private val Tight = (-0.02).em

/** Material's own size and line-height ladder, in the app's typeface: the scale is Material's
 * job, the family, the weight and the figures are the design's. */
private fun TextStyle.ledger(weight: FontWeight, tracking: TextUnit = letterSpacing) = copy(
    fontFamily = Geist,
    fontWeight = weight,
    letterSpacing = tracking,
    fontFeatureSettings = TABULAR,
)

private val Material = Typography()

/** Hierarchy comes from size and weight, never color: headings and titles are SemiBold, labels
 * (buttons, chips, status lines) Medium, running text Normal. Call sites set no weight. */
val DineTypography = Typography(
    displayLarge = Material.displayLarge.ledger(FontWeight.SemiBold, Tight),
    displayMedium = Material.displayMedium.ledger(FontWeight.SemiBold, Tight),
    displaySmall = Material.displaySmall.ledger(FontWeight.SemiBold, Tight),
    headlineLarge = Material.headlineLarge.ledger(FontWeight.SemiBold, Tight),
    headlineMedium = Material.headlineMedium.ledger(FontWeight.SemiBold, Tight),
    headlineSmall = Material.headlineSmall.ledger(FontWeight.SemiBold, Tight),
    titleLarge = Material.titleLarge.ledger(FontWeight.SemiBold, Tight),
    titleMedium = Material.titleMedium.ledger(FontWeight.SemiBold),
    titleSmall = Material.titleSmall.ledger(FontWeight.SemiBold),
    bodyLarge = Material.bodyLarge.ledger(FontWeight.Normal),
    bodyMedium = Material.bodyMedium.ledger(FontWeight.Normal),
    bodySmall = Material.bodySmall.ledger(FontWeight.Normal),
    labelLarge = Material.labelLarge.ledger(FontWeight.Medium),
    labelMedium = Material.labelMedium.ledger(FontWeight.Medium),
    labelSmall = Material.labelSmall.ledger(FontWeight.Medium),
)
