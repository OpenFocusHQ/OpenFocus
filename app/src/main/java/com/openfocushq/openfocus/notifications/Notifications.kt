package com.openfocushq.openfocus.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.openfocushq.openfocus.MainActivity
import com.openfocushq.openfocus.R
import com.openfocushq.openfocus.data.prefs.ActiveFocus

object Notifications {
    const val CH_FOCUS = "focus"
    const val CH_REMINDERS = "reminders"
    const val FOCUS_ONGOING_ID = 1001
    const val FOCUS_DONE_ID = 1002
    const val REMINDER_ID = 1003

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_FOCUS, "Focus sessions", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_REMINDERS, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun openApp(context: Context, route: String? = null): PendingIntent =
        PendingIntent.getActivity(
            context, route.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun focusOngoing(context: Context, s: ActiveFocus?): Notification =
        NotificationCompat.Builder(context, CH_FOCUS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (s?.label.isNullOrBlank()) "Focusing" else "Focusing: ${s?.label}")
            .setContentText(if (s?.deep == true) "Deep focus — only allowed apps are available" else "Distracting apps are blocked")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(s?.endsAt ?: System.currentTimeMillis())
            .setShowWhen(true)
            .setContentIntent(openApp(context, "focus"))
            .build()

    fun focusComplete(context: Context, minutes: Int, xp: Int) = notify(
        context, FOCUS_DONE_ID,
        NotificationCompat.Builder(context, CH_FOCUS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Focus session complete!")
            .setContentText(context.getString(R.string.minutes_done_xp_time_for_a, minutes, xp))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, "focus"))
            .build(),
    )

    fun habitReminder(context: Context, pending: Int, line: String) = notify(
        context, REMINDER_ID,
        NotificationCompat.Builder(context, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$pending habit${if (pending > 1) "s" else ""} left today")
            .setContentText(line)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, "habits"))
            .build(),
    )

    private fun notify(context: Context, id: Int, n: Notification) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            android.os.Build.VERSION.SDK_INT < 33
        ) {
            runCatching { NotificationManagerCompat.from(context).notify(id, n) }
        }
    }
}
