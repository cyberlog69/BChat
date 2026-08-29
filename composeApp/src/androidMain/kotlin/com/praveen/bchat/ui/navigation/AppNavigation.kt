package com.praveen.bchat.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.praveen.bchat.ui.screens.chat.ChatDetailScreen
import com.praveen.bchat.ui.screens.chat.ChatListScreen
import com.praveen.bchat.ui.screens.discover.DiscoverScreen
import com.praveen.bchat.ui.screens.hotspot.HotspotScreen
import com.praveen.bchat.ui.screens.settings.SettingsScreen
import com.praveen.bchat.ui.screens.transfers.TransfersScreen
import com.praveen.bchat.ui.theme.CyanNeon
import com.praveen.bchat.ui.theme.DarkBackground
import com.praveen.bchat.ui.theme.DarkSurface
import com.praveen.bchat.ui.theme.TextSecondary

@Composable
fun AppNavigation(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = bottomNavItems.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = DarkSurface,
                    tonalElevation = 8.dp
                ) {
                    bottomNavItems.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Discover.route) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = DarkBackground,
                                selectedTextColor = CyanNeon,
                                indicatorColor = CyanNeon,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            )
                        )
                    }
                }
            }
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Discover.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Discover.route) {
                DiscoverScreen(
                    onNavigateToChat = { peerId, peerName ->
                        navController.navigate(Screen.ChatDetail.createRoute(peerId, peerName))
                    }
                )
            }

            composable(Screen.Chats.route) {
                ChatListScreen(
                    onNavigateToChat = { peerId, peerName ->
                        navController.navigate(Screen.ChatDetail.createRoute(peerId, peerName))
                    },
                    onNavigateToDiscover = {
                        navController.navigate(Screen.Discover.route)
                    }
                )
            }

            composable(Screen.Transfers.route) {
                TransfersScreen(
                    onNavigateToChat = { peerId, peerName ->
                        navController.navigate(Screen.ChatDetail.createRoute(peerId, peerName))
                    }
                )
            }

            composable(Screen.Hotspot.route) {
                HotspotScreen(
                    onNavigateToChat = { peerId, peerName ->
                        navController.navigate(Screen.ChatDetail.createRoute(peerId, peerName))
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen()
            }

            composable(
                route = Screen.ChatDetail.route,
                arguments = listOf(
                    navArgument("conversationId") { type = NavType.StringType },
                    navArgument("peerName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
                val peerName = backStackEntry.arguments?.getString("peerName") ?: "Peer"

                ChatDetailScreen(
                    conversationId = conversationId,
                    peerName = peerName,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
