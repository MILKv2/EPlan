package dev.milkthe.qbitplan.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import dev.milkthe.qbitplan.core.push.PushTopics
import dev.milkthe.qbitplan.data.prefs.UserMode
import dev.milkthe.qbitplan.data.prefs.UserPreferences
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import dev.milkthe.qbitplan.di.ApplicationScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface TopicSubscriber {
    suspend fun subscribe(topic: String)
    suspend fun unsubscribe(topic: String)
}

class FirebaseTopicSubscriber @Inject constructor() : TopicSubscriber {
    override suspend fun subscribe(topic: String) = await { FirebaseMessaging.getInstance().subscribeToTopic(topic) }
    override suspend fun unsubscribe(topic: String) = await { FirebaseMessaging.getInstance().unsubscribeFromTopic(topic) }

    private suspend fun await(task: () -> com.google.android.gms.tasks.Task<Void>) = suspendCancellableCoroutine { cont ->
        task().addOnCompleteListener { done ->
            val error = done.exception
            if (error == null) cont.resume(Unit) else cont.resumeWithException(error)
        }
    }
}

/**
 * Keeps this install subscribed to its profile's topic (class or teacher) and the new-timetable
 * topic, and to none while notifications are off. The topics in use are saved, so a profile
 * change unsubscribes the old one.
 */
@Singleton
class PushSubscriptions @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val subscriber: TopicSubscriber,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            preferences.preferences
                .map { desiredTopics(it) to it.pushTopics }
                .distinctUntilChanged()
                .collectLatest { (wanted, current) -> if (wanted != current) switchTo(wanted, current) }
        }
    }

    /** A new FCM token can come without the old subscriptions. */
    fun resubscribe() {
        scope.launch {
            for (topic in preferences.preferences.first().pushTopics) {
                runCatching { subscriber.subscribe(topic) }.onFailure { Log.w(TAG, "Resubscribing to $topic failed", it) }
            }
        }
    }

    private suspend fun switchTo(wanted: Set<String>, current: Set<String>) {
        try {
            (current - wanted).forEach { subscriber.unsubscribe(it) }
            (wanted - current).forEach { subscriber.subscribe(it) }
            preferences.setPushTopics(wanted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Tried again on the next start or profile change.
            Log.w(TAG, "Switching push topic $current → $wanted failed", e)
        }
    }

    private companion object {
        const val TAG = "PushSubscriptions"
    }
}

fun desiredTopics(prefs: UserPreferences): Set<String> {
    if (!prefs.notificationsEnabled || !prefs.isOnboarded) return emptySet()
    val profile = when (prefs.mode) {
        UserMode.STUDENT -> prefs.classShort?.let(PushTopics::forClass)
        UserMode.TEACHER -> prefs.teacherFullName?.let(PushTopics::forTeacher)
        null -> null
    }
    return setOfNotNull(profile, PushTopics.TIMETABLE)
}
