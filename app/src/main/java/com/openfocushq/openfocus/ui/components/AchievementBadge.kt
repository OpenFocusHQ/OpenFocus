package com.openfocushq.openfocus.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import com.openfocushq.openfocus.ui.pixel.PixelSprite
import com.openfocushq.openfocus.ui.pixel.SpriteCatalog
import com.openfocushq.openfocus.ui.pixel.SpriteLayer

/** Pixel-art medal for an achievement. Locked badges are drawn as a flat silhouette. */
@Composable
fun AchievementBadge(id: String, unlocked: Boolean, modifier: Modifier = Modifier, contentDescription: String? = null) {
    if (id !in SpriteCatalog.badges.anims) return
    val silhouette = if (unlocked) null else ColorFilter.tint(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), BlendMode.SrcIn)
    PixelSprite(
        listOf(SpriteLayer(SpriteCatalog.badges, id)), modifier,
        animate = false, contentDescription = contentDescription, colorFilter = silhouette,
    )
}
