package com.openfocushq.openfocus.ui.hero

import androidx.compose.foundation.layout.Arrangement
import com.openfocushq.openfocus.blocking.formatDuration
import com.openfocushq.openfocus.blocking.DurationChips
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Map
import androidx.compose.ui.res.stringResource
import com.openfocushq.openfocus.R
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
import com.openfocushq.openfocus.core.Achievements
import com.openfocushq.openfocus.core.GameRules
import com.openfocushq.openfocus.core.GameStats
import com.openfocushq.openfocus.core.ShopCatalog
import com.openfocushq.openfocus.data.db.ProfileEntity
import com.openfocushq.openfocus.data.db.equippedMap
import com.openfocushq.openfocus.data.db.RewardEntity
import com.openfocushq.openfocus.ui.components.AchievementBadge
import com.openfocushq.openfocus.ui.components.AppIcons
import com.openfocushq.openfocus.ui.components.HeroAvatar
import com.openfocushq.openfocus.ui.components.HeroCard
import com.openfocushq.openfocus.ui.components.SkinTonePicker
import com.openfocushq.openfocus.ui.components.appContainer
import com.openfocushq.openfocus.ui.components.formatMinutes
import com.openfocushq.openfocus.ui.theme.Coin
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
            title = { Text(stringResource(R.string.your_hero)) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { nav.navigate("quests") }) { Icon(Icons.Filled.Map, stringResource(R.string.quests_title)) }
                IconButton(onClick = { rename = true }) { Icon(Icons.Filled.Edit, "Customise hero") }
            },
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
            title = { Text(stringResource(R.string.customise_your_hero)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(name, { name = it.take(24) }, singleLine = true, label = { Text(stringResource(R.string.name)) })
                    Text(stringResource(R.string.skin_tone_2), style = MaterialTheme.typography.titleSmall)
                    SkinTonePicker(tone, profile?.equippedMap().orEmpty(), onPick = { tone = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    rename = false
                    scope.launch { c.game.updateProfile { it.copy(displayName = name.ifBlank { "Adventurer" }, skinTone = tone) } }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { rename = false }) { Text(stringResource(R.string.cancel)) } },
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
        item { Text(stringResource(R.string.unlocked, ids.size, Achievements.all.size), style = MaterialTheme.typography.titleSmall) }
        items(Achievements.all.sortedBy { it.id !in ids }, key = { it.id }) { a ->
            val done = a.id in ids
            val (cur, goal) = a.progress(stats)
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.alpha(if (done) 1f else 0.75f)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AchievementBadge(a.id, done, Modifier.size(48.dp), contentDescription = if (done) "Unlocked" else "Locked")
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
    val ownedIds = owned.map { it.itemId }.toSet() + ShopCatalog.freeItems.map { it.id }
    val level = GameRules.levelFor(profile?.totalXp ?: 0)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(stringResource(R.string.spend_coins_on_cosmetics_for_your),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val wearing = ShopCatalog.resolveEquipped(profile?.equippedMap().orEmpty())
        ShopCatalog.slots.forEach { slot ->
            val slotItems = ShopCatalog.itemsIn(slot.id)
            if (slotItems.isEmpty()) return@forEach
            item(key = "slot_${slot.id}") { Text(slot.name, style = MaterialTheme.typography.titleSmall) }
            items(slotItems, key = { it.id }) { item ->
                val own = item.id in ownedIds
                val equipped = wearing[slot.id] == item.id
                Card(shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Preview: what you're wearing now, with this item swapped in.
                        HeroAvatar(wearing + (slot.id to item.id), size = 52.dp, skinTone = profile?.skinTone, animate = false)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall)
                            if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodySmall)
                            if (!own) Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.MonetizationOn, null, tint = Coin, modifier = Modifier.size(14.dp))
                                Text(" ${item.price}" + if (level < item.minLevel) " · Lv ${item.minLevel}+" else "", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        when {
                            own -> OutlinedButton(onClick = { scope.launch { c.shop.equip(item) } }, enabled = !(equipped && slot.required)) {
                                Text(if (equipped) (if (slot.required) "Wearing" else "Take off") else "Wear")
                            }
                            else -> FilledTonalButton(
                                enabled = level >= item.minLevel && (profile?.coins ?: 0) >= item.price,
                                onClick = { scope.launch { c.shop.buy(item) } },
                            ) { Text(stringResource(R.string.buy)) }
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
    val rules by c.blocks.rules.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<RewardEntity?>(null) }
    fun appName(pkg: String?) = when (pkg) {
        null -> null
        RewardEntity.ANY_BLOCKED_APP -> "any blocked app"
        else -> rules.firstOrNull { it.packageName == pkg }?.label ?: pkg
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(stringResource(R.string.treat_yourself_create_rewards_and_buy) +
                "for a while, and then it shows up on that app's pause screen too.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(rewards, key = { it.id }) { r ->
            Card(shape = RoundedCornerShape(16.dp), onClick = { editing = r }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.get(r.icon), null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${r.cost} coins · redeemed ${r.timesRedeemed}×" +
                                if (r.unlocksApp) " · unlocks ${appName(r.unlockPackage)} for ${formatDuration(r.unlockMinutes)}" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    FilledTonalButton(enabled = (profile?.coins ?: 0) >= r.cost, onClick = { scope.launch { c.shop.redeem(r.id) } }) {
                        Text(if (r.unlocksApp) "Redeem & unlock" else "Redeem")
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { editing = RewardEntity(ownerId = "", title = "", cost = 50) }) {
                Icon(Icons.Filled.Add, null); Text(stringResource(R.string.new_reward))
            }
        }
    }
    editing?.let { r ->
        var title by remember(r.id) { mutableStateOf(r.title) }
        var cost by remember(r.id) { mutableStateOf(r.cost.toString()) }
        var icon by remember(r.id) { mutableStateOf(r.icon) }
        var unlockPkg by remember(r.id) { mutableStateOf(r.unlockPackage) }
        var unlockMin by remember(r.id) { mutableIntStateOf(r.unlockMinutes.takeIf { it > 0 } ?: 30) }
        var appMenu by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.reward)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.e_g_watch_an_episode_guilt)) }, singleLine = true)
                    OutlinedTextField(cost, { cost = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(R.string.cost_in_coins)) }, singleLine = true)
                    Row { AppIcons.rewardChoices.forEach { n -> IconButton(onClick = { icon = n }) { Icon(AppIcons.get(n), n, tint = if (n == icon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) } } }
                    Text(stringResource(R.string.also_unlocks_optional), style = MaterialTheme.typography.titleSmall)
                    Box {
                        OutlinedButton(onClick = { appMenu = true }) { Text(appName(unlockPkg)?.replaceFirstChar { it.uppercase() } ?: "Nothing, just a treat") }
                        DropdownMenu(appMenu, { appMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.nothing_just_a_treat)) }, onClick = { unlockPkg = null; appMenu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.any_blocked_app)) }, onClick = { unlockPkg = RewardEntity.ANY_BLOCKED_APP; appMenu = false })
                            rules.forEach { rule ->
                                DropdownMenuItem(text = { Text(rule.label) }, onClick = { unlockPkg = rule.packageName; appMenu = false })
                            }
                        }
                    }
                    if (unlockPkg != null) {
                        DurationChips("For", GameRules.unlockDurations, unlockMin, { unlockMin = it })
                        Text(stringResource(R.string.your_price_set_above_doesn_t), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank() && (cost.toIntOrNull() ?: 0) > 0, onClick = {
                    scope.launch {
                        c.shop.saveReward(r.copy(title = title.trim(), cost = cost.toInt(), icon = icon,
                            unlockPackage = unlockPkg, unlockMinutes = if (unlockPkg != null) unlockMin else 0))
                    }
                    editing = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                if (r.title.isNotBlank()) TextButton(onClick = { scope.launch { c.shop.deleteReward(r) }; editing = null }) { Text(stringResource(R.string.delete)) }
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
