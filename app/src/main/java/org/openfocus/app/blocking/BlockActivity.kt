package org.openfocus.app.blocking

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.openfocus.app.OpenFocusApp
import org.openfocus.app.core.Days
import org.openfocus.app.core.Encouragement
import org.openfocus.app.core.GameRules
import org.openfocus.app.game.EventTypes
import org.openfocus.app.ui.pixel.PixelSprite
import org.openfocus.app.ui.pixel.SpriteCatalog
import org.openfocus.app.ui.theme.OpenFocusTheme

/**
 * The interstitial shown when the Shield intervenes. Inspired by "pause before you scroll" apps:
 * a short breathing pause, a nudge, and a deliberate (coin-priced) way through if you really need it.
 */
class BlockActivity : ComponentActivity() {

    private val container by lazy { (application as OpenFocusApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome(resisted = false)
        })
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render()
    }

    private fun render() {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: ""
        val reason = runCatching { BlockReason.valueOf(intent.getStringExtra(EXTRA_REASON) ?: "") }.getOrDefault(BlockReason.ALWAYS)
        val detail = intent.getStringExtra(EXTRA_DETAIL) ?: ""
        val label = InstalledApps.label(this, pkg)
        setContent {
            OpenFocusTheme {
                BlockScreen(
                    appLabel = label, reason = reason, detail = detail,
                    onClose = { goHome(resisted = true) },
                    onBackToApp = { finish() },
                    onUnlock = { minutes -> unlock(pkg, minutes) },
                )
            }
        }
    }

    private fun goHome(resisted: Boolean) {
        if (resisted) {
            container.appScope.launch {
                container.game.apply(EventTypes.RESISTED, intent.getStringExtra(EXTRA_PACKAGE),
                    xp = GameRules.RESIST_XP, coins = GameRules.RESIST_COINS, message = Encouragement.resist())
            }
        }
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun unlock(pkg: String, minutes: Int) {
        lifecycleScope.launch {
            if (container.shop.buyUnlock(pkg, minutes)) {
                packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                finish()
            }
        }
    }

    @Composable
    private fun BlockScreen(
        appLabel: String,
        reason: BlockReason,
        detail: String,
        onClose: () -> Unit,
        onBackToApp: () -> Unit,
        onUnlock: (Int) -> Unit,
    ) {
        val settings by container.settings.state.collectAsState()
        val profile by remember { container.db.profileDao().observe() }.collectAsState(initial = null)
        val message = remember { if (reason == BlockReason.SHORTS) Encouragement.shorts() else Encouragement.blocked() }
        var breath by remember { mutableIntStateOf(BREATH_SECONDS) }
        LaunchedEffect(Unit) { while (breath > 0) { delay(1000); breath-- } }

        val focusRunning = settings.activeFocus?.let { it.endsAt > System.currentTimeMillis() } == true
        val unlockAllowed = reason != BlockReason.SHORTS && !(focusRunning && settings.strictMode)
        val unlocksToday = settings.unlocksTodayEffective(Days.today())

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.safeDrawingPadding().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            ) {
                // Pixel shield whose glow "breathes" once every 4 s, pacing the breathing prompt below.
                PixelSprite(SpriteCatalog.shield, SpriteCatalog.IDLE, Modifier.size(160.dp), contentDescription = "Shield")
                Text(
                    when (reason) {
                        BlockReason.SHORTS -> "$detail blocked"
                        BlockReason.FOCUS, BlockReason.DEEP_FOCUS -> "You're in a focus session"
                        BlockReason.DAILY_LIMIT -> "Daily limit reached for $appLabel"
                        BlockReason.SCHEDULE -> "$appLabel is resting ($detail)"
                        BlockReason.ALWAYS -> "$appLabel is blocked"
                    },
                    style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                )
                Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                if (focusRunning) {
                    val left = ((settings.activeFocus!!.endsAt - System.currentTimeMillis()) / 60_000).toInt() + 1
                    Text("About $left min left${if (detail.isNotBlank()) " on \"$detail\"" else ""}.", style = MaterialTheme.typography.bodyMedium)
                }
                Text(if (breath > 0) "Breathe in… breathe out… ($breath)" else "Your call, adventurer.",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (reason == BlockReason.SHORTS) {
                    Button(onClick = onBackToApp, modifier = Modifier.fillMaxWidth()) { Text("Back to $appLabel (no Shorts)") }
                    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close the app  ·  +${GameRules.RESIST_XP} XP") }
                } else {
                    Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close $appLabel  ·  +${GameRules.RESIST_XP} XP") }
                }
                AnimatedVisibility(breath == 0 && unlockAllowed, enter = fadeIn()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Need it for something specific?", style = MaterialTheme.typography.bodySmall)
                        GameRules.unlockOptions.forEach { m ->
                            val cost = GameRules.unlockCost(m, unlocksToday)
                            TextButton(enabled = (profile?.coins ?: 0) >= cost, onClick = { onUnlock(m) }) {
                                Text("Unlock for $m min · $cost coins")
                            }
                        }
                        Text("You have ${profile?.coins ?: 0} coins. Unlocks get pricier each time today.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "pkg"
        const val EXTRA_REASON = "reason"
        const val EXTRA_DETAIL = "detail"
        private const val BREATH_SECONDS = 5
    }
}
