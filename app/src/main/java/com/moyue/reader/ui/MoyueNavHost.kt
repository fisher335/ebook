package com.moyue.reader.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moyue.reader.AppContainer
import com.moyue.reader.ui.discover.DiscoverScreen
import com.moyue.reader.ui.profile.ProfileScreen
import com.moyue.reader.ui.reader.ReaderScreen
import com.moyue.reader.ui.shelf.ShelfScreen

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val topLevelDestinations = listOf(
    TopLevelDestination("shelf", "书架", Icons.Outlined.MenuBook),
    TopLevelDestination("discover", "书城", Icons.Outlined.Explore),
    TopLevelDestination("profile", "我的", Icons.Outlined.Person)
)

@Composable
fun MoyueNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = topLevelDestinations.any { it.route == currentRoute }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                    tonalElevation = 0.dp
                ) {
                    topLevelDestinations.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    destination.icon,
                                    contentDescription = destination.label
                                )
                            },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "shelf",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("shelf") {
                ShelfScreen(
                    container = container,
                    onOpenBook = { bookId -> navController.navigate("reader/$bookId") },
                    onDiscover = { navController.navigate("discover") }
                )
            }
            composable("discover") {
                DiscoverScreen(
                    container = container,
                    onOpenBook = { bookId -> navController.navigate("reader/$bookId") }
                )
            }
            composable("profile") {
                ProfileScreen(container = container)
            }
            composable("reader/{bookId}") { entry ->
                val bookId = entry.arguments?.getString("bookId") ?: return@composable
                ReaderScreen(
                    bookId = bookId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
