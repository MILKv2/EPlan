package dev.milkthe.qbitplan.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milkthe.qbitplan.data.Profile
import dev.milkthe.qbitplan.data.SchoolRepository
import dev.milkthe.qbitplan.data.SyncManager
import dev.milkthe.qbitplan.data.SyncState
import dev.milkthe.qbitplan.data.prefs.ThemeMode
import dev.milkthe.qbitplan.data.prefs.UserPreferences
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import dev.milkthe.qbitplan.data.resolve
import dev.milkthe.qbitplan.ui.onboarding.Division
import dev.milkthe.qbitplan.ui.onboarding.divisionsOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@Immutable
data class SettingsState(
    val prefs: UserPreferences = UserPreferences(),
    val profile: Profile? = null,
    val divisions: List<Division> = emptyList(),
    val timetableVersion: String? = null,
    val validity: String? = null,
    val lastRefresh: Instant? = null,
    val sync: SyncState = SyncState(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    repository: SchoolRepository,
    private val preferences: UserPreferencesRepository,
    private val sync: SyncManager,
) : ViewModel() {

    val state: StateFlow<SettingsState> = combine(
        preferences.preferences, repository.timetable, repository.lastRefresh, sync.state,
    ) { prefs, tt, last, s ->
        val profile = tt?.let { prefs.resolve(it.value) }
        SettingsState(
            prefs = prefs,
            profile = profile,
            divisions = if (profile is Profile.Student) divisionsOf(tt.value, profile.schoolClass.id) else emptyList(),
            timetableVersion = tt?.value?.ttNum,
            validity = tt?.value?.validityText?.ifBlank { null },
            lastRefresh = last,
            sync = s,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun setTheme(theme: ThemeMode) = viewModelScope.launch { preferences.setTheme(theme) }
    fun setNotifications(enabled: Boolean) = viewModelScope.launch { preferences.setNotificationsEnabled(enabled) }
    fun setGroups(names: Set<String>) = viewModelScope.launch { preferences.setGroups(names) }
    fun changeProfile() = viewModelScope.launch { preferences.clearMode() }
    fun refresh() = sync.refresh()
}
