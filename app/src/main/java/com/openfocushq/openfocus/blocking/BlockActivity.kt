package com.openfocushq.openfocus.blocking

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.openfocushq.openfocus.OpenFocusApp
import com.openfocushq.openfocus.core.Encouragement
import com.openfocushq.openfocus.core.GameRules
import com.openfocushq.openfocus.data.db.BlockGate
import com.openfocushq.openfocus.data.db.RewardEntity
import com.openfocushq.openfocus.game.EventTypes
import com.openfocushq.openfocus.ui.pixel.PixelSprite
import com.openfocushq.openfocus.ui.pixel.SpriteCatalog
import com.openfocushq.openfocus.ui.theme.OpenFocusTheme

/**
 * The pause screen shown over a blocked app (Ascent-style: the app stays open underneath and
 * the user decides). The rule's gate controls the way through:
 *  - HARD: "Close app" only; coins can buy an unlock pass (gift button) unless strict focus.
 *  - WAIT: "Continue in app" fills up over the wait time, then opens the app for a while;
 *          coins can skip the wait.
 * Leaving the screen (home, recents, screen off) discards it, so the wait starts over next time.
 */
class BlockActivity : ComponentActivity() {

    private val container by lazy { (application as OpenFocusApp).container }

    /** Set when the user chose a way into the app, so onStop doesn't treat it as walking away. */
    private var leavingToApp = false

    /** Drives the fade-out back into the app before the activity finishes. */
    private val fadingOut = androidx.compose.runtime.mutableStateOf(false)

    private data class Args(
        val pkg: String,
        val reason: BlockReason,
        val detail: String,
        val gate: BlockGate,
        val waitSeconds: Int,
        val allowMinutes: Int,
        val canPay: Boolean,
        val backToApp: Boolean,
        val initialElapsedMs: Long,
    )

    private fun args(i: Intent) = Args(
        pkg = i.getStringExtra(EXTRA_PACKAGE) ?: "",
        reason = runCatching { BlockReason.valueOf(i.getStringExtra(EXTRA_REASON) ?: "") }.getOrDefault(BlockReason.ALWAYS),
        detail = i.getStringExtra(EXTRA_DETAIL) ?: "",
        gate = runCatching { BlockGate.valueOf(i.getStringExtra(EXTRA_GATE) ?: "") }.getOrDefault(BlockGate.HARD),
        waitSeconds = i.getIntExtra(EXTRA_WAIT_SECONDS, 0),
        allowMinutes = i.getIntExtra(EXTRA_ALLOW_MINUTES, 0),
        canPay = i.getBooleanExtra(EXTRA_CAN_PAY, true),
        backToApp = i.getBooleanExtra(EXTRA_BACK_TO_APP, false),
        initialElapsedMs = i.getLongExtra(EXTRA_INITIAL_ELAPSED_MS, 0L),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = closeApp(resisted = false)
        })
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render()
    }

    override fun onStop() {
        super.onStop()
        // Walking away resets the pause: the next attempt starts a fresh wait.
        if (!isChangingConfigurations && !isFinishing && !leavingToApp) finish()
    }

    private fun render() {
        val a = args(intent)
        val label = InstalledApps.label(this, a.pkg)
        setContent {
            OpenFocusTheme {
                PauseScreen(
                    a, label,
                    onClose = { closeApp(resisted = true) },
                    onContinue = { minutes -> continueToApp { container.shop.grantWaitedPass(a.pkg, minutes); true } },
                    onBackToApp = { continueToApp { true } },
                    onRedeem = { r -> continueToApp { container.shop.redeem(r.id, forPackage = a.pkg) } },
                    onUnlock = { minutes ->
                        // Skipping only costs extra while the wait is still running.
                        continueToApp { container.shop.buyUnlock(a.pkg, minutes, skipWaitSeconds = if (a.gate == BlockGate.WAIT) a.waitSeconds else null) }
                    },
                )
            }
        }
    }

    private fun closeApp(resisted: Boolean) {
        if (resisted) {
            // The pause screen stays quiet; the reward shows as a toast over the home screen.
            android.widget.Toast.makeText(this, "+${GameRules.RESIST_XP} XP · ${Encouragement.resist()}", android.widget.Toast.LENGTH_SHORT).show()
            container.appScope.launch {
                container.game.apply(EventTypes.RESISTED, intent.getStringExtra(EXTRA_PACKAGE),
                    xp = GameRules.RESIST_XP, coins = GameRules.RESIST_COINS, message = Encouragement.resist())
            }
        }
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    /** Runs [grant] (which records a pass / spends coins) and, if it succeeds, reveals the app underneath. */
    private fun continueToApp(grant: suspend () -> Boolean) {
        lifecycleScope.launch {
            if (grant()) {
                leavingToApp = true
                fadingOut.value = true // PauseScreen fades out, then finishes: the app is still open beneath
            }
        }
    }

    @Composable
    private fun PauseScreen(
        a: Args,
        appLabel: String,
        onClose: () -> Unit,
        onContinue: (Int) -> Unit,
        onBackToApp: () -> Unit,
        onUnlock: (Int) -> Unit,
        onRedeem: (RewardEntity) -> Unit,
    ) {
        val settings by container.settings.state.collectAsState()
        val profile by remember { container.db.profileDao().observe() }.collectAsState(initial = null)
        val coins = profile?.coins ?: 0L
        val rewards by container.shop.rewards.collectAsState(initial = emptyList())
        var sheetKind by rememberSaveable { mutableStateOf("") } // "", "free" or "paid"

        // Wait timer: elapsed time survives rotation; the screen is discarded if the user leaves.
        val waitMs = a.waitSeconds * 1000L
        var elapsed by rememberSaveable { mutableLongStateOf(a.initialElapsedMs) }
        LaunchedEffect(waitMs) {
            if (a.gate != BlockGate.WAIT) return@LaunchedEffect
            // Driven by the frame clock, so it only advances while the pause screen is on screen.
            val timer = WaitTimer(waitMs, elapsed)
            while (!timer.done) withFrameMillis { elapsed = timer.onFrame(it) }
        }

        val state = PauseState(
            appLabel = appLabel, reason = a.reason, detail = a.detail, gate = a.gate,
            waitMs = waitMs, elapsedMs = elapsed, allowMinutes = a.allowMinutes,
            canPay = a.canPay, backToApp = a.backToApp, focusEndsAt = settings.activeFocus?.endsAt,
            question = settings.pauseQuestion, reminder = settings.pauseReminder,
            icon = PauseIcon.of(settings.pauseIcon), colors = PauseColors.of(settings.pauseColors),
        )

        // Fade in over the app (the window is translucent), and fade out before revealing it again.
        val alpha = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(Unit) { alpha.animateTo(1f, androidx.compose.animation.core.tween(220)) }
        val leaving by fadingOut
        LaunchedEffect(leaving) {
            if (leaving) {
                alpha.animateTo(0f, androidx.compose.animation.core.tween(200))
                finish()
                @Suppress("DEPRECATION") overridePendingTransition(0, 0)
            }
        }
        val sheet = when (sheetKind) {
            "free" -> UnlockSheets.free(state)
            "paid" -> UnlockSheets.paid(state, coins, settings, rewards, a.pkg)
            else -> null
        }
        PauseScreenContent(
            state,
            onClose = onClose,
            onContinue = {
                // Only one choice? Don't ask.
                val only = state.freeDurations.singleOrNull()
                if (only != null) onContinue(only) else sheetKind = "free"
            },
            onBackToApp = onBackToApp,
            onOpenCoins = { sheetKind = "paid" },
            sheet = sheet,
            onDismissSheet = { sheetKind = "" },
            modifier = Modifier.graphicsLayer { this.alpha = alpha.value },
            onPick = { key ->
                sheetKind = ""
                when {
                    key.startsWith(UnlockSheets.FREE) -> onContinue(key.removePrefix(UnlockSheets.FREE).toInt())
                    key.startsWith(UnlockSheets.PAID) -> onUnlock(key.removePrefix(UnlockSheets.PAID).toInt())
                    key.startsWith(UnlockSheets.REWARD) -> rewards.firstOrNull { it.id == key.removePrefix(UnlockSheets.REWARD) }?.let(onRedeem)
                }
            },
        )
    }

    companion object {
        const val EXTRA_PACKAGE = "pkg"
        const val EXTRA_REASON = "reason"
        const val EXTRA_DETAIL = "detail"
        const val EXTRA_GATE = "gate"
        const val EXTRA_WAIT_SECONDS = "waitSeconds"
        const val EXTRA_ALLOW_MINUTES = "allowMinutes"
        const val EXTRA_CAN_PAY = "canPay"
        const val EXTRA_BACK_TO_APP = "backToApp"
        /** Start part-way through the wait. Used by screenshot tests for a deterministic mid-wait frame. */
        const val EXTRA_INITIAL_ELAPSED_MS = "initialElapsedMs"
    }
}
