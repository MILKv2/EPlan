@file:OptIn(ExperimentalLayoutApi::class)

package dev.milkthe.qbitplan.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.core.model.Timetable
import dev.milkthe.qbitplan.ui.theme.QbitTheme

/** Mutually exclusive groups of a class (one EduPage division). */
@Immutable
data class Division(val id: String, val options: List<String>)

fun divisionsOf(tt: Timetable, classId: String): List<Division> =
    tt.groupsOfClass(classId)
        .groupBy { it.division }
        .map { (division, groups) -> Division(division, groups.map { it.name }) }
        .filter { it.options.size > 1 }

/** One chip row per division; `null` in [selection] = "all" (don't filter). */
@Composable
fun GroupPicker(divisions: List<Division>, selection: MutableMap<String, String?>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        divisions.forEachIndexed { index, division ->
            if (index > 0) HorizontalDivider(color = QbitTheme.colors.divider)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selection[division.id] == null,
                    onClick = { selection[division.id] = null },
                    label = { Text(stringResource(R.string.groups_all)) },
                )
                division.options.forEach { name ->
                    FilterChip(
                        selected = selection[division.id] == name,
                        onClick = { selection[division.id] = name },
                        label = { Text(name) },
                    )
                }
            }
        }
    }
}
