package org.openfocus.app.core

import org.openfocus.app.data.db.HabitEntity
import org.openfocus.app.data.db.HabitFrequency
import org.openfocus.app.data.db.HabitKind

/** Pure functions describing when a habit is due, satisfied and how long its streak is. */
object HabitSchedule {

    fun isScheduledOn(h: HabitEntity, day: Long): Boolean = when (h.frequency) {
        HabitFrequency.DAILY, HabitFrequency.WEEKLY -> true
        HabitFrequency.SPECIFIC_DAYS -> Days.maskHas(h.daysMask, Days.dayIndex(day))
    }

    /** Completions that count towards the period containing [day]. */
    fun progressFor(h: HabitEntity, counts: Map<Long, Int>, day: Long): Int = when (h.frequency) {
        HabitFrequency.WEEKLY -> {
            val start = Days.weekStart(day)
            (start..start + 6).sumOf { counts[it] ?: 0 }
        }
        else -> counts[day] ?: 0
    }

    fun isSatisfied(h: HabitEntity, counts: Map<Long, Int>, day: Long): Boolean =
        progressFor(h, counts, day) >= h.target.coerceAtLeast(1)

    /**
     * Streak length in "periods" (days for daily habits, weeks for weekly ones).
     * The current period only counts once it is satisfied, but an unfinished current
     * period never breaks the streak. For BREAK habits it is the number of clean days.
     */
    fun streak(h: HabitEntity, counts: Map<Long, Int>, today: Long): Int {
        val createdDay = Days.dayOfMillis(h.createdAt)
        if (h.kind == HabitKind.BREAK) {
            val lastSlip = counts.filter { it.value > 0 && it.key <= today }.keys.maxOrNull()
            val from = maxOf(createdDay, (lastSlip ?: (createdDay - 1)) + 1)
            return (today - from + 1).coerceAtLeast(0).toInt()
        }
        var streak = 0
        if (h.frequency == HabitFrequency.WEEKLY) {
            var week = Days.weekStart(today)
            if (isSatisfied(h, counts, week)) streak++
            week -= 7
            while (week + 6 >= createdDay && isSatisfied(h, counts, week)) {
                streak++; week -= 7
            }
            return streak
        }
        var day = today
        if (isScheduledOn(h, day) && isSatisfied(h, counts, day)) streak++
        day--
        while (day >= createdDay) {
            if (isScheduledOn(h, day)) {
                if (isSatisfied(h, counts, day)) streak++ else break
            }
            day--
        }
        return streak
    }

    /** True if a BUILD habit was due on [day] and not done (used at day rollover). */
    fun missedOn(h: HabitEntity, counts: Map<Long, Int>, day: Long): Boolean {
        if (h.kind != HabitKind.BUILD) return false
        if (day < Days.dayOfMillis(h.createdAt)) return false
        return when (h.frequency) {
            HabitFrequency.WEEKLY ->
                // Only judged on the last day of the week.
                Days.dayIndex(day) == 6 && !isSatisfied(h, counts, day)
            else -> isScheduledOn(h, day) && !isSatisfied(h, counts, day)
        }
    }
}

fun Days.dayOfMillis(millis: Long): Long =
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
