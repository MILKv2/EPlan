package dev.milkthe.qbitplan.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.core.schedule.LessonStatus
import dev.milkthe.qbitplan.data.SyncState
import dev.milkthe.qbitplan.ui.theme.QbitTheme
import dev.milkthe.qbitplan.ui.theme.label
import dev.milkthe.qbitplan.ui.theme.tint
import java.time.Instant
import java.time.LocalDate

/** "old → new" with the old value struck out; just [new] when unchanged. */
fun changeText(old: String?, new: String?, missing: String): AnnotatedString = buildAnnotatedString {
    if (old != null) {
        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(old) }
        append(" → ")
    }
    if (new != null) {
        withStyle(SpanStyle(fontWeight = if (old != null) FontWeight.SemiBold else null)) { append(new) }
    } else if (old != null) {
        append(missing)
    }
}

@Composable
fun StatusBadge(status: LessonStatus, modifier: Modifier = Modifier) {
    val tint = status.tint() ?: return
    val label = status.label() ?: return
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.labelSmall,
        color = tint.content,
        modifier = modifier
            .clip(RoundedCornerShape(QbitTheme.radius.small))
            .background(tint.container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun LessonCard(item: LessonItem, modifier: Modifier = Modifier) {
    val colors = QbitTheme.colors
    val tint = item.status.tint()
    val dimmed = !item.status.takesPlace
    val shape = RoundedCornerShape(QbitTheme.radius.medium)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.content1)
            .border(1.dp, tint?.accent?.copy(alpha = 0.5f) ?: colors.divider, shape)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(item.periods.text(), style = MaterialTheme.typography.titleLarge, color = tint?.content ?: colors.foreground)
            item.start?.let { Text(it.text(), style = MaterialTheme.typography.labelSmall, color = colors.muted) }
            item.end?.let { Text(it.text(), style = MaterialTheme.typography.labelSmall, color = colors.muted) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.subject,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (dimmed) TextDecoration.LineThrough else null,
                    color = if (dimmed) colors.muted else colors.foreground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                StatusBadge(item.status)
            }
            item.oldSubject?.let { InsteadOf(it) }
            val noTeacher = stringResource(R.string.lesson_no_teacher)
            val details = buildAnnotatedString {
                item.classes?.let { append(it); append(" · ") }
                if (item.teacher != null || item.oldTeacher != null) {
                    append(changeText(item.oldTeacher, item.teacher, noTeacher))
                    append(" · ")
                }
                if (item.room != null || item.oldRoom != null) append(changeText(item.oldRoom, item.room, ""))
            }.let { if (it.text.endsWith(" · ")) it.subSequence(0, it.length - 3) else it }
            Text(details, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            item.groups?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.muted) }
            when {
                item.movedTo != null -> Text(stringResource(R.string.lesson_moved_to, item.movedTo), style = MaterialTheme.typography.bodySmall, color = tint?.content ?: colors.muted)
                item.movedFrom != null -> Text(stringResource(R.string.lesson_moved_from, item.movedFrom), style = MaterialTheme.typography.bodySmall, color = tint?.content ?: colors.muted)
            }
        }
    }
}

/** Small struck-out "instead of X" line under a changed subject. */
@Composable
fun InsteadOf(oldSubject: String) {
    Text(
        text = buildAnnotatedString {
            append(stringResource(R.string.lesson_instead_of))
            append(" ")
            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(oldSubject) }
        },
        style = MaterialTheme.typography.bodySmall,
        color = QbitTheme.colors.muted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = QbitTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QbitTheme.radius.medium))
            .background(colors.content2)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = colors.muted)
        content()
    }
}

/** Shown while the last refresh failed; the screen keeps showing cached data. */
@Composable
fun OfflineBanner(sync: SyncState, lastRefresh: Instant?, modifier: Modifier = Modifier) {
    if (sync.error == null || sync.refreshing) return
    val tint = QbitTheme.colors.warning
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(tint.container)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = tint.content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (lastRefresh != null) stringResource(R.string.offline_banner, lastRefresh.text())
            else stringResource(R.string.offline_banner_no_data),
            style = MaterialTheme.typography.bodySmall,
            color = tint.content,
        )
    }
}

@Composable
fun DateChips(
    dates: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(dates, key = { _, d -> d.toEpochDay() }) { _, date ->
            FilterChip(
                selected = date == selected,
                onClick = { onSelect(date) },
                label = { Text(dateLabel(date, today)) },
            )
        }
    }
}

@Composable
fun dateLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.today)
    today.plusDays(1) -> stringResource(R.string.tomorrow)
    else -> date.shortText()
}

@Composable
fun CenteredMessage(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = QbitTheme.colors.muted, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
        }
        Text(text, textAlign = TextAlign.Center, color = QbitTheme.colors.muted)
        action?.let { (label, onClick) ->
            Spacer(Modifier.height(16.dp))
            Button(onClick = onClick) { Text(label) }
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
