package dev.milkthe.qbitplan.ui.common

import androidx.compose.foundation.layout.RowScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.ui.theme.QbitTheme
import dev.milkthe.qbitplan.ui.theme.style
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

@Immutable
data class DayChip(val date: LocalDate, val weekday: String, val day: String, val isToday: Boolean, val hasChanges: Boolean)

/**
 * Five day buttons with a sliding accent indicator. [position] may be fractional (pager scroll);
 * a negative value hides the indicator (weekend).
 */
@Composable
fun DaySelector(
    days: List<DayChip>,
    position: Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
) {
    val colors = QbitTheme.colors
    val height by animateDpAsState(if (collapsed) 36.dp else 52.dp, label = "dayHeight")
    val selected = if (position < 0) -1 else Math.round(position)
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 12.dp).height(height)) {
        val cell = maxWidth / days.size
        if (position >= 0) {
            Box(
                Modifier
                    .offset(x = cell * position)
                    .width(cell)
                    .fillMaxHeight()
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(QbitTheme.radius.tile))
                    .background(colors.accent.solid),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { index, d ->
                val on = index == selected
                val fg = when {
                    on -> colors.onAccent
                    d.isToday -> colors.accent.softForeground
                    else -> colors.foreground
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(QbitTheme.radius.tile))
                        .clickable(role = Role.Tab) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (collapsed) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(d.weekday, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (on) colors.onAccent else colors.muted, style = MaterialTheme.typography.bodySmall)
                            Text(d.day, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = fg, style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(d.weekday, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = if (on) colors.onAccent else colors.muted, style = MaterialTheme.typography.bodySmall)
                            Text(d.day, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = fg, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (d.hasChanges) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 5.dp, end = 7.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (on) colors.accent.solid else colors.background)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(colors.warning.solid),
                        )
                    }
                }
            }
        }
    }
}

private const val PX_PER_MIN = 1.5f
private val GRID_PAD = 14.dp
private val LABEL_WIDTH = 52.dp
private val BLOCKS_START = 64.dp
private val BLOCKS_END = 12.dp

/** Current time for the timeline; ticks every 15 s and only recomposes the timeline. */
@Composable
private fun rememberNow(): LocalTime {
    val clock = LocalClock.current
    val now by produceState(LocalTime.now(clock), clock) {
        while (true) {
            delay(15_000)
            value = LocalTime.now(clock)
        }
    }
    return now
}

/**
 * Day timeline: hour lines on the left, lesson blocks placed by time (1.5 dp per minute),
 * overlapping lessons side by side, a "now" line for today.
 */
@Composable
fun Timeline(items: List<LessonItem>, isToday: Boolean, modifier: Modifier = Modifier) {
    val timed = remember(items) { items.filter { it.start != null && it.end != null } }
    if (timed.isEmpty()) return
    val now = if (isToday) rememberNow() else null
    val nowMin = now?.let { it.hour * 60 + it.minute }

    val layout = remember(timed) { layoutBlocks(timed) }
    var opened by remember { mutableStateOf<LessonItem?>(null) }
    opened?.let { LessonSheet(it, onDismiss = { opened = null }) }
    val firstStart = timed.minOf { it.start!!.minutes() }
    val t0 = firstStart - 15
    var end = timed.maxOf { it.end!!.minutes() } + 25
    if (nowMin != null && nowMin in t0 until end + 60) end = maxOf(end, nowMin + 20)
    fun topOf(m: Int): Dp = ((m - t0) * PX_PER_MIN).dp + GRID_PAD
    val totalHeight = topOf(end) + GRID_PAD
    val colors = QbitTheme.colors

    BoxWithConstraints(modifier.fillMaxWidth().height(totalHeight)) {
        val area = maxWidth - BLOCKS_START - BLOCKS_END
        // hour lines
        var hour = (t0 + 59) / 60
        while (hour * 60 <= end) {
            Row(
                Modifier.fillMaxWidth().offset(y = topOf(hour * 60) - 8.dp).height(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "$hour:00",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.muted,
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(LABEL_WIDTH).padding(end = 8.dp),
                )
                Box(Modifier.weight(1f).height(1.dp).background(colors.separator))
            }
            hour++
        }
        layout.forEach { placed ->
            val item = placed.item
            val start = item.start!!.minutes()
            val stop = item.end!!.minutes()
            val width = area / placed.columns
            val isNow = nowMin != null && item.status.takesPlace && nowMin in start until stop
            LessonBlock(
                item = item,
                minutesLeft = if (isNow) stop - nowMin else null,
                compact = placed.columns > 1,
                // A block of two periods or more has room for a second title line.
                titleLines = if (stop - start >= 80) 2 else 1,
                onClick = { opened = item },
                modifier = Modifier
                    .offset(x = BLOCKS_START + width * placed.column, y = topOf(start) + 1.dp)
                    .width(width - if (placed.column < placed.columns - 1) 4.dp else 0.dp)
                    .height(((stop - start) * PX_PER_MIN).dp - 3.dp),
            )
        }
        if (nowMin != null && nowMin in t0..end) {
            Row(
                Modifier.fillMaxWidth().offset(y = topOf(nowMin) - 10.dp).padding(start = 4.dp).height(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    now.text(),
                    fontSize = 11.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onAccent,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(colors.danger.solid).padding(horizontal = 6.dp),
                )
                Box(Modifier.weight(1f).height(1.dp).background(colors.danger.solid))
            }
        }
    }
}

private data class PlacedBlock(val item: LessonItem, val column: Int, val columns: Int)

/** Greedy column assignment inside clusters of overlapping lessons. */
private fun layoutBlocks(items: List<LessonItem>): List<PlacedBlock> {
    val sorted = items.sortedWith(compareBy({ it.start }, { it.end }))
    val result = mutableListOf<PlacedBlock>()
    val cluster = mutableListOf<Pair<LessonItem, Int>>()
    val columnEnds = mutableListOf<LocalTime>()
    var clusterEnd = LocalTime.MIN
    fun flush() {
        val columns = columnEnds.size.coerceAtLeast(1)
        cluster.forEach { (item, col) -> result += PlacedBlock(item, col, columns) }
        cluster.clear()
        columnEnds.clear()
    }
    for (item in sorted) {
        val start = item.start!!
        if (cluster.isNotEmpty() && !start.isBefore(clusterEnd)) flush()
        if (cluster.isEmpty()) clusterEnd = LocalTime.MIN
        var col = columnEnds.indexOfFirst { !start.isBefore(it) }
        if (col < 0) {
            col = columnEnds.size
            columnEnds += item.end!!
        } else {
            columnEnds[col] = item.end!!
        }
        cluster += item to col
        if (item.end.isAfter(clusterEnd)) clusterEnd = item.end
    }
    flush()
    return result
}

private fun LocalTime.minutes() = hour * 60 + minute

/** Diagonal stripes for lessons that don't take place. */
fun Modifier.striped(stripe: Color, base: Color): Modifier = drawBehind {
    drawRect(base)
    val step = 12.dp.toPx()
    val width = 6.dp.toPx()
    clipRect {
        var x = -size.height
        while (x < size.width) {
            drawLine(stripe, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = width)
            x += step
        }
    }
}

@Composable
private fun LessonBlock(item: LessonItem, minutesLeft: Int?, compact: Boolean, titleLines: Int, onClick: () -> Unit, modifier: Modifier) {
    val colors = QbitTheme.colors
    val style = item.status.style()
    val shape = RoundedCornerShape(QbitTheme.radius.tile)
    val fg = if (style.struck) colors.muted else colors.foreground
    val base = if (style.struck) {
        modifier.clip(shape).striped(colors.muted.copy(alpha = 0.14f), colors.surfaceSecondary).border(1.dp, colors.border, shape)
    } else {
        modifier.surface(shape)
    }
    Row(base.clickable(role = Role.Button, onClickLabel = stringResource(R.string.lesson_details), onClick = onClick)) {
        style.bar?.let { Box(Modifier.width(4.dp).fillMaxHeight().background(it)) }
        if (compact) {
            CompactBlockContent(item, fg, style.struck, titleLines)
            return@Row
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LessonNumber(item.periods.text(), fg)
                item.oldSubjectShort?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = colors.muted, textDecoration = TextDecoration.LineThrough, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 72.dp))
                    Text("→", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = fg,
                    textDecoration = if (style.struck) TextDecoration.LineThrough else null,
                    maxLines = titleLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = true),
                )
                item.oldRoom?.let {
                    Text(it, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.muted, textDecoration = TextDecoration.LineThrough, style = MaterialTheme.typography.bodySmall)
                    Text("→", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
                item.room?.let {
                    val roomColor = when {
                        item.oldRoom == null -> fg
                        item.status == dev.milkthe.qbitplan.core.schedule.LessonStatus.MOVED_HERE -> colors.accent.softForeground
                        else -> colors.warning.softForeground
                    }
                    Text(it, style = MaterialTheme.typography.titleSmall, color = roomColor, maxLines = 1)
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val time = listOfNotNull(item.start?.text(), item.end?.text()).joinToString("–")
                Text(time, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = colors.muted, style = MaterialTheme.typography.bodySmall)
                val note = item.note?.let { noteText(it) }
                if (item.who != null || item.oldWho != null || note != null) {
                    Box(Modifier.size(3.dp).clip(CircleShape).background(colors.muted.copy(alpha = 0.6f)))
                }
                val whoColor = if (item.oldWho != null) colors.warning.softForeground else colors.muted
                // Substitution names are full names; the struck old teacher doesn't fit a block on narrow phones.
                val missing = stringResource(R.string.lesson_no_teacher)
                val who = androidx.compose.ui.text.AnnotatedString(item.who ?: if (item.oldWho != null) missing else "")
                val line = if (note != null && who.isNotEmpty()) who + androidx.compose.ui.text.AnnotatedString(" · $note") else if (note != null) androidx.compose.ui.text.AnnotatedString(note) else who
                Text(line, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = whoColor, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f, fill = false))
                item.groups?.let { Pill(it) }
                Spacer(Modifier.weight(1f))
                when {
                    minutesLeft != null -> TagChip(stringResource(R.string.tag_minutes_left, minutesLeft), colors.accent.solid, colors.onAccent)
                    item.isCovering -> TagChip(stringResource(R.string.tag_covering), Color.Transparent, colors.warning.softForeground)
                    item.status != dev.milkthe.qbitplan.core.schedule.LessonStatus.CHANGED -> StatusTag(item.status)
                }
            }
        }
    }
}

@Composable
fun LessonNumber(text: String, color: Color) {
    Text(
        text,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.10f))
            .widthIn(min = 18.dp)
            .padding(horizontal = 4.dp),
    )
}

@Composable
fun noteText(note: LessonNote): String = when (note) {
    is LessonNote.MovedFrom -> stringResource(R.string.note_moved_from, note.period)
    is LessonNote.MovedTo -> stringResource(R.string.note_moved_to, note.period)
    is LessonNote.Covering -> stringResource(R.string.note_covering, note.name)
}

/** Side-by-side group lessons: short subject, room and one detail line, so narrow columns stay readable. */
@Composable
private fun RowScope.CompactBlockContent(item: LessonItem, fg: Color, struck: Boolean, titleLines: Int) {
    val colors = QbitTheme.colors
    Column(
        Modifier.weight(1f).fillMaxHeight().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LessonNumber(item.periods.text(), fg)
            Text(
                item.shortTitle,
                style = MaterialTheme.typography.titleSmall,
                color = fg,
                textDecoration = if (struck) TextDecoration.LineThrough else null,
                maxLines = titleLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Spacer(Modifier.height(2.dp))
        val roomColor = if (item.oldRoom != null) colors.warning.softForeground else colors.muted
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            item.room?.let { Text(it, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = roomColor, maxLines = 1, style = MaterialTheme.typography.bodySmall) }
            val detail = listOfNotNull(item.groups, item.who).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(detail, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
