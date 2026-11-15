package com.openfocushq.openfocus.ui.onboarding

import com.openfocushq.openfocus.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.layout.size
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
import com.openfocushq.openfocus.core.content.GameContent
import com.openfocushq.openfocus.data.db.HabitEntity
import com.openfocushq.openfocus.data.db.HabitKind
import com.openfocushq.openfocus.ui.components.AccessibilityDisclosureDialog
import com.openfocushq.openfocus.ui.components.HeroAvatar
import com.openfocushq.openfocus.ui.components.openAccessibilitySettings
import com.openfocushq.openfocus.ui.pixel.PixelSprite
import com.openfocushq.openfocus.ui.pixel.SpriteCatalog
import com.openfocushq.openfocus.ui.components.SkinTonePicker
import com.openfocushq.openfocus.ui.components.appContainer

private val starters get() = GameContent.current.starterHabits.filter { it.starter }

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
                    HeroAvatar(mapOf("backdrop" to "bg_sunrise"), size = 140.dp, skinTone = tone)
                    Text(stringResource(R.string.welcome_to_openfocus), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Text(
                        "Turn your attention into an adventure. Build habits, focus deeply, block the endless feeds, and climb towards the goals that matter.",
                        textAlign = TextAlign.Center,
                    )
                    OutlinedTextField(name, { name = it.take(24) }, label = { Text(stringResource(R.string.name_your_hero)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    SkinTonePicker(tone, emptyMap(), onPick = { tone = it })
                    Button(onClick = { page = 1 }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.next)) }
                }
                1 -> {
                    PixelSprite(SpriteCatalog.empty, SpriteCatalog.EMPTY_TODAY, Modifier.size(width = 192.dp, height = 144.dp), animate = false)
                    Text(stringResource(R.string.pick_a_few_starter_habits), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.start_small_you_can_edit_or), textAlign = TextAlign.Center)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        starters.forEachIndexed { i, s ->
                            FilterChip(i in picked, { picked = if (i in picked) picked - i else picked + i },
                                label = { Text((if (s.kind == HabitKind.BREAK) "Quit: " else "") + s.title) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { page = 0 }) { Text(stringResource(R.string.back)) }
                        Button(onClick = { page = 2 }) { Text(stringResource(R.string.next)) }
                    }
                }
                else -> {
                    PixelSprite(SpriteCatalog.shield, SpriteCatalog.IDLE, Modifier.size(144.dp))
                    Text(stringResource(R.string.raise_your_shield), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text(
                        "To block apps and Shorts/Reels, OpenFocus uses Android's accessibility service to see which app is in front. " +
                            "It only looks at screen content inside the video apps you enable, never reads what you type, never records the screen and never sends anything off your device.",
                        textAlign = TextAlign.Center,
                    )
                    var disclose by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { disclose = true }) { Text(stringResource(R.string.turn_on_the_shield)) }
                    if (disclose) AccessibilityDisclosureDialog(onAgree = { disclose = false; openAccessibilitySettings(context) }, onDismiss = { disclose = false })
                    Text(stringResource(R.string.you_can_also_do_this_later), style = MaterialTheme.typography.bodySmall)
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                            scope.launch {
                                c.game.updateProfile { it.copy(displayName = name.trim().ifBlank { "Adventurer" }, skinTone = tone) }
                                picked.sorted().forEachIndexed { order, i ->
                                    val s = starters[i]
                                    c.habits.save(HabitEntity(ownerId = "", title = s.title, icon = s.icon, kind = s.kind,
                                        colorIndex = s.color, difficulty = s.difficulty, frequency = s.frequency,
                                        target = s.target, sortOrder = order))
                                }
                                c.settings.update { it.copy(onboardingDone = true) }
                            }
                        },
                    ) { Text(stringResource(R.string.begin_the_adventure)) }
                }
            }
        }
    }
}
