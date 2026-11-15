package org.openfocus.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openfocus.app.core.content.GameContent
import org.openfocus.app.data.db.AppDatabase
import org.openfocus.app.data.db.QuestStatus
import org.openfocus.app.data.prefs.SettingsStore
import org.openfocus.app.data.repo.Conversation
import org.openfocus.app.data.repo.QuestRepository
import org.openfocus.app.data.repo.QuestState
import org.openfocus.app.game.EventTypes
import org.openfocus.app.game.GameEngine
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class QuestRepositoryTest {
    private val content = GameContent.parse(
        """
        {"slots":[],"items":[{"id":"prize","name":"Prize","slot":"hat","price":0}],"achievements":[],"quests":[],
         "titles":[{"minLevel":1,"title":"T"}],"messages":{"general":["g"],"resist":["r"],"blocked":["b"],"shorts":["s"],"comeback":["c"]},
         "npcs":[{"id":"guide","name":"Guide","dialogue":{"greeting":["Hi {hero}"]}}],
         "enemies":[{"id":"blob","name":"Blob","hp":20,"strength":5,"dialogue":{"appear":["Rawr"],"hit":["Gotcha"],"defeat":["Bleh"]}}],
         "questLines":[
          {"id":"story","title":"Story","npc":"guide","stages":[
            {"id":"a","title":"Do 2 habits","intro":[{"speaker":"guide","text":"Go, {hero}!"}],"objective":{"stat":"habitCompletions","amount":2},
             "outro":[{"speaker":"guide","text":"Nice."}],"reward":{"xp":10,"coins":5}},
            {"id":"b","title":"Beat the blob","boss":"blob","reward":{"coins":7,"item":"prize"}}]},
          {"id":"gather","title":"Gather","npc":"guide","requires":"story","cost":10,"repeatable":true,"stages":[
            {"id":"g","title":"Find 2 shells","collect":{"item":"shell","amount":2}}]},
          {"id":"late","title":"Late","npc":"guide","unlockLevel":50,"stages":[{"id":"x","title":"x","objective":{"stat":"level","mode":"REACH","amount":50}}]}
         ]}
        """.trimIndent(),
    )

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
    private val game = GameEngine(db)
    private val settings = SettingsStore(ctx).also { s -> s.update { it.copy(gentleMode = false) } }
    private val alwaysDrop = object : Random() { override fun nextBits(bitCount: Int) = 0 } // nextDouble() == 0.0
    private val quests = QuestRepository(db, game, settings, org.openfocus.app.core.Strings.of(ctx), content = { content }, random = alwaysDrop)
        .also { q -> game.listeners += { e -> q.onEvent(e) } }
    private val talks = mutableListOf<Conversation>()

    @After fun close() = db.close()

    private fun board() = runBlocking { quests.observeBoard().first() }
    private fun state(id: String) = board().entries.first { it.line.id == id }.state
    private fun habit(xp: Int = 12) = runBlocking { game.apply(EventTypes.HABIT_DONE, "h", xp = xp) }
    /** Stats count real habit logs, so objective tests insert a habit + log like the app does. */
    private fun logHabits(n: Int) = runBlocking {
        val h = org.openfocus.app.data.db.HabitEntity(ownerId = "", title = "h")
        db.habitDao().upsert(h)
        db.habitDao().upsertLog(org.openfocus.app.data.db.HabitLogEntity(habitId = h.id, epochDay = 1, count = n))
        game.apply(EventTypes.HABIT_DONE, h.id, xp = 1)
    }

    @Test fun locksFollowLevelAndPrerequisites() {
        runBlocking { game.profile() }
        assertEquals(QuestState.AVAILABLE, state("story"))
        assertEquals(QuestState.LOCKED, state("gather"))  // needs "story"
        assertEquals(QuestState.LOCKED, state("late"))    // level 50
        assertFalse(runBlocking { quests.accept("gather") })
    }

    @Test fun storyObjectiveThenBossFight() = runBlocking {
        val collector = CoroutineScope(Dispatchers.Unconfined).launch { quests.conversations.collect { talks += it } }
        assertTrue(quests.accept("story"))
        assertEquals(QuestState.ACTIVE, state("story"))
        assertTrue(talks.last().lines.first().text.startsWith("Go, ")) // {hero} filled in

        logHabits(2) // objective: gain 2 habit completions since the stage started
        val boss = board().active!!
        assertEquals("b", boss.stage.id)
        assertEquals(20, boss.current) // full HP
        assertEquals("Rawr", talks.last().lines.last().text) // boss appears after the outro

        habit(xp = 12)
        assertEquals(8, board().active!!.current)
        val coinsBefore = game.profile().coins
        habit(xp = 12) // overkill is fine
        assertNull(board().active)
        assertEquals(QuestState.COMPLETED, state("story"))
        assertEquals(coinsBefore + 7, game.profile().coins)
        assertTrue(db.gameDao().observeInventory().first().any { it.itemId == "prize" })
        assertEquals("Bleh", talks.last().lines.first().text)
        collector.cancel()
    }

    @Test fun bossStrikesBackUnlessGentle() = runBlocking {
        quests.accept("story"); logHabits(2) // now fighting the blob
        val hp = game.profile().hp
        game.apply(EventTypes.HABIT_MISSED, "2") // two missed dailies at rollover
        assertEquals(hp - 10, game.profile().hp)
        game.apply(EventTypes.HABIT_SLIP, "h")
        assertEquals(hp - 15, game.profile().hp)
        settings.update { it.copy(gentleMode = true) }
        game.apply(EventTypes.HABIT_SLIP, "h")
        assertEquals(hp - 15, game.profile().hp)
    }

    @Test fun collectionQuestCostsCoinsAndIsRepeatable() = runBlocking {
        quests.accept("story"); logHabits(2); habit(30) // finish the story first
        game.apply("test", coins = 100)
        assertEquals(QuestState.AVAILABLE, state("gather"))
        val before = game.profile().coins
        assertTrue(quests.accept("gather"))
        assertEquals(before - 10, game.profile().coins) // bought the scroll
        assertEquals(1, db.questDao().observeAll().first().count { it.status == QuestStatus.ACTIVE })
        habit(); habit() // always drops in this test
        assertEquals(QuestState.AVAILABLE, state("gather")) // repeatable: back on the board
        assertEquals(1, db.questDao().get("gather")!!.timesCompleted)
    }

    @Test fun onlyOneQuestAtATime() = runBlocking {
        quests.accept("story"); logHabits(2); habit(30)
        game.apply("test", coins = 100)
        quests.accept("gather")
        assertEquals(QuestState.LOCKED, state("story").let { if (it == QuestState.COMPLETED) QuestState.LOCKED else it })
        assertFalse(quests.accept("story")) // completed and not repeatable
    }
}
