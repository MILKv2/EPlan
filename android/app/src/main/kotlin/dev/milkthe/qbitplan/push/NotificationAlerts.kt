package dev.milkthe.qbitplan.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.milkthe.qbitplan.MainActivity
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.core.schedule.LessonStatus
import dev.milkthe.qbitplan.ui.common.LessonItem
import dev.milkthe.qbitplan.ui.common.LessonNote
import dev.milkthe.qbitplan.ui.common.PolishLocale
import dev.milkthe.qbitplan.ui.common.text
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** One notification per day of changes; a newer push about the same day adds to it while it is still shown. */
@Singleton
class NotificationAlerts @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : ChangeAlerts, TimetableAlerts {

    override fun show(date: LocalDate, changes: List<LessonChange>) {
        post(date, changes.map { NotificationLine(it.lesson.periods, if (it.dropped) droppedLine(it.lesson) else line(it.lesson)) })
    }

    override fun showUnspecified(date: LocalDate) {
        post(date, listOf(NotificationLine(null, context.getString(R.string.push_unknown))))
    }

    override fun showNewTimetable(firstDay: LocalDate, downloaded: Boolean) {
        if (!canPost()) return
        createChannel()
        val notification = NotificationCompat.Builder(context, TIMETABLE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.push_timetable_title, DAY.format(firstDay)))
            .setContentText(context.getString(if (downloaded) R.string.push_timetable_text else R.string.push_timetable_text_offline))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()
        NotificationManagerCompat.from(context).notify(TIMETABLE_TAG, 0, notification)
    }

    /** Both channels, so either can be muted on its own in the system settings. */
    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.push_channel), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.push_channel_desc) },
        )
        manager.createNotificationChannel(
            NotificationChannel(TIMETABLE_CHANNEL_ID, context.getString(R.string.push_timetable_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.push_timetable_channel_desc) },
        )
    }

    private fun canPost(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun post(date: LocalDate, added: List<NotificationLine>) {
        if (!canPost()) return
        createChannel()
        val id = date.toEpochDay().toInt()
        val shown = context.getSystemService(NotificationManager::class.java).activeNotifications
            .firstOrNull { it.id == id && it.tag == null }?.notification?.extras?.notificationLines().orEmpty()
        val merged = mergeLines(shown, added)
        val lines = merged.map { it.text }
        val open = openApp()
        val style = NotificationCompat.InboxStyle()
        lines.take(MAX_LINES).forEach(style::addLine)
        if (lines.size > MAX_LINES) style.setSummaryText(context.getString(R.string.push_more, lines.size - MAX_LINES))
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.push_title, dayText(date)))
            .setContentText(added.first().text)
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .addExtras(merged.toExtras())
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun dayText(date: LocalDate): String {
        val today = LocalDate.now(clock)
        return when (date) {
            today -> context.getString(R.string.today).lowercase(PolishLocale)
            today.plusDays(1) -> context.getString(R.string.tomorrow).lowercase(PolishLocale)
            else -> DAY.format(date)
        }
    }

    /** "4. Informatyka: zamiast PEiE, sala B23, Anna Grzebień" / "6. Religia: odwołana". */
    private fun line(item: LessonItem): String {
        val details = when {
            item.isCovering -> listOfNotNull(
                (item.note as? LessonNote.Covering)?.let { context.getString(R.string.push_covering, context.getString(R.string.note_covering, it.name)) },
                item.room?.let { context.getString(R.string.push_room, it) },
            )
            else -> when (item.status) {
                LessonStatus.CANCELLED -> listOf(context.getString(R.string.tag_cancelled))
                LessonStatus.NO_SUBSTITUTE -> listOf(context.getString(R.string.tag_no_substitute))
                LessonStatus.CLASS_ABSENT -> listOf(context.getString(R.string.tag_class_absent))
                LessonStatus.MOVED_AWAY, LessonStatus.MOVED_HERE -> listOfNotNull(
                    item.note?.let { context.getString(R.string.push_moved, noteText(it)) },
                    item.room?.let { context.getString(R.string.push_room, it) },
                )
                LessonStatus.NORMAL -> listOf(context.getString(R.string.push_back_to_plan))
                LessonStatus.CHANGED -> listOfNotNull(
                    item.oldSubjectShort?.let { context.getString(R.string.push_instead, it) },
                    item.oldRoom?.let { item.room?.let { room -> context.getString(R.string.push_room, room) } },
                    item.oldWho?.let { item.who?.let { who -> context.getString(R.string.push_teacher, who) } },
                ).ifEmpty { listOf(context.getString(R.string.push_changed)) }
            }
        }
        return context.getString(R.string.push_line, item.periods.text(), item.title, details.joinToString(", "))
    }

    /** "4. 1at · Informatyka: już nie zastępujesz". */
    private fun droppedLine(item: LessonItem): String {
        val details = context.getString(if (item.isCovering) R.string.push_cover_dropped else R.string.push_dropped)
        return context.getString(R.string.push_line, item.periods.text(), item.title, details)
    }

    private fun noteText(note: LessonNote): String = when (note) {
        is LessonNote.MovedFrom -> context.getString(R.string.note_moved_from, note.period)
        is LessonNote.MovedTo -> context.getString(R.string.note_moved_to, note.period)
        is LessonNote.Covering -> context.getString(R.string.note_covering, note.name)
    }

    private companion object {
        const val CHANNEL_ID = "changes"
        const val TIMETABLE_CHANNEL_ID = "timetable"
        // Tagged, so it never collides with the per-day ids (epoch days) of change notifications.
        const val TIMETABLE_TAG = "timetable"
        const val MAX_LINES = 6
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d.MM", PolishLocale)
    }
}
