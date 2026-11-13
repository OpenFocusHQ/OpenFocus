package org.openfocus.app.ui.hero

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.openfocus.app.core.Achievements
import org.openfocus.app.core.GameRules
import org.openfocus.app.core.GameStats
import org.openfocus.app.core.ItemSlot
import org.openfocus.app.core.ShopCatalog
import org.openfocus.app.data.db.ProfileEntity
import org.openfocus.app.data.db.RewardEntity
import org.openfocus.app.ui.components.AppIcons
import org.openfocus.app.ui.components.HeroAvatar
import org.openfocus.app.ui.components.HeroCard
import org.openfocus.app.ui.components.SkinTonePicker
import org.openfocus.app.ui.components.appContainer
import org.openfocus.app.ui.components.formatMinutes
import org.openfocus.app.ui.theme.Coin
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroScreen(nav: NavController) {
    val c = appContainer()
    val profile by remember { c.db.profileDao().observe() }.collectAsState(initial = null)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var rename by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Your hero") },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { IconButton(onClick = { rename = true }) { Icon(Icons.Filled.Edit, "Customise hero") } },
        )
        HeroCard(profile, Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        PrimaryTabRow(tab) {
            listOf("Trophies", "Shop", "Rewards", "Journal").forEachIndexed { i, t ->
                Tab(tab == i, { tab = i }, text = { Text(t) })
            }
        }
        when (tab) {
            0 -> AchievementsTab()
            1 -> ShopTab(profile)
            2 -> RewardsTab(profile)
            else -> JournalTab()
        }
    }
    if (rename) {
        var name by remember { mutableStateOf(profile?.displayName ?: "") }
        var tone by remember { mutableStateOf(profile?.skinTone) }
        val scope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Customise your hero") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(name, { name = it.take(24) }, singleLine = true, label = { Text("Name") })
                    Text("Skin tone", style = MaterialTheme.typography.titleSmall)
                    SkinTonePicker(tone, profile?.avatarColor, profile?.avatarHat, onPick = { tone = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    rename = false
                    scope.launch { c.game.updateProfile { it.copy(displayName = name.ifBlank { "Adventurer" }, skinTone = tone) } }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AchievementsTab() {
    val c = appContainer()
    val unlocked by c.shop.achievements.collectAsState(initial = emptyList())
    var stats by remember { mutableStateOf(GameStats()) }
    LaunchedEffect(unlocked.size) { stats = c.game.stats() }
    val ids = unlocked.map { it.achievementId }.toSet()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat("Habits done", "${stats.habitCompletions}", Modifier.weight(1f))
                MiniStat("Focused", formatMinutes(stats.focusMinutes), Modifier.weight(1f))
                MiniStat("Best streak", "${stats.bestHabitStreak}", Modifier.weight(1f))
                MiniStat("Resisted", "${stats.temptationsResisted}", Modifier.weight(1f))
            }
        }
        item { Text("${ids.size}/${Achievements.all.size} unlocked", style = MaterialTheme.typography.titleSmall) }
        items(Achievements.all.sortedBy { it.id !in ids }, key = { it.id }) { a ->
            val done = a.id in ids
            val (cur, goal) = a.progress(stats)
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.alpha(if (done) 1f else 0.75f)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (done) AppIcons.get(a.icon) else Icons.Filled.Lock, null,
                        tint = if (done) Coin else MaterialTheme.colorScheme.outline, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.title, style = MaterialTheme.typography.titleSmall)
                        Text(a.description, style = MaterialTheme.typography.bodySmall)
                        if (!done) {
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (cur.toFloat() / goal).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), drawStopIndicator = {},
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ShopTab(profile: ProfileEntity?) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val owned by c.shop.inventory.collectAsState(initial = emptyList())
    val ownedIds = owned.map { it.itemId }.toSet() + ShopCatalog.items.filter { it.price == 0 }.map { it.id }
    val level = GameRules.levelFor(profile?.totalXp ?: 0)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Spend coins on cosmetics for your hero. (Placeholder art — contributions welcome!)",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ItemSlot.entries.forEach { slot ->
            item { Text(slot.name.lowercase().replaceFirstChar { it.uppercase() } + "s", style = MaterialTheme.typography.titleSmall) }
            items(ShopCatalog.items.filter { it.slot == slot }, key = { it.id }) { item ->
                val own = item.id in ownedIds
                val equipped = when (slot) {
                    ItemSlot.HAT -> profile?.avatarHat == item.id
                    ItemSlot.BACKDROP -> profile?.avatarBackdrop == item.id
                    ItemSlot.COLOR -> (profile?.avatarColor ?: ShopCatalog.defaultColor.id) == item.id
                }
                Card(shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        HeroAvatar(
                            colorId = if (slot == ItemSlot.COLOR) item.id else profile?.avatarColor,
                            hatId = if (slot == ItemSlot.HAT) item.id else null,
                            backdropId = if (slot == ItemSlot.BACKDROP) item.id else null,
                            size = 52.dp,
                            skinTone = profile?.skinTone,
                            animate = false,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall)
                            if (!own) Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.MonetizationOn, null, tint = Coin, modifier = Modifier.size(14.dp))
                                Text(" ${item.price}" + if (level < item.minLevel) " · Lv ${item.minLevel}+" else "", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        when {
                            own -> OutlinedButton(onClick = { scope.launch { c.shop.equip(item) } }, enabled = !(equipped && slot == ItemSlot.COLOR)) {
                                Text(if (equipped) (if (slot == ItemSlot.COLOR) "Wearing" else "Take off") else "Wear")
                            }
                            else -> FilledTonalButton(
                                enabled = level >= item.minLevel && (profile?.coins ?: 0) >= item.price,
                                onClick = { scope.launch { c.shop.buy(item) } },
                            ) { Text("Buy") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RewardsTab(profile: ProfileEntity?) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val rewards by c.shop.rewards.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<RewardEntity?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Treat yourself! Create real-life rewards and buy them with the coins you earn.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(rewards, key = { it.id }) { r ->
            Card(shape = RoundedCornerShape(16.dp), onClick = { editing = r }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.get(r.icon), null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.title, style = MaterialTheme.typography.titleSmall)
                        Text("${r.cost} coins · redeemed ${r.timesRedeemed}×", style = MaterialTheme.typography.bodySmall)
                    }
                    FilledTonalButton(enabled = (profile?.coins ?: 0) >= r.cost, onClick = { scope.launch { c.shop.redeem(r.id) } }) { Text("Redeem") }
                }
            }
        }
        item {
            OutlinedButton(onClick = { editing = RewardEntity(ownerId = "", title = "", cost = 50) }) {
                Icon(Icons.Filled.Add, null); Text("New reward")
            }
        }
    }
    editing?.let { r ->
        var title by remember(r.id) { mutableStateOf(r.title) }
        var cost by remember(r.id) { mutableStateOf(r.cost.toString()) }
        var icon by remember(r.id) { mutableStateOf(r.icon) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Reward") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("e.g. Watch an episode guilt-free") }, singleLine = true)
                    OutlinedTextField(cost, { cost = it.filter(Char::isDigit).take(5) }, label = { Text("Cost in coins") }, singleLine = true)
                    Row { AppIcons.rewardChoices.forEach { n -> IconButton(onClick = { icon = n }) { Icon(AppIcons.get(n), n, tint = if (n == icon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) } } }
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank() && (cost.toIntOrNull() ?: 0) > 0, onClick = {
                    scope.launch { c.shop.saveReward(r.copy(title = title.trim(), cost = cost.toInt(), icon = icon)) }
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = {
                if (r.title.isNotBlank()) TextButton(onClick = { scope.launch { c.shop.deleteReward(r) }; editing = null }) { Text("Delete") }
            },
        )
    }
}

@Composable
private fun JournalTab() {
    val c = appContainer()
    val events by c.shop.events.collectAsState(initial = emptyList())
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(events.filter { it.message.isNotBlank() }, key = { it.id }) { e ->
            Column {
                Text(e.message, style = MaterialTheme.typography.bodyMedium)
                val deltas = buildList {
                    if (e.xpDelta != 0) add("${if (e.xpDelta > 0) "+" else ""}${e.xpDelta} XP")
                    if (e.coinDelta != 0) add("${if (e.coinDelta > 0) "+" else ""}${e.coinDelta} coins")
                    if (e.hpDelta != 0) add("${if (e.hpDelta > 0) "+" else ""}${e.hpDelta} HP")
                }
                Text((listOf(fmt.format(Date(e.timestamp))) + deltas).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
