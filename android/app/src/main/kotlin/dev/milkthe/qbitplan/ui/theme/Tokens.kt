package dev.milkthe.qbitplan.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A status colour and its "soft" variants: [soft] is the tinted fill, [softForeground]
 * text on it (HeroUI's `--x-soft` / `--x-soft-foreground`).
 */
@Immutable
data class Tone(val solid: Color, val soft: Color, val softForeground: Color)

/** HeroUI v3 default theme, converted from OKLCH (see the design canvas, `ds/heroui/tokens.json`). */
@Immutable
data class QbitColors(
    val background: Color,
    val foreground: Color,
    val surface: Color,
    val surfaceSecondary: Color,
    val surfaceTertiary: Color,
    val muted: Color,
    val default: Color,
    val segment: Color,
    val border: Color,
    val separator: Color,
    val onAccent: Color,
    val onWarning: Color,
    val accent: Tone,
    val success: Tone,
    val warning: Tone,
    val danger: Tone,
    val isDark: Boolean,
)

@Immutable
data class QbitRadius(
    val small: Dp = 6.dp,
    val chip: Dp = 10.dp,
    val field: Dp = 12.dp,
    val tile: Dp = 14.dp,
    val card: Dp = 20.dp,
    val sheet: Dp = 28.dp,
)

internal val LightColors = QbitColors(
    background = Color(0xFFF5F5F5),
    foreground = Color(0xFF18181B),
    surface = Color(0xFFFFFFFF),
    surfaceSecondary = Color(0xFFEFEFF0),
    surfaceTertiary = Color(0xFFEAEAEB),
    muted = Color(0xFF71717A),
    default = Color(0xFFEBEBEC),
    segment = Color(0xFFFFFFFF),
    border = Color(0xFFDEDEE0),
    separator = Color(0xFFE4E4E7),
    onAccent = Color(0xFFFCFCFC),
    onWarning = Color(0xFF18181B),
    accent = Tone(Color(0xFF0485F7), Color(0xFF0485F7).copy(alpha = 0.15f), Color(0xFF1E63AE)),
    success = Tone(Color(0xFF17C964), Color(0xFF17C964).copy(alpha = 0.15f), Color(0xFF2B7745)),
    warning = Tone(Color(0xFFF5A524), Color(0xFFF5A524).copy(alpha = 0.15f), Color(0xFF855F2E)),
    danger = Tone(Color(0xFFFF383C), Color(0xFFFF383C).copy(alpha = 0.15f), Color(0xFFA43433)),
    isDark = false,
)

internal val DarkColors = QbitColors(
    background = Color(0xFF060607),
    foreground = Color(0xFFFCFCFC),
    surface = Color(0xFF18181B),
    surfaceSecondary = Color(0xFF232325),
    surfaceTertiary = Color(0xFF262728),
    muted = Color(0xFF9F9FA9),
    default = Color(0xFF27272A),
    segment = Color(0xFF46464C),
    border = Color(0xFF28282C),
    separator = Color(0xFF212124),
    onAccent = Color(0xFFFCFCFC),
    onWarning = Color(0xFF18181B),
    accent = Tone(Color(0xFF0485F7), Color(0xFF0485F7).copy(alpha = 0.12f), Color(0xFF61A8FB)),
    success = Tone(Color(0xFF17C964), Color(0xFF17C964).copy(alpha = 0.12f), Color(0xFF74D88F)),
    warning = Tone(Color(0xFFF7B750), Color(0xFFF7B750).copy(alpha = 0.12f), Color(0xFFF9CB86)),
    danger = Tone(Color(0xFFDB3B3E), Color(0xFFDB3B3E).copy(alpha = 0.15f), Color(0xFFEB7872)),
    isDark = true,
)
