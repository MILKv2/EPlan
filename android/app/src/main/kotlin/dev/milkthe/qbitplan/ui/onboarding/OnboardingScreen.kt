@file:OptIn(ExperimentalLayoutApi::class)

package dev.milkthe.qbitplan.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.data.prefs.UserMode
import dev.milkthe.qbitplan.ui.common.AppIcon
import dev.milkthe.qbitplan.ui.common.ButtonVariant
import dev.milkthe.qbitplan.ui.common.Hairline
import dev.milkthe.qbitplan.ui.common.LoadingBox
import dev.milkthe.qbitplan.ui.common.QbitButton
import dev.milkthe.qbitplan.ui.common.QbitTextField
import dev.milkthe.qbitplan.ui.common.RadioDot
import dev.milkthe.qbitplan.ui.common.RoundIconButton
import dev.milkthe.qbitplan.ui.common.SearchField
import dev.milkthe.qbitplan.ui.common.SectionLabel
import dev.milkthe.qbitplan.ui.common.SegmentedControl
import dev.milkthe.qbitplan.ui.common.StateMessage
import dev.milkthe.qbitplan.ui.common.surface
import dev.milkthe.qbitplan.ui.search.GridTile
import dev.milkthe.qbitplan.ui.theme.QbitTheme
import kotlinx.coroutines.CancellationException

/** Visual order of the steps; drives the slide direction. */
private val OnboardingStep.order: Int
    get() = when (this) {
        OnboardingStep.ROLE -> 0
        OnboardingStep.CLASS, OnboardingStep.TEACHER -> 1
        OnboardingStep.GROUPS, OnboardingStep.NAME -> 2
        OnboardingStep.NOTIFICATIONS -> 3
    }

/**
 * [onClose]: leave the profile editor (saved, or back from its first step). The first-run
 * onboarding never closes itself; the app switches to the plan once the profile is saved.
 */
@Composable
fun OnboardingScreen(vm: OnboardingViewModel = hiltViewModel(), onClose: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Checked on each composition so a permission granted in system settings is picked up;
    // passed on while the profile is still loading so the step counter doesn't jump.
    val notificationsAllowed = NotificationManagerCompat.from(LocalContext.current).areNotificationsEnabled()
    LaunchedEffect(notificationsAllowed) { vm.setNotificationsAllowed(notificationsAllowed) }
    LaunchedEffect(state.finished) { if (state.finished) onClose() }
    val sel = state.selection ?: return

    val steps = remember { SeekableTransitionState(sel.step) }
    LaunchedEffect(sel.step) { steps.animateTo(sel.step) }
    val previous = state.previousStep
    PredictiveBackHandler(enabled = previous != null) { events ->
        val target = previous ?: return@PredictiveBackHandler
        try {
            events.collect { steps.seekTo(it.progress, targetState = target) }
            vm.back()
        } catch (_: CancellationException) {
            steps.animateTo(sel.step)
        }
    }
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        ProgressBar(
            current = state.stepNumber,
            total = state.steps.size,
            showBack = previous != null || sel.editing,
            onBack = { if (!vm.back()) onClose() },
        )
        rememberTransition(steps, label = "onboardingStep").AnimatedContent(
            transitionSpec = {
                val forward = targetState.order > initialState.order
                val spec = tween<IntOffset>(280, easing = FastOutSlowInEasing)
                (slideInHorizontally(spec) { w -> if (forward) w / 3 else -w / 3 } + fadeIn(tween(200, delayMillis = 60)))
                    .togetherWith(slideOutHorizontally(spec) { w -> if (forward) -w / 3 else w / 3 } + fadeOut(tween(120)))
            },
            modifier = Modifier.weight(1f),
        ) { step ->
            Column(Modifier.fillMaxSize()) {
                val next = {
                    focus.clearFocus()
                    vm.next()
                }
                when {
                    step == OnboardingStep.ROLE -> RoleStep(sel.mode, vm::chooseMode, next)
                    !state.hasTimetable && !state.failed -> LoadingBox()
                    !state.hasTimetable -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        StateMessage(Icons.Outlined.CloudOff, QbitTheme.colors.danger, stringResource(R.string.error_title), stringResource(R.string.error_text)) {
                            QbitButton(stringResource(R.string.retry), vm::retry)
                        }
                    }
                    step == OnboardingStep.CLASS -> ClassStep(state, sel, vm, next)
                    step == OnboardingStep.GROUPS -> GroupsStep(state, sel, vm, next)
                    step == OnboardingStep.TEACHER -> TeacherStep(state, sel, vm, next)
                    step == OnboardingStep.NAME -> NameStep(state, sel, vm, next)
                    step == OnboardingStep.NOTIFICATIONS -> NotificationsStep(state, sel, vm)
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(current: Int, total: Int, showBack: Boolean, onBack: () -> Unit) {
    val colors = QbitTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showBack) RoundIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), onBack) else Spacer(Modifier.size(48.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..total).forEach { i ->
                key(i) {
                    val fill by animateColorAsState(if (i <= current) colors.accent.solid else colors.default, label = "progress")
                    Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(fill))
                }
            }
        }
        Text(stringResource(R.string.onboarding_step, current, total), fontSize = 13.sp, color = colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(40.dp))
    }
}

/** Primary button of a step: "Dalej", or "Zapisz" on the last step of the profile editor. */
@Composable
private fun nextLabel(state: OnboardingState, sel: Selection, chosen: String?): String = when {
    sel.editing && state.isLastStep -> stringResource(R.string.save)
    chosen != null -> stringResource(R.string.next_with, chosen)
    else -> stringResource(R.string.next)
}

/** The description folds away while the keyboard is up so lists keep their room. */
@Composable
private fun Heading(title: String, text: String) {
    val keyboard = WindowInsets.isImeVisible
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = if (keyboard) 4.dp else 16.dp, bottom = if (keyboard) 12.dp else 20.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        AnimatedVisibility(!keyboard) {
            Text(text, fontSize = 15.sp, lineHeight = 22.sp, color = QbitTheme.colors.muted, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ColumnScope.Footer(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

/** For search steps: while typing the list gets the room; picking a tile closes the keyboard and brings the button back. */
@Composable
private fun ColumnScope.KeyboardHiddenFooter(content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(!WindowInsets.isImeVisible, enter = expandVertically(), exit = shrinkVertically()) {
        Footer(content)
    }
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
private fun ColumnScope.ClassStep(state: OnboardingState, sel: Selection, vm: OnboardingViewModel, onNext: () -> Unit) {
    val focus = LocalFocusManager.current
    Heading(stringResource(R.string.onboarding_class_title), stringResource(R.string.onboarding_class_text, state.classCount))
    SearchField(sel.classQuery, vm::setClassQuery, stringResource(R.string.onboarding_class_search), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.classes, key = { it.id }) { option ->
            GridTile(option.title, highlighted = false, selected = option.id == sel.classId) {
                focus.clearFocus()
                vm.chooseClass(option.id)
            }
        }
    }
    KeyboardHiddenFooter {
        QbitButton(
            text = nextLabel(state, sel, state.className),
            onClick = onNext,
            enabled = sel.classId != null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ColumnScope.GroupsStep(state: OnboardingState, sel: Selection, vm: OnboardingViewModel, onNext: () -> Unit) {
    Heading(stringResource(R.string.onboarding_groups_title), stringResource(R.string.onboarding_groups_text))
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        state.divisions.forEach { division ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(division.label?.let { stringResource(it) } ?: division.options.joinToString(" / "))
                val options = division.options + stringResource(if (division.optional) R.string.groups_none else R.string.groups_all)
                val current = sel.pickIn(division)
                SegmentedControl(
                    options = options,
                    selected = current?.let { division.options.indexOf(it) } ?: division.options.size,
                    onSelect = {
                        val name = division.options.getOrNull(it)
                        if (name == null && division.optional) vm.skipDivision(division.id) else vm.setGroup(division.id, name)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    Footer { QbitButton(nextLabel(state, sel, chosen = null), onNext, Modifier.fillMaxWidth()) }
}

@Composable
private fun ColumnScope.TeacherStep(state: OnboardingState, sel: Selection, vm: OnboardingViewModel, onNext: () -> Unit) {
    val focus = LocalFocusManager.current
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
            GridTile(option.title, highlighted = false, selected = option.id == sel.teacherId, subtitle = option.subtitle) {
                focus.clearFocus()
                vm.chooseTeacher(option.id)
            }
        }
    }
    KeyboardHiddenFooter {
        QbitButton(
            text = nextLabel(state, sel, state.teacherShort),
            onClick = onNext,
            enabled = sel.teacherId != null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ColumnScope.NameStep(state: OnboardingState, sel: Selection, vm: OnboardingViewModel, onNext: () -> Unit) {
    val colors = QbitTheme.colors
    Heading(stringResource(R.string.onboarding_name_title), stringResource(R.string.onboarding_name_text, state.teacherShort.orEmpty()))
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        Column(Modifier.fillMaxWidth().surface(RoundedCornerShape(QbitTheme.radius.card))) {
            state.suggestedNames.forEachIndexed { i, name ->
                if (i > 0) Hairline()
                NameOption(name, stringResource(R.string.onboarding_name_matches, state.teacherShort.orEmpty()), sel.pickedName == name) { vm.pickName(name) }
            }
            if (state.suggestedNames.isNotEmpty()) Hairline()
            NameOption(stringResource(R.string.onboarding_name_manual), stringResource(R.string.onboarding_name_manual_desc), sel.pickedName == null) { vm.pickName(null) }
        }
    }
    Footer {
        // Kept above the button rather than in the list so the keyboard never covers it.
        if (sel.pickedName == null) {
            Box(
                Modifier.fillMaxWidth().padding(bottom = 6.dp).height(48.dp).surface(RoundedCornerShape(QbitTheme.radius.field)).padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (sel.typedName.isEmpty()) Text(stringResource(R.string.onboarding_name_hint), style = MaterialTheme.typography.bodyLarge, color = colors.muted)
                QbitTextField(
                    value = sel.typedName,
                    onValueChange = vm::setTypedName,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        QbitButton(
            text = nextLabel(state, sel, chosen = null),
            onClick = onNext,
            enabled = sel.fullName.isNotBlank(),
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
private fun ColumnScope.NotificationsStep(state: OnboardingState, sel: Selection, vm: OnboardingViewModel) {
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
            AppIcon(size = 36.dp, cornerRadius = 12.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.onboarding_notif_app), fontSize = 12.sp, color = colors.muted, style = MaterialTheme.typography.bodySmall)
                val teacher = sel.mode == UserMode.TEACHER
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
