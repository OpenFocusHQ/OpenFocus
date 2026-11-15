package com.openfocushq.openfocus.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.openfocushq.openfocus.core.content.GameContent
import com.openfocushq.openfocus.data.db.ProfileEntity
import com.openfocushq.openfocus.data.db.QuestProgressEntity
import com.openfocushq.openfocus.data.db.QuestStatus
import com.openfocushq.openfocus.data.repo.ActiveQuest
import com.openfocushq.openfocus.data.repo.QuestBoard
import com.openfocushq.openfocus.data.repo.QuestEntry
import com.openfocushq.openfocus.data.repo.QuestLock
import com.openfocushq.openfocus.data.repo.QuestState
import com.openfocushq.openfocus.ui.quests.DialogueCard
import com.openfocushq.openfocus.ui.quests.QuestsContent
import com.openfocushq.openfocus.ui.theme.OpenFocusTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Renders the quest board, a boss fight and NPC dialogue to docs/screenshots/quest_*.png. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class QuestScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val c = GameContent.current

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { OpenFocusTheme { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() } } }
        compose.mainClock.advanceTimeByFrame()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun entry(id: String, state: QuestState, lock: QuestLock? = null, times: Int = 0) =
        QuestEntry(c.questLine(id)!!, state, QuestProgressEntity(id, timesCompleted = times, status = QuestStatus.COMPLETED).takeIf { times > 0 }, lock)

    private fun board(active: ActiveQuest?) = QuestBoard(active, listOf(
        entry("first_ascent", QuestState.COMPLETED, times = 1),
        entry("slime_hunt", QuestState.AVAILABLE),
        entry("lost_markers", QuestState.AVAILABLE),
        entry("quiet_mind", QuestState.ACTIVE),
        entry("golem", QuestState.LOCKED, QuestLock.Level(5)),
    ))

    private val bossFight: ActiveQuest get() {
        val line = c.questLine("quiet_mind")!!
        val stage = line.stages.first { it.boss != null }
        val enemy = c.enemy(stage.boss)!!
        return ActiveQuest(line, line.stages.indexOf(stage), stage, c.npc(line.npc), enemy, current = enemy.hp - 47, target = enemy.hp)
    }

    private val storyStage: ActiveQuest get() {
        val line = c.questLine("first_ascent")!!
        val stage = line.stages[1]
        return ActiveQuest(line, 1, stage, c.npc(line.npc), null, current = 15, target = stage.objective!!.amount)
    }

    @Test fun bossFight() = shoot("quest_boss") { QuestsContent(board(bossFight), coins = 75, gentle = false, onStart = {}, onTalk = {}, onLeave = {}) }

    @Test fun storyStage() = shoot("quest_story") { QuestsContent(board(storyStage), coins = 20, gentle = true, onStart = {}, onTalk = {}, onLeave = {}) }

    @Test fun dialogue() = shoot("quest_dialogue") {
        Box(Modifier.fillMaxSize().background(Color(0x99000000)).padding(24.dp), contentAlignment = Alignment.Center) {
            val line = c.questLine("first_ascent")!!.stages.first().intro.first()
            DialogueCard(c.questLine("first_ascent")!!.title, line.copy(text = line.text.replace("{hero}", "Aria")), ProfileEntity(displayName = "Aria"), onTap = {})
        }
    }

    @Test fun bossTaunt() = shoot("quest_dialogue_boss") {
        Box(Modifier.fillMaxSize().background(Color(0x99000000)).padding(24.dp), contentAlignment = Alignment.Center) {
            val e = c.enemy("scroll_spirit")!!
            DialogueCard(c.questLine("quiet_mind")!!.title, com.openfocushq.openfocus.core.content.DialogueLine(e.id, e.dialogue["appear"]!!.first()), null, onTap = {})
        }
    }
}
