package dev.milkthe.qbitplan.core.model

import java.time.LocalDate

/**
 * A version chosen by hand in Settings. [newestAtPin] is the newest published version when the
 * pin was set: once a newer one appears the pin no longer applies, so nobody stays on an old plan.
 */
data class VersionPin(val ttNum: String, val newestAtPin: String?)

data class VersionChoice(val ttNum: String, val pinned: Boolean)

/** Visible versions of [schoolYear], newest first. Hidden ones are drafts or history. */
fun TimetableVersions.published(schoolYear: Int): List<TimetableVersion> =
    versions.filter { !it.hidden && it.schoolYear == schoolYear }
        .sortedWith(compareByDescending<TimetableVersion> { it.validFrom }.thenByDescending { it.ttNum.toIntOrNull() ?: 0 })

fun TimetableVersions.newest(schoolYear: Int): TimetableVersion? = published(schoolYear).firstOrNull()

/** The version in force on [date]. EduPage publishes versions days ahead and switches `default_num` by hand. */
fun TimetableVersions.validOn(date: LocalDate, schoolYear: Int): TimetableVersion? =
    published(schoolYear).firstOrNull { it.validFrom <= date }

fun TimetableVersions.isPinValid(pin: VersionPin, schoolYear: Int): Boolean {
    val published = published(schoolYear)
    return published.any { it.ttNum == pin.ttNum } && published.firstOrNull()?.ttNum == pin.newestAtPin
}

/**
 * The version to show when the plan opens on [date]: a valid [pin], else the one in force on
 * [date], else `default_num`. `null` when the school has no timetable for [schoolYear].
 */
fun TimetableVersions.choose(date: LocalDate, schoolYear: Int, pin: VersionPin?): VersionChoice? {
    if (pin != null && isPinValid(pin, schoolYear)) return VersionChoice(pin.ttNum, pinned = true)
    val ttNum = validOn(date, schoolYear)?.ttNum ?: defaultTtNum ?: return null
    return VersionChoice(ttNum, pinned = false)
}
