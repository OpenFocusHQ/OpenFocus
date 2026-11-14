package org.openfocus.app.blocking

import org.openfocus.app.core.Days
import org.openfocus.app.data.db.BlockGate
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

/**
 * @param gate HARD = only "Close app" (plus a paid unlock if [canPay]); WAIT = "Continue in app"
 *        becomes available after [waitSeconds]; the user then picks how long to unlock, up to
 *        [allowMinutes] for free (paying coins allows longer).
 * @param canPay whether coins may be spent to unlock / skip the wait (false in strict focus).
 * @param backToApp the offending part of the app was already left (e.g. a Shorts feed), so the
 *        rest of the app is safe to return to straight away.
 */
data class BlockDecision(
    val reason: BlockReason,
    val detail: String = "",
    val gate: BlockGate = BlockGate.HARD,
    val waitSeconds: Int = 0,
    val allowMinutes: Int = 0,
    val canPay: Boolean = true,
    val backToApp: Boolean = false,
)

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
            // Strict mode turns every focus block into a hard block with no paid way through.
            val strict = s.strictMode
            if (rule != null) {
                return if (strict) BlockDecision(BlockReason.FOCUS, focus.label, BlockGate.HARD, canPay = false)
                else gated(rule, BlockReason.FOCUS, focus.label)
            }
            if (focus.deep && pkg !in s.focusAllowlist) {
                return BlockDecision(BlockReason.DEEP_FOCUS, focus.label, BlockGate.HARD, canPay = !strict)
            }
        }
        if (rule == null || unlockHonoured) return null

        return when (rule.mode) {
            BlockMode.ALWAYS -> gated(rule, BlockReason.ALWAYS)
            BlockMode.DAILY_LIMIT -> {
                val used = usageMinutesToday(pkg)
                if (used >= rule.dailyLimitMinutes) gated(rule, BlockReason.DAILY_LIMIT, "${rule.dailyLimitMinutes} min")
                else activeSchedule(config, epochDay, minuteOfDay)?.let { gated(rule, BlockReason.SCHEDULE, it.name) }
            }
            BlockMode.FOCUS_AND_SCHEDULE ->
                activeSchedule(config, epochDay, minuteOfDay)?.let { gated(rule, BlockReason.SCHEDULE, it.name) }
        }
    }

    /** A decision that follows the rule's chosen gate (hard block or wait timer). */
    private fun gated(rule: BlockRuleEntity, reason: BlockReason, detail: String = "") = BlockDecision(
        reason, detail, rule.gate,
        waitSeconds = if (rule.gate == BlockGate.WAIT) rule.waitSeconds.coerceAtLeast(1) else 0,
        allowMinutes = if (rule.gate == BlockGate.WAIT) rule.allowMinutes.coerceAtLeast(1) else 0,
        canPay = rule.allowCoins,
    )
}
