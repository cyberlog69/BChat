package com.praveen.bchat.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Discover : Screen("discover", "Discover", Icons.Default.Radar)
    object Chats : Screen("chats", "Chats", Icons.Default.Chat)
    object Transfers : Screen("transfers", "Transfers", Icons.Default.Bolt)
    object Hotspot : Screen("hotspot", "Hotspot", Icons.Default.WifiTethering)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object ChatDetail : Screen("chat_detail/{conversationId}/{peerName}", "Chat", Icons.Default.Chat) {
        fun createRoute(conversationId: String, peerName: String) = "chat_detail/$conversationId/$peerName"
    }
}

val bottomNavItems = listOf(
    Screen.Discover,
    Screen.Chats,
    Screen.Transfers,
    Screen.Hotspot,
    Screen.Settings
)
