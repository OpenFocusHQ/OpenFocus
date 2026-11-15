package com.openfocushq.openfocus.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.openfocushq.openfocus.data.db.AppDatabase
import com.openfocushq.openfocus.data.db.BlockGate
import com.openfocushq.openfocus.data.db.equippedMap
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun v1ToLatestKeepsProfileAndRules() {
        helper.createDatabase(name, 1).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, displayName, totalXp, hp, maxHp, coins, bestStreak, currentStreak, lastRolloverDay, " +
                    "avatarColor, avatarHat, avatarBackdrop, accountKind, createdAt, updatedAt) " +
                    "VALUES ('p1', 'Hero', 740, 42, 50, 135, 7, 2, 20000, 'color_teal', 'hat_party', NULL, 'local', 0, 0)",
            )
            db.execSQL("INSERT INTO rewards (id, ownerId, title, cost, icon, timesRedeemed, deleted, updatedAt) VALUES ('r1', 'p1', 'Cake', 40, 'Cake', 2, 0, 0)")
            db.execSQL("INSERT INTO block_rules (packageName, label, mode, dailyLimitMinutes, enabled, updatedAt) VALUES ('com.example.feed', 'Feed', 'ALWAYS', 30, 1, 0)")
        }
        helper.runMigrationsAndValidate(name, 7, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, name)
            .allowMainThreadQueries().build()
        val p = runBlocking { db.profileDao().get() }!!
        assertEquals("Hero", p.displayName)
        assertEquals(740, p.totalXp)
        assertEquals("hat_party", p.avatarHat)
        assertNull(p.skinTone)
        // v5: outfits move to the slot map; the legacy columns still resolve until re-saved.
        assertEquals("", p.equipped)
        assertEquals(mapOf("cloak" to "color_teal", "hat" to "hat_party"), p.equippedMap())
        // v3: rules that existed before the pause-screen gates keep their old, fully-blocked behaviour.
        val rule = runBlocking { db.blockDao().observeRules().first() }.single()
        assertEquals(BlockGate.HARD, rule.gate)
        assertEquals(30, rule.waitSeconds)
        assertEquals(10, rule.allowMinutes)
        assertEquals(true, rule.allowCoins) // v4: coins stay allowed for existing rules
        // v6: existing rewards stay plain treats.
        val reward = runBlocking { db.gameDao().reward("r1") }!!
        assertNull(reward.unlockPackage)
        assertEquals(0, reward.unlockMinutes)
        assertEquals(2, reward.timesRedeemed)
        // v7: quest progress starts empty.
        assertNull(runBlocking { db.questDao().active() })
        db.close()
    }
}
