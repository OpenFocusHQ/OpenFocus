package com.openfocushq.openfocus.blocking

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.openfocushq.openfocus.core.Days

/** Reads today's per-app foreground time from UsageStatsManager (needs "Usage access"). */
class UsageTracker(private val context: Context) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)
    @Volatile private var cache: Pair<Long, Map<String, Long>>? = null

    fun hasPermission(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Foreground millis per package since local midnight. Cached for [maxAgeMs]. */
    fun todayMillis(maxAgeMs: Long = 20_000): Map<String, Long> {
        val now = System.currentTimeMillis()
        cache?.let { (at, data) -> if (now - at < maxAgeMs) return data }
        if (!hasPermission()) return emptyMap()
        val start = Days.startOfDayMillis(Days.today())
        val result = HashMap<String, Long>()
        val openSince = HashMap<String, Long>()
        val events = runCatching { usm.queryEvents(start, now) }.getOrNull() ?: return emptyMap()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> openSince[e.packageName] = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    openSince.remove(e.packageName)?.let { s ->
                        result[e.packageName] = (result[e.packageName] ?: 0L) + (e.timeStamp - s)
                    }
                }
            }
        }
        // Apps still in the foreground.
        openSince.forEach { (pkg, s) -> result[pkg] = (result[pkg] ?: 0L) + (now - s) }
        cache = now to result
        return result
    }

    fun todayMinutes(pkg: String): Int = ((todayMillis()[pkg] ?: 0L) / 60_000).toInt()
}
