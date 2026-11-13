package org.openfocus.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.openfocus.app.AppContainer
import org.openfocus.app.OpenFocusApp
import org.openfocus.app.core.GameRules
import org.openfocus.app.data.db.ProfileEntity
import org.openfocus.app.ui.pixel.PixelSprite
import org.openfocus.app.ui.pixel.SpriteCatalog
import org.openfocus.app.ui.theme.Hp
import org.openfocus.app.ui.theme.Xp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as OpenFocusApp).container

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val c = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(c) } })
}

@Composable
fun StatBar(icon: ImageVector, label: String, value: Float, color: Color, modifier: Modifier = Modifier) {
    val v by animateFloatAsState(value.coerceIn(0f, 1f), label = "bar")
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        LinearProgressIndicator(
            progress = { v },
            modifier = Modifier.weight(1f).height(8.dp).clip(CircleShape),
            color = color,
            trackColor = color.copy(alpha = 0.18f),
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(64.dp), maxLines = 1)
    }
}

@Composable
fun HeroCard(profile: ProfileEntity?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val p = profile ?: ProfileEntity()
    val lp = GameRules.progress(p.totalXp)
    val content: @Composable () -> Unit = {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            HeroAvatar(p, size = 80.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.displayName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    Pill("Lv ${lp.level}", MaterialTheme.colorScheme.primary)
                }
                Text(GameRules.titleFor(lp.level), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatBar(Icons.Filled.Bolt, "${lp.intoLevel}/${lp.levelSpan}", lp.fraction, Xp)
                StatBar(Icons.Filled.Favorite, "${p.hp}/${p.maxHp}", p.hp / p.maxHp.toFloat().coerceAtLeast(1f), Hp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelSprite(SpriteCatalog.coin, "spin", Modifier.size(16.dp), animate = false, contentDescription = "Coins")
                    Spacer(Modifier.width(6.dp))
                    Text("${p.coins}", style = MaterialTheme.typography.labelLarge)
                    if (p.currentStreak > 0) {
                        Spacer(Modifier.width(12.dp))
                        Text("🔥 ${p.currentStreak} perfect day${if (p.currentStreak > 1) "s" else ""}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) { content() }
    else Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) { content() }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(CircleShape).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 3.dp),
    ) { Text(text, color = color, style = MaterialTheme.typography.labelMedium) }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    /** Pixel-art scene from [SpriteCatalog.empty]; falls back to [icon] when null. */
    scene: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (scene != null && scene in SpriteCatalog.empty.anims) {
                PixelSprite(SpriteCatalog.empty, scene, Modifier.width(128.dp).height(96.dp), animate = false)
            } else {
                Icon(icon, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke()
        }
    }
}

fun formatMinutes(m: Int): String = when {
    m < 60 -> "${m}m"
    m % 60 == 0 -> "${m / 60}h"
    else -> "${m / 60}h ${m % 60}m"
}

fun formatMinuteOfDay(m: Int): String = "%02d:%02d".format(m / 60, m % 60)
