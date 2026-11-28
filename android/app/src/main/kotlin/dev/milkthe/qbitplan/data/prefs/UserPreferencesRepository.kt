package dev.milkthe.qbitplan.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.milkthe.qbitplan.core.model.VersionPin
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

    suspend fun setQuietWeekends(enabled: Boolean) = dataStore.edit { it[QUIET_WEEKENDS] = enabled }

    suspend fun setOnlyMyGroups(enabled: Boolean) = dataStore.edit { it[ONLY_MY_GROUPS] = enabled }

    suspend fun setPushTopics(topics: Set<String>) = dataStore.edit {
        it.remove(LEGACY_PUSH_TOPIC)
        it[PUSH_TOPICS] = topics
    }

    suspend fun setVersionPin(pin: VersionPin?) = dataStore.edit {
        if (pin == null) {
            it.remove(PIN_TT_NUM)
            it.remove(PIN_NEWEST)
        } else {
            it[PIN_TT_NUM] = pin.ttNum
            val newest = pin.newestAtPin
            if (newest == null) it.remove(PIN_NEWEST) else it[PIN_NEWEST] = newest
        }
    }

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
        quietWeekends = this[QUIET_WEEKENDS] ?: true,
        onlyMyGroups = this[ONLY_MY_GROUPS] ?: true,
        // Before the timetable topic there was only the profile's topic.
        pushTopics = this[PUSH_TOPICS] ?: setOfNotNull(this[LEGACY_PUSH_TOPIC]),
        versionPin = this[PIN_TT_NUM]?.let { VersionPin(it, this[PIN_NEWEST]) },
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
        val QUIET_WEEKENDS = booleanPreferencesKey("quiet_weekends")
        val ONLY_MY_GROUPS = booleanPreferencesKey("only_my_groups")
        val LEGACY_PUSH_TOPIC = stringPreferencesKey("push_topic")
        val PUSH_TOPICS = stringSetPreferencesKey("push_topics")
        val PIN_TT_NUM = stringPreferencesKey("version_pin")
        val PIN_NEWEST = stringPreferencesKey("version_pin_newest")
    }
}
