package dev.milkthe.qbitplan.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.core.model.SubstitutionType
import dev.milkthe.qbitplan.core.schedule.LessonStatus

/** How a lesson state is drawn: one 4dp edge colour per state, the card stays an opaque surface. */
@Immutable
data class StatusStyle(
    val bar: Color?,
    val tagBackground: Color,
    val tagForeground: Color,
    /** Outlined tag (no fill), used for "brak zastępcy". */
    val tagOutlined: Boolean,
    val struck: Boolean,
)

@Composable
@ReadOnlyComposable
fun LessonStatus.style(): StatusStyle = with(QbitTheme.colors) {
    when (this@style) {
        LessonStatus.NORMAL -> StatusStyle(null, Color.Transparent, muted, tagOutlined = false, struck = false)
        LessonStatus.CHANGED -> StatusStyle(warning.solid, warning.soft, warning.softForeground, tagOutlined = false, struck = false)
        LessonStatus.NO_SUBSTITUTE -> StatusStyle(danger.solid, Color.Transparent, danger.softForeground, tagOutlined = true, struck = false)
        LessonStatus.CANCELLED -> StatusStyle(danger.solid, danger.soft, danger.softForeground, tagOutlined = false, struck = true)
        LessonStatus.MOVED_AWAY -> StatusStyle(accent.solid, accent.soft, accent.softForeground, tagOutlined = false, struck = true)
        LessonStatus.MOVED_HERE -> StatusStyle(accent.solid, accent.soft, accent.softForeground, tagOutlined = false, struck = false)
        LessonStatus.CLASS_ABSENT -> StatusStyle(muted, surfaceSecondary, muted, tagOutlined = false, struck = true)
    }
}

@StringRes
fun LessonStatus.tagLabel(): Int? = when (this) {
    LessonStatus.NORMAL -> null
    LessonStatus.CHANGED -> R.string.tag_changed
    LessonStatus.NO_SUBSTITUTE -> R.string.tag_no_substitute
    LessonStatus.CANCELLED -> R.string.tag_cancelled
    LessonStatus.MOVED_AWAY, LessonStatus.MOVED_HERE -> R.string.tag_moved
    LessonStatus.CLASS_ABSENT -> R.string.tag_class_absent
}

fun SubstitutionType.toLessonStatus(): LessonStatus = when (this) {
    SubstitutionType.CHANGE -> LessonStatus.CHANGED
    SubstitutionType.NO_SUBSTITUTE -> LessonStatus.NO_SUBSTITUTE
    SubstitutionType.CANCELLED -> LessonStatus.CANCELLED
    SubstitutionType.MOVED_AWAY -> LessonStatus.MOVED_AWAY
    SubstitutionType.MOVED_HERE -> LessonStatus.MOVED_HERE
    SubstitutionType.CLASS_ABSENT -> LessonStatus.CLASS_ABSENT
}
