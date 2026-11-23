package dev.milkthe.qbitplan.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.ui.common.AppScaffold
import dev.milkthe.qbitplan.ui.common.DateChips
import dev.milkthe.qbitplan.ui.common.LessonCard
import dev.milkthe.qbitplan.ui.common.LoadingBox
import dev.milkthe.qbitplan.ui.common.SectionCard
import dev.milkthe.qbitplan.ui.theme.QbitTheme

@Composable
fun DetailScreen(onBack: () -> Unit, vm: DetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val selected by vm.selectedDate.collectAsStateWithLifecycle()

    AppScaffold(title = state.title, subtitle = state.subtitle, onBack = onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            DateChips(vm.dates, selected, vm.today, vm::select, Modifier.padding(vertical = 8.dp))
            if (state.loading) {
                LoadingBox()
                return@Column
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.lessons.isEmpty()) {
                    item(key = "empty") { Text(stringResource(R.string.plan_no_lessons), color = QbitTheme.colors.muted) }
                }
                items(state.lessons, key = { it.key }) { LessonCard(it) }
                if (state.info.isNotEmpty()) {
                    item(key = "info") {
                        val title = when (vm.kind) {
                            DetailKind.TEACHER -> R.string.detail_teacher_classes
                            DetailKind.CLASS -> R.string.detail_class_teachers
                            DetailKind.ROOM -> R.string.detail_room
                        }
                        SectionCard(stringResource(title), Modifier.padding(top = 8.dp)) {
                            state.info.forEach { line ->
                                Row {
                                    Text(line.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(140.dp))
                                    Text(line.value, style = MaterialTheme.typography.bodyMedium, color = QbitTheme.colors.muted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
