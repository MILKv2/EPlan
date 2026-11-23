package dev.milkthe.qbitplan.core.schedule

import dev.milkthe.qbitplan.core.model.Timetable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Normalises DayPlanner output and EduPage's current timetable (`curentttGetData`)
 * to one unit per lesson period. Teachers are compared only for unchanged lessons:
 * substitutes are full names in the HTML but shorts in the oracle.
 */
class OracleComparison(private val tt: Timetable) {

    data class Unit(
        val period: Int,
        val subject: String,
        val groups: List<String>,
        val rooms: String,
        val flag: String, // "", "changed", "removed"
        val teachers: String,
    )

    fun fromPlanner(lessons: List<PlannedLesson>): List<Unit> = lessons.flatMap { lesson ->
        val removed = !lesson.status.takesPlace
        val flag = when {
            removed -> "removed"
            lesson.status == LessonStatus.NORMAL -> ""
            else -> "changed"
        }
        (lesson.periods.first..lesson.periods.last).map { p ->
            Unit(
                period = p,
                subject = (if (removed) lesson.subject.old else lesson.subject.new).orEmpty(),
                groups = oracleGroups(lesson),
                rooms = (if (removed) lesson.classroom?.old else lesson.classroom?.new).orEmpty(),
                flag = flag,
                teachers = if (flag == "") lesson.plannedTeachers.joinToString { it.short } else "",
            )
        }
    }.sortedBy { it.toString() }

    fun fromOracle(raw: String): Map<String, List<Unit>> {
        val root = Json.parseToJsonElement(raw).jsonObject
        val items = root["r"]!!.jsonObject["ttitems"]!!.jsonArray.map { it.jsonObject }
        return items.filter { it["type"]!!.jsonPrimitive.content == "card" }.flatMap { item ->
            fun strings(key: String) = item[key]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            val start = item["uniperiod"]!!.jsonPrimitive.int
            val duration = item["durationperiods"]?.jsonPrimitive?.int ?: 1
            val flag = when {
                item["removed"]?.jsonPrimitive?.boolean == true -> "removed"
                item["changed"]?.jsonPrimitive?.boolean == true -> "changed"
                else -> ""
            }
            (start until start + duration).map { p ->
                item["date"]!!.jsonPrimitive.content to Unit(
                    period = p,
                    subject = tt.subjects[item["subjectid"]!!.jsonPrimitive.content]?.short.orEmpty(),
                    groups = strings("groupnames").filter { it.isNotEmpty() }.sorted(),
                    rooms = strings("classroomids").mapNotNull { tt.classrooms[it]?.short }.joinToString(", "),
                    flag = flag,
                    teachers = if (flag == "") strings("teacherids").mapNotNull { tt.teachers[it]?.short }.joinToString() else "",
                )
            }
        }.groupBy({ it.first }, { it.second }).mapValues { (_, units) -> units.sortedBy { it.toString() } }
    }

    /**
     * The oracle reports group names of the whole joint lesson, across classes;
     * DayPlanner keeps only the class's own groups, so mimic the oracle here.
     */
    private fun oracleGroups(lesson: PlannedLesson): List<String> =
        lesson.lesson?.groupIds?.mapNotNull { tt.groups[it] }?.filter { !it.entireClass }?.map { it.name }?.distinct()?.sorted()
            ?: lesson.groupNames.sorted()
}
