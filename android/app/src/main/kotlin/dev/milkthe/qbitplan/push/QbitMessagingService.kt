package dev.milkthe.qbitplan.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * The worker pushes `date` (YYYY-MM-DD) when that day's substitutions changed for this install's
 * topic, or `type=timetable` with `validFrom` when a new timetable version was published.
 */
@AndroidEntryPoint
class QbitMessagingService : FirebaseMessagingService() {

    @Inject lateinit var notifier: ChangeNotifier
    @Inject lateinit var timetableNotifier: TimetableNotifier
    @Inject lateinit var subscriptions: PushSubscriptions

    // Runs on a background thread; the message's process lifetime ends when this returns.
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val timetable = data["type"] == "timetable"
        val date = try {
            LocalDate.parse((if (timetable) data["validFrom"] else data["date"]) ?: return)
        } catch (e: DateTimeParseException) {
            Log.w(TAG, "Push with a bad date: $data", e)
            return
        }
        runBlocking { if (timetable) timetableNotifier.onNewTimetable(date) else notifier.onDayChanged(date) }
    }

    // Replaces onNewToken in firebase-messaging 26: a new registration can come without the old topics.
    override fun onRegistered(token: String) {
        subscriptions.resubscribe()
    }

    private companion object {
        const val TAG = "QbitMessaging"
    }
}
