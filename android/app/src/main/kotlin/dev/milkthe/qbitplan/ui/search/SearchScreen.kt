@file:OptIn(ExperimentalLayoutApi::class)

package dev.milkthe.qbitplan.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import dev.milkthe.qbitplan.ui.common.Chevron
import dev.milkthe.qbitplan.ui.common.Hairline
import dev.milkthe.qbitplan.ui.common.LoadingBox
import dev.milkthe.qbitplan.ui.common.SearchField
import dev.milkthe.qbitplan.ui.common.SectionLabel
import dev.milkthe.qbitplan.ui.common.SegmentedControl
import dev.milkthe.qbitplan.ui.common.surface
import dev.milkthe.qbitplan.ui.common.text
import dev.milkthe.qbitplan.ui.theme.QbitTheme

@Composable
fun SearchScreen(onOpen: (DetailKind, String) -> Unit, vm: SearchViewModel = hiltViewModel()) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.search_teacher),
                    stringResource(R.string.search_class),
                    stringResource(R.string.search_room),
                    stringResource(R.string.search_free),
                ),
                selected = tab.ordinal,
                onSelect = { vm.selectTab(SearchTab.entries[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (tab != SearchTab.FREE_ROOMS) {
                val hint = when (tab) {
                    SearchTab.TEACHERS -> R.string.search_hint_teacher
                    SearchTab.CLASSES -> R.string.search_hint_class
                    else -> R.string.search_hint_room
                }
                SearchField(query, vm::setQuery, stringResource(hint))
            }
        }
        when (tab) {
            SearchTab.TEACHERS -> TeacherList(results.teachers) { onOpen(DetailKind.TEACHER, it) }
            SearchTab.CLASSES -> EntryGrid(results.classes) { onOpen(DetailKind.CLASS, it) }
            SearchTab.ROOMS -> EntryGrid(results.rooms) { onOpen(DetailKind.ROOM, it) }
            SearchTab.FREE_ROOMS -> FreeRooms(vm)
        }
    }
}

@Composable
private fun TeacherList(entries: List<SearchEntry>, onOpen: (String) -> Unit) {
    if (entries.isEmpty()) return NothingFound()
    val colors = QbitTheme.colors
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(entries, key = { it.id }) { entry ->
            Hairline()
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable(role = Role.Button) { onOpen(entry.id) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(colors.accent.soft), contentAlignment = Alignment.Center) {
                    Text(entry.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.accent.softForeground, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                }
                Column(Modifier.weight(1f)) {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium)
                    entry.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Chevron()
            }
        }
    }
}

@Composable
private fun EntryGrid(entries: List<SearchEntry>, onOpen: (String) -> Unit) {
    if (entries.isEmpty()) return NothingFound()
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries, key = { it.id }) { entry -> GridTile(entry.title, entry.highlighted) { onOpen(entry.id) } }
    }
}

/** 48dp tile used for class / room / teacher-short grids. */
@Composable
fun GridTile(text: String, highlighted: Boolean, selected: Boolean = false, subtitle: String? = null, onClick: () -> Unit) {
    val colors = QbitTheme.colors
    val shape = RoundedCornerShape(QbitTheme.radius.tile)
    val bg = when {
        selected -> colors.accent.solid
        highlighted -> colors.accent.soft
        else -> colors.surface
    }
    val fg = when {
        selected -> colors.onAccent
        highlighted -> colors.accent.softForeground
        else -> colors.foreground
    }
    val base = Modifier.fillMaxWidth().height(48.dp)
    Column(
        modifier = (if (selected || highlighted) base.clip(shape).background(bg) else base.surface(shape, bg))
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
        subtitle?.let { Text(it, fontSize = 10.sp, lineHeight = 12.sp, color = if (selected) colors.onAccent else colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun NothingFound() {
    Text(
        stringResource(R.string.search_nothing),
        style = MaterialTheme.typography.bodyMedium,
        color = QbitTheme.colors.muted,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun FreeRooms(vm: SearchViewModel) {
    val state by vm.freeRooms.collectAsStateWithLifecycle()
    val colors = QbitTheme.colors
    if (state.loading) return LoadingBox()
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "mode") {
            SegmentedControl(
                options = listOf(stringResource(R.string.free_now), stringResource(R.string.free_picked)),
                selected = if (state.pickedLesson) 1 else 0,
                onSelect = { vm.pickLesson(if (it == 0) null else state.period) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "lessons") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.lessons, key = { it }) { n ->
                    val on = n == state.period
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(QbitTheme.radius.tile))
                            .background(if (on) colors.accent.solid else Color.Transparent)
                            .then(if (on) Modifier else Modifier.border(1.dp, colors.border, RoundedCornerShape(QbitTheme.radius.tile)))
                            .clickable(role = Role.Button) { vm.pickLesson(n) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$n", style = MaterialTheme.typography.labelLarge, color = if (on) colors.onAccent else colors.foreground)
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "info") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = colors.muted, modifier = Modifier.size(18.dp))
                val time = listOfNotNull(state.start?.text(), state.end?.text()).joinToString("–")
                val info = stringResource(R.string.free_info, state.period ?: 0, time) + if (state.pickedLesson) "" else " · " + stringResource(R.string.free_now_suffix)
                Text(info, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "free-title") {
            SectionLabel(stringResource(R.string.free_count, state.free.size))
        }
        items(state.free, key = { "f-" + it.room }) { tile ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(QbitTheme.radius.tile))
                    .background(colors.success.solid.copy(alpha = 0.16f))
                    .border(1.dp, colors.success.solid.copy(alpha = 0.45f), RoundedCornerShape(QbitTheme.radius.tile)),
                contentAlignment = Alignment.Center,
            ) { Text(tile.room, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp)) }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "busy-title") {
            SectionLabel(stringResource(R.string.busy_count, state.busy.size), Modifier.padding(top = 4.dp))
        }
        items(state.busy, key = { "b-" + it.room }) { tile ->
            Column(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(QbitTheme.radius.tile)).background(colors.surfaceSecondary),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(tile.room, style = MaterialTheme.typography.titleSmall, color = colors.muted, textDecoration = if (tile.unavailable) TextDecoration.LineThrough else null, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (tile.unavailable) stringResource(R.string.room_unavailable) else tile.usedBy.orEmpty(),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = if (tile.unavailable) colors.danger.softForeground else colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "end") { Spacer(Modifier.height(8.dp)) }
    }
}
