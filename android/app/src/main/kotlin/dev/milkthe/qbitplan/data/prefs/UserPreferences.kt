package dev.milkthe.qbitplan.data.prefs

enum class UserMode { STUDENT, TEACHER }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Ustawienia użytkownika (DataStore).
 *
 * Klasy i nauczycieli zapisujemy po ID (stałe między wersjami planu), a do
 * tego skrót, żeby dało się odnaleźć wpis, gdyby ID kiedyś się zmieniło.
 * Grupy zapisujemy po **nazwach**, bo ich ID zmieniają się z każdą wersją planu.
 */
data class UserPreferences(
    /** `null` = onboarding nieukończony. */
    val mode: UserMode? = null,
    val classId: String? = null,
    val classShort: String? = null,
    val groupNames: Set<String> = emptySet(),
    val teacherId: String? = null,
    val teacherShort: String? = null,
    /** Imię i nazwisko z zastępstw, łączy plan (skróty) z zastępstwami. */
    val teacherFullName: String? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val notificationsEnabled: Boolean = false,
) {
    val isOnboarded: Boolean
        get() = when (mode) {
            UserMode.STUDENT -> classId != null
            UserMode.TEACHER -> teacherId != null && !teacherFullName.isNullOrBlank()
            null -> false
        }
}
