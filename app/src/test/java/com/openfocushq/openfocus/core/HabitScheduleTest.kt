package com.openfocushq.openfocus.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.openfocushq.openfocus.data.db.HabitEntity
import com.openfocushq.openfocus.data.db.HabitFrequency
import com.openfocushq.openfocus.data.db.HabitKind
import java.time.LocalDate
import java.time.ZoneId

class HabitScheduleTest {
    // 2026-11-09 is a Monday.
    private val monday = LocalDate.of(2026, 11, 9).toEpochDay()
    private fun createdDaysAgo(today: Long, n: Long) =
        LocalDate.ofEpochDay(today - n).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun habit(today: Long, f: HabitFrequency = HabitFrequency.DAILY, target: Int = 1, mask: Int = 0b1111111, kind: HabitKind = HabitKind.BUILD) =
        HabitEntity(ownerId = "me", title = "t", frequency = f, target = target, daysMask = mask, kind = kind, createdAt = createdDaysAgo(today, 60))

    @Test fun dailyStreakCountsConsecutiveDays() {
        val today = monday + 3
        val h = habit(today)
        val counts = mapOf(today - 1 to 1, today - 2 to 1, today - 3 to 1, today - 5 to 1)
        assertEquals(3, HabitSchedule.streak(h, counts, today)) // today not done yet doesn't break it
        assertEquals(4, HabitSchedule.streak(h, counts + (today to 1), today))
    }

    @Test fun specificDaysSkipsUnscheduledDays() {
        // Mon, Wed, Fri
        val mask = 0b0010101
        val today = monday + 7 // next Monday
        val h = habit(today, HabitFrequency.SPECIFIC_DAYS, mask = mask)
        val counts = mapOf(monday to 1, monday + 2 to 1, monday + 4 to 1, today to 1)
        assertEquals(4, HabitSchedule.streak(h, counts, today))
        assertFalse(HabitSchedule.isScheduledOn(h, monday + 1))
        assertFalse(HabitSchedule.missedOn(h, emptyMap(), monday + 1))
        assertTrue(HabitSchedule.missedOn(h, emptyMap(), monday + 2))
    }

    @Test fun weeklyTargetSumsAcrossWeek() {
        val today = monday + 4
        val h = habit(today, HabitFrequency.WEEKLY, target = 3)
        val counts = mapOf(monday to 1, monday + 2 to 1)
        assertFalse(HabitSchedule.isSatisfied(h, counts, today))
        assertTrue(HabitSchedule.isSatisfied(h, counts + (today to 1), today))
        // Weekly habits are only judged on Sunday.
        assertFalse(HabitSchedule.missedOn(h, counts, today))
        assertTrue(HabitSchedule.missedOn(h, counts, monday + 6))
    }

    @Test fun weeklyStreakCountsWeeks() {
        val today = monday + 2
        val h = habit(today, HabitFrequency.WEEKLY, target = 2)
        val counts = mapOf(monday - 7 to 2, monday - 14 to 1, monday - 13 to 1, monday - 21 to 1)
        assertEquals(2, HabitSchedule.streak(h, counts, today))
    }

    @Test fun breakHabitCountsCleanDays() {
        val today = monday + 10
        val h = habit(today, kind = HabitKind.BREAK)
        assertEquals(3, HabitSchedule.streak(h, mapOf(today - 3 to 1), today))
        assertEquals(0, HabitSchedule.streak(h, mapOf(today to 2), today))
        assertFalse(HabitSchedule.missedOn(h, emptyMap(), today - 1))
    }
}
