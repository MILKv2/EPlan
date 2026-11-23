package dev.milkthe.qbitplan.data

import dev.milkthe.qbitplan.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class SyncState(val refreshing: Boolean = false, val error: Throwable? = null)

/** Runs refreshes in the application scope so leaving a screen doesn't cancel them. */
@Singleton
class SyncManager @Inject constructor(
    private val repository: SchoolRepository,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val running = MutableStateFlow(0)
    private val error = MutableStateFlow<Throwable?>(null)
    private val requestedDays = ConcurrentHashMap.newKeySet<LocalDate>()

    val state: StateFlow<SyncState> = combine(running, error) { count, e -> SyncState(count > 0, e) }
        .stateIn(scope, SharingStarted.Eagerly, SyncState())

    fun refresh() = launchTracked { repository.refresh() }

    fun refreshIfStale(maxAge: Duration = STALE_AFTER) {
        scope.launch {
            val last = repository.lastRefresh.first()
            if (last == null || last.plus(maxAge).isBefore(clock.instant())) refresh()
        }
    }

    /** Fetches a day once per process; days outside the default refresh are loaded on demand. */
    fun ensureSubstitutions(date: LocalDate) {
        if (requestedDays.add(date)) {
            launchTracked(onFailure = { requestedDays.remove(date) }) { repository.refreshSubstitutions(date) }
        }
    }

    fun refreshSubstitutions(date: LocalDate) {
        requestedDays.add(date)
        launchTracked { repository.refreshSubstitutions(date) }
    }

    private fun launchTracked(onFailure: () -> Unit = {}, block: suspend () -> RefreshResult) {
        scope.launch {
            running.update { it + 1 }
            try {
                when (val result = block()) {
                    RefreshResult.Success -> error.value = null
                    is RefreshResult.Failure -> {
                        error.value = result.error
                        onFailure()
                    }
                }
            } finally {
                running.update { it - 1 }
            }
        }
    }

    private companion object {
        val STALE_AFTER: Duration = Duration.ofMinutes(10)
    }
}
