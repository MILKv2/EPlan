package dev.milkthe.qbitplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milkthe.qbitplan.data.PlanDayTracker
import dev.milkthe.qbitplan.data.SyncManager
import dev.milkthe.qbitplan.data.UpdateRepository
import dev.milkthe.qbitplan.data.prefs.ThemeMode
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AppState(val theme: ThemeMode, val onboarded: Boolean)

@HiltViewModel
class AppViewModel @Inject constructor(
    preferences: UserPreferencesRepository,
    private val sync: SyncManager,
    private val updates: UpdateRepository,
    private val planDays: PlanDayTracker,
) : ViewModel() {

    /** `null` until preferences are read, so the first frame doesn't flash onboarding. */
    val state: StateFlow<AppState?> = preferences.preferences
        .map { AppState(it.theme, it.isOnboarded) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // The start day moved (last lesson over, a new day): it may need another timetable version.
        planDays.days.filterNotNull().map { it.start }.distinctUntilChanged().drop(1)
            .onEach { sync.refreshIfStale() }
            .launchIn(viewModelScope)
    }

    fun onStart() {
        planDays.onForeground()
        sync.refreshIfStale()
        updates.checkIfDue()
    }
}
