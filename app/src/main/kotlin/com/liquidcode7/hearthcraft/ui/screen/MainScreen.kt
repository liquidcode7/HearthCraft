package com.liquidcode7.hearthcraft.ui.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home",     "Home",     Icons.Filled.Home),
    Tab("gather",   "Gather",   Icons.Filled.Forest),
    Tab("kitchen",  "Kitchen",  Icons.Filled.LocalDining),
    Tab("band",     "Band",     Icons.Filled.Groups),
    Tab("missions", "Missions", Icons.Filled.Flag),
    Tab("market",   "Market",   Icons.Filled.Storefront),
)

private val tabRoutes = tabs.map { it.route }.toSet()

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in tabRoutes) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home")     { HomeScreen() }
            composable("gather")   { GatheringScreen() }
            composable("kitchen")  {
                KitchenScreen(
                    onViewRecipes = { navController.navigate("recipe_book") },
                    onViewPantry  = { navController.navigate("pantry") }
                )
            }
            composable("recipe_book") {
                RecipeBookScreen(onBack = { navController.popBackStack() })
            }
            composable("pantry") {
                PantryScreen(onBack = { navController.popBackStack() })
            }
            composable("band")     { BandScreen() }
            composable("missions") { MissionsScreen() }
            composable("market")   { MarketScreen() }
        }
    }
}
