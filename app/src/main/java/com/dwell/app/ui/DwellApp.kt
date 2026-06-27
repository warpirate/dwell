package com.dwell.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dwell.app.ui.navigation.DwellDestination
import com.dwell.app.ui.screens.MoreScreen
import com.dwell.app.ui.screens.WidgetsScreen
import com.dwell.app.ui.wallpapers.WallpapersScreen

/**
 * App shell: a Scaffold with the bottom navigation bar and a NavHost over the
 * three destinations. Wallpapers is the start destination.
 */
@Composable
fun DwellApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            DwellBottomBar(
                currentRoute = currentRoute,
                onSelect = { destination ->
                    if (destination.route != currentRoute) {
                        navController.navigate(destination.route) {
                            // Single instance per tab; preserve and restore tab state.
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = DwellDestination.START.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(DwellDestination.WALLPAPERS.route) { WallpapersScreen() }
            composable(DwellDestination.WIDGETS.route) { WidgetsScreen() }
            composable(DwellDestination.MORE.route) { MoreScreen() }
        }
    }
}

@Composable
private fun DwellBottomBar(
    currentRoute: String?,
    onSelect: (DwellDestination) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        DwellDestination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            val label = stringResource(destination.labelRes)
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    // Label carries the name for screen readers, so the icon is
                    // decorative here (contentDescription = null) to avoid a
                    // duplicate announcement.
                    androidx.compose.material3.Icon(
                        painter = painterResource(destination.iconRes),
                        contentDescription = null,
                    )
                },
                label = { Text(text = label) },
                alwaysShowLabel = true,
                // Accent appears ONLY on the selected item, and only on the
                // glyph + label, never as a large pill fill (indicator is
                // transparent). Brand rule: accent on the acted-on thing only.
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
