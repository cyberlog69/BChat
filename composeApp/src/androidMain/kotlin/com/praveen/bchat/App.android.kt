package com.praveen.bchat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.praveen.bchat.ui.navigation.AppNavigation
import com.praveen.bchat.ui.screens.settings.SettingsViewModel
import com.praveen.bchat.ui.theme.BChatTheme

@Composable
actual fun App() {
    val settingsViewModel: SettingsViewModel = viewModel()
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val dynamicColor by settingsViewModel.dynamicColor.collectAsState()

    BChatTheme(
        themeMode = themeMode,
        dynamicColor = dynamicColor
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val navController = rememberNavController()
            AppNavigation(navController = navController)
        }
    }
}
