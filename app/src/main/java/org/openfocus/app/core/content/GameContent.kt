package org.openfocus.app.core.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.openfocus.app.data.db.Difficulty
import org.openfocus.app.data.db.HabitFrequency
import org.openfocus.app.data.db.HabitKind

/*
 * All game content (cosmetics, achievements, starter quests, titles, messages) lives in
 * src/main/resources/content/content.json and is edited with the developer tool in
 * tools/content-editor. Nothing about specific items is hard-coded in Kotlin.
 */

enum class SlotKind {
    /** 48x48 round scene drawn behind the hero. */
    BACKDROP,
    /** 32x32 sheet that animates with the body (same rows/frames as the hero body), e.g. cloaks. */
    BODY,
    /** 32x32 single-frame overlay that follows an anchor on the body ("head", "body"). */
    OVERLAY,
}

@Serializable
data class SlotDef(
    val id: String,
    val name: String,
    /** Draw order; the hero's body sits at layer 1. */
    val layer: Int,
    val kind: SlotKind = SlotKind.OVERLAY,
    val anchor: String? = null,
    /** A required slot always has something equipped (falls back to [defaultItem]). */
    val required: Boolean = false,
    val defaultItem: String? = null,
    /** Sprite shown when nothing is equipped (backdrops). */
    val defaultSprite: String? = null,
)

@Serializable
data class ItemDef(
    val id: String,
    val name: String,
    val slot: String,
    val price: Int,
    val minLevel: Int = 1,
    /** "#RRGGBB": shop accent and shape-drawn fallback colour. */
    val color: String = "#7C6CF2",
    /** Drawable name in res/drawable-nodpi (without extension), or null if art isn't drawn yet. */
    val sprite: String? = null,
    /** Animation frames in the sheet's first row (e.g. twinkling night sky). */
    val frames: Int = 1,
    val fps: Float = 1f,
    val description: String = "",
) {
    val argb: Long get() = 0xFF000000 or color.removePrefix("#").toLong(16)
}

@Serializable
data class AchievementDef(
    val id: String,
    val title: String,
    val description: String,
    val icon: String = "Star",
    val xp: Int = 0,
    val coins: Int = 0,
    /** One of [org.openfocus.app.core.GameStats.STATS]. */
    val stat: String,
    val target: Int,
    /** Medal tier for the generated badge: bronze, silver or gold. */
    val tier: String = "bronze",
)

@Serializable
data class QuestDef(
    val id: String,
    val title: String,
    val icon: String = "Star",
    val color: Int = 0,
    val kind: HabitKind = HabitKind.BUILD,
    val difficulty: Difficulty = Difficulty.EASY,
    val frequency: HabitFrequency = HabitFrequency.DAILY,
    val target: Int = 1,
    /** Offered on the onboarding "pick a few starter quests" page. */
    val starter: Boolean = true,
)

@Serializable
data class TitleDef(val minLevel: Int, val title: String)

@Serializable
data class MessagesDef(
    val general: List<String>,
    val resist: List<String>,
    val blocked: List<String>,
    val shorts: List<String>,
    val comeback: List<String>,
)

@Serializable
data class GameContentData(
    val version: Int = 1,
    val slots: List<SlotDef>,
    val items: List<ItemDef>,
    val achievements: List<AchievementDef>,
    val quests: List<QuestDef>,
    val titles: List<TitleDef>,
    val messages: MessagesDef,
) {
    fun slot(id: String) = slots.firstOrNull { it.id == id }
    fun item(id: String?) = items.firstOrNull { it.id == id }
    fun itemsIn(slot: String) = items.filter { it.slot == slot }
}

/** Loads content.json once from the classpath (works in the APK and in plain JVM tests). */
object GameContent {
    const val RESOURCE = "content/content.json"

    val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = false }

    @Volatile private var override: GameContentData? = null

    val current: GameContentData by lazy { override ?: load() }

    fun parse(text: String): GameContentData = json.decodeFromString(GameContentData.serializer(), text)

    private fun load(): GameContentData {
        val stream = GameContent::class.java.classLoader?.getResourceAsStream(RESOURCE)
            ?: error("$RESOURCE missing from the classpath")
        return stream.bufferedReader().use { parse(it.readText()) }
    }
}
