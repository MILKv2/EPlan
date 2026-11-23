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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
                small = RoundedCornerShape(radius.small),
                medium = RoundedCornerShape(radius.medium),
                large = RoundedCornerShape(radius.large),
                extraLarge = RoundedCornerShape(radius.large),
            ),
            typography = AppTypography,
            content = content,
        )
    }
}

private fun QbitColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary.accent,
        onPrimary = Color.White,
        primaryContainer = primary.container,
        onPrimaryContainer = primary.content,
        secondary = secondary.accent,
        onSecondary = Color.White,
        secondaryContainer = primary.container,
        onSecondaryContainer = primary.content,
        tertiary = success.accent,
        error = danger.accent,
        onError = Color.White,
        errorContainer = danger.container,
        onErrorContainer = danger.content,
        background = background,
        onBackground = foreground,
        surface = background,
        onSurface = foreground,
        surfaceVariant = content2,
        onSurfaceVariant = muted,
        surfaceContainerLowest = background,
        surfaceContainerLow = content1,
        surfaceContainer = content1,
        surfaceContainerHigh = content2,
        surfaceContainerHighest = content3,
        outline = muted,
        outlineVariant = divider,
    )
}

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
    )
}
