package com.openfocushq.openfocus.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.openfocushq.openfocus.ui.components.appContainer
import com.openfocushq.openfocus.ui.focus.FocusScreen
import com.openfocushq.openfocus.ui.goals.GoalDetailScreen
import com.openfocushq.openfocus.ui.goals.GoalEditorScreen
import com.openfocushq.openfocus.ui.goals.GoalsScreen
import com.openfocushq.openfocus.ui.habits.HabitDetailScreen
import com.openfocushq.openfocus.ui.habits.HabitEditorScreen
import com.openfocushq.openfocus.ui.habits.HabitsScreen
import com.openfocushq.openfocus.ui.hero.HeroScreen
import com.openfocushq.openfocus.ui.quests.DialogueHost
import com.openfocushq.openfocus.ui.quests.QuestsScreen
import com.openfocushq.openfocus.ui.onboarding.OnboardingScreen
import com.openfocushq.openfocus.ui.settings.SettingsScreen
import com.openfocushq.openfocus.ui.shield.AppPickerScreen
import com.openfocushq.openfocus.ui.shield.ShieldScreen
import com.openfocushq.openfocus.ui.today.TodayScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("today", "Today", Icons.Filled.Home),
    Tab("habits", "Habits", Icons.Filled.CheckCircle),
    Tab("focus", "Focus", Icons.Filled.Timer),
    Tab("goals", "Goals", Icons.Filled.Landscape),
    Tab("shield", "Shield", Icons.Filled.Shield),
)

@Composable
fun OpenFocusRoot(pendingRoute: String?, onRouteConsumed: () -> Unit) {
    val container = appContainer()
    val settings by container.settings.state.collectAsState()
    if (!settings.onboardingDone) {
        OnboardingScreen()
        return
    }
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    // Wait for the NavHost to install its graph (backStack != null) before deep-linking.
    val graphReady = backStack != null
    LaunchedEffect(pendingRoute, graphReady) {
        if (pendingRoute != null && graphReady) {
            nav.navigateTab(pendingRoute)
            onRouteConsumed()
        }
    }

    DialogueHost()
    Scaffold(
        snackbarHost = { FeedbackHost(snackbar) },
        bottomBar = {
            if (tabs.any { it.route == current }) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = current == t.route,
                            onClick = { nav.navigateTab(t.route) },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "today", modifier = Modifier.padding(padding)) {
            composable("today") { TodayScreen(nav) }
            composable("habits") { HabitsScreen(nav) }
            composable("focus") { FocusScreen(nav) }
            composable("goals") { GoalsScreen(nav) }
            composable("shield") { ShieldScreen(nav) }
            composable("hero") { HeroScreen(nav) }
            composable("quests") { QuestsScreen(nav) }
            composable("settings") { SettingsScreen(nav) }
            composable("habit_edit/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                HabitEditorScreen(nav, it.arguments?.getString("id")?.takeIf { id -> id != "new" })
            }
            composable("habit/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                HabitDetailScreen(nav, it.arguments!!.getString("id")!!)
            }
            composable("goal_edit/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                GoalEditorScreen(nav, it.arguments?.getString("id")?.takeIf { id -> id != "new" })
            }
            composable("goal/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                GoalDetailScreen(nav, it.arguments!!.getString("id")!!)
            }
            composable("apps/{mode}", listOf(navArgument("mode") { type = NavType.StringType })) {
                AppPickerScreen(nav, it.arguments!!.getString("mode")!!)
            }
        }
    }
}

fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
