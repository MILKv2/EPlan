package dev.milkthe.qbitplan.crash

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import dev.milkthe.qbitplan.BuildConfig
import kotlin.system.exitProcess

/**
 * Shows [CrashActivity] instead of the system "app keeps stopping" dialog when the app crashes
 * while the user is looking at it. Crashes in the background, or on the crash screen itself,
 * go to the default handler.
 */
object CrashHandler {

    private var installed = false

    fun install(app: Application) {
        if (installed) return
        installed = true
        val tracker = ForegroundTracker().also(app::registerActivityLifecycleCallbacks)
        val fallback = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (!tracker.inForeground || tracker.onCrashScreen) {
                fallback?.uncaughtException(thread, error)
                return@setDefaultUncaughtExceptionHandler
            }
            try {
                app.startActivity(
                    Intent(app, CrashActivity::class.java)
                        .putExtra(CrashActivity.EXTRA_DETAILS, details(error))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                )
            } catch (_: Throwable) {
                fallback?.uncaughtException(thread, error)
                return@setDefaultUncaughtExceptionHandler
            }
            // The crashed process can't go on; the crash screen starts in a fresh one.
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    private fun details(error: Throwable): String =
        "E-Plan ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
            "${Build.MANUFACTURER} ${Build.MODEL}\n\n" + error.stackTraceToString().take(MAX_DETAILS)

    // Keeps the Intent far below the Binder transaction limit.
    private const val MAX_DETAILS = 20_000

    private class ForegroundTracker : Application.ActivityLifecycleCallbacks {
        private var started = 0
        private var top: Activity? = null
        val inForeground: Boolean get() = started > 0
        val onCrashScreen: Boolean get() = top is CrashActivity

        override fun onActivityStarted(activity: Activity) {
            started++
            top = activity
        }

        override fun onActivityStopped(activity: Activity) {
            started--
            if (top === activity) top = null
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
