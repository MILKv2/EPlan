package dev.milkthe.qbitplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milkthe.qbitplan.data.SyncManager
import dev.milkthe.qbitplan.data.prefs.ThemeMode
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AppState(val theme: ThemeMode, val onboarded: Boolean)

@HiltViewModel
class AppViewModel @Inject constructor(
    preferences: UserPreferencesRepository,
    private val sync: SyncManager,
) : ViewModel() {

    /** `null` until preferences are read, so the first frame doesn't flash onboarding. */
    val state: StateFlow<AppState?> = preferences.preferences
        .map { AppState(it.theme, it.isOnboarded) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onStart() = sync.refreshIfStale()
}
