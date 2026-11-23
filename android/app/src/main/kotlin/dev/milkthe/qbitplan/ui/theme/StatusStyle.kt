package dev.milkthe.qbitplan.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.core.model.SubstitutionType
import dev.milkthe.qbitplan.core.schedule.LessonStatus

/** Semantic colour per lesson status; `null` = no badge (normal lesson). */
@Composable
@ReadOnlyComposable
fun LessonStatus.tint(): Tint? = with(QbitTheme.colors) {
    when (this@tint) {
        LessonStatus.NORMAL -> null
        LessonStatus.CHANGED -> primary
        LessonStatus.NO_SUBSTITUTE -> warning
        LessonStatus.CANCELLED -> danger
        LessonStatus.MOVED_AWAY, LessonStatus.MOVED_HERE -> secondary
        LessonStatus.CLASS_ABSENT -> neutral
    }
}

@StringRes
fun LessonStatus.label(): Int? = when (this) {
    LessonStatus.NORMAL -> null
    LessonStatus.CHANGED -> R.string.status_changed
    LessonStatus.NO_SUBSTITUTE -> R.string.status_no_substitute
    LessonStatus.CANCELLED -> R.string.status_cancelled
    LessonStatus.MOVED_AWAY -> R.string.status_moved_away
    LessonStatus.MOVED_HERE -> R.string.status_moved_here
    LessonStatus.CLASS_ABSENT -> R.string.status_class_absent
}

fun SubstitutionType.toLessonStatus(): LessonStatus = when (this) {
    SubstitutionType.CHANGE -> LessonStatus.CHANGED
    SubstitutionType.NO_SUBSTITUTE -> LessonStatus.NO_SUBSTITUTE
    SubstitutionType.CANCELLED -> LessonStatus.CANCELLED
    SubstitutionType.MOVED_AWAY -> LessonStatus.MOVED_AWAY
    SubstitutionType.MOVED_HERE -> LessonStatus.MOVED_HERE
    SubstitutionType.CLASS_ABSENT -> LessonStatus.CLASS_ABSENT
}
