package dev.milkthe.qbitplan.core.edupage

import dev.milkthe.qbitplan.core.model.Classroom
import dev.milkthe.qbitplan.core.model.Group
import dev.milkthe.qbitplan.core.model.Period
import dev.milkthe.qbitplan.core.model.PeriodRange
import dev.milkthe.qbitplan.core.model.SchoolClass
import dev.milkthe.qbitplan.core.model.SchoolDay
import dev.milkthe.qbitplan.core.model.SchoolWeek
import dev.milkthe.qbitplan.core.model.Subject
import dev.milkthe.qbitplan.core.model.Teacher
import dev.milkthe.qbitplan.core.model.Timetable
import dev.milkthe.qbitplan.core.model.TimetableLesson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalTime

/**
 * Parser odpowiedzi `regularttGetData` (plan stały wszystkich klas).
 * Format opisany w docs/edupage-api.md, sekcja 2.
 */
object RegularTimetableParser {

    fun parse(raw: String, ttNum: String): Timetable {
        val result = try {
            EdupageJson.json.decodeFromJsonElement(ResultDto.serializer(), EdupageJson.unwrap(raw))
        } catch (e: SerializationException) {
            throw EdupageException("Nieoczekiwany format planu", e)
        }
        // Tabele są listą {id, data_rows}; robimy z nich mapę id → wiersze.
        val tables = result.dbiAccessorRes.tables.associate { it.id to it.rows }

        // Pomocnik: dekoduje wiersze tabeli `name` do podanego DTO.
        // <T> = typ generyczny, ustalany przy wywołaniu (np. PeriodDto).
        fun <T> rows(name: String, serializer: KSerializer<T>): List<T> =
            tables[name].orEmpty().map { EdupageJson.json.decodeFromJsonElement(serializer, it) }

        val periodDtos = rows("periods", PeriodDto.serializer())
        val periods = periodDtos.map {
            Period(it.period.toInt(), it.name, LocalTime.parse(it.startTime), LocalTime.parse(it.endTime))
        }.sortedBy { it.number }
        // Karta wskazuje lekcję przez `id` wiersza periods, nie przez numer.
        val periodNumberById = periodDtos.associate { it.id to it.period.toInt() }

        val days = rows("days", NamedDto.serializer()).map { SchoolDay(it.id.toInt(), it.name, it.short) }
        val weeks = rows("weeks", NamedDto.serializer()).map { SchoolWeek(it.id.toInt(), it.name, it.short) }
        val allWeeks = weeks.map { it.index }.toSet().ifEmpty { setOf(0) }

        val lessonsById = rows("lessons", LessonDto.serializer()).associateBy { it.id }

        val lessons = rows("cards", CardDto.serializer()).flatMap { card ->
            val lesson = lessonsById[card.lessonId]
            val start = periodNumberById[card.period]
            // Karta bez lekcji/godziny/dnia = nierozmieszczona w planie, pomijamy.
            if (lesson == null || start == null) return@flatMap emptyList()
            val weekSet = card.weeks.indicesOf('1').toSet().ifEmpty { allWeeks }
            card.days.indicesOf('1').map { day ->
                TimetableLesson(
                    cardId = card.id,
                    lessonId = lesson.id,
                    day = day,
                    periods = PeriodRange(start, start + lesson.durationPeriods.coerceAtLeast(1) - 1),
                    weeks = weekSet,
                    subjectId = lesson.subjectId,
                    teacherIds = lesson.teacherIds,
                    classIds = lesson.classIds,
                    groupIds = lesson.groupIds,
                    classroomIds = card.classroomIds,
                )
            }
        }.sortedWith(compareBy({ it.day }, { it.periods.first }))

        val globals = rows("globals", GlobalsDto.serializer()).firstOrNull()

        return Timetable(
            ttNum = ttNum,
            schoolName = globals?.regName.orEmpty().trim(),
            validityText = (globals?.settings?.get("m_strDateBellowTimeTable") as? JsonPrimitive)?.content.orEmpty(),
            periods = periods,
            days = days,
            weeks = weeks,
            teachers = rows("teachers", TeacherDto.serializer()).associate { it.id to Teacher(it.id, it.short) },
            classes = rows("classes", ClassDto.serializer()).associate {
                it.id to SchoolClass(it.id, it.name, it.short, it.teacherId.ifBlank { null })
            },
            classrooms = rows("classrooms", NamedDto.serializer()).associate {
                it.id to Classroom(it.id, it.name, it.short)
            },
            subjects = rows("subjects", NamedDto.serializer()).associate { it.id to Subject(it.id, it.name, it.short) },
            groups = rows("groups", GroupDto.serializer()).associate {
                it.id to Group(it.id, it.name, it.classId, it.entireClass, it.division)
            },
            lessons = lessons,
        )
    }

    /** "00100".indicesOf('1') == [2] */
    private fun String.indicesOf(char: Char): List<Int> = indices.filter { this[it] == char }

    @Serializable
    private class ResultDto(val dbiAccessorRes: DbiDto)

    @Serializable
    private class DbiDto(val tables: List<TableDto> = emptyList())

    @Serializable
    private class TableDto(val id: String, @SerialName("data_rows") val rows: List<JsonObject> = emptyList())

    @Serializable
    private class GlobalsDto(@SerialName("reg_name") val regName: String = "", val settings: JsonObject? = null)

    @Serializable
    private class PeriodDto(
        val id: String,
        val period: String,
        val name: String = "",
        @SerialName("starttime") val startTime: String,
        @SerialName("endtime") val endTime: String,
    )

    @Serializable
    private class NamedDto(val id: String, val name: String = "", val short: String = "")

    @Serializable
    private class TeacherDto(val id: String, val short: String = "")

    @Serializable
    private class ClassDto(
        val id: String,
        val name: String = "",
        val short: String = "",
        @SerialName("teacherid") val teacherId: String = "",
    )

    @Serializable
    private class GroupDto(
        val id: String,
        val name: String = "",
        @SerialName("classid") val classId: String = "",
        @SerialName("entireclass") val entireClass: Boolean = false,
        @SerialName("ascttdivision") val division: String = "",
    )

    @Serializable
    private class LessonDto(
        val id: String,
        @SerialName("subjectid") val subjectId: String = "",
        @SerialName("teacherids") val teacherIds: List<String> = emptyList(),
        @SerialName("groupids") val groupIds: List<String> = emptyList(),
        @SerialName("classids") val classIds: List<String> = emptyList(),
        @SerialName("durationperiods") val durationPeriods: Int = 1,
    )

    @Serializable
    private class CardDto(
        val id: String,
        @SerialName("lessonid") val lessonId: String = "",
        val period: String = "",
        val days: String = "",
        val weeks: String = "",
        @SerialName("classroomids") val classroomIds: List<String> = emptyList(),
    )
}
