package dev.milkthe.qbitplan.data

import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import dev.milkthe.qbitplan.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** [resumed]: worked out because the app came back to the foreground, so a screen left on an old day may move on. */
data class PlanDays(val today: LocalDate, val start: LocalDate, val resumed: Boolean = false)

/**
 * Today and the day the plan opens on, kept current for as long as the process lives: worked out
 * again when the app comes to the foreground, when the last lesson ends, at midnight, and when the
 * timetable or the profile changes. Screens take their days from here instead of reading the clock once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PlanDayTracker @Inject constructor(
    private val startDay: StartDay,
    repository: SchoolRepository,
    preferences: UserPreferencesRepository,
    private val clock: Clock,
    @ApplicationScope scope: CoroutineScope,
) {
    private val resumes = MutableStateFlow(0)
    private var seenResumes = 0

    val days: StateFlow<PlanDays?> = combine(
        resumes,
        repository.timetable.map { it?.fetchedAt }.distinctUntilChanged(),
        preferences.preferences.map { listOf(it.mode, it.classId, it.groupNames, it.skippedGroupNames, it.teacherId, it.onlyMyGroups) }.distinctUntilChanged(),
    ) { resumeCount, _, _ -> resumeCount }
        .transformLatest { resumeCount ->
            var resumed = resumeCount != seenResumes
            seenResumes = resumeCount
            while (true) {
                val info = startDay.info()
                emit(PlanDays(info.today, info.start, resumed))
                resumed = false
                // A timer, not a ticker: one wake-up per lesson end or midnight; the cap catches clock changes.
                val wait = Duration.between(LocalDateTime.now(clock), info.nextChange).toMillis() + MARGIN_MS
                delay(wait.coerceIn(MARGIN_MS, MAX_WAIT_MS))
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun onForeground() = resumes.update { it + 1 }

    private companion object {
        const val MARGIN_MS = 1_000L
        const val MAX_WAIT_MS = 60 * 60 * 1_000L
    }
}
