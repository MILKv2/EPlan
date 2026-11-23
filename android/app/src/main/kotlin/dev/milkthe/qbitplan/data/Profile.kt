package dev.milkthe.qbitplan.data

import dev.milkthe.qbitplan.core.model.SchoolClass
import dev.milkthe.qbitplan.core.model.Teacher
import dev.milkthe.qbitplan.core.model.Timetable
import dev.milkthe.qbitplan.data.prefs.UserMode
import dev.milkthe.qbitplan.data.prefs.UserPreferences

sealed interface Profile {
    data class Student(val schoolClass: SchoolClass, val groupNames: Set<String>) : Profile
    data class TeacherProfile(val teacher: Teacher, val fullName: String?) : Profile
}

/** Resolves saved ids against the current timetable, falling back to the saved short. */
fun UserPreferences.resolve(tt: Timetable): Profile? = when (mode) {
    UserMode.STUDENT -> {
        val cls = classId?.let { tt.classes[it] } ?: classShort?.let { tt.classByShort(it) }
        cls?.let { Profile.Student(it, groupNames) }
    }
    UserMode.TEACHER -> {
        val teacher = teacherId?.let { tt.teachers[it] }
            ?: teacherShort?.let { short -> tt.teachers.values.singleOrNull { it.short == short } }
        teacher?.let { Profile.TeacherProfile(it, teacherFullName) }
    }
    null -> null
}
