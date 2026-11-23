package dev.milkthe.qbitplan.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<UserPreferences> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toUserPreferences() }

    suspend fun setStudent(classId: String, classShort: String, groupNames: Set<String>) = dataStore.edit {
        it[MODE] = UserMode.STUDENT.name
        it[CLASS_ID] = classId
        it[CLASS_SHORT] = classShort
        it[GROUP_NAMES] = groupNames
    }

    suspend fun setGroups(groupNames: Set<String>) = dataStore.edit { it[GROUP_NAMES] = groupNames }

    suspend fun setTeacher(teacherId: String, teacherShort: String, fullName: String) = dataStore.edit {
        it[MODE] = UserMode.TEACHER.name
        it[TEACHER_ID] = teacherId
        it[TEACHER_SHORT] = teacherShort
        it[TEACHER_FULL_NAME] = fullName.trim()
    }

    suspend fun setTheme(theme: ThemeMode) = dataStore.edit { it[THEME] = theme.name }

    suspend fun setNotificationsEnabled(enabled: Boolean) = dataStore.edit { it[NOTIFICATIONS] = enabled }

    suspend fun clearMode() = dataStore.edit { prefs: MutablePreferences -> prefs.remove(MODE) }

    private fun Preferences.toUserPreferences() = UserPreferences(
        mode = this[MODE]?.let { name -> UserMode.entries.find { it.name == name } },
        classId = this[CLASS_ID],
        classShort = this[CLASS_SHORT],
        groupNames = this[GROUP_NAMES].orEmpty(),
        teacherId = this[TEACHER_ID],
        teacherShort = this[TEACHER_SHORT],
        teacherFullName = this[TEACHER_FULL_NAME],
        theme = this[THEME]?.let { name -> ThemeMode.entries.find { it.name == name } } ?: ThemeMode.SYSTEM,
        notificationsEnabled = this[NOTIFICATIONS] ?: false,
    )

    private companion object {
        val MODE = stringPreferencesKey("mode")
        val CLASS_ID = stringPreferencesKey("class_id")
        val CLASS_SHORT = stringPreferencesKey("class_short")
        val GROUP_NAMES = stringSetPreferencesKey("group_names")
        val TEACHER_ID = stringPreferencesKey("teacher_id")
        val TEACHER_SHORT = stringPreferencesKey("teacher_short")
        val TEACHER_FULL_NAME = stringPreferencesKey("teacher_full_name")
        val THEME = stringPreferencesKey("theme")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
    }
}
