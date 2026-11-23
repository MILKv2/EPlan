@file:OptIn(ExperimentalMaterial3Api::class)

package dev.milkthe.qbitplan.ui.plan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.data.Profile
import dev.milkthe.qbitplan.ui.common.ButtonVariant
import dev.milkthe.qbitplan.ui.common.DaySelector
import dev.milkthe.qbitplan.ui.common.Hairline
import dev.milkthe.qbitplan.ui.common.LessonItem
import dev.milkthe.qbitplan.ui.common.LoadingBox
import dev.milkthe.qbitplan.ui.common.QbitButton
import dev.milkthe.qbitplan.ui.common.RoundIconButton
import dev.milkthe.qbitplan.ui.common.StateMessage
import dev.milkthe.qbitplan.ui.common.SyncLine
import dev.milkthe.qbitplan.ui.common.Timeline
import dev.milkthe.qbitplan.ui.common.dayOfWeekText
import dev.milkthe.qbitplan.ui.common.shortText
import dev.milkthe.qbitplan.ui.common.striped
import dev.milkthe.qbitplan.ui.common.surface
import dev.milkthe.qbitplan.ui.common.text
import dev.milkthe.qbitplan.ui.theme.QbitTheme
import dev.milkthe.qbitplan.ui.theme.style
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun PlanScreen(onOpenProfile: () -> Unit, vm: PlanViewModel = hiltViewModel()) {
    val header by vm.header.collectAsStateWithLifecycle()
    val chips by vm.dayChips.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val weekend by vm.showWeekend.collectAsStateWithLifecycle()
    val pager = rememberPagerState(initialPage = vm.initialPage) { vm.dates.size }
    val scope = rememberCoroutineScope()
    var collapsed by remember { mutableStateOf(false) }
    val isWeek = mode == PlanMode.WEEK && !weekend

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        AnimatedVisibility(!collapsed, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileChip(header.profile, onOpenProfile, Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                RoundIconButton(
                    icon = Icons.Outlined.CalendarViewWeek,
                    contentDescription = stringResource(if (isWeek) R.string.plan_show_day else R.string.plan_show_week),
                    onClick = vm::toggleWeek,
                    size = 40.dp,
                    background = if (isWeek) QbitTheme.colors.accent.soft else androidx.compose.ui.graphics.Color.Transparent,
                    tint = if (isWeek) QbitTheme.colors.accent.softForeground else QbitTheme.colors.foreground,
                )
            }
        }
        if (!isWeek) {
            DaySelector(
                days = chips,
                position = if (weekend) -1f else pager.currentPage + pager.currentPageOffsetFraction,
                onSelect = { index ->
                    vm.showDay()
                    scope.launch { pager.animateScrollToPage(index) }
                },
                collapsed = collapsed,
                modifier = Modifier.padding(vertical = if (collapsed) 8.dp else 0.dp),
            )
        }
        AnimatedVisibility(!collapsed, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SyncLine(header.sync, header.lastRefresh, vm::refresh)
            }
        }
        Hairline()
        when {
            !header.hasTimetable && header.sync.refreshing -> LoadingBox()
            !header.hasTimetable -> StateMessage(
                icon = Icons.Outlined.EventBusy,
                tone = QbitTheme.colors.danger,
                title = stringResource(R.string.error_title),
                text = stringResource(R.string.error_text),
            ) { QbitButton(stringResource(R.string.retry), vm::refresh) }
            weekend -> Weekend(vm.dates.first(), onShowMonday = {
                vm.showDay()
                scope.launch { pager.scrollToPage(0) }
            })
            isWeek -> WeekGrid(vm, header.isTeacher, onPick = { index ->
                vm.showDay()
                scope.launch { pager.scrollToPage(index) }
            })
            else -> PullToRefreshBox(isRefreshing = header.sync.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                HorizontalPager(state = pager, key = { vm.dates[it].toEpochDay() }, modifier = Modifier.fillMaxSize()) { page ->
                    DayPage(vm, vm.dates[page], isCurrent = pager.currentPage == page, onCollapsed = { collapsed = it })
                }
            }
        }
    }
}

@Composable
private fun ProfileChip(profile: Profile?, onClick: () -> Unit, modifier: Modifier) {
    val colors = QbitTheme.colors
    val (main, sub) = when (profile) {
        is Profile.Student -> profile.schoolClass.short to profile.groupNames.sorted().joinToString(" · ").ifEmpty { stringResource(R.string.plan_whole_class) }
        is Profile.TeacherProfile -> profile.teacher.short to profile.fullName.orEmpty()
        null -> "" to ""
    }
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(colors.default)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.plan_change_profile), onClick = onClick)
            .padding(start = 14.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(main, fontSize = 16.sp, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        if (sub.isNotEmpty()) {
            Text(sub, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f, fill = false))
        }
        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = colors.muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun DayPage(vm: PlanViewModel, date: LocalDate, isCurrent: Boolean, onCollapsed: (Boolean) -> Unit) {
    val state by remember(date) { vm.day(date) }.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val scrolled by remember { derivedStateOf { scroll.value > 28 } }
    LaunchedEffect(scrolled, isCurrent) { if (isCurrent) onCollapsed(scrolled) }

    when (val s = state) {
        DayPlanState.Loading -> LoadingBox()
        DayPlanState.NoData -> Unit
        is DayPlanState.Ready -> if (s.lessons.isEmpty()) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                StateMessage(
                    icon = Icons.Outlined.WbSunny,
                    tone = QbitTheme.colors.accent,
                    title = stringResource(R.string.plan_no_lessons_title),
                    text = stringResource(R.string.plan_no_lessons_text),
                )
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom = 16.dp)) {
                Timeline(s.lessons, isToday = date == vm.today)
            }
        }
    }
}

@Composable
private fun Weekend(monday: LocalDate, onShowMonday: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        StateMessage(
            icon = Icons.Outlined.WbSunny,
            tone = QbitTheme.colors.accent,
            title = stringResource(R.string.plan_weekend_title),
            text = stringResource(R.string.plan_weekend_text, monday.shortText()),
        ) { QbitButton(stringResource(R.string.plan_show_monday), onShowMonday, variant = ButtonVariant.Secondary) }
    }
}

@Composable
private fun WeekGrid(vm: PlanViewModel, isTeacher: Boolean, onPick: (Int) -> Unit) {
    val states = vm.dates.map { remember(it) { vm.day(it) }.collectAsStateWithLifecycle().value }
    val ready = states.filterIsInstance<DayPlanState.Ready>()
    if (ready.size < states.size) {
        LoadingBox()
        return
    }
    val maxPeriod = ready.flatMap { it.lessons }.maxOfOrNull { it.periods.last } ?: 7
    val minPeriod = ready.flatMap { it.lessons }.minOfOrNull { it.periods.first } ?: 1
    val periods = (minPeriod..maxOf(maxPeriod, 7)).toList()
    val colors = QbitTheme.colors

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp)) {
        stickyHeader(key = "head") {
            Row(Modifier.fillMaxWidth().background(colors.background).padding(vertical = 6.dp)) {
                Spacer(Modifier.width(40.dp))
                vm.dates.forEachIndexed { i, date ->
                    val today = date == vm.today
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (today) colors.accent.solid else colors.background)
                            .clickable { onPick(i) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(date.dayOfWeekText(), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, color = if (today) colors.onAccent else colors.muted, style = MaterialTheme.typography.bodySmall)
                        Text(date.dayOfMonth.toString(), fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = if (today) colors.onAccent else colors.foreground, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        items(periods, key = { it }) { period ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$period", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                    ready.first().periodStarts[period]?.let {
                        Text(it.text(), fontSize = 10.sp, lineHeight = 13.sp, color = colors.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                ready.forEachIndexed { i, day ->
                    val cell = day.lessons.filter { period in it.periods }
                    Box(Modifier.weight(1f).padding(horizontal = 2.dp)) {
                        if (cell.isNotEmpty()) WeekCell(cell, isTeacher) { onPick(i) } else Spacer(Modifier.height(54.dp))
                    }
                }
            }
        }
        item(key = "legend") {
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            ) {
                Legend(colors.warning.solid, stringResource(R.string.tag_changed))
                Legend(colors.danger.solid, stringResource(R.string.tag_cancelled))
                Legend(colors.accent.solid, stringResource(R.string.tag_moved))
            }
        }
    }
}

@Composable
private fun WeekCell(lessons: List<LessonItem>, isTeacher: Boolean, onClick: () -> Unit) {
    val colors = QbitTheme.colors
    val first = lessons.first()
    val style = first.status.style()
    val shape = RoundedCornerShape(12.dp)
    val base = Modifier.fillMaxWidth().height(54.dp)
    val surfaced = if (style.struck) {
        base.clip(shape).striped(colors.muted.copy(alpha = 0.14f), colors.surfaceSecondary).border(1.dp, colors.border, shape)
    } else {
        base.surface(shape)
    }
    Row(surfaced.clickable(onClick = onClick)) {
        style.bar?.let { Box(Modifier.width(3.dp).fillMaxHeight().background(it)) }
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp, end = 6.dp), verticalArrangement = Arrangement.Center) {
            val name = lessons.joinToString("/") { if (isTeacher) it.classes.orEmpty() else it.subjectShort }
            Text(
                name,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (style.struck) colors.muted else colors.foreground,
                textDecoration = if (style.struck) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
            first.room?.let { Text(it, fontSize = 11.sp, lineHeight = 14.sp, color = colors.muted, maxLines = 1, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.width(3.dp).height(12.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, fontSize = 12.sp, color = QbitTheme.colors.muted, style = MaterialTheme.typography.bodySmall)
    }
}
