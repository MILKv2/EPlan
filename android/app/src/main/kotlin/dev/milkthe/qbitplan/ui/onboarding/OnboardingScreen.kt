@file:OptIn(ExperimentalLayoutApi::class)

package dev.milkthe.qbitplan.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.data.prefs.UserMode
import dev.milkthe.qbitplan.ui.common.CenteredMessage
import dev.milkthe.qbitplan.ui.common.LoadingBox
import dev.milkthe.qbitplan.ui.theme.QbitTheme

@Composable
fun OnboardingScreen(vm: OnboardingViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sel = state.selection
    BackHandler(enabled = sel.step != OnboardingStep.MODE) { vm.back() }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp)) {
        when {
            sel.step == OnboardingStep.MODE -> ModeStep(vm::chooseMode, Modifier.weight(1f))
            !state.hasTimetable && !state.failed -> LoadingBox(Modifier.weight(1f))
            !state.hasTimetable -> CenteredMessage(
                text = stringResource(R.string.onboarding_no_data),
                icon = Icons.Outlined.CloudOff,
                action = stringResource(R.string.retry) to vm::retry,
                modifier = Modifier.weight(1f),
            )
            else -> {
                Column(Modifier.weight(1f)) {
                    when (sel.step) {
                        OnboardingStep.CLASS -> ClassStep(state, vm::chooseClass)
                        OnboardingStep.GROUPS -> GroupsStep(state, vm::setGroups)
                        OnboardingStep.TEACHER -> TeacherStep(state, vm::chooseTeacher, vm::setTeacherQuery)
                        OnboardingStep.TEACHER_NAME -> NameStep(state, vm::setFullName)
                        OnboardingStep.MODE -> Unit
                    }
                }
                val canContinue = when (sel.step) {
                    OnboardingStep.CLASS -> sel.classId != null
                    OnboardingStep.TEACHER -> sel.teacherId != null
                    OnboardingStep.TEACHER_NAME -> sel.fullName.isNotBlank()
                    else -> true
                }
                val finishing = sel.step == OnboardingStep.GROUPS || sel.step == OnboardingStep.TEACHER_NAME
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { vm.back() }) { Text(stringResource(R.string.back)) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = vm::next, enabled = canContinue) {
                        Text(stringResource(if (finishing) R.string.onboarding_finish else R.string.next))
                    }
                }
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    subtitle?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, style = MaterialTheme.typography.bodyMedium, color = QbitTheme.colors.muted)
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ModeStep(onChoose: (UserMode) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.onboarding_welcome), color = QbitTheme.colors.muted)
        Spacer(Modifier.height(24.dp))
        ModeCard(Icons.Outlined.School, stringResource(R.string.mode_student), stringResource(R.string.mode_student_desc)) { onChoose(UserMode.STUDENT) }
        Spacer(Modifier.height(12.dp))
        ModeCard(Icons.Outlined.Person, stringResource(R.string.mode_teacher), stringResource(R.string.mode_teacher_desc)) { onChoose(UserMode.TEACHER) }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.about_unofficial_short), style = MaterialTheme.typography.bodySmall, color = QbitTheme.colors.muted)
    }
}

@Composable
private fun ModeCard(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(QbitTheme.radius.large)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(QbitTheme.colors.content1)
            .border(1.dp, QbitTheme.colors.divider, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = QbitTheme.colors.primary.accent, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = QbitTheme.colors.muted)
        }
    }
}

@Composable
private fun ClassStep(state: OnboardingState, onChoose: (String) -> Unit) {
    StepTitle(stringResource(R.string.onboarding_class_title))
    FlowRow(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.classes.forEach { option ->
            FilterChip(
                selected = state.selection.classId == option.id,
                onClick = { onChoose(option.id) },
                label = { Text(option.title) },
            )
        }
    }
}

@Composable
private fun GroupsStep(state: OnboardingState, onChange: (Map<String, String?>) -> Unit) {
    StepTitle(stringResource(R.string.onboarding_groups_title), stringResource(R.string.onboarding_groups_desc))
    val selection = remember(state.divisions) {
        mutableStateMapOf<String, String?>().apply { state.divisions.forEach { put(it.id, state.selection.groups[it.id]) } }
    }
    LaunchedEffect(selection) { snapshotFlow { selection.toMap() }.collect(onChange) }
    GroupPicker(state.divisions, selection, Modifier.verticalScroll(rememberScrollState()))
}

@Composable
private fun TeacherStep(state: OnboardingState, onChoose: (String) -> Unit, onQuery: (String) -> Unit) {
    StepTitle(stringResource(R.string.onboarding_teacher_title), stringResource(R.string.onboarding_teacher_desc))
    OutlinedTextField(
        value = state.selection.teacherQuery,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(QbitTheme.radius.medium),
    )
    LazyColumn(Modifier.fillMaxWidth()) {
        items(state.teachers, key = { it.id }) { option ->
            ListItem(
                supportingContent = option.subtitle?.let { { Text(it, maxLines = 1) } },
                leadingContent = { RadioButton(selected = state.selection.teacherId == option.id, onClick = null) },
                colors = ListItemDefaults.colors(containerColor = QbitTheme.colors.background),
                modifier = Modifier.clickable { onChoose(option.id) },
            ) { Text(option.title) }
            HorizontalDivider(color = QbitTheme.colors.divider)
        }
    }
}

@Composable
private fun NameStep(state: OnboardingState, onName: (String) -> Unit) {
    StepTitle(stringResource(R.string.onboarding_name_title), stringResource(R.string.onboarding_name_desc))
    OutlinedTextField(
        value = state.selection.fullName,
        onValueChange = onName,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.onboarding_name_label)) },
        singleLine = true,
        shape = RoundedCornerShape(QbitTheme.radius.medium),
    )
    Spacer(Modifier.height(8.dp))
    if (state.refreshing && state.names.isEmpty()) {
        LoadingBox(Modifier.height(80.dp))
    }
    LazyColumn(Modifier.fillMaxWidth()) {
        items(state.names, key = { it }) { name ->
            ListItem(
                leadingContent = { RadioButton(selected = state.selection.fullName == name, onClick = null) },
                colors = ListItemDefaults.colors(containerColor = QbitTheme.colors.background),
                modifier = Modifier.clickable { onName(name) },
            ) { Text(name) }
        }
    }
}
