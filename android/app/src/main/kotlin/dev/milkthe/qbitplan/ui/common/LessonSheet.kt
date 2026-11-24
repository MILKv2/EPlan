@file:OptIn(ExperimentalMaterial3Api::class)

package dev.milkthe.qbitplan.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.core.schedule.LessonStatus
import dev.milkthe.qbitplan.ui.theme.QbitTheme
import dev.milkthe.qbitplan.ui.theme.style

/** Everything a lesson tile has no room for: full names and old → new values. */
@Composable
fun LessonSheet(item: LessonItem, onDismiss: () -> Unit) {
    val colors = QbitTheme.colors
    val struck = item.status.style().struck
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = QbitTheme.radius.sheet, topEnd = QbitTheme.radius.sheet),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    item.isCovering -> TagChip(stringResource(R.string.tag_covering), colors.warning.soft, colors.warning.softForeground, large = true)
                    item.status != LessonStatus.NORMAL -> StatusTag(item.status, large = true)
                }
                item.oldSubjectName?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, color = colors.muted, textDecoration = TextDecoration.LineThrough)
                }
                Text(
                    item.subjectName,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (struck) colors.muted else colors.foreground,
                    textDecoration = if (struck) TextDecoration.LineThrough else null,
                )
            }
            val time = listOfNotNull(item.start?.text(), item.end?.text()).joinToString("–")
            val rows = buildList {
                add(stringResource(R.string.lesson_period) to AnnotatedString(listOf(item.periods.text(), time).filter { it.isNotEmpty() }.joinToString(" · ")))
                item.classes?.let { add(stringResource(R.string.lesson_class) to AnnotatedString(it)) }
                item.groups?.let { add(stringResource(R.string.lesson_group) to AnnotatedString(it)) }
                if (item.room != null || item.oldRoom != null) add(stringResource(R.string.lesson_room) to changeText(item.oldRoom, item.room))
                if (item.who != null || item.oldWho != null) {
                    add(stringResource(R.string.lesson_teacher) to changeText(item.oldWho, item.who, missing = stringResource(R.string.lesson_no_teacher)))
                }
                item.note?.let { add(stringResource(R.string.lesson_note) to AnnotatedString(noteText(it))) }
            }
            ListCard {
                rows.forEachIndexed { i, (label, value) ->
                    if (i > 0) Hairline()
                    DetailRow(label, value)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: AnnotatedString) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = QbitTheme.colors.muted, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
