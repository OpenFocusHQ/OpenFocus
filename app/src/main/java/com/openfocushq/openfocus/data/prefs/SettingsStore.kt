package com.openfocushq.openfocus.data.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import com.openfocushq.openfocus.core.Days
import com.openfocushq.openfocus.core.GameRules
import com.openfocushq.openfocus.data.db.BlockGate

/** A focus session that is currently running. Persisted so it survives process death. */
data class ActiveFocus(
    val id: String,
    val startedAt: Long,
    val endsAt: Long,
    val plannedMinutes: Int,
    val label: String,
    val goalId: String?,
    val deep: Boolean,
)

enum class ShortsPlatform(val label: String, val packageName: String, val note: String) {
    YOUTUBE("YouTube Shorts", "com.google.android.youtube", "Blocks the Shorts player; the rest of YouTube still works."),
    INSTAGRAM("Instagram Reels", "com.instagram.android", "Blocks the Reels viewer; DMs and feed still work."),
    TIKTOK("TikTok", "com.zhiliaoapp.musically", "TikTok is all short video, so the whole app is blocked."),
    TIKTOK_LITE("TikTok Lite", "com.zhiliaoapp.musically.go", "Whole app is blocked."),
    SNAPCHAT("Snapchat Spotlight", "com.snapchat.android", "Experimental: blocks the Spotlight tab when detected."),
}

data class Settings(
    val onboardingDone: Boolean = false,
    val shorts: Set<ShortsPlatform> = setOf(ShortsPlatform.YOUTUBE, ShortsPlatform.INSTAGRAM, ShortsPlatform.TIKTOK),
    val strictMode: Boolean = false,
    val gentleMode: Boolean = true,
    val shieldPaused: Boolean = false,
    val unlockPasses: Map<String, Long> = emptyMap(),
    val unlocksToday: Int = 0,
    val unlocksDay: Long = 0,
    val activeFocus: ActiveFocus? = null,
    val reminderHour: Int = 20,
    val lastReminderDay: Long = 0,
    val focusAllowlist: Set<String> = emptySet(),
    // Pause screen: defaults for newly blocked apps
    val defaultGate: BlockGate = BlockGate.WAIT,
    val defaultWaitSeconds: Int = 30,
    val defaultAllowMinutes: Int = 10,
    // Pause screen: coin prices
    val skipCostPer10s: Int = GameRules.DEFAULT_SKIP_COST_PER_10S,
    val unlockCostPerMinute: Int = GameRules.DEFAULT_UNLOCK_COST_PER_MINUTE,
    val escalatingPrices: Boolean = true,
    // Pause screen: message ({app} is replaced with the app's name; blank = built-in text)
    val pauseQuestion: String = "",
    val pauseReminder: String = "",
    // Pause screen: look (ids from blocking.PauseIcon / PauseColors)
    val pauseIcon: String = "shield",
    val pauseColors: String = "system",
) {
    fun unlocksTodayEffective(today: Long = Days.today()) = if (unlocksDay == today) unlocksToday else 0

    /** Unlocks that count towards escalating prices (0 when escalation is turned off). */
    fun priceStep(today: Long = Days.today()) = if (escalatingPrices) unlocksTodayEffective(today) else 0

    fun skipCost(waitSeconds: Int) = GameRules.skipWaitCost(waitSeconds, priceStep(), skipCostPer10s)
    fun unlockCost(minutes: Int) = GameRules.unlockCost(minutes, priceStep(), unlockCostPerMinute)

    /**
     * Price of a paid unlock for [minutes]: longer unlocks cost more. On a wait-timer app, skipping
     * the wait ([skipWaitSeconds]) adds its own fee on top.
     */
    fun paidUnlockCost(minutes: Int, skipWaitSeconds: Int? = null): Int =
        unlockCost(minutes) + (skipWaitSeconds?.let { skipCost(it) } ?: 0)
}

/**
 * Tiny SharedPreferences-backed settings store exposing a StateFlow so both the UI and the
 * accessibility service (which needs synchronous reads) see the same values.
 */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("openfocus", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val value: Settings get() = _state.value

    @Synchronized
    fun update(transform: (Settings) -> Settings) {
        val next = transform(_state.value)
        write(next)
        _state.value = next
    }

    private fun read(): Settings = Settings(
        onboardingDone = prefs.getBoolean("onboardingDone", false),
        shorts = prefs.getStringSet("shorts", null)
            ?.mapNotNull { runCatching { ShortsPlatform.valueOf(it) }.getOrNull() }?.toSet()
            ?: Settings().shorts,
        strictMode = prefs.getBoolean("strictMode", false),
        gentleMode = prefs.getBoolean("gentleMode", true),
        shieldPaused = prefs.getBoolean("shieldPaused", false),
        unlockPasses = prefs.getString("unlockPasses", null)?.let { parseMap(it) } ?: emptyMap(),
        unlocksToday = prefs.getInt("unlocksToday", 0),
        unlocksDay = prefs.getLong("unlocksDay", 0),
        activeFocus = prefs.getString("activeFocus", null)?.let { parseFocus(it) },
        reminderHour = prefs.getInt("reminderHour", 20),
        lastReminderDay = prefs.getLong("lastReminderDay", 0),
        focusAllowlist = prefs.getStringSet("focusAllowlist", emptySet()) ?: emptySet(),
        defaultGate = runCatching { BlockGate.valueOf(prefs.getString("defaultGate", null) ?: "") }.getOrDefault(BlockGate.WAIT),
        defaultWaitSeconds = prefs.getInt("defaultWaitSeconds", 30),
        defaultAllowMinutes = prefs.getInt("defaultAllowMinutes", 10),
        skipCostPer10s = prefs.getInt("skipCostPer10s", GameRules.DEFAULT_SKIP_COST_PER_10S),
        unlockCostPerMinute = prefs.getInt("unlockCostPerMinute", GameRules.DEFAULT_UNLOCK_COST_PER_MINUTE),
        escalatingPrices = prefs.getBoolean("escalatingPrices", true),
        pauseQuestion = prefs.getString("pauseQuestion", "") ?: "",
        pauseReminder = prefs.getString("pauseReminder", "") ?: "",
        pauseIcon = prefs.getString("pauseIcon", "shield") ?: "shield",
        pauseColors = prefs.getString("pauseColors", "system") ?: "system",
    )

    private fun write(s: Settings) {
        prefs.edit()
            .putBoolean("onboardingDone", s.onboardingDone)
            .putStringSet("shorts", s.shorts.map { it.name }.toSet())
            .putBoolean("strictMode", s.strictMode)
            .putBoolean("gentleMode", s.gentleMode)
            .putBoolean("shieldPaused", s.shieldPaused)
            .putString("unlockPasses", JSONObject(s.unlockPasses.mapValues { it.value }).toString())
            .putInt("unlocksToday", s.unlocksToday)
            .putLong("unlocksDay", s.unlocksDay)
            .putString("activeFocus", s.activeFocus?.let { focusJson(it) })
            .putInt("reminderHour", s.reminderHour)
            .putLong("lastReminderDay", s.lastReminderDay)
            .putStringSet("focusAllowlist", s.focusAllowlist)
            .putString("defaultGate", s.defaultGate.name)
            .putInt("defaultWaitSeconds", s.defaultWaitSeconds)
            .putInt("defaultAllowMinutes", s.defaultAllowMinutes)
            .putInt("skipCostPer10s", s.skipCostPer10s)
            .putInt("unlockCostPerMinute", s.unlockCostPerMinute)
            .putBoolean("escalatingPrices", s.escalatingPrices)
            .putString("pauseQuestion", s.pauseQuestion)
            .putString("pauseReminder", s.pauseReminder)
            .putString("pauseIcon", s.pauseIcon)
            .putString("pauseColors", s.pauseColors)
            .apply()
    }

    private fun parseMap(json: String): Map<String, Long> = runCatching {
        val o = JSONObject(json)
        o.keys().asSequence().associateWith { o.getLong(it) }
    }.getOrDefault(emptyMap())

    private fun focusJson(f: ActiveFocus) = JSONObject().apply {
        put("id", f.id); put("startedAt", f.startedAt); put("endsAt", f.endsAt)
        put("plannedMinutes", f.plannedMinutes); put("label", f.label)
        put("goalId", f.goalId ?: ""); put("deep", f.deep)
    }.toString()

    private fun parseFocus(json: String): ActiveFocus? = runCatching {
        val o = JSONObject(json)
        ActiveFocus(
            id = o.getString("id"), startedAt = o.getLong("startedAt"), endsAt = o.getLong("endsAt"),
            plannedMinutes = o.getInt("plannedMinutes"), label = o.optString("label"),
            goalId = o.optString("goalId").ifBlank { null }, deep = o.optBoolean("deep"),
        )
    }.getOrNull()
}
