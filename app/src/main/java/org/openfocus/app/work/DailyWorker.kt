package org.openfocus.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.openfocus.app.OpenFocusApp
import org.openfocus.app.core.Days
import org.openfocus.app.core.Encouragement
import org.openfocus.app.notifications.Notifications
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Runs every few hours: processes day rollover and sends one gentle evening reminder. */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as OpenFocusApp).container
        c.habits.rollover()
        c.focus.recoverIfNeeded()
        val s = c.settings.value
        val today = Days.today()
        if (LocalTime.now().hour >= s.reminderHour && s.lastReminderDay != today) {
            val pending = c.habits.pendingTodayCount()
            if (pending > 0) {
                Notifications.habitReminder(applicationContext, pending, Encouragement.general())
                c.settings.update { it.copy(lastReminderDay = today) }
            }
        }
        // Outbox flush — a no-op until a SyncGateway is configured.
        if (c.sync.isEnabled) {
            val events = c.db.gameDao().unsynced()
            if (events.isNotEmpty() && c.sync.pushEvents(events).isSuccess) {
                c.db.gameDao().markSynced(events.map { it.id }, System.currentTimeMillis())
            }
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DailyWorker>(2, TimeUnit.HOURS).build(),
            )
        }
    }
}
