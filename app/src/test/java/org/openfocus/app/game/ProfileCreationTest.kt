package org.openfocus.app.game

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openfocus.app.data.db.AppDatabase
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileCreationTest {
    /** First launch: the app's startup work and the UI ask for the profile at the same time. */
    @Test fun concurrentFirstCallsCreateExactlyOneProfile() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val game = GameEngine(db)
        val ids = (1..16).map { async(Dispatchers.IO) { game.profile().id } }.awaitAll().toSet()
        assertEquals(1, ids.size)
        assertEquals(1, db.query("SELECT COUNT(*) FROM profiles", null).use { it.moveToFirst(); it.getInt(0) })
        db.close()
    }
}
