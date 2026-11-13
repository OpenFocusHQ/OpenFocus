package org.openfocus.app.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openfocus.app.data.db.AppDatabase
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun v1ToLatestKeepsProfile() {
        helper.createDatabase(name, 1).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, displayName, totalXp, hp, maxHp, coins, bestStreak, currentStreak, lastRolloverDay, " +
                    "avatarColor, avatarHat, avatarBackdrop, accountKind, createdAt, updatedAt) " +
                    "VALUES ('p1', 'Hero', 740, 42, 50, 135, 7, 2, 20000, 'color_teal', 'hat_party', NULL, 'local', 0, 0)",
            )
        }
        helper.runMigrationsAndValidate(name, 2, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, name)
            .allowMainThreadQueries().build()
        val p = runBlocking { db.profileDao().get() }!!
        assertEquals("Hero", p.displayName)
        assertEquals(740, p.totalXp)
        assertEquals("hat_party", p.avatarHat)
        assertNull(p.skinTone)
        db.close()
    }
}
