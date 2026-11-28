package dev.milkthe.qbitplan.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milkthe.qbitplan.core.model.VersionPin
import dev.milkthe.qbitplan.core.model.isPinValid
import dev.milkthe.qbitplan.core.model.newest
import dev.milkthe.qbitplan.core.model.published
import dev.milkthe.qbitplan.core.model.validOn
import dev.milkthe.qbitplan.data.PlanDayTracker
import dev.milkthe.qbitplan.data.SchoolRepository
import dev.milkthe.qbitplan.data.SyncManager
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@Immutable
data class VersionOption(val ttNum: String, val name: String, val validFrom: LocalDate)

@Immutable
data class VersionState(
    val loading: Boolean = true,
    /** What automatic mode picks for the day the plan opens on. */
    val automaticName: String? = null,
    /** The pinned version, `null` = automatic. */
    val pinned: String? = null,
    val options: List<VersionOption> = emptyList(),
)

@HiltViewModel
class VersionViewModel @Inject constructor(
    private val repository: SchoolRepository,
    private val preferences: UserPreferencesRepository,
    private val sync: SyncManager,
    tracker: PlanDayTracker,
) : ViewModel() {

    val state: StateFlow<VersionState> = combine(repository.versions, preferences.preferences, tracker.days) { current, prefs, days ->
        if (current == null) return@combine VersionState(loading = false)
        val (versions, year) = current
        val automatic = days?.let { versions.validOn(it.start, year) } ?: versions.default
        VersionState(
            loading = false,
            automaticName = automatic?.shortName,
            pinned = prefs.versionPin?.takeIf { versions.isPinValid(it, year) }?.ttNum,
            options = versions.published(year).map { VersionOption(it.ttNum, it.shortName, it.validFrom) },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VersionState())

    fun chooseAutomatic() = viewModelScope.launch {
        preferences.setVersionPin(null)
        sync.refresh()
    }

    fun choose(ttNum: String) = viewModelScope.launch {
        val (versions, year) = repository.versions.first() ?: return@launch
        preferences.setVersionPin(VersionPin(ttNum, versions.newest(year)?.ttNum))
        sync.refresh()
    }
}
