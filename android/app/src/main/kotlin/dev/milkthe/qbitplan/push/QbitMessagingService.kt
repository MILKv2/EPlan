package dev.milkthe.qbitplan.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

/** The worker pushes only `date` (YYYY-MM-DD): that day's substitutions changed for this install's topic. */
@AndroidEntryPoint
class QbitMessagingService : FirebaseMessagingService() {

    @Inject lateinit var notifier: ChangeNotifier
    @Inject lateinit var subscriptions: PushSubscriptions

    // Runs on a background thread; the message's process lifetime ends when this returns.
    override fun onMessageReceived(message: RemoteMessage) {
        val date = try {
            LocalDate.parse(message.data["date"] ?: return)
        } catch (e: DateTimeParseException) {
            Log.w(TAG, "Push with a bad date: ${message.data}", e)
            return
        }
        runBlocking { notifier.onDayChanged(date) }
    }

    // Replaces onNewToken in firebase-messaging 26: a new registration can come without the old topics.
    override fun onRegistered(token: String) {
        subscriptions.resubscribe()
    }

    private companion object {
        const val TAG = "QbitMessaging"
    }
}
