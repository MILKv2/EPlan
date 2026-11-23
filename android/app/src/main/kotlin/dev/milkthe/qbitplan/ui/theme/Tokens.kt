package dev.milkthe.qbitplan.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A foreground/background pair for tinted surfaces (badges, status accents). */
@Immutable
data class Tint(val container: Color, val content: Color, val accent: Color)

/**
 * Design tokens, modelled on the HeroUI default theme. Screens read colours from
 * here (not hard-coded), so a new design only needs new token values.
 */
@Immutable
data class QbitColors(
    val background: Color,
    val foreground: Color,
    val content1: Color,
    val content2: Color,
    val content3: Color,
    val divider: Color,
    val muted: Color,
    val primary: Tint,
    val secondary: Tint,
    val success: Tint,
    val warning: Tint,
    val danger: Tint,
    val neutral: Tint,
    val isDark: Boolean,
)

@Immutable
data class QbitRadius(val small: Dp = 8.dp, val medium: Dp = 12.dp, val large: Dp = 16.dp)

internal val LightColors = QbitColors(
    background = Color(0xFFFFFFFF),
    foreground = Color(0xFF11181C),
    content1 = Color(0xFFFFFFFF),
    content2 = Color(0xFFF4F4F5),
    content3 = Color(0xFFE4E4E7),
    divider = Color(0x26111111),
    muted = Color(0xFF71717A),
    primary = Tint(container = Color(0xFFE6F1FE), content = Color(0xFF004493), accent = Color(0xFF006FEE)),
    secondary = Tint(container = Color(0xFFF2EAFA), content = Color(0xFF481878), accent = Color(0xFF7828C8)),
    success = Tint(container = Color(0xFFE8FAF0), content = Color(0xFF0E793C), accent = Color(0xFF17C964)),
    warning = Tint(container = Color(0xFFFEFCE8), content = Color(0xFF936316), accent = Color(0xFFF5A524)),
    danger = Tint(container = Color(0xFFFEE7EF), content = Color(0xFF920B3A), accent = Color(0xFFF31260)),
    neutral = Tint(container = Color(0xFFF4F4F5), content = Color(0xFF3F3F46), accent = Color(0xFFA1A1AA)),
    isDark = false,
)

internal val DarkColors = QbitColors(
    background = Color(0xFF000000),
    foreground = Color(0xFFECEDEE),
    content1 = Color(0xFF18181B),
    content2 = Color(0xFF27272A),
    content3 = Color(0xFF3F3F46),
    divider = Color(0x26FFFFFF),
    muted = Color(0xFFA1A1AA),
    primary = Tint(container = Color(0xFF001731), content = Color(0xFF99C7FB), accent = Color(0xFF006FEE)),
    secondary = Tint(container = Color(0xFF180828), content = Color(0xFFC9A9E9), accent = Color(0xFF9353D3)),
    success = Tint(container = Color(0xFF052814), content = Color(0xFF74DFA2), accent = Color(0xFF17C964)),
    warning = Tint(container = Color(0xFF312107), content = Color(0xFFF9C97C), accent = Color(0xFFF5A524)),
    danger = Tint(container = Color(0xFF310413), content = Color(0xFFF871A0), accent = Color(0xFFF31260)),
    neutral = Tint(container = Color(0xFF27272A), content = Color(0xFFD4D4D8), accent = Color(0xFF71717A)),
    isDark = true,
)
