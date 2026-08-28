package com.praveen.bchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.praveen.bchat.ui.navigation.AppNavigation
import com.praveen.bchat.ui.theme.BChatTheme
import com.praveen.bchat.ui.theme.DarkBackground
import com.praveen.bchat.util.PermissionUtils

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions granted callback
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BChatTheme(darkTheme = true) {
                LaunchedEffect(Unit) {
                    val required = PermissionUtils.getRequiredPermissions()
                    if (!PermissionUtils.hasPermissions(this@MainActivity, required)) {
                        permissionLauncher.launch(required.toTypedArray())
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    val navController = rememberNavController()
                    AppNavigation(navController = navController)
                }
            }
        }
    }
}
