package dev.milkthe.qbitplan.ui.onboarding

import dev.milkthe.qbitplan.ui.common.QbitTextField
import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.data.prefs.UserMode
import dev.milkthe.qbitplan.ui.common.ButtonVariant
import dev.milkthe.qbitplan.ui.common.Hairline
import dev.milkthe.qbitplan.ui.common.LoadingBox
import dev.milkthe.qbitplan.ui.common.QbitButton
import dev.milkthe.qbitplan.ui.common.RadioDot
import dev.milkthe.qbitplan.ui.common.RoundIconButton
import dev.milkthe.qbitplan.ui.common.SearchField
import dev.milkthe.qbitplan.ui.common.SectionLabel
import dev.milkthe.qbitplan.ui.common.SegmentedControl
import dev.milkthe.qbitplan.ui.common.StateMessage
import dev.milkthe.qbitplan.ui.common.surface
import dev.milkthe.qbitplan.ui.search.GridTile
import dev.milkthe.qbitplan.ui.theme.QbitTheme

@Composable
fun OnboardingScreen(vm: OnboardingViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sel = state.selection
    BackHandler(enabled = sel.step != OnboardingStep.ROLE) { vm.back() }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        ProgressBar(sel.step, showBack = sel.step != OnboardingStep.ROLE, onBack = { vm.back() })
        when {
            sel.step == OnboardingStep.ROLE -> RoleStep(sel.mode, vm::chooseMode, vm::next)
            !state.hasTimetable && !state.failed -> LoadingBox()
            !state.hasTimetable -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                StateMessage(Icons.Outlined.CloudOff, QbitTheme.colors.danger, stringResource(R.string.error_title), stringResource(R.string.error_text)) {
                    QbitButton(stringResource(R.string.retry), vm::retry)
                }
            }
            sel.step == OnboardingStep.CLASS -> ClassStep(state, vm)
            sel.step == OnboardingStep.GROUPS -> GroupsStep(state, vm)
            sel.step == OnboardingStep.TEACHER -> TeacherStep(state, vm)
            sel.step == OnboardingStep.NOTIFICATIONS -> NotificationsStep(state, vm)
        }
    }
}

@Composable
private fun ProgressBar(step: OnboardingStep, showBack: Boolean, onBack: () -> Unit) {
    val colors = QbitTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showBack) RoundIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), onBack) else Spacer(Modifier.size(48.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..3).forEach { i ->
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (i <= step.number) colors.accent.solid else colors.default))
            }
        }
        Text(stringResource(R.string.onboarding_step, step.number), fontSize = 13.sp, color = colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(40.dp))
    }
}

@Composable
private fun Heading(title: String, text: String) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(text, fontSize = 15.sp, lineHeight = 22.sp, color = QbitTheme.colors.muted, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ColumnScope.Footer(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@Composable
private fun ColumnScope.RoleStep(mode: UserMode, onChoose: (UserMode) -> Unit, onNext: () -> Unit) {
    Heading(stringResource(R.string.onboarding_role_title), stringResource(R.string.onboarding_role_text))
    Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RoleCard(Icons.Outlined.School, stringResource(R.string.mode_student), stringResource(R.string.mode_student_desc), mode == UserMode.STUDENT) { onChoose(UserMode.STUDENT) }
        RoleCard(Icons.Outlined.Person, stringResource(R.string.mode_teacher), stringResource(R.string.mode_teacher_desc), mode == UserMode.TEACHER) { onChoose(UserMode.TEACHER) }
    }
    Footer {
        Text(stringResource(R.string.about_unofficial_short), fontSize = 12.sp, lineHeight = 18.sp, color = QbitTheme.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp))
        QbitButton(stringResource(R.string.next), onNext, Modifier.fillMaxWidth())
    }
}

@Composable
private fun RoleCard(icon: ImageVector, title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    val colors = QbitTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .surface(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent.solid else colors.border, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(if (selected) colors.accent.soft else colors.surfaceSecondary),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = if (selected) colors.accent.softForeground else colors.foreground, modifier = Modifier.size(26.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        }
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) colors.accent.solid else Color.Transparent)
                .border(if (selected) 0.dp else 2.dp, colors.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
private fun ColumnScope.ClassStep(state: OnboardingState, vm: OnboardingViewModel) {
    Heading(stringResource(R.string.onboarding_class_title), stringResource(R.string.onboarding_class_text, state.classCount))
    SearchField(state.selection.classQuery, vm::setClassQuery, stringResource(R.string.onboarding_class_search), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.classes, key = { it.id }) { option ->
            GridTile(option.title, highlighted = false, selected = option.id == state.selection.classId) { vm.chooseClass(option.id) }
        }
    }
    Footer {
        QbitButton(
            text = state.className?.let { stringResource(R.string.next_with, it) } ?: stringResource(R.string.next),
            onClick = vm::next,
            enabled = state.selection.classId != null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ColumnScope.GroupsStep(state: OnboardingState, vm: OnboardingViewModel) {
    Heading(stringResource(R.string.onboarding_groups_title), stringResource(R.string.onboarding_groups_text))
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        state.divisions.forEach { division ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(division.label?.let { stringResource(it) } ?: division.options.joinToString(" / "))
                val options = division.options + stringResource(R.string.groups_all)
                val current = state.selection.groups[division.id]
                SegmentedControl(
                    options = options,
                    selected = current?.let { division.options.indexOf(it) } ?: division.options.size,
                    onSelect = { vm.setGroup(division.id, division.options.getOrNull(it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    Footer { QbitButton(stringResource(R.string.next), vm::next, Modifier.fillMaxWidth()) }
}

@Composable
private fun ColumnScope.TeacherStep(state: OnboardingState, vm: OnboardingViewModel) {
    val colors = QbitTheme.colors
    val sel = state.selection
    Heading(stringResource(R.string.onboarding_teacher_title), stringResource(R.string.onboarding_teacher_text))
    SearchField(sel.teacherQuery, vm::setTeacherQuery, stringResource(R.string.onboarding_teacher_search), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.teachers, key = { it.id }) { option ->
            GridTile(option.title, highlighted = false, selected = option.id == sel.teacherId, subtitle = option.subtitle) { vm.chooseTeacher(option.id) }
        }
        if (sel.teacherId != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "name-title") {
                SectionLabel(stringResource(R.string.onboarding_name_title), Modifier.padding(top = 10.dp))
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "names") {
                Column(Modifier.fillMaxWidth().surface(RoundedCornerShape(QbitTheme.radius.card))) {
                    state.suggestedNames.forEachIndexed { i, name ->
                        if (i > 0) Hairline()
                        NameOption(name, stringResource(R.string.onboarding_name_matches, state.teacherShort.orEmpty()), sel.pickedName == name) { vm.pickName(name) }
                    }
                    if (state.suggestedNames.isNotEmpty()) Hairline()
                    NameOption(stringResource(R.string.onboarding_name_manual), stringResource(R.string.onboarding_name_manual_desc), sel.pickedName == null) { vm.pickName(null) }
                }
            }
            if (sel.pickedName == null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "typed") {
                    Box(
                        Modifier.fillMaxWidth().height(48.dp).surface(RoundedCornerShape(QbitTheme.radius.field)).padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (sel.typedName.isEmpty()) Text(stringResource(R.string.onboarding_name_hint), style = MaterialTheme.typography.bodyLarge, color = colors.muted)
                        QbitTextField(value = sel.typedName, onValueChange = vm::setTypedName, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    Footer {
        QbitButton(
            text = state.teacherShort?.let { stringResource(R.string.next_with, it) } ?: stringResource(R.string.next),
            onClick = vm::next,
            enabled = sel.teacherId != null && sel.fullName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NameOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RadioDot(selected)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = QbitTheme.colors.muted)
        }
    }
}

@Composable
private fun ColumnScope.NotificationsStep(state: OnboardingState, vm: OnboardingViewModel) {
    val colors = QbitTheme.colors
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.finish(granted) }
    Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(colors.accent.soft), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = colors.accent.softForeground, modifier = Modifier.size(40.dp))
        }
    }
    Heading(stringResource(R.string.onboarding_notif_title), stringResource(R.string.onboarding_notif_text))
    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().surface(RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(colors.accent.solid), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.onboarding_notif_app), fontSize = 12.sp, color = colors.muted, style = MaterialTheme.typography.bodySmall)
                val teacher = state.selection.mode == UserMode.TEACHER
                Text(
                    if (teacher) stringResource(R.string.onboarding_notif_sample_teacher_title)
                    else stringResource(R.string.onboarding_notif_sample_title, state.className.orEmpty()),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(if (teacher) R.string.onboarding_notif_sample_teacher_text else R.string.onboarding_notif_sample_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
            }
        }
    }
    Footer {
        QbitButton(
            stringResource(R.string.onboarding_notif_enable),
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.finish(true)
            },
            icon = Icons.Outlined.NotificationsActive,
            modifier = Modifier.fillMaxWidth(),
        )
        QbitButton(stringResource(R.string.onboarding_notif_later), { vm.finish(false) }, Modifier.fillMaxWidth(), variant = ButtonVariant.Ghost)
    }
}
