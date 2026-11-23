@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.milkthe.qbitplan.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.ui.common.AppScaffold
import dev.milkthe.qbitplan.ui.common.CenteredMessage
import dev.milkthe.qbitplan.ui.common.LoadingBox
import dev.milkthe.qbitplan.ui.common.shortText
import dev.milkthe.qbitplan.ui.common.text
import dev.milkthe.qbitplan.ui.theme.QbitTheme
import java.time.LocalTime

@Composable
fun SearchScreen(onOpen: (DetailKind, String) -> Unit, vm: SearchViewModel = hiltViewModel()) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()

    AppScaffold(title = stringResource(R.string.tab_search)) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val tabs = listOf(
                SearchTab.TEACHERS to R.string.search_teachers,
                SearchTab.CLASSES to R.string.search_classes,
                SearchTab.ROOMS to R.string.search_rooms,
                SearchTab.FREE_ROOMS to R.string.search_free_rooms,
            )
            PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 16.dp, containerColor = QbitTheme.colors.background) {
                tabs.forEach { (t, label) ->
                    Tab(selected = tab == t, onClick = { vm.selectTab(t) }, text = { Text(stringResource(label), maxLines = 1) })
                }
            }
            if (tab == SearchTab.FREE_ROOMS) {
                FreeRooms(vm)
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.clear)) }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(QbitTheme.radius.medium),
                )
                val (entries, kind) = when (tab) {
                    SearchTab.TEACHERS -> results.teachers to DetailKind.TEACHER
                    SearchTab.CLASSES -> results.classes to DetailKind.CLASS
                    else -> results.rooms to DetailKind.ROOM
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(entries, key = { it.id }) { entry ->
                        ListItem(
                            supportingContent = entry.subtitle?.let { { Text(it, maxLines = 1) } },
                            colors = ListItemDefaults.colors(containerColor = QbitTheme.colors.background),
                            modifier = Modifier.clickable { onOpen(kind, entry.id) },
                        ) { Text(entry.title) }
                        HorizontalDivider(color = QbitTheme.colors.divider)
                    }
                }
            }
        }
    }
}

@Composable
private fun FreeRooms(vm: SearchViewModel) {
    val state by vm.freeRooms.collectAsStateWithLifecycle()
    var pickerOpen by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.time == null, onClick = { vm.pickTime(null) }, label = { Text(stringResource(R.string.free_rooms_now)) })
            FilterChip(
                selected = state.time != null,
                onClick = { pickerOpen = true },
                label = { Text(state.time?.let { stringResource(R.string.free_rooms_at, it.text()) } ?: stringResource(R.string.free_rooms_pick_time)) },
            )
        }
        when {
            state.loading -> LoadingBox()
            state.period == null -> CenteredMessage(stringResource(R.string.free_rooms_no_lesson))
            else -> {
                Text(
                    text = stringResource(if (state.isNext) R.string.free_rooms_next_period else R.string.free_rooms_period, state.period!!, vm.date.shortText()),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.rooms.isEmpty()) {
                    Text(stringResource(R.string.free_rooms_none), color = QbitTheme.colors.muted)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.rooms.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                    }
                }
            }
        }
    }

    if (pickerOpen) {
        val initial = state.time ?: LocalTime.now()
        val picker = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.pickTime(LocalTime.of(picker.hour, picker.minute))
                    pickerOpen = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickerOpen = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = picker) },
        )
    }
}
