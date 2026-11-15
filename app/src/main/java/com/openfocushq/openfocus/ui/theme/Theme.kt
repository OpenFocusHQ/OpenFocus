package com.openfocushq.openfocus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.sp
import com.openfocushq.openfocus.R

// Game palette
val Xp = Color(0xFF7C6CF2)
val Hp = Color(0xFFF0506E)
val Coin = Color(0xFFF2B33D)
val Streak = Color(0xFFFF8A3D)
val Success = Color(0xFF2BB673)

/** Colors users can pick for habits & goals. */
val PaletteColors = listOf(
    Color(0xFF7C6CF2), Color(0xFF2BB3A3), Color(0xFFF06A4D), Color(0xFFE0A526),
    Color(0xFF4DA3F0), Color(0xFFE85D75), Color(0xFF3E8E5E), Color(0xFF9B59B6),
)

fun paletteColor(i: Int) = PaletteColors[((i % PaletteColors.size) + PaletteColors.size) % PaletteColors.size]

private val Light = lightColorScheme(
    primary = Color(0xFF5B4BD6), onPrimary = Color.White,
    primaryContainer = Color(0xFFE4DFFF), onPrimaryContainer = Color(0xFF1A0E63),
    secondary = Color(0xFF00897B), secondaryContainer = Color(0xFFC8F1EA),
    tertiary = Color(0xFFE07A2E), tertiaryContainer = Color(0xFFFFE2CC),
    background = Color(0xFFF7F4FF), surface = Color(0xFFF7F4FF),
    surfaceContainer = Color(0xFFEFEBFA), surfaceContainerHigh = Color(0xFFE9E4F7),
    surfaceContainerLow = Color(0xFFF3F0FC),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFC5BCFF), onPrimary = Color(0xFF2A1C8F),
    primaryContainer = Color(0xFF4134B0), onPrimaryContainer = Color(0xFFE4DFFF),
    secondary = Color(0xFF7FD8CB), secondaryContainer = Color(0xFF005046),
    tertiary = Color(0xFFFFB68A), tertiaryContainer = Color(0xFF6B3A12),
    background = Color(0xFF14121F), surface = Color(0xFF14121F),
    surfaceContainer = Color(0xFF211E30), surfaceContainerHigh = Color(0xFF2B2840),
    surfaceContainerLow = Color(0xFF1B1928),
)

// Fonts: Nunito (body) and Fredoka (display), both SIL Open Font License 1.1 — see /licenses.
@OptIn(ExperimentalTextApi::class)
private fun nunito(w: Int) = Font(R.font.nunito, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
@OptIn(ExperimentalTextApi::class)
private fun fredoka(w: Int) = Font(R.font.fredoka, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Body = FontFamily(nunito(400), nunito(600), nunito(700), nunito(800))
val Display = FontFamily(fredoka(400), fredoka(500), fredoka(600))

private val base = Typography()
private val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
    displayMedium = base.displayMedium.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
    displaySmall = base.displaySmall.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Display, fontWeight = FontWeight.Medium),
    titleLarge = base.titleLarge.copy(fontFamily = Display, fontWeight = FontWeight.Medium),
    titleMedium = base.titleMedium.copy(fontFamily = Body, fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontFamily = Body, fontWeight = FontWeight.Bold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Body),
    bodyMedium = base.bodyMedium.copy(fontFamily = Body),
    bodySmall = base.bodySmall.copy(fontFamily = Body),
    labelLarge = base.labelLarge.copy(fontFamily = Body, fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontFamily = Body, fontWeight = FontWeight.Bold),
    labelSmall = base.labelSmall.copy(fontFamily = Body, fontWeight = FontWeight.Bold),
)

val NumberStyle = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 56.sp)

@Composable
fun OpenFocusTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}

@Suppress("unused") private val sdk = Build.VERSION.SDK_INT
