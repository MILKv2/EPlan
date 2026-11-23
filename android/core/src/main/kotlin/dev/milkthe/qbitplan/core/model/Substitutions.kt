package dev.milkthe.qbitplan.core.model

import java.time.LocalDate
import java.time.LocalDateTime

/** Zastępstwa na jeden dzień, sparsowane z widoku „klasy”. */
data class DaySubstitutions(
    val date: LocalDate,
    val absentTeachers: List<AbsenceNote>,
    val absentClasses: List<AbsenceNote>,
    val unavailableClassrooms: List<AbsenceNote>,
    /** Nagłówki, których nie rozpoznajemy (pełny tekst), żeby nic nie zginęło. */
    val otherNotes: List<String>,
    val substitutions: List<Substitution>,
    /** Komunikat zamiast tabeli, np. „W tym dniu nie ma żadnych zastępstw.” */
    val message: String?,
    /** Kiedy szkoła wygenerowała zastępstwa (stopka „asctimetables.com - …”). */
    val generatedAt: LocalDateTime?,
)

/** Wpis z nagłówka, np. „Justyna Drozd (2)”. [periods] = `null` → cały dzień. */
data class AbsenceNote(val name: String, val periods: PeriodRange?)

/**
 * Wartość przed i po zmianie. `old == new` → bez zmian (np. „Nauczyciel: X”).
 * `new == null` → wartość usunięta (np. brak zastępcy).
 */
data class Change(val old: String?, val new: String?) {
    val isChanged: Boolean get() = old != new

    companion object {
        fun unchanged(value: String) = Change(value, value)
    }
}

enum class SubstitutionType {
    /** Zastępca, inny przedmiot i/lub inna sala. */
    CHANGE,

    /** Nauczyciel nieobecny, zastępcy (jeszcze) nie przydzielono. */
    NO_SUBSTITUTE,

    /** Lekcja odwołana („Anulowano”). */
    CANCELLED,

    /** Lekcja przeniesiona z tego miejsca na [Substitution.movedToPeriod]. */
    MOVED_AWAY,

    /** Lekcja przeniesiona tutaj z [Substitution.movedFromPeriod]. */
    MOVED_HERE,

    /** Cała klasa nieobecna (wycieczka, praktyki…). */
    CLASS_ABSENT,
}

/** Jeden wiersz tabeli zastępstw. */
data class Substitution(
    /** Skrót klasy, np. „1at” (= `SchoolClass.short`). */
    val className: String,
    /** `null` = cały dzień. */
    val periods: PeriodRange?,
    val type: SubstitutionType,
    /** Nazwy grup (= `Group.name`), puste = cała klasa. */
    val groups: List<String>,
    /** Skróty przedmiotów (= `Subject.short`). */
    val subject: Change?,
    /** Pełne imiona i nazwiska nauczycieli. */
    val teacher: Change?,
    /** Skróty sal (= `Classroom.short`). */
    val classroom: Change?,
    val movedFromPeriod: Int?,
    val movedToPeriod: Int?,
    /** „(Bez klasy 1dt)”: lekcja odbywa się bez tych klas. */
    val withoutClasses: List<String>,
    /** Części, których nie rozpoznaliśmy, np. nowa etykieta EduPage. */
    val unknownParts: List<String>,
    /** Oryginalny tekst kolumn, do wyświetlenia awaryjnego. */
    val whatText: String,
    val infoText: String,
) {
    /** Czy wiersz dotyczy nauczyciela o tym imieniu i nazwisku (stary lub nowy). */
    fun involvesTeacher(fullName: String): Boolean {
        val wanted = normalizeName(fullName)
        return listOfNotNull(teacher?.old, teacher?.new).any { normalizeName(it) == wanted }
    }
}

/** Porównywanie nazwisk bez względu na wielkość liter i nadmiarowe spacje. */
fun normalizeName(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase()
