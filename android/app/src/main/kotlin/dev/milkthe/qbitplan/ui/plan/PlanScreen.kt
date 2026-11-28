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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
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
import dev.milkthe.qbitplan.ui.common.striped
import dev.milkthe.qbitplan.ui.common.surface
import dev.milkthe.qbitplan.ui.common.text
import dev.milkthe.qbitplan.ui.theme.QbitTheme
import dev.milkthe.qbitplan.ui.theme.style
import kotlinx.coroutines.launch
import java.time.LocalDate

/** [onEditProfile]: the profile editor; [onOpenSettings]: the Settings tab; [onOpenVersion]: the timetable version picker. */
@Composable
fun PlanScreen(onEditProfile: () -> Unit, onOpenSettings: () -> Unit, onOpenVersion: () -> Unit = {}, vm: PlanViewModel = hiltViewModel()) {
    val menu = remember(onEditProfile, onOpenSettings, onOpenVersion, vm) { PlanMenuActions(onEditProfile, onOpenSettings, onOpenVersion, vm::setOnlyMyGroups) }
    val header by vm.header.collectAsStateWithLifecycle()
    val week by vm.week.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val isWeek = mode == PlanMode.WEEK

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        when (val w = week) {
            null -> {
                PlanTopBar(header, isWeek, menu, vm::toggleWeek)
                LoadingBox()
            }
            // A new week gets a new pager, opened on the week's start day.
            else -> key(w.dates.first()) { PlanContent(vm, w, header, isWeek, menu) }
        }
    }
}

@Immutable
private class PlanMenuActions(val onEditProfile: () -> Unit, val onOpenSettings: () -> Unit, val onOpenVersion: () -> Unit, val onOnlyMyGroups: (Boolean) -> Unit)

@Composable
private fun PlanTopBar(header: PlanHeader, isWeek: Boolean, menu: PlanMenuActions, onToggleWeek: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The only weighted child, so the chip may take all the width the button leaves.
        Box(Modifier.weight(1f)) { ProfileMenu(header, menu) }
        RoundIconButton(
            icon = Icons.Outlined.CalendarViewWeek,
            contentDescription = stringResource(if (isWeek) R.string.plan_show_day else R.string.plan_show_week),
            onClick = onToggleWeek,
            size = 40.dp,
            background = if (isWeek) QbitTheme.colors.accent.soft else androidx.compose.ui.graphics.Color.Transparent,
            tint = if (isWeek) QbitTheme.colors.accent.softForeground else QbitTheme.colors.foreground,
        )
    }
}

@Composable
private fun ColumnScope.PlanContent(
    vm: PlanViewModel,
    week: PlanWeek,
    header: PlanHeader,
    isWeek: Boolean,
    menu: PlanMenuActions,
) {
    val chips by vm.dayChips.collectAsStateWithLifecycle()
    val pager = rememberPagerState(initialPage = week.initialPage) { week.dates.size }
    val scope = rememberCoroutineScope()
    var collapsed by remember { mutableStateOf(false) }
    var headerHeight by remember { mutableIntStateOf(0) }
    // The start day moved within the week (last lesson over); the view model only moves it when the user hasn't swiped away.
    LaunchedEffect(week.initialPage) { if (pager.currentPage != week.initialPage) pager.animateScrollToPage(week.initialPage) }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect(vm::onPageSettled) }

    Column(Modifier.onSizeChanged { if (!collapsed) headerHeight = it.height }) {
        AnimatedVisibility(!collapsed, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            PlanTopBar(header, isWeek, menu, vm::toggleWeek)
        }
        if (!isWeek) {
            DaySelector(
                days = chips.ifEmpty { week.chips },
                position = pager.currentPage + pager.currentPageOffsetFraction,
                onSelect = { index ->
                    vm.showDay()
                    scope.launch { pager.animateScrollToPage(index) }
                },
                collapsed = collapsed,
                modifier = Modifier.padding(vertical = if (collapsed) 8.dp else 0.dp),
            )
        }
        AnimatedVisibility(!collapsed, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                SyncLine(header.sync, header.lastRefresh, vm::refresh)
                header.pinnedVersion?.let { PinnedVersionLine(it, menu.onOpenVersion) }
            }
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
        isWeek -> WeekGrid(vm, week.dates, week.today, header.isTeacher, onPick = { index ->
            vm.showDay()
            scope.launch { pager.scrollToPage(index) }
        })
        else -> PullToRefreshBox(isRefreshing = header.sync.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            HorizontalPager(state = pager, key = { week.dates[it].toEpochDay() }, modifier = Modifier.fillMaxSize()) { page ->
                DayPage(vm, week.dates[page], isToday = week.dates[page] == week.today, isCurrent = pager.currentPage == page, collapsed = collapsed, headerHeight = { headerHeight }, onCollapsed = { collapsed = it })
            }
        }
    }
}

/** A pinned version can go stale if the school edits it, so the plan says it isn't automatic. */
@Composable
private fun PinnedVersionLine(name: String, onClick: () -> Unit) {
    val colors = QbitTheme.colors
    Row(
        Modifier
            .padding(bottom = 6.dp)
            .clip(CircleShape)
            .background(colors.warning.soft)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.plan_version_pinned, name), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, color = colors.warning.softForeground, style = MaterialTheme.typography.bodySmall)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.warning.softForeground, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun ProfileMenu(header: PlanHeader, actions: PlanMenuActions) {
    val colors = QbitTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        ProfileChip(header.profile, onClick = { open = true })
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(QbitTheme.radius.card),
            containerColor = colors.surface,
        ) {
            if (header.profile is Profile.Student) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_only_my_groups), style = MaterialTheme.typography.bodyLarge) },
                    onClick = { actions.onOnlyMyGroups(!header.onlyMyGroups) },
                    leadingIcon = { Icon(Icons.Outlined.FilterList, contentDescription = null, tint = colors.muted) },
                    trailingIcon = { if (header.onlyMyGroups) Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.accent.softForeground) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(if (header.isTeacher) R.string.plan_change_teacher else R.string.plan_change_profile), style = MaterialTheme.typography.bodyLarge) },
                onClick = {
                    open = false
                    actions.onEditProfile()
                },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = colors.muted) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.bodyLarge) },
                onClick = {
                    open = false
                    actions.onOpenSettings()
                },
                leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null, tint = colors.muted) },
            )
        }
    }
}

@Composable
private fun ProfileChip(profile: Profile?, onClick: () -> Unit) {
    val colors = QbitTheme.colors
    val (main, sub) = when (profile) {
        is Profile.Student -> profile.schoolClass.short to profile.groups.picked.sorted().joinToString(" · ").ifEmpty { stringResource(R.string.plan_whole_class) }
        is Profile.TeacherProfile -> profile.teacher.short to profile.fullName.orEmpty()
        null -> "" to ""
    }
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(colors.default)
            .clickable(role = Role.DropdownList, onClickLabel = stringResource(R.string.plan_options), onClick = onClick)
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
private fun DayPage(
    vm: PlanViewModel,
    date: LocalDate,
    isToday: Boolean,
    isCurrent: Boolean,
    collapsed: Boolean,
    headerHeight: () -> Int,
    onCollapsed: (Boolean) -> Unit,
) {
    val state by remember(date) { vm.day(date) }.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val isCollapsed by rememberUpdatedState(collapsed)
    // Collapsing gives the page up to the header's height more room. A page with less than that
    // to scroll would then fit, snap back to the top and expand the header again, over and over.
    val scrolled by remember {
        derivedStateOf { scroll.value > CollapseAfterPx && (isCollapsed || scroll.maxValue > headerHeight() + CollapseAfterPx) }
    }
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
                Timeline(s.lessons, isToday = isToday)
            }
        }
    }
}

@Composable
private fun WeekGrid(vm: PlanViewModel, dates: List<LocalDate>, today: LocalDate, isTeacher: Boolean, onPick: (Int) -> Unit) {
    val states = dates.map { remember(it) { vm.day(it) }.collectAsStateWithLifecycle().value }
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
                dates.forEachIndexed { i, date ->
                    val isToday = date == today
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isToday) colors.accent.solid else colors.background)
                            .clickable { onPick(i) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(date.dayOfWeekText(), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, color = if (isToday) colors.onAccent else colors.muted, style = MaterialTheme.typography.bodySmall)
                        Text(date.dayOfMonth.toString(), fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = if (isToday) colors.onAccent else colors.foreground, style = MaterialTheme.typography.bodyMedium)
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

private const val CollapseAfterPx = 28
