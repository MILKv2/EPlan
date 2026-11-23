package dev.milkthe.qbitplan.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.data.Profile
import dev.milkthe.qbitplan.data.prefs.ThemeMode
import dev.milkthe.qbitplan.ui.common.AppScaffold
import dev.milkthe.qbitplan.ui.common.SectionCard
import dev.milkthe.qbitplan.ui.common.text
import dev.milkthe.qbitplan.ui.onboarding.Division
import dev.milkthe.qbitplan.ui.onboarding.GroupPicker
import dev.milkthe.qbitplan.ui.theme.QbitTheme

@Composable
fun SettingsScreen(onAbout: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var groupsOpen by rememberSaveable { mutableStateOf(false) }

    AppScaffold(title = stringResource(R.string.tab_settings)) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "profile") {
                SectionCard(stringResource(R.string.settings_profile)) {
                    Text(profileText(state.profile), style = MaterialTheme.typography.bodyLarge)
                    val profile = state.profile
                    if (profile is Profile.Student && state.divisions.isNotEmpty()) {
                        val groups = state.prefs.groupNames.sorted().joinToString(", ").ifEmpty { stringResource(R.string.settings_groups_all) }
                        SettingRow(stringResource(R.string.settings_groups), groups) { groupsOpen = true }
                    }
                    OutlinedButton(onClick = vm::changeProfile, modifier = Modifier.padding(top = 4.dp)) {
                        Text(stringResource(R.string.settings_change_profile))
                    }
                }
            }
            item(key = "theme") {
                SectionCard(stringResource(R.string.settings_theme)) {
                    val options = listOf(
                        ThemeMode.SYSTEM to R.string.theme_system,
                        ThemeMode.LIGHT to R.string.theme_light,
                        ThemeMode.DARK to R.string.theme_dark,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        options.forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                selected = state.prefs.theme == mode,
                                onClick = { vm.setTheme(mode) },
                                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                            ) { Text(stringResource(label)) }
                        }
                    }
                }
            }
            item(key = "notifications") {
                SectionCard(stringResource(R.string.settings_notifications)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_notifications_toggle), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.settings_notifications_soon), style = MaterialTheme.typography.bodySmall, color = QbitTheme.colors.muted)
                        }
                        Switch(checked = state.prefs.notificationsEnabled, onCheckedChange = vm::setNotifications)
                    }
                }
            }
            item(key = "data") {
                SectionCard(stringResource(R.string.settings_data)) {
                    Text(
                        state.lastRefresh?.let { stringResource(R.string.settings_last_refresh, it.text()) }
                            ?: stringResource(R.string.settings_never_refreshed),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    state.timetableVersion?.let {
                        Text(listOfNotNull(stringResource(R.string.settings_timetable_version, it), state.validity).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = QbitTheme.colors.muted)
                    }
                    state.sync.error?.let {
                        Text(stringResource(R.string.settings_refresh_failed), style = MaterialTheme.typography.bodySmall, color = QbitTheme.colors.danger.content)
                    }
                    FilledTonalButton(onClick = vm::refresh, enabled = !state.sync.refreshing, modifier = Modifier.padding(top = 4.dp)) {
                        if (state.sync.refreshing) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.settings_refresh))
                    }
                }
            }
            item(key = "about") {
                SectionCard(stringResource(R.string.settings_about_section)) {
                    SettingRow(stringResource(R.string.settings_about), stringResource(R.string.about_unofficial_short), onClick = onAbout)
                }
            }
        }
    }

    if (groupsOpen) {
        GroupsDialog(state.divisions, state.prefs.groupNames, onDismiss = { groupsOpen = false }) {
            vm.setGroups(it)
            groupsOpen = false
        }
    }
}

@Composable
private fun profileText(profile: Profile?): String = when (profile) {
    is Profile.Student -> stringResource(R.string.settings_profile_student, profile.schoolClass.short)
    is Profile.TeacherProfile -> stringResource(R.string.settings_profile_teacher, listOfNotNull(profile.teacher.short, profile.fullName).joinToString(" · "))
    null -> stringResource(R.string.settings_profile_unknown)
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodySmall, color = QbitTheme.colors.muted)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = QbitTheme.colors.muted)
    }
}

@Composable
private fun GroupsDialog(divisions: List<Division>, current: Set<String>, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    val selection = remember {
        mutableStateMapOf<String, String?>().apply {
            divisions.forEach { d -> put(d.id, d.options.firstOrNull { it in current }) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_groups)) },
        text = { GroupPicker(divisions, selection, Modifier) },
        confirmButton = { TextButton(onClick = { onSave(selection.values.filterNotNull().toSet()) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
