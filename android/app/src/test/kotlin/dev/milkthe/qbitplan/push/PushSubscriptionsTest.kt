package dev.milkthe.qbitplan.push

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.milkthe.qbitplan.core.model.GroupSelection
import dev.milkthe.qbitplan.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals

class PushSubscriptionsTest {

    @get:Rule val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var prefs: UserPreferencesRepository
    private val subscriber = RecordingSubscriber()

    private class RecordingSubscriber : TopicSubscriber {
        val calls = mutableListOf<String>()
        @Volatile var failing = false
        override suspend fun subscribe(topic: String) = record("+$topic")
        override suspend fun unsubscribe(topic: String) = record("-$topic")
        private fun record(call: String) {
            if (failing) throw IOException("offline")
            synchronized(calls) { calls += call }
        }
    }

    @Before
    fun setUp() {
        prefs = UserPreferencesRepository(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "test.preferences_pb") })
        PushSubscriptions(prefs, subscriber, scope).start()
    }

    @After
    fun tearDown() = scope.cancel()

    private fun awaitTopics(vararg topics: String) = runBlocking { withTimeout(10_000) { prefs.preferences.first { it.pushTopics == topics.toSet() } } }

    @Test
    fun `follows the profile and the notifications switch`() = runBlocking {
        prefs.setNotificationsEnabled(true)
        prefs.setStudent("-210", "1at", GroupSelection(emptySet()))
        awaitTopics("klasa-1at", "plan-lekcji")

        prefs.setTeacher("-161", "AG", "Anna Grzebień")
        awaitTopics("naucz-anna-grzebien", "plan-lekcji")

        prefs.setNotificationsEnabled(false)
        awaitTopics()

        assertEquals(
            listOf("+klasa-1at", "+plan-lekcji", "-klasa-1at", "+naucz-anna-grzebien", "-naucz-anna-grzebien", "-plan-lekcji"),
            subscriber.calls,
        )
    }

    @Test
    fun `nothing is saved when subscribing fails`() = runBlocking {
        subscriber.failing = true
        prefs.setStudent("-210", "1at", GroupSelection(emptySet()))
        prefs.setNotificationsEnabled(true)
        Thread.sleep(300)
        assertEquals(emptySet(), prefs.preferences.first().pushTopics)
    }
}
