package org.openfocus.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Name -> Material icon lookup (Material Icons, Apache License 2.0). Entities store icon names
 * as strings so the data stays platform-neutral.
 */
object AppIcons {
    val habitChoices: Map<String, ImageVector> = linkedMapOf(
        "Star" to Icons.Filled.Star,
        "Run" to Icons.AutoMirrored.Filled.DirectionsRun,
        "Book" to Icons.AutoMirrored.Filled.MenuBook,
        "Water" to Icons.Filled.WaterDrop,
        "Meditate" to Icons.Filled.SelfImprovement,
        "Fitness" to Icons.Filled.FitnessCenter,
        "Sleep" to Icons.Filled.Bedtime,
        "Food" to Icons.Filled.Restaurant,
        "Code" to Icons.Filled.Code,
        "Music" to Icons.Filled.MusicNote,
        "Brush" to Icons.Filled.Brush,
        "Language" to Icons.Filled.Translate,
        "School" to Icons.Filled.School,
        "Clean" to Icons.Filled.CleaningServices,
        "Money" to Icons.Filled.Savings,
        "Heart" to Icons.Filled.Favorite,
        "Phone" to Icons.Filled.PhoneAndroid,
        "NoPhone" to Icons.Filled.PhonelinkErase,
        "Smoke" to Icons.Filled.SmokeFree,
        "Sun" to Icons.Filled.WbSunny,
        "Walk" to Icons.AutoMirrored.Filled.DirectionsWalk,
        "Journal" to Icons.Filled.EditNote,
        "Plant" to Icons.Filled.Spa,
        "Call" to Icons.Filled.Call,
    )

    private val extra: Map<String, ImageVector> = mapOf(
        "DirectionsWalk" to Icons.AutoMirrored.Filled.DirectionsWalk,
        "Repeat" to Icons.Filled.Repeat,
        "AllInclusive" to Icons.Filled.AllInclusive,
        "LocalFireDepartment" to Icons.Filled.LocalFireDepartment,
        "Whatshot" to Icons.Filled.Whatshot,
        "MilitaryTech" to Icons.Filled.MilitaryTech,
        "SelfImprovement" to Icons.Filled.SelfImprovement,
        "ScubaDiving" to Icons.Filled.ScubaDiving,
        "Waves" to Icons.Filled.Waves,
        "Flag" to Icons.Filled.Flag,
        "Landscape" to Icons.Filled.Landscape,
        "Terrain" to Icons.Filled.Terrain,
        "Shield" to Icons.Filled.Shield,
        "Security" to Icons.Filled.Security,
        "Block" to Icons.Filled.Block,
        "TrendingUp" to Icons.AutoMirrored.Filled.TrendingUp,
        "AutoAwesome" to Icons.Filled.AutoAwesome,
        "Cake" to Icons.Filled.Cake,
        "Checkroom" to Icons.Filled.Checkroom,
        "CardGiftcard" to Icons.Filled.CardGiftcard,
        "Coffee" to Icons.Filled.Coffee,
        "Movie" to Icons.Filled.Movie,
        "Gamepad" to Icons.Filled.SportsEsports,
        "Icecream" to Icons.Filled.Icecream,
    )

    val rewardChoices = listOf("CardGiftcard", "Coffee", "Movie", "Gamepad", "Icecream", "Cake")

    fun get(name: String?): ImageVector = habitChoices[name] ?: extra[name] ?: Icons.Filled.Star
}
