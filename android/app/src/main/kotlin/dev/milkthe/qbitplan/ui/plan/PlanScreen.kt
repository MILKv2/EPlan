@file:OptIn(ExperimentalMaterial3Api::class)

package dev.milkthe.qbitplan.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.data.Profile
import dev.milkthe.qbitplan.ui.common.AppScaffold
import dev.milkthe.qbitplan.ui.common.CenteredMessage
import dev.milkthe.qbitplan.ui.common.LessonCard
import dev.milkthe.qbitplan.ui.common.LessonItem
import dev.milkthe.qbitplan.ui.common.LoadingBox
import dev.milkthe.qbitplan.ui.common.NowBar
import dev.milkthe.qbitplan.ui.common.OfflineBanner
import dev.milkthe.qbitplan.ui.common.dateLabel
import dev.milkthe.qbitplan.ui.common.dayOfWeekText
import dev.milkthe.qbitplan.ui.common.titleText
import dev.milkthe.qbitplan.ui.theme.QbitTheme
import dev.milkthe.qbitplan.ui.theme.tint
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun PlanScreen(vm: PlanViewModel = hiltViewModel()) {
    val header by vm.header.collectAsStateWithLifecycle()
    val pager = rememberPagerState(initialPage = vm.initialPage) { vm.dates.size }

    AppScaffold(title = stringResource(R.string.tab_plan), subtitle = header.profile?.let { profileLabel(it) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OfflineBanner(header.sync, header.lastRefresh)
            PlanControls(header, onMode = vm::setMode, onOnlyMine = vm::setOnlyMyGroups)
            when {
                !header.hasTimetable && header.sync.refreshing -> LoadingBox()
                !header.hasTimetable -> CenteredMessage(
                    text = stringResource(R.string.no_data),
                    icon = Icons.Outlined.EventBusy,
                    action = stringResource(R.string.retry) to vm::refresh,
                )
                header.mode == PlanMode.DAY -> DayPager(vm, pager, header.sync.refreshing)
                else -> WeekView(vm, pager.currentPage / 5)
            }
        }
    }
}

@Composable
private fun profileLabel(profile: Profile): String = when (profile) {
    is Profile.Student -> listOf(profile.schoolClass.short).plus(profile.groupNames.sorted()).joinToString(" · ")
    is Profile.TeacherProfile -> listOfNotNull(profile.teacher.short, profile.fullName).joinToString(" · ")
}

@Composable
private fun PlanControls(header: PlanHeader, onMode: (PlanMode) -> Unit, onOnlyMine: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val modes = listOf(PlanMode.DAY to R.string.plan_day, PlanMode.WEEK to R.string.plan_week)
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            modes.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = header.mode == mode,
                    onClick = { onMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                ) { Text(stringResource(label)) }
            }
        }
        if (header.hasGroups) {
            FilterChip(
                selected = header.onlyMyGroups,
                onClick = { onOnlyMine(!header.onlyMyGroups) },
                label = { Text(stringResource(R.string.plan_my_groups)) },
            )
        }
    }
}

@Composable
private fun DayPager(vm: PlanViewModel, pager: PagerState, refreshing: Boolean) {
    val scope = rememberCoroutineScope()
    PrimaryScrollableTabRow(
        selectedTabIndex = pager.currentPage,
        edgePadding = 16.dp,
        containerColor = QbitTheme.colors.background,
    ) {
        vm.dates.forEachIndexed { index, date ->
            Tab(
                selected = pager.currentPage == index,
                onClick = { scope.launch { pager.animateScrollToPage(index) } },
                text = { Text(dateLabel(date, vm.today).takeIf { date == vm.today || date == vm.today.plusDays(1) } ?: "${date.dayOfWeekText()} ${date.dayOfMonth}") },
            )
        }
    }
    HorizontalPager(state = pager, key = { vm.dates[it].toEpochDay() }, modifier = Modifier.fillMaxSize()) { page ->
        DayPage(vm, vm.dates[page], refreshing)
    }
}

@Composable
private fun DayPage(vm: PlanViewModel, date: LocalDate, refreshing: Boolean) {
    val state by remember(date) { vm.day(date) }.collectAsStateWithLifecycle()
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        when (val s = state) {
            DayPlanState.Loading -> LoadingBox()
            DayPlanState.NoData -> CenteredMessage(stringResource(R.string.no_data))
            is DayPlanState.Ready -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "title") {
                    Text(date.titleText(), style = MaterialTheme.typography.titleMedium, color = QbitTheme.colors.muted)
                }
                if (date == vm.today) {
                    item(key = "now") { NowBar(s.periods, s.lessons) }
                }
                if (!s.substitutionsLoaded) {
                    item(key = "subs") { Hint(stringResource(R.string.plan_substitutions_missing)) }
                } else if (s.message != null && s.lessons.none { it.status != dev.milkthe.qbitplan.core.schedule.LessonStatus.NORMAL }) {
                    item(key = "msg") { Hint(s.message) }
                }
                if (s.lessons.isEmpty()) {
                    item(key = "empty") { Hint(stringResource(R.string.plan_no_lessons)) }
                }
                items(s.lessons, key = { it.key }) { LessonCard(it) }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = QbitTheme.colors.muted)
}

@Composable
private fun WeekView(vm: PlanViewModel, initialWeek: Int) {
    var week by rememberSaveable { mutableIntStateOf(initialWeek) }
    val days = vm.dates.subList(week * 5, week * 5 + 5)
    val states = days.map { date -> remember(date) { vm.day(date) }.collectAsStateWithLifecycle().value }
    val ready = states.filterIsInstance<DayPlanState.Ready>()
    if (ready.size < states.size) {
        LoadingBox()
        return
    }
    val periods = ready.flatMap { it.lessons }.flatMap { it.periods.first..it.periods.last }.toSortedSet()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.plan_this_week, R.string.plan_next_week).forEachIndexed { index, label ->
                FilterChip(selected = week == index, onClick = { week = index }, label = { Text(stringResource(label)) })
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "header") {
                Row {
                    Spacer(Modifier.width(24.dp))
                    days.forEach { date ->
                        Text(
                            text = "${date.dayOfWeekText()} ${date.dayOfMonth}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (date == vm.today) QbitTheme.colors.primary.accent else QbitTheme.colors.muted,
                            modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                        )
                    }
                }
            }
            items(periods.toList(), key = { it }) { period ->
                Row {
                    Text("$period", style = MaterialTheme.typography.labelMedium, color = QbitTheme.colors.muted, modifier = Modifier.width(24.dp).padding(top = 6.dp))
                    ready.forEach { day ->
                        Column(Modifier.weight(1f).padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            day.lessons.filter { period in it.periods }.forEach { WeekCell(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekCell(item: LessonItem) {
    val colors = QbitTheme.colors
    val tint = item.status.tint()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(QbitTheme.radius.small))
            .background(tint?.container ?: colors.content2)
            .padding(4.dp),
    ) {
        Text(
            item.subjectShort,
            style = MaterialTheme.typography.labelMedium,
            color = tint?.content ?: colors.foreground,
            textDecoration = if (item.status.takesPlace) null else TextDecoration.LineThrough,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        (item.classes ?: item.room)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = tint?.content ?: colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
