@file:OptIn(ExperimentalMaterial3Api::class)

package dev.milkthe.qbitplan.ui.substitutions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.ui.common.AppScaffold
import dev.milkthe.qbitplan.ui.common.CenteredMessage
import dev.milkthe.qbitplan.ui.common.DateChips
import dev.milkthe.qbitplan.ui.common.InsteadOf
import dev.milkthe.qbitplan.ui.common.LoadingBox
import dev.milkthe.qbitplan.ui.common.OfflineBanner
import dev.milkthe.qbitplan.ui.common.SectionCard
import dev.milkthe.qbitplan.ui.common.StatusBadge
import dev.milkthe.qbitplan.ui.common.changeText
import dev.milkthe.qbitplan.ui.common.text
import dev.milkthe.qbitplan.ui.common.titleText
import dev.milkthe.qbitplan.ui.theme.QbitTheme
import dev.milkthe.qbitplan.ui.theme.tint

@Composable
fun SubstitutionsScreen(vm: SubstitutionsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val selected by vm.selectedDate.collectAsStateWithLifecycle()
    val query by vm.queryText.collectAsStateWithLifecycle()
    val sync by vm.syncState.collectAsStateWithLifecycle()
    val lastRefresh by vm.lastRefresh.collectAsStateWithLifecycle()

    AppScaffold(title = stringResource(R.string.tab_substitutions), subtitle = selected.titleText()) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OfflineBanner(sync, lastRefresh)
            DateChips(vm.dates, selected, vm.today, vm::select, Modifier.padding(vertical = 8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(R.string.substitutions_filter)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.clear)) }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(QbitTheme.radius.medium),
            )
            PullToRefreshBox(isRefreshing = sync.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading || (state.missing && sync.refreshing) -> LoadingBox()
                    state.missing -> CenteredMessage(
                        text = stringResource(R.string.substitutions_missing),
                        icon = Icons.Outlined.SwapHoriz,
                        action = stringResource(R.string.retry) to vm::refresh,
                    )
                    else -> SubstitutionsList(state)
                }
            }
        }
    }
}

@Composable
private fun SubstitutionsList(state: SubstitutionsState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        notes(R.string.substitutions_absent_teachers, state.absentTeachers, "teachers")
        notes(R.string.substitutions_unavailable_rooms, state.unavailableRooms, "rooms")
        notes(R.string.substitutions_absent_classes, state.absentClasses, "classes")
        state.message?.let { msg ->
            item(key = "message") { Text(msg, color = QbitTheme.colors.muted, style = MaterialTheme.typography.bodyMedium) }
        }
        if (state.pinned.isNotEmpty()) {
            item(key = "pinned-title") {
                Text(stringResource(R.string.substitutions_mine), style = MaterialTheme.typography.titleMedium, color = QbitTheme.colors.primary.accent)
            }
            sections(state.pinned, "pinned")
        }
        sections(state.sections, "all")
        state.fetchedAt?.let {
            item(key = "fetched") {
                Text(stringResource(R.string.data_fetched_at, it.text()), style = MaterialTheme.typography.bodySmall, color = QbitTheme.colors.muted)
            }
        }
    }
}

private fun LazyListScope.notes(title: Int, entries: List<String>, key: String) {
    if (entries.isEmpty()) return
    item(key = "note-$key") {
        SectionCard(stringResource(title)) {
            Text(entries.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun LazyListScope.sections(sections: List<ClassSection>, prefix: String) {
    sections.forEach { section ->
        item(key = "$prefix-h-${section.className}") {
            Text(section.className, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        }
        items(section.rows, key = { "$prefix-${it.key}" }) { SubstitutionCard(it) }
    }
}

@Composable
private fun SubstitutionCard(row: SubstitutionRow) {
    val colors = QbitTheme.colors
    val tint = row.status.tint()
    val shape = RoundedCornerShape(QbitTheme.radius.medium)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.content1)
            .border(1.dp, tint?.accent?.copy(alpha = 0.5f) ?: colors.divider, shape)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            row.periods ?: stringResource(R.string.whole_day),
            style = MaterialTheme.typography.titleMedium,
            color = tint?.content ?: colors.foreground,
            modifier = Modifier.width(48.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.subject.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                StatusBadge(row.status)
            }
            row.oldSubject?.let { InsteadOf(it) }
            val noTeacher = stringResource(R.string.lesson_no_teacher)
            val details = buildAnnotatedString {
                append(changeText(row.oldTeacher, row.teacher, noTeacher))
                if (row.room != null) {
                    append(" · ")
                    append(changeText(row.oldRoom, row.room, ""))
                }
            }
            if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            row.groups?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.muted) }
            row.movedTo?.let { Text(stringResource(R.string.lesson_moved_to, it), style = MaterialTheme.typography.bodySmall, color = tint?.content ?: colors.muted) }
            row.movedFrom?.let { Text(stringResource(R.string.lesson_moved_from, it), style = MaterialTheme.typography.bodySmall, color = tint?.content ?: colors.muted) }
            row.withoutClasses?.let { Text(stringResource(R.string.substitutions_without_classes, it), style = MaterialTheme.typography.bodySmall, color = colors.muted) }
        }
    }
}
