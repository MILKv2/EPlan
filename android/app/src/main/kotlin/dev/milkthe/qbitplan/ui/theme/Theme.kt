package dev.milkthe.qbitplan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.data.prefs.ThemeMode

private val LocalQbitColors = staticCompositionLocalOf { LightColors }
private val LocalQbitRadius = staticCompositionLocalOf { QbitRadius() }

object QbitTheme {
    val colors: QbitColors
        @Composable @ReadOnlyComposable get() = LocalQbitColors.current
    val radius: QbitRadius
        @Composable @ReadOnlyComposable get() = LocalQbitRadius.current
}

/** No dynamic colour on purpose: the app keeps its own palette on every device. */
@Composable
fun AppTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkColors else LightColors
    val radius = QbitRadius()
    CompositionLocalProvider(LocalQbitColors provides colors, LocalQbitRadius provides radius) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(radius.small),
                small = RoundedCornerShape(radius.field),
                medium = RoundedCornerShape(radius.tile),
                large = RoundedCornerShape(radius.card),
                extraLarge = RoundedCornerShape(radius.sheet),
            ),
            typography = AppTypography,
            content = content,
        )
    }
}

// Inter (variable, subset to Latin + Polish, wght 400–700) bundled in res/font.
private fun inter(weight: Int) = Font(
    R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700))

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) = TextStyle(
    fontFamily = Inter,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
)

/** Sizes follow the design: 30 headline, 22 screen title, 15–16 body, 13 section labels, 11–12 meta. */
private val AppTypography = Typography(
    displaySmall = style(30, 36, FontWeight.SemiBold, -0.025),
    headlineMedium = style(30, 36, FontWeight.SemiBold, -0.025),
    headlineSmall = style(22, 28, FontWeight.SemiBold, -0.025),
    titleLarge = style(20, 28, FontWeight.SemiBold, -0.02),
    titleMedium = style(16, 22, FontWeight.SemiBold),
    titleSmall = style(15, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 22),
    bodyMedium = style(14, 20),
    bodySmall = style(13, 18),
    labelLarge = style(15, 20, FontWeight.SemiBold),
    labelMedium = style(13, 20, FontWeight.SemiBold),
    labelSmall = style(12, 16, FontWeight.Medium),
)

private fun QbitColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent.solid,
        onPrimary = onAccent,
        primaryContainer = accent.soft,
        onPrimaryContainer = accent.softForeground,
        secondary = accent.solid,
        onSecondary = onAccent,
        secondaryContainer = accent.soft,
        onSecondaryContainer = accent.softForeground,
        tertiary = success.solid,
        error = danger.solid,
        onError = onAccent,
        errorContainer = danger.soft,
        onErrorContainer = danger.softForeground,
        background = background,
        onBackground = foreground,
        surface = background,
        onSurface = foreground,
        surfaceVariant = surfaceSecondary,
        onSurfaceVariant = muted,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = default,
        inverseSurface = foreground,
        inverseOnSurface = background,
        outline = border,
        outlineVariant = separator,
        scrim = androidx.compose.ui.graphics.Color.Black,
    )
}
