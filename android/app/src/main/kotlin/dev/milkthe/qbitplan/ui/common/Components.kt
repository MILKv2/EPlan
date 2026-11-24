package dev.milkthe.qbitplan.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.core.schedule.LessonStatus
import dev.milkthe.qbitplan.data.SyncState
import dev.milkthe.qbitplan.ui.theme.QbitTheme
import dev.milkthe.qbitplan.ui.theme.Tone
import dev.milkthe.qbitplan.ui.theme.style
import dev.milkthe.qbitplan.ui.theme.tagLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** HeroUI surface: white card with a soft shadow in light mode, flat fill in dark mode. */
@Composable
fun Modifier.surface(shape: Shape, color: Color = QbitTheme.colors.surface): Modifier {
    val colors = QbitTheme.colors
    val shadowed = if (colors.isDark) this else this.shadow(
        elevation = 2.dp,
        shape = shape,
        ambientColor = Color.Black.copy(alpha = 0.06f),
        spotColor = Color.Black.copy(alpha = 0.10f),
    )
    return shadowed.clip(shape).background(color)
}

enum class ButtonVariant { Primary, Secondary, Ghost }

/** 52dp pill button (HeroUI `button--lg`). */
@Composable
fun QbitButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = QbitTheme.colors
    val (bg, fg) = when (variant) {
        ButtonVariant.Primary -> colors.accent.solid to colors.onAccent
        ButtonVariant.Secondary -> colors.default to colors.foreground
        ButtonVariant.Ghost -> Color.Transparent to colors.foreground
    }
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(CircleShape)
            .background(if (enabled) bg else bg.copy(alpha = bg.alpha * 0.5f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) fg else fg.copy(alpha = 0.5f))
    }
}

/** 40dp round icon button. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Color.Transparent,
    tint: Color = QbitTheme.colors.foreground,
    size: Dp = 48.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Pill segmented control with a sliding white segment (HeroUI ToggleButtonGroup). */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QbitTheme.colors
    val position by animateFloatAsState(selected.coerceAtLeast(0).toFloat(), spring(dampingRatio = 0.85f, stiffness = 500f), label = "segment")
    BoxWithConstraints(
        modifier = modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(colors.default)
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        if (selected >= 0) {
            Box(
                Modifier
                    .offset(x = segment * position)
                    .width(segment)
                    .fillMaxHeight()
                    .surface(CircleShape, colors.segment),
            )
        }
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, label ->
                val on = index == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(role = Role.Tab) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (on) colors.foreground else colors.muted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 48dp search field on a surface (HeroUI SearchField). */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = QbitTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .surface(RoundedCornerShape(QbitTheme.radius.field))
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.muted, maxLines = 1)
            }
            QbitTextField(
                value = value,
                onValueChange = onValueChange,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
        if (value.isNotEmpty()) {
            RoundIconButton(Icons.Outlined.Close, stringResource(R.string.clear), { onValueChange("") }, size = 40.dp, tint = colors.muted)
        }
    }
}

/** "Zsynchronizowano 08:12" line with a status dot; tapping refreshes. */
@Composable
fun SyncLine(sync: SyncState, lastRefresh: Instant?, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val colors = QbitTheme.colors
    val time = lastRefresh?.syncText()
    val (dot, text) = when {
        sync.refreshing -> colors.accent.solid to stringResource(if (lastRefresh == null) R.string.sync_loading else R.string.sync_running)
        sync.error != null -> colors.warning.solid to (time?.let { stringResource(R.string.sync_offline, it) } ?: stringResource(R.string.sync_offline_no_data))
        time != null -> colors.success.solid to stringResource(R.string.sync_done, time)
        else -> colors.muted to stringResource(R.string.sync_never)
    }
    Row(
        modifier = modifier
            .height(26.dp)
            // No ripple: the spinner is the feedback, and a box around a caption looks like a glitch.
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClickLabel = stringResource(R.string.refresh), onClick = onRefresh)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(dot))
        Text(text, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, color = colors.muted, fontFamily = MaterialTheme.typography.bodySmall.fontFamily)
        if (sync.refreshing) {
            CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp, color = colors.muted)
        } else {
            Icon(Icons.Outlined.Refresh, contentDescription = null, tint = colors.muted, modifier = Modifier.size(11.dp))
        }
    }
}

private fun Instant.syncText(): String {
    val local = atZone(ZoneId.systemDefault())
    return if (local.toLocalDate() == LocalDate.now()) local.toLocalTime().text() else text()
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = QbitTheme.colors.muted) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color, modifier = modifier)
}

/** Small neutral pill, e.g. a group name. */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    val colors = QbitTheme.colors
    Text(
        text,
        fontSize = 11.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
        color = colors.foreground,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(colors.foreground.copy(alpha = 0.08f))
            .padding(horizontal = 7.dp),
    )
}

/** Status chip ("zmiana", "odwołana"…). [large] is the list chip, otherwise the 18dp timeline tag. */
@Composable
fun StatusTag(status: LessonStatus, modifier: Modifier = Modifier, large: Boolean = false, textOverride: String? = null) {
    val style = status.style()
    val label = textOverride ?: status.tagLabel()?.let { stringResource(it) } ?: return
    TagChip(label, style.tagBackground, style.tagForeground, outlined = style.tagOutlined, large = large, modifier = modifier)
}

@Composable
fun TagChip(text: String, background: Color, foreground: Color, modifier: Modifier = Modifier, outlined: Boolean = false, large: Boolean = false) {
    val shape = CircleShape
    Text(
        text,
        fontSize = 12.sp,
        lineHeight = if (large) 24.sp else 18.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
        color = foreground,
        maxLines = 1,
        modifier = modifier
            .clip(shape)
            .background(background)
            .then(if (outlined) Modifier.border(1.5.dp, foreground, shape) else Modifier)
            .padding(horizontal = if (large) 10.dp else if (background == Color.Transparent && !outlined) 0.dp else 8.dp),
    )
}

/** "old → new" with the old value struck out; just [new] when unchanged. */
fun changeText(old: String?, new: String?, missing: String = ""): AnnotatedString = buildAnnotatedString {
    if (old != null) {
        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(old) }
        append(" → ")
    }
    when {
        new != null -> append(new)
        old != null -> append(missing)
    }
}

/** Centered state: tinted circle icon, title, text, optional action (empty, error, weekend). */
@Composable
fun StateMessage(
    icon: ImageVector,
    tone: Tone,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(tone.soft), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tone.softForeground, modifier = Modifier.size(30.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = QbitTheme.colors.muted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 290.dp))
        action?.let {
            Spacer(Modifier.height(4.dp))
            it()
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = QbitTheme.colors.accent.solid)
    }
}

/** Header with a 48dp back button, a title and an optional subtitle. */
@Composable
fun BackHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RoundIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), onBack)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = QbitTheme.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Card holding list rows separated by hairlines. */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().surface(RoundedCornerShape(QbitTheme.radius.card))) { content() }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(QbitTheme.colors.separator))
}

// ───────── settings rows ─────────

@Composable
fun SettingsHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = QbitTheme.colors.accent.softForeground,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val alpha = if (enabled) 1f else 0.5f
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = QbitTheme.colors.foreground.copy(alpha = alpha))
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = QbitTheme.colors.muted.copy(alpha = alpha)) }
        }
        trailing?.invoke(this)
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val colors = QbitTheme.colors
    SettingsRow(title, subtitle, onClick = { onCheckedChange(!checked) }, enabled = enabled) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.accent.solid,
                checkedThumbColor = colors.onAccent,
                uncheckedTrackColor = colors.default,
                uncheckedThumbColor = colors.surface,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/** Radio ring: 7dp accent ring when selected, 2dp border otherwise. */
@Composable
fun RadioDot(selected: Boolean) {
    val colors = QbitTheme.colors
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .border(if (selected) 7.dp else 2.dp, if (selected) colors.accent.solid else colors.border, CircleShape),
    )
}

@Composable
fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit, subtitle: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RadioDot(selected)
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = QbitTheme.colors.muted) }
        }
    }
}

@Composable
fun Chevron() {
    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = QbitTheme.colors.muted)
}

val ScreenPadding = PaddingValues(horizontal = 16.dp)

/** Single-line text input bridged to a String-based view model; external changes (e.g. clear) flow back in. */
@Composable
fun QbitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = QbitTheme.colors
    val state = rememberTextFieldState(value)
    val onChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(state) { snapshotFlow { state.text.toString() }.collect { onChange(it) } }
    LaunchedEffect(value) { if (state.text.toString() != value) state.setTextAndPlaceCursorAtEnd(value) }
    BasicTextField(
        state = state,
        lineLimits = TextFieldLineLimits.SingleLine,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.foreground),
        cursorBrush = SolidColor(colors.accent.solid),
        keyboardOptions = keyboardOptions,
        modifier = modifier,
    )
}

/** The launcher icon: its 108dp adaptive foreground, cropped to the visible middle two thirds. */
@Composable
fun AppIcon(size: Dp, cornerRadius: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(cornerRadius)).background(colorResource(R.color.ic_launcher_background)), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.requiredSize(size * 1.5f))
    }
}
