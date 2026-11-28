package dev.milkthe.qbitplan.data.prefs

import dev.milkthe.qbitplan.core.model.VersionPin

enum class UserMode { STUDENT, TEACHER }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Classes and teachers are stored by id (stable across timetable versions) plus their short
 * as a fallback. Groups are stored by NAME because their ids change with every version.
 */
data class UserPreferences(
    /** `null` = onboarding not finished. */
    val mode: UserMode? = null,
    val classId: String? = null,
    val classShort: String? = null,
    val groupNames: Set<String> = emptySet(),
    val teacherId: String? = null,
    val teacherShort: String? = null,
    /** Full name as used in substitutions; links the plan (shorts) to substitutions. */
    val teacherFullName: String? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val notificationsEnabled: Boolean = false,
    val quietWeekends: Boolean = true,
    /** Hide lessons of groups the student isn't in. */
    val onlyMyGroups: Boolean = true,
    /** FCM topics this install is subscribed to. */
    val pushTopics: Set<String> = emptySet(),
    /** Timetable version chosen by hand in Settings; `null` = automatic. */
    val versionPin: VersionPin? = null,
) {
    val isOnboarded: Boolean
        get() = when (mode) {
            UserMode.STUDENT -> classId != null
            UserMode.TEACHER -> teacherId != null && !teacherFullName.isNullOrBlank()
            null -> false
        }
}
