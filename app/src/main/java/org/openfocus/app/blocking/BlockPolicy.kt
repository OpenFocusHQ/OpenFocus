package org.openfocus.app.blocking

import org.openfocus.app.core.Days
import org.openfocus.app.data.db.BlockMode
import org.openfocus.app.data.db.BlockRuleEntity
import org.openfocus.app.data.db.BlockScheduleEntity
import org.openfocus.app.data.prefs.Settings

data class BlockConfig(
    val rules: Map<String, BlockRuleEntity> = emptyMap(),
    val schedules: List<BlockScheduleEntity> = emptyList(),
    val settings: Settings = Settings(),
)

enum class BlockReason { ALWAYS, DAILY_LIMIT, FOCUS, DEEP_FOCUS, SCHEDULE, SHORTS }

data class BlockDecision(val reason: BlockReason, val detail: String = "")

/** Pure decision logic; unit-tested in BlockPolicyTest. */
object BlockPolicy {

    fun isScheduleActive(s: BlockScheduleEntity, epochDay: Long, minuteOfDay: Int): Boolean {
        if (!s.enabled) return false
        val today = Days.dayIndex(epochDay)
        val yesterday = (today + 6) % 7
        return if (s.startMinute <= s.endMinute) {
            Days.maskHas(s.daysMask, today) && minuteOfDay >= s.startMinute && minuteOfDay < s.endMinute
        } else {
            // Overnight window, e.g. 22:00 -> 07:00. The early-morning part belongs to yesterday's schedule.
            (minuteOfDay >= s.startMinute && Days.maskHas(s.daysMask, today)) ||
                (minuteOfDay < s.endMinute && Days.maskHas(s.daysMask, yesterday))
        }
    }

    fun activeSchedule(config: BlockConfig, epochDay: Long, minuteOfDay: Int): BlockScheduleEntity? =
        config.schedules.firstOrNull { isScheduleActive(it, epochDay, minuteOfDay) }

    /**
     * @param exempt packages that must never be blocked (launcher, dialer, this app, keyboards ...).
     * @param usageMinutesToday lazily computed foreground minutes for [pkg] today.
     */
    fun decide(
        pkg: String,
        config: BlockConfig,
        nowMillis: Long,
        epochDay: Long,
        minuteOfDay: Int,
        exempt: Set<String>,
        usageMinutesToday: (String) -> Int,
    ): BlockDecision? {
        if (pkg in exempt) return null
        val s = config.settings
        val focus = s.activeFocus?.takeIf { it.endsAt > nowMillis }
        val unlockedUntil = s.unlockPasses[pkg] ?: 0L
        val unlocked = unlockedUntil > nowMillis
        // Strict mode: no unlock passes honoured during focus.
        val unlockHonoured = unlocked && !(focus != null && s.strictMode)

        if (s.shieldPaused && focus == null) return null

        val rule = config.rules[pkg]?.takeIf { it.enabled }

        if (focus != null && !unlockHonoured) {
            if (rule != null) return BlockDecision(BlockReason.FOCUS, focus.label)
            if (focus.deep && pkg !in s.focusAllowlist) return BlockDecision(BlockReason.DEEP_FOCUS, focus.label)
        }
        if (rule == null || unlockHonoured) return null

        return when (rule.mode) {
            BlockMode.ALWAYS -> BlockDecision(BlockReason.ALWAYS)
            BlockMode.DAILY_LIMIT -> {
                val used = usageMinutesToday(pkg)
                if (used >= rule.dailyLimitMinutes) BlockDecision(BlockReason.DAILY_LIMIT, "${rule.dailyLimitMinutes} min")
                else activeSchedule(config, epochDay, minuteOfDay)?.let { BlockDecision(BlockReason.SCHEDULE, it.name) }
            }
            BlockMode.FOCUS_AND_SCHEDULE ->
                activeSchedule(config, epochDay, minuteOfDay)?.let { BlockDecision(BlockReason.SCHEDULE, it.name) }
        }
    }
}
