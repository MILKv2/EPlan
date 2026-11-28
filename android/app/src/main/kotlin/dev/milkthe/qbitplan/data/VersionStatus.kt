package dev.milkthe.qbitplan.data

import dev.milkthe.qbitplan.core.model.Timetable
import dev.milkthe.qbitplan.core.model.VersionPin
import dev.milkthe.qbitplan.core.model.isPinValid

/** The timetable version on screen and whether it was chosen by hand; what Settings and the plan show. */
data class VersionStatus(val name: String, val pinned: Boolean)

fun versionStatus(versions: SchoolVersions?, timetable: Timetable?, pin: VersionPin?): VersionStatus? {
    if (timetable == null) return null
    val name = versions?.versions?.versions?.find { it.ttNum == timetable.ttNum }?.shortName ?: timetable.ttNum
    val pinned = pin != null && pin.ttNum == timetable.ttNum && versions?.versions?.isPinValid(pin, versions.schoolYear) == true
    return VersionStatus(name, pinned)
}
