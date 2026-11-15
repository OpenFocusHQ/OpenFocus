package com.openfocushq.openfocus.blocking

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.ui.graphics.vector.ImageVector

/** Icon shown on the pause screen. [vector] null = the pixel-art shield. Material Icons are Apache-2.0. */
enum class PauseIcon(val id: String, val label: String, val vector: ImageVector?) {
    SHIELD("shield", "Shield", null),
    HEART("heart", "Heart", Icons.Outlined.FavoriteBorder),
    CARE("care", "Care", Icons.Outlined.VolunteerActivism),
    HANDSHAKE("handshake", "Promise", Icons.Outlined.Handshake),
    LOTUS("lotus", "Lotus", Icons.Outlined.Spa),
    MEDITATE("meditate", "Breathe", Icons.Outlined.SelfImprovement),
    MOUNTAIN("mountain", "Summit", Icons.Outlined.Landscape);

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: SHIELD
    }
}

/**
 * Background / foreground pair for the pause screen. Calm, neutral colours by default;
 * [SYSTEM] follows the phone's light/dark mode with neutral greys.
 */
enum class PauseColors(val id: String, val label: String, val background: Long, val foreground: Long) {
    SYSTEM("system", "Auto", 0, 0),
    PAPER("paper", "Paper", 0xFFF3F3F3, 0xFF000000),
    INK("ink", "Ink", 0xFF000000, 0xFFFFFFFF),
    STONE("stone", "Stone", 0xFFBDBDBD, 0xFF000000),
    SAND("sand", "Sand", 0xFFCFC6AE, 0xFF4B4741),
    BLUSH("blush", "Blush", 0xFFF2DCCB, 0xFF4A3128),
    CLAY("clay", "Clay", 0xFFE8B596, 0xFF6E2A07),
    DUSK("dusk", "Dusk", 0xFF2D2A6E, 0xFFEDE7FF);

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: SYSTEM
        /** Neutral pair used by [SYSTEM]. */
        const val LIGHT_BG = 0xFFF3F3F3
        const val LIGHT_FG = 0xFF000000
        const val DARK_BG = 0xFF121212
        const val DARK_FG = 0xFFF2F2F2
    }
}
