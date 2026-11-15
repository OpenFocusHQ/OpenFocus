package com.openfocushq.openfocus.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** Small helpers around java.time so the rest of the code can talk in epoch days. */
object Days {
    fun today(zone: ZoneId = ZoneId.systemDefault()): Long = LocalDate.now(zone).toEpochDay()

    fun date(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    /** 0 = Monday ... 6 = Sunday. */
    fun dayIndex(epochDay: Long): Int = date(epochDay).dayOfWeek.value - 1

    fun weekStart(epochDay: Long): Long =
        date(epochDay).with(DayOfWeek.MONDAY).toEpochDay()

    fun maskHas(mask: Int, dayIndex: Int): Boolean = (mask shr dayIndex) and 1 == 1

    fun startOfDayMillis(epochDay: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        date(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()

    val shortNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
}
