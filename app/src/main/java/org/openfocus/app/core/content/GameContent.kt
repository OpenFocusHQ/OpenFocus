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

/** A character who tells the story. Their portrait is generated from [color]/[skin]/[hat] unless hand-drawn. */
@Serializable
data class NpcDef(
    val id: String,
    val name: String,
    val title: String = "",
    /** Cloak colour for the generated portrait, "#RRGGBB". */
    val color: String = "#7C6CF2",
    /** "light" | "medium" | "deep". */
    val skin: String = "light",
    /** Id of a hat item worn in the generated portrait, or null. */
    val hat: String? = null,
    /** Drawable name of the portrait (32x32), e.g. "px_npc_mira". */
    val sprite: String? = null,
    /**
     * Lines by situation. The app uses "greeting" (quest card while a quest of theirs is active),
     * "idle" (no quest running) and "comeback" (after a missed day); other keys are free for future use.
     * `{hero}` becomes the player's name.
     */
    val dialogue: Map<String, List<String>> = emptyMap(),
)

/** One line of a conversation. [speaker] is an NPC id or "hero". */
@Serializable
data class DialogueLine(val speaker: String, val text: String)

enum class ObjectiveMode {
    /** Progress counts from when the stage started (e.g. "do 3 habits"). */
    GAIN,
    /** An absolute target (e.g. "reach level 5", "a 7-day streak"). */
    REACH,
}

@Serializable
data class ObjectiveDef(
    /** One of [org.openfocus.app.core.GameStats.STATS]. */
    val stat: String,
    val mode: ObjectiveMode = ObjectiveMode.GAIN,
    val amount: Int,
)

/** A boss or monster fought in a [StageKind.BOSS] stage. Portrait generated from [color]/[shape]. */
@Serializable
data class EnemyDef(
    val id: String,
    val name: String,
    val description: String = "",
    /** Health; your actions deal damage until it reaches 0. */
    val hp: Int,
    /** HP the hero loses per missed daily habit or logged slip while fighting (0 = harmless). */
    val strength: Int = 0,
    val color: String = "#F06A4D",
    /** Generated portrait shape: "slime", "ghost" or "golem". */
    val shape: String = "slime",
    /** Drawable name of the portrait (48x48), e.g. "px_enemy_scroll_spirit". */
    val sprite: String? = null,
    /** Taunts by situation: "appear", "hit" (when it strikes you), "defeat". `{hero}` is replaced. */
    val dialogue: Map<String, List<String>> = emptyMap(),
)

/** Items to gather in a [StageKind.COLLECT] stage; they drop from your actions. */
@Serializable
data class CollectDef(
    /** Singular / plural display names, e.g. "trail marker" / "trail markers". */
    val item: String,
    val itemPlural: String = item + "s",
    /** Material icon name for the item (see AppIcons). */
    val icon: String = "Flag",
    val amount: Int,
)

enum class StageKind { OBJECTIVE, BOSS, COLLECT }

@Serializable
data class StageRewardDef(val xp: Int = 0, val coins: Int = 0, /** Cosmetic item id granted for free. */ val item: String? = null)

@Serializable
data class StageDef(
    val id: String,
    val title: String,
    /** Narration shown on the quest card / journal. `{hero}` is replaced. */
    val story: String = "",
    /** Conversation when the stage starts. */
    val intro: List<DialogueLine> = emptyList(),
    /** Exactly one of [objective], [boss] or [collect] describes how the stage is won. */
    val objective: ObjectiveDef? = null,
    /** Enemy id to defeat (Habitica-style boss fight). */
    val boss: String? = null,
    val collect: CollectDef? = null,
    /** Conversation when the stage is completed. */
    val outro: List<DialogueLine> = emptyList(),
    val reward: StageRewardDef = StageRewardDef(),
) {
    val kind: StageKind get() = when {
        boss != null -> StageKind.BOSS
        collect != null -> StageKind.COLLECT
        else -> StageKind.OBJECTIVE
    }
}

/** A multi-stage storyline told by an NPC. */
@Serializable
data class QuestLineDef(
    val id: String,
    val title: String,
    val summary: String = "",
    /** NPC id who gives the quest. */
    val npc: String,
    val unlockLevel: Int = 1,
    /** Another quest line that must be completed first, or null. */
    val requires: String? = null,
    /** Coins for the quest scroll (0 = free from the quest board). */
    val cost: Int = 0,
    /** Can be started again after finishing (e.g. a boss you can re-fight). */
    val repeatable: Boolean = false,
    val stages: List<StageDef>,
)

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
    val questLines: List<QuestLineDef> = emptyList(),
    val npcs: List<NpcDef> = emptyList(),
    val enemies: List<EnemyDef> = emptyList(),
) {
    fun enemy(id: String?) = enemies.firstOrNull { it.id == id }
    fun npc(id: String?) = npcs.firstOrNull { it.id == id }
    fun questLine(id: String?) = questLines.firstOrNull { it.id == id }

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

/** Fill in `{hero}` with the player's name. */
fun String.withHero(name: String): String = replace("{hero}", name)
