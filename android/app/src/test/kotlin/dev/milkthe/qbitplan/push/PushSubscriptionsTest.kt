package dev.milkthe.qbitplan.push

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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

    private fun awaitTopic(topic: String?) = runBlocking { withTimeout(10_000) { prefs.preferences.first { it.pushTopic == topic } } }

    @Test
    fun `follows the profile and the notifications switch`() = runBlocking {
        prefs.setNotificationsEnabled(true)
        prefs.setStudent("-210", "1at", emptySet())
        awaitTopic("klasa-1at")

        prefs.setTeacher("-161", "AG", "Anna Grzebień")
        awaitTopic("naucz-anna-grzebien")

        prefs.setNotificationsEnabled(false)
        awaitTopic(null)

        assertEquals(listOf("+klasa-1at", "-klasa-1at", "+naucz-anna-grzebien", "-naucz-anna-grzebien"), subscriber.calls)
    }

    @Test
    fun `nothing is saved when subscribing fails`() = runBlocking {
        subscriber.failing = true
        prefs.setStudent("-210", "1at", emptySet())
        prefs.setNotificationsEnabled(true)
        Thread.sleep(300)
        assertEquals(null, prefs.preferences.first().pushTopic)
    }
}
