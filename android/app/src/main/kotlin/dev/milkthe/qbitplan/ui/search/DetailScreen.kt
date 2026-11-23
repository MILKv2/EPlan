package dev.milkthe.qbitplan.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.ui.common.BackHeader
import dev.milkthe.qbitplan.ui.common.DaySelector
import dev.milkthe.qbitplan.ui.common.Hairline
import dev.milkthe.qbitplan.ui.common.LoadingBox
import dev.milkthe.qbitplan.ui.common.StateMessage
import dev.milkthe.qbitplan.ui.common.Timeline
import dev.milkthe.qbitplan.ui.theme.QbitTheme

@Composable
fun DetailScreen(onBack: () -> Unit, onOpen: (DetailKind, String) -> Unit, vm: DetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val selected by vm.selectedIndex.collectAsStateWithLifecycle()

    val title = when (vm.kind) {
        DetailKind.TEACHER -> state.fullName ?: state.title
        DetailKind.CLASS -> stringResource(R.string.detail_class_title, state.title)
        DetailKind.ROOM -> stringResource(R.string.detail_room_title, state.title)
    }
    val subtitle = when (vm.kind) {
        DetailKind.TEACHER -> stringResource(R.string.detail_teacher_sub) + (state.fullName?.let { " · ${state.title}" } ?: "")
        DetailKind.CLASS -> stringResource(R.string.detail_class_sub)
        DetailKind.ROOM -> state.roomNow?.let { stringResource(R.string.detail_room_busy, it.classes, it.subject) } ?: stringResource(R.string.detail_room_free)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackHeader(title, onBack, subtitle = subtitle)
        if (state.related.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                items(state.related, key = { it.kind.name + it.id }) { rel ->
                    Text(
                        rel.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = QbitTheme.colors.foreground,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(QbitTheme.colors.default)
                            .clickable(role = Role.Button) { onOpen(rel.kind, rel.id) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        DaySelector(vm.chips, selected.toFloat(), vm::select)
        Spacer(Modifier.height(8.dp))
        Hairline()
        when {
            state.loading -> LoadingBox()
            state.lessons.isEmpty() -> StateMessage(
                icon = Icons.Outlined.WbSunny,
                tone = QbitTheme.colors.accent,
                title = stringResource(R.string.plan_no_lessons_title),
                text = stringResource(R.string.detail_no_lessons),
            )
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                Timeline(state.lessons, isToday = vm.dates[selected] == vm.today)
            }
        }
    }
}
