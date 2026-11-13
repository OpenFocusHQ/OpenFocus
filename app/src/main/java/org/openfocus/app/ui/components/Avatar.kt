package org.openfocus.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openfocus.app.core.ShopCatalog
import org.openfocus.app.ui.pixel.PixelSprite
import org.openfocus.app.data.db.ProfileEntity
import org.openfocus.app.ui.pixel.HeroPose
import org.openfocus.app.ui.pixel.SkinTone
import org.openfocus.app.ui.pixel.SpriteCatalog

/**
 * The hero: layered pixel-art sprites (round backdrop, body in the chosen skin tone, cloak, hat).
 * Sprites are looked up by ShopItem id in [SpriteCatalog]; if one is missing the shape-drawn
 * fallback is used, so new items can ship before their art does.
 */
@Composable
fun HeroAvatar(
    colorId: String?,
    hatId: String?,
    backdropId: String?,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    pose: HeroPose = HeroPose.IDLE,
    skinTone: String? = null,
    animate: Boolean = true,
) {
    val layers = remember(colorId, hatId, backdropId, pose, skinTone) {
        SpriteCatalog.heroLayers(colorId, hatId, pose, SkinTone.of(skinTone), withBackdrop = true, backdropId = backdropId)
    }
    if (layers != null) {
        PixelSprite(layers, modifier.size(size), animate = animate, contentDescription = "Your hero")
    } else {
        Canvas(modifier.size(size)) {
            drawBackdrop(ShopCatalog.byId(backdropId))
            drawVectorHero(colorId, hatId)
        }
    }
}

/** Avatar for a profile row (null = defaults). */
@Composable
fun HeroAvatar(
    profile: ProfileEntity?,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    pose: HeroPose = HeroPose.IDLE,
    animate: Boolean = true,
) = HeroAvatar(profile?.avatarColor, profile?.avatarHat, profile?.avatarBackdrop, modifier, size, pose, profile?.skinTone, animate)

private fun DrawScope.drawBackdrop(backdrop: org.openfocus.app.core.ShopItem?) {
    val w = this.size.width
    val bg = backdrop?.let { Color(it.color) } ?: Color(0xFFDCD6FF)
    drawCircle(Brush.verticalGradient(listOf(bg.copy(alpha = 0.55f), bg)), radius = w / 2)
    when (backdrop?.id) {
        "bg_night" -> repeat(7) { i ->
            drawCircle(Color.White, radius = w * 0.012f, center = Offset(w * (0.15f + 0.11f * i), w * (0.18f + 0.07f * ((i * 3) % 4))))
        }
        "bg_forest" -> for (i in 0..2) drawTree(Offset(w * (0.15f + 0.35f * i), w * 0.72f), w * 0.14f)
        "bg_sunrise" -> drawCircle(Color(0xFFFFE08A), radius = w * 0.14f, center = Offset(w * 0.78f, w * 0.3f))
        "bg_ocean" -> drawRect(Color(0xFF7FC8F8), topLeft = Offset(0f, w * 0.68f), size = Size(w, w * 0.32f))
    }
}

/** Fallback hero drawn from shapes, used when a cosmetic has no sprite yet. */
private fun DrawScope.drawVectorHero(colorId: String?, hatId: String?) {
    val w = this.size.width
    val cloak = Color(ShopCatalog.byId(colorId)?.color ?: ShopCatalog.defaultColor.color)
    drawRoundRect(cloak, topLeft = Offset(w * 0.27f, w * 0.52f), size = Size(w * 0.46f, w * 0.4f), cornerRadius = CornerRadius(w * 0.18f))
    val headC = Offset(w / 2, w * 0.42f)
    drawCircle(Color(0xFFFFD9B8), radius = w * 0.17f, center = headC)
    drawCircle(Color(0xFF2B2440), radius = w * 0.022f, center = headC + Offset(-w * 0.06f, -w * 0.01f))
    drawCircle(Color(0xFF2B2440), radius = w * 0.022f, center = headC + Offset(w * 0.06f, -w * 0.01f))
    drawArc(Color(0xFF2B2440), 20f, 140f, false, topLeft = headC + Offset(-w * 0.05f, w * 0.01f), size = Size(w * 0.1f, w * 0.07f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.015f))
    ShopCatalog.byId(hatId)?.let { drawHat(it.id, Color(it.color), headC, w) }
}

private fun DrawScope.drawTree(base: Offset, h: Float) {
    val p = Path().apply {
        moveTo(base.x, base.y - h); lineTo(base.x - h * 0.45f, base.y); lineTo(base.x + h * 0.45f, base.y); close()
    }
    drawPath(p, Color(0xFF235C3B))
}

private fun DrawScope.drawHat(id: String, color: Color, head: Offset, w: Float) {
    val top = head.y - w * 0.15f
    when (id) {
        "hat_beanie" -> {
            drawArc(color, 180f, 180f, true, topLeft = Offset(head.x - w * 0.18f, top - w * 0.08f), size = Size(w * 0.36f, w * 0.26f))
            drawCircle(color, radius = w * 0.04f, center = Offset(head.x, top - w * 0.09f))
        }
        "hat_party", "hat_wizard" -> {
            val tall = if (id == "hat_wizard") 0.34f else 0.24f
            val p = Path().apply {
                moveTo(head.x, top - w * tall)
                lineTo(head.x - w * 0.15f, top + w * 0.03f)
                lineTo(head.x + w * 0.15f, top + w * 0.03f); close()
            }
            drawPath(p, color)
            if (id == "hat_wizard") drawRect(color, topLeft = Offset(head.x - w * 0.22f, top), size = Size(w * 0.44f, w * 0.04f))
            else drawCircle(Color(0xFFFFC857), radius = w * 0.03f, center = Offset(head.x, top - w * tall))
        }
        "hat_crown" -> {
            val p = Path().apply {
                moveTo(head.x - w * 0.15f, top + w * 0.02f)
                lineTo(head.x - w * 0.15f, top - w * 0.1f)
                lineTo(head.x - w * 0.07f, top - w * 0.03f)
                lineTo(head.x, top - w * 0.13f)
                lineTo(head.x + w * 0.07f, top - w * 0.03f)
                lineTo(head.x + w * 0.15f, top - w * 0.1f)
                lineTo(head.x + w * 0.15f, top + w * 0.02f); close()
            }
            drawPath(p, color)
        }
    }
}
