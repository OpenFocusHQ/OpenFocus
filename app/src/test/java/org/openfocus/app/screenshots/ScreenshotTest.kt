package org.openfocus.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openfocus.app.OpenFocusApp
import org.openfocus.app.core.Days
import org.openfocus.app.data.db.BlockMode
import org.openfocus.app.data.db.BlockRuleEntity
import org.openfocus.app.data.db.Difficulty
import org.openfocus.app.data.db.GoalEntity
import org.openfocus.app.data.db.HabitEntity
import org.openfocus.app.data.db.HabitFrequency
import org.openfocus.app.data.db.HabitKind
import org.openfocus.app.data.db.HabitLogEntity
import org.openfocus.app.data.db.newId
import org.openfocus.app.ui.nav.OpenFocusRoot
import org.openfocus.app.ui.theme.OpenFocusTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File

/**
 * Renders key screens with sample data to docs/screenshots. Run with:
 *   ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
open class ScreenshotTest {
    /** Subfolder of docs/screenshots for this variant ("" = light theme). */
    protected open val variant = ""

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var goalId = ""
    private val app get() = ApplicationProvider.getApplicationContext<OpenFocusApp>()

    private fun seed() = runBlocking {
        val c = app.container
        c.settings.update { it.copy(onboardingDone = true) }
        c.game.updateProfile { it.copy(displayName = "Aria", totalXp = 740, coins = 135, hp = 42, currentStreak = 4, avatarHat = "hat_party", avatarBackdrop = "bg_forest", skinTone = "medium") }
        val today = Days.today()
        val created = System.currentTimeMillis() - 30L * 86_400_000
        val goal = GoalEntity(ownerId = "", title = "Run a half marathon", why = "Prove to myself I can stick with something hard.", summitName = "Half Marathon Peak", targetEpochDay = today + 120, colorIndex = 1)
        c.goals.save(goal, listOf("Run 5 km non-stop", "Run 10 km", "Sign up for a race", "Run 16 km", "Race day"))
        c.db.goalDao().observeMilestones(goal.id).first().take(2).forEach { c.db.goalDao().upsertMilestone(it.copy(done = true)) }
        goalId = goal.id
        val habits = listOf(
            HabitEntity(ownerId = "", title = "Read 10 pages", icon = "Book", colorIndex = 0, createdAt = created, difficulty = Difficulty.EASY),
            HabitEntity(ownerId = "", title = "Morning run", icon = "Run", colorIndex = 1, createdAt = created, goalId = goal.id, difficulty = Difficulty.HARD),
            HabitEntity(ownerId = "", title = "Drink water", icon = "Water", colorIndex = 4, target = 6, createdAt = created),
            HabitEntity(ownerId = "", title = "Call a friend", icon = "Call", colorIndex = 5, frequency = HabitFrequency.WEEKLY, target = 2, createdAt = created),
            HabitEntity(ownerId = "", title = "Doomscrolling", icon = "Phone", colorIndex = 2, kind = HabitKind.BREAK, createdAt = created),
        )
        habits.forEach { c.habits.save(it) }
        val dao = c.db.habitDao()
        for (d in 1..12) dao.upsertLog(HabitLogEntity(newId(), habits[0].id, today - d, 1))
        for (d in 0..20 step 2) dao.upsertLog(HabitLogEntity(newId(), habits[1].id, today - d, 1))
        dao.upsertLog(HabitLogEntity(newId(), habits[2].id, today, 3))
        dao.upsertLog(HabitLogEntity(newId(), habits[4].id, today - 5, 1))
        c.blocks.upsertRule(BlockRuleEntity("com.example.social", "SocialApp", BlockMode.DAILY_LIMIT, 30))
        c.blocks.upsertRule(BlockRuleEntity("com.example.feed", "FeedApp", BlockMode.ALWAYS))
    }

    private fun shoot(name: String, route: String?, after: (() -> Unit)? = null) {
        compose.setContent { OpenFocusTheme { OpenFocusRoot(pendingRoute = route, onRouteConsumed = {}) } }
        compose.waitForIdle()
        after?.invoke()
        compose.waitForIdle()
        Thread.sleep(300)
        compose.mainClock.advanceTimeBy(2_000) // let one-shot animations (e.g. the climber) settle
        compose.waitForIdle()
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots", variant).apply { mkdirs() }
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        view.draw(canvas)
        // Dialogs live in their own window: draw the latest one on top, centred over a scrim.
        ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView?.let { d ->
            canvas.drawColor(0x99000000.toInt())
            canvas.save()
            canvas.translate((bmp.width - d.width) / 2f, (bmp.height - d.height) / 2f)
            d.draw(canvas)
            canvas.restore()
        }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun today() { seed(); shoot("today", null) }
    @Test fun habits() { seed(); shoot("habits", "habits") }
    @Test fun focus() { seed(); shoot("focus", "focus") }
    @Test fun goals() { seed(); shoot("goals", "goals") }
    @Test fun shield() { seed(); shoot("shield", "shield") }
    @Test fun goalDetail() { seed(); shoot("goal_detail", "goal/$goalId") }
    @Test fun emptyToday() {
        runBlocking { app.container.settings.update { it.copy(onboardingDone = true) } }
        shoot("empty_today", null)
    }

    @Test fun celebration() {
        seed()
        shoot("celebration", null) {
            // Enough XP for a level-up, which also unlocks an achievement.
            runBlocking { app.container.game.apply("test", xp = 300, message = "Level up!") }
        }
    }
    @Test fun hero() { seed(); runBlocking { app.container.game.syncAchievements() }; shoot("hero", "hero") }
}
