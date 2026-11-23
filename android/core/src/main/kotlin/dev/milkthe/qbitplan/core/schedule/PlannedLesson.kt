package dev.milkthe.qbitplan.core.schedule

import dev.milkthe.qbitplan.core.model.Change
import dev.milkthe.qbitplan.core.model.PeriodRange
import dev.milkthe.qbitplan.core.model.Substitution
import dev.milkthe.qbitplan.core.model.Teacher
import dev.milkthe.qbitplan.core.model.TimetableLesson

/** Stan lekcji po nałożeniu zastępstw. */
enum class LessonStatus {
    /** Bez zmian względem planu stałego. */
    NORMAL,

    /** Zastępca, inny przedmiot i/lub inna sala. */
    CHANGED,

    /** Nauczyciel nieobecny, zastępcy brak. */
    NO_SUBSTITUTE,

    /** Odwołana. */
    CANCELLED,

    /** Przeniesiona stąd na [PlannedLesson.movedToPeriod]. */
    MOVED_AWAY,

    /** Przeniesiona tutaj z [PlannedLesson.movedFromPeriod]. */
    MOVED_HERE,

    /** Klasa nieobecna (wycieczka, praktyki…). */
    CLASS_ABSENT,
    ;

    /** Czy lekcja się odbywa (zajmuje salę, nauczyciela i uczniów). */
    val takesPlace: Boolean get() = this == NORMAL || this == CHANGED || this == NO_SUBSTITUTE || this == MOVED_HERE
}

/**
 * Jeden kafelek planu dnia: lekcja z planu stałego z nałożonym zastępstwem
 * albo lekcja spoza planu (np. przeniesiona, zastępstwo nauczyciela).
 */
data class PlannedLesson(
    val periods: PeriodRange,
    val status: LessonStatus,
    /** Skróty przedmiotów: `old` = z planu, `new` = aktualny. */
    val subject: Change,
    /** Nauczyciele z planu stałego (skróty); pusta lista dla lekcji spoza planu. */
    val plannedTeachers: List<Teacher>,
    /** Nauczyciel z zastępstwa (pełne imiona i nazwiska); `null` = brak zastępstwa. */
    val teacher: Change?,
    /** Skróty sal: `old` = z planu, `new` = aktualna; `null` = brak sali. */
    val classroom: Change?,
    /** Skróty klas (kilka przy lekcjach łączonych). */
    val classNames: List<String>,
    /** Nazwy grup; puste = cała klasa. */
    val groupNames: List<String>,
    val movedFromPeriod: Int? = null,
    val movedToPeriod: Int? = null,
    /** Źródło w planie stałym; `null` dla lekcji spoza planu. */
    val lesson: TimetableLesson?,
    /** Wiersz zastępstw, który zmienił tę lekcję. */
    val substitution: Substitution?,
) {
    /** Zastępca, jeśli jest (pełne imię i nazwisko). */
    val substituteName: String? get() = teacher?.takeIf { it.isChanged }?.new
}
