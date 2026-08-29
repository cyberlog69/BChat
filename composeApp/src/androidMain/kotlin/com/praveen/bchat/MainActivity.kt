package com.praveen.bchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.praveen.bchat.util.PermissionUtils

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request runtime permissions for Bluetooth, Nearby Wi-Fi, and Camera
        val requiredPerms = PermissionUtils.getRequiredPermissions()
        if (!PermissionUtils.hasPermissions(this, requiredPerms)) {
            permissionLauncher.launch(requiredPerms.toTypedArray())
        }

        setContent {
            App()
        }
    }
}
