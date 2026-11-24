@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package dev.milkthe.qbitplan.ui.substitutions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.core.schedule.LessonStatus
import dev.milkthe.qbitplan.core.schedule.isSchoolDay
import dev.milkthe.qbitplan.ui.common.Hairline
import dev.milkthe.qbitplan.ui.common.ListCard
import dev.milkthe.qbitplan.ui.common.LoadingBox
import dev.milkthe.qbitplan.ui.common.Pill
import dev.milkthe.qbitplan.ui.common.PolishLocale
import dev.milkthe.qbitplan.ui.common.QbitButton
import dev.milkthe.qbitplan.ui.common.ButtonVariant
import dev.milkthe.qbitplan.ui.common.RoundIconButton
import dev.milkthe.qbitplan.ui.common.SearchField
import dev.milkthe.qbitplan.ui.common.SectionLabel
import dev.milkthe.qbitplan.ui.common.SegmentedControl
import dev.milkthe.qbitplan.ui.common.StateMessage
import dev.milkthe.qbitplan.ui.common.StatusTag
import dev.milkthe.qbitplan.ui.common.SyncLine
import dev.milkthe.qbitplan.ui.common.dayOfWeekText
import dev.milkthe.qbitplan.ui.common.titleText
import dev.milkthe.qbitplan.ui.theme.QbitTheme
import dev.milkthe.qbitplan.ui.theme.style
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun SubstitutionsScreen(vm: SubstitutionsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val selected by vm.selectedDate.collectAsStateWithLifecycle()
    val query by vm.queryText.collectAsStateWithLifecycle()
    val sync by vm.syncState.collectAsStateWithLifecycle()
    val lastRefresh by vm.lastRefresh.collectAsStateWithLifecycle()
    var calendarOpen by rememberSaveable { mutableStateOf(false) }
    val colors = QbitTheme.colors

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SegmentedControl(
                    options = vm.quickDays.map { quickLabel(it, vm.today) },
                    selected = vm.quickDays.indexOf(selected),
                    onSelect = { vm.select(vm.quickDays[it]) },
                    modifier = Modifier.weight(1f),
                )
                val custom = selected !in vm.quickDays
                Row(
                    Modifier
                        .height(48.dp)
                        .clip(CircleShape)
                        .background(if (custom) colors.accent.solid else colors.default)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.calendar_open)) { calendarOpen = true }
                        .padding(horizontal = if (custom) 14.dp else 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = stringResource(R.string.calendar_open), tint = if (custom) colors.onAccent else colors.foreground, modifier = Modifier.size(22.dp))
                    if (custom) Text("${selected.dayOfWeekText().lowercase(PolishLocale)} ${selected.dayOfMonth}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.onAccent)
                }
            }
            Row(Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(selected.titleText(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                SyncLine(sync, lastRefresh, vm::refresh)
            }
        }
        Hairline()
        PullToRefreshBox(isRefreshing = sync.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            when {
                state.loading || (state.missing && sync.refreshing) -> LoadingBox()
                state.missing -> Column(Modifier.fillMaxSize()) {
                    StateMessage(
                        icon = Icons.Outlined.CloudOff,
                        tone = colors.danger,
                        title = stringResource(R.string.substitutions_error_title),
                        text = stringResource(R.string.substitutions_error_text),
                    ) { QbitButton(stringResource(R.string.retry), vm::refresh, icon = Icons.Outlined.Refresh) }
                }
                else -> SubstitutionsList(state, query, vm::setQuery)
            }
        }
    }

    if (calendarOpen) {
        CalendarSheet(vm, selected, onDismiss = { calendarOpen = false })
    }
}

@Composable
private fun quickLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.today)
    today.plusDays(1) -> stringResource(R.string.tomorrow)
    else -> "${date.dayOfWeekText()} ${date.dayOfMonth}"
}

@Composable
private fun SubstitutionsList(state: SubstitutionsState, query: String, onQuery: (String) -> Unit) {
    var absentOpen by rememberSaveable { mutableStateOf(false) }
    val colors = QbitTheme.colors
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "absent") {
            AbsentSection(state, absentOpen) { absentOpen = !absentOpen }
        }
        if (state.isEmpty) {
            item(key = "empty") {
                StateMessage(
                    icon = Icons.Outlined.CheckCircle,
                    tone = colors.success,
                    title = stringResource(R.string.substitutions_empty_title),
                    text = stringResource(R.string.substitutions_empty_text),
                )
            }
            return@LazyColumn
        }
        item(key = "mine-title") {
            SectionLabel(
                if (state.isTeacher) stringResource(R.string.substitutions_mine_teacher)
                else stringResource(R.string.substitutions_mine_class, state.mineTitle.orEmpty()),
                Modifier.padding(top = 4.dp, start = 2.dp),
            )
        }
        if (state.mine.isEmpty()) {
            item(key = "mine-none") {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceSecondary).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = colors.success.softForeground, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.substitutions_no_mine), style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            item(key = "mine") { RowsCard(state.mine) }
        }
        item(key = "others-title") {
            SectionLabel(stringResource(R.string.substitutions_others), Modifier.padding(top = 12.dp, start = 2.dp))
        }
        item(key = "search") { SearchField(query, onQuery, stringResource(R.string.substitutions_search_class)) }
        others(state.others)
    }
}

private fun LazyListScope.others(sections: List<ClassSection>) {
    sections.forEach { section ->
        stickyHeader(key = "h-${section.className}") {
            Row(
                Modifier.fillMaxWidth().background(QbitTheme.colors.background).height(40.dp).padding(top = 10.dp, start = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(section.className, style = MaterialTheme.typography.titleMedium)
                Text(pluralStringResource(R.plurals.changes_count, section.rows.size, section.rows.size), style = MaterialTheme.typography.bodySmall, color = QbitTheme.colors.muted)
            }
        }
        item(key = "c-${section.className}") { RowsCard(section.rows) }
    }
}

@Composable
private fun AbsentSection(state: SubstitutionsState, open: Boolean, onToggle: () -> Unit) {
    val colors = QbitTheme.colors
    val hasAbsent = state.absentTeachers.isNotEmpty() || state.unavailableRooms.isNotEmpty() || state.absentClasses.isNotEmpty()
    val rotation by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(QbitTheme.radius.tile))
                .clickable(enabled = hasAbsent, onClick = onToggle)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.PersonOff, contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp))
            val summary = if (!hasAbsent) stringResource(R.string.absent_none) else buildList {
                if (state.absentTeachers.isNotEmpty()) add(pluralStringResource(R.plurals.teachers_count, state.absentTeachers.size, state.absentTeachers.size))
                if (state.unavailableRooms.isNotEmpty()) add(pluralStringResource(R.plurals.rooms_count, state.unavailableRooms.size, state.unavailableRooms.size))
                if (state.absentClasses.isNotEmpty()) add(pluralStringResource(R.plurals.classes_count, state.absentClasses.size, state.absentClasses.size))
            }.joinToString(", ")
            Text(
                buildAnnotatedString {
                    pushStyleBold { append(stringResource(R.string.absent_title)) }
                    append(" · ")
                    append(summary)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.foreground,
                modifier = Modifier.weight(1f),
            )
            if (hasAbsent) Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = colors.muted, modifier = Modifier.rotate(rotation))
        }
        AnimatedVisibility(open && hasAbsent) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceSecondary).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AbsentLine(R.string.absent_teachers, state.absentTeachers)
                AbsentLine(R.string.absent_rooms, state.unavailableRooms)
                AbsentLine(R.string.absent_classes, state.absentClasses)
            }
        }
    }
}

@Composable
private fun AbsentLine(label: Int, values: List<String>) {
    if (values.isEmpty()) return
    Text(
        buildAnnotatedString {
            pushStyleBold { append(stringResource(label)); append(": ") }
            append(values.joinToString(", "))
        },
        fontSize = 13.sp,
        lineHeight = 20.sp,
        style = MaterialTheme.typography.bodySmall,
    )
}

private inline fun androidx.compose.ui.text.AnnotatedString.Builder.pushStyleBold(block: androidx.compose.ui.text.AnnotatedString.Builder.() -> Unit) {
    val index = pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold))
    block()
    pop(index)
}

@Composable
private fun RowsCard(rows: List<SubstitutionRow>) {
    ListCard {
        rows.forEachIndexed { i, row ->
            if (i > 0) Hairline()
            SubstitutionRowView(row)
        }
    }
}

@Composable
private fun SubstitutionRowView(row: SubstitutionRow) {
    val colors = QbitTheme.colors
    val struck = row.status.style().struck
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(QbitTheme.radius.chip)).background(colors.surfaceSecondary),
            contentAlignment = Alignment.Center,
        ) {
            Text(row.period.ifEmpty { "–" }, fontSize = if (row.period.length > 2) 11.sp else 14.sp, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
        Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
                row.oldTitle?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = colors.muted, textDecoration = TextDecoration.LineThrough)
                    Text("→", style = MaterialTheme.typography.titleSmall, color = colors.muted)
                }
                Text(row.title, style = MaterialTheme.typography.titleSmall, color = if (struck) colors.muted else colors.foreground, textDecoration = if (struck) TextDecoration.LineThrough else null)
                row.group?.let { Pill(it, Modifier.align(Alignment.CenterVertically)) }
            }
            detailText(row)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.muted) }
        }
        StatusTag(row.status, Modifier.padding(top = 4.dp), large = true)
    }
}

@Composable
private fun detailText(row: SubstitutionRow): String? {
    val parts = buildList {
        when {
            row.oldTeacher != null -> add("${row.oldTeacher} → ${row.teacher ?: stringResource(R.string.lesson_no_teacher)}")
            row.status == LessonStatus.NO_SUBSTITUTE && row.teacher != null -> add(stringResource(R.string.detail_teacher_absent, row.teacher))
            row.status != LessonStatus.CHANGED && row.teacher != null -> add(row.teacher)
        }
        if (row.oldRoom != null && row.room != null) add(stringResource(R.string.detail_room_change, row.oldRoom, row.room))
        row.movedTo?.let { add(stringResource(R.string.note_moved_to, it)) }
        row.movedFrom?.let { add(stringResource(R.string.note_moved_from, it)) }
        row.withoutClasses?.let { add(stringResource(R.string.detail_without_classes, it)) }
    }
    return parts.joinToString(" · ").replaceFirstChar { it.titlecase(PolishLocale) }.ifEmpty { null }
}

private val MonthTitle = DateTimeFormatter.ofPattern("LLLL yyyy", PolishLocale)

@Composable
private fun CalendarSheet(vm: SubstitutionsViewModel, selected: LocalDate, onDismiss: () -> Unit) {
    val sheet = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val dots by remember(month) { vm.daysWithChanges(month) }.collectAsStateWithLifecycle(emptySet())
    val colors = QbitTheme.colors
    fun pick(date: LocalDate) {
        vm.select(date)
        scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = QbitTheme.radius.sheet, topEnd = QbitTheme.radius.sheet),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp).navigationBarsPadding()) {
            Row(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.calendar_title), style = MaterialTheme.typography.titleLarge)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(colors.warning.solid))
                        Text(stringResource(R.string.calendar_has_changes), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    }
                }
                QbitButton(stringResource(R.string.today), { month = YearMonth.from(vm.today); pick(vm.today) }, variant = ButtonVariant.Secondary, modifier = Modifier.height(40.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.calendar_prev), { month = month.minusMonths(1) }, size = 44.dp)
                Text(month.atDay(1).format(MonthTitle).replaceFirstChar { it.titlecase(PolishLocale) }, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                RoundIconButton(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.calendar_next), { month = month.plusMonths(1) }, size = 44.dp)
            }
            val weekdays = listOf("pn", "wt", "śr", "cz", "pt", "so", "nd")
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                weekdays.forEach { Text(it, fontSize = 12.sp, color = colors.muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall) }
            }
            val first = month.atDay(1)
            val lead = first.dayOfWeek.value - 1
            val cells = (0 until 42).map { first.minusDays(lead.toLong()).plusDays(it.toLong()) }
            cells.chunked(7).forEach { week ->
                if (week.first().month != month.month && week.first().isAfter(first)) return@forEach
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    week.forEach { date ->
                        val inMonth = date.month == month.month
                        val enabled = inMonth && date.isSchoolDay()
                        val sel = date == selected
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (sel) colors.accent.solid else Color.Transparent)
                                    .clickable(enabled = enabled) { pick(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    date.dayOfMonth.toString(),
                                    fontSize = 15.sp,
                                    fontWeight = if (date == vm.today) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        sel -> colors.onAccent
                                        !inMonth || !enabled -> colors.muted.copy(alpha = 0.5f)
                                        date == vm.today -> colors.accent.softForeground
                                        else -> colors.foreground
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                if (date in dots) {
                                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).size(5.dp).clip(CircleShape).background(if (sel) colors.onAccent else colors.warning.solid))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
