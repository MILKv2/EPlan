package dev.milkthe.qbitplan.core.edupage

import dev.milkthe.qbitplan.core.model.TimetableVersion
import dev.milkthe.qbitplan.core.model.TimetableVersions
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Parses `getTTViewerData` (list of timetable versions). */
object TimetableVersionsParser {

    fun parse(raw: String): TimetableVersions {
        val result = EdupageJson.json.decodeFromJsonElement(ViewerDto.serializer(), EdupageJson.unwrap(raw))
        val regular = result.regular ?: throw EdupageException("Missing \"regular\" in timetable list")
        return TimetableVersions(
            defaultTtNum = regular.defaultNum.takeIf { it.isNotBlank() },
            versions = regular.timetables.map {
                TimetableVersion(
                    ttNum = it.ttNum,
                    schoolYear = it.year,
                    name = it.text,
                    validFrom = LocalDate.parse(it.dateFrom),
                    hidden = it.hidden,
                )
            },
        )
    }

    @Serializable
    private class ViewerDto(val regular: RegularDto? = null)

    @Serializable
    private class RegularDto(
        @SerialName("default_num") val defaultNum: String = "",
        val timetables: List<VersionDto> = emptyList(),
    )

    @Serializable
    private class VersionDto(
        @SerialName("tt_num") val ttNum: String,
        val year: Int,
        val text: String = "",
        @SerialName("datefrom") val dateFrom: String,
        val hidden: Boolean = false,
    )
}
