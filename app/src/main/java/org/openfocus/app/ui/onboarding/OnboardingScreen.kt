package org.openfocus.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.openfocus.app.data.db.Difficulty
import org.openfocus.app.data.db.HabitEntity
import org.openfocus.app.data.db.HabitKind
import org.openfocus.app.ui.components.HeroAvatar
import org.openfocus.app.ui.components.SkinTonePicker
import org.openfocus.app.ui.components.appContainer

private data class Starter(val title: String, val icon: String, val kind: HabitKind = HabitKind.BUILD, val color: Int)

private val starters = listOf(
    Starter("Drink a glass of water", "Water", color = 4),
    Starter("Read 10 pages", "Book", color = 0),
    Starter("10 minute walk", "Walk", color = 6),
    Starter("Meditate 5 minutes", "Meditate", color = 1),
    Starter("Journal one line", "Journal", color = 3),
    Starter("No phone in bed", "NoPhone", color = 5),
    Starter("Doomscrolling", "Phone", HabitKind.BREAK, color = 2),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen() {
    val c = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var tone by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf(setOf(0, 1)) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            when (page) {
                0 -> {
                    HeroAvatar(null, null, "bg_sunrise", size = 140.dp, skinTone = tone)
                    Text("Welcome to OpenFocus", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Text(
                        "Turn your attention into an adventure. Build habits, focus deeply, block the endless feeds, and climb towards the goals that matter.",
                        textAlign = TextAlign.Center,
                    )
                    OutlinedTextField(name, { name = it.take(24) }, label = { Text("Name your hero") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    SkinTonePicker(tone, null, null, onPick = { tone = it })
                    Button(onClick = { page = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Next") }
                }
                1 -> {
                    Text("Pick a few starter quests", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text("Start small. You can edit or add more any time.", textAlign = TextAlign.Center)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        starters.forEachIndexed { i, s ->
                            FilterChip(i in picked, { picked = if (i in picked) picked - i else picked + i },
                                label = { Text((if (s.kind == HabitKind.BREAK) "Quit: " else "") + s.title) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { page = 0 }) { Text("Back") }
                        Button(onClick = { page = 2 }) { Text("Next") }
                    }
                }
                else -> {
                    Text("Raise your Shield", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text(
                        "To block apps and Shorts/Reels, OpenFocus uses Android's accessibility service to see which app is in front. " +
                            "It never reads your messages, never records the screen and never sends anything off your device.",
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("Open accessibility settings") }
                    Text("You can also do this later from the Shield tab.", style = MaterialTheme.typography.bodySmall)
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                            scope.launch {
                                c.game.updateProfile { it.copy(displayName = name.trim().ifBlank { "Adventurer" }, skinTone = tone) }
                                picked.sorted().forEachIndexed { order, i ->
                                    val s = starters[i]
                                    c.habits.save(HabitEntity(ownerId = "", title = s.title, icon = s.icon, kind = s.kind,
                                        colorIndex = s.color, difficulty = Difficulty.EASY, sortOrder = order))
                                }
                                c.settings.update { it.copy(onboardingDone = true) }
                            }
                        },
                    ) { Text("Begin the adventure") }
                }
            }
        }
    }
}
