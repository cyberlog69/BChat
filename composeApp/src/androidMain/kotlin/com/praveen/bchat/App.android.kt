package com.praveen.bchat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.praveen.bchat.ui.navigation.AppNavigation
import com.praveen.bchat.ui.theme.BChatTheme
import com.praveen.bchat.ui.theme.DarkBackground

@Composable
actual fun App() {
    BChatTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = DarkBackground
        ) {
            val navController = rememberNavController()
            AppNavigation(navController = navController)
        }
    }
}
