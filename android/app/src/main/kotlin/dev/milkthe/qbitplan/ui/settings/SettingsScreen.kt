package dev.milkthe.qbitplan.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.data.Profile
import dev.milkthe.qbitplan.data.prefs.ThemeMode
import dev.milkthe.qbitplan.ui.common.Chevron
import dev.milkthe.qbitplan.ui.common.RadioRow
import dev.milkthe.qbitplan.ui.common.SettingsHeader
import dev.milkthe.qbitplan.ui.common.SettingsRow
import dev.milkthe.qbitplan.ui.common.SwitchRow
import dev.milkthe.qbitplan.ui.common.text
import dev.milkthe.qbitplan.ui.theme.QbitTheme
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun SettingsScreen(onAbout: () -> Unit, onEditProfile: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = QbitTheme.colors
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 16.dp),
    ) {
        SettingsHeader(stringResource(R.string.settings_profile))
        SettingsRow(
            title = stringResource(if (state.profile is Profile.TeacherProfile) R.string.mode_teacher else R.string.mode_student),
            subtitle = profileText(state.profile),
            onClick = onEditProfile,
        ) {
            Text(stringResource(R.string.settings_change), style = MaterialTheme.typography.bodyMedium, color = colors.accent.softForeground, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        }
        if (state.profile is Profile.Student) {
            SwitchRow(stringResource(R.string.settings_only_my_groups), stringResource(R.string.settings_only_my_groups_desc), state.prefs.onlyMyGroups, { vm.setOnlyMyGroups(it) })
        }

        SettingsHeader(stringResource(R.string.settings_theme))
        listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark).forEach { (mode, label) ->
            RadioRow(stringResource(label), state.prefs.theme == mode, { vm.setTheme(mode) })
        }

        SettingsHeader(stringResource(R.string.settings_notifications))
        SwitchRow(stringResource(R.string.settings_notifications_toggle), stringResource(R.string.settings_notifications_desc), state.prefs.notificationsEnabled, { vm.setNotifications(it) })
        SwitchRow(stringResource(R.string.settings_quiet), stringResource(R.string.settings_quiet_desc), state.prefs.quietWeekends, { vm.setQuietWeekends(it) }, enabled = state.prefs.notificationsEnabled)

        SettingsHeader(stringResource(R.string.settings_data))
        val last = state.lastRefresh?.let {
            val local = it.atZone(ZoneId.systemDefault())
            if (local.toLocalDate() == LocalDate.now()) stringResource(R.string.settings_today_at, local.toLocalTime().text()) else it.text()
        } ?: stringResource(R.string.settings_never_refreshed)
        SettingsRow(stringResource(R.string.settings_last_sync), stringResource(R.string.settings_last_sync_value, last)) {
            Row(
                Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(colors.default)
                    .clickable(enabled = !state.sync.refreshing, role = Role.Button, onClick = vm::refresh)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (state.sync.refreshing) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.foreground)
                } else {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Text(stringResource(R.string.refresh), style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
        }
        if (state.sync.error != null && !state.sync.refreshing) {
            Text(
                stringResource(R.string.settings_refresh_failed),
                style = MaterialTheme.typography.bodySmall,
                color = colors.danger.softForeground,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        SettingsHeader(stringResource(R.string.settings_info))
        SettingsRow(stringResource(R.string.settings_about), stringResource(R.string.settings_about_desc), onClick = onAbout) { Chevron() }
    }
}

@Composable
private fun profileText(profile: Profile?): String = when (profile) {
    is Profile.Student -> listOf(profile.schoolClass.short, profile.groupNames.sorted().joinToString(", ").ifEmpty { stringResource(R.string.plan_whole_class) }).joinToString(" · ")
    is Profile.TeacherProfile -> listOfNotNull(profile.teacher.short, profile.fullName).joinToString(" · ")
    null -> stringResource(R.string.settings_profile_unknown)
}
