package dev.milkthe.qbitplan

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.milkthe.qbitplan.push.NotificationAlerts
import dev.milkthe.qbitplan.push.PushSubscriptions
import javax.inject.Inject

@HiltAndroidApp
class QbitApp : Application() {

    @Inject lateinit var pushSubscriptions: PushSubscriptions
    @Inject lateinit var notificationAlerts: NotificationAlerts

    override fun onCreate() {
        super.onCreate()
        notificationAlerts.createChannel()
        pushSubscriptions.start()
    }
}
