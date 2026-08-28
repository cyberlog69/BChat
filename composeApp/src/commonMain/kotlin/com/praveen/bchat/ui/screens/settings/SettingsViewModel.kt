package com.praveen.bchat.ui.screens.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.praveen.bchat.domain.model.TransportType
import com.praveen.bchat.util.CryptoEngine
import com.praveen.bchat.util.FileManager
import com.praveen.bchat.util.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("bchat_settings", Context.MODE_PRIVATE)

    private val _deviceName = MutableStateFlow(
        prefs.getString("device_name", NetworkUtils.getDeviceName(application)) ?: "BChat Device"
    )
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _preferredTransport = MutableStateFlow(
        try {
            TransportType.valueOf(prefs.getString("preferred_transport", TransportType.NEARBY_SHARE.name)!!)
        } catch (e: Exception) {
            TransportType.NEARBY_SHARE
        }
    )
    val preferredTransport: StateFlow<TransportType> = _preferredTransport.asStateFlow()

    private val _autoAcceptFiles = MutableStateFlow(
        prefs.getBoolean("auto_accept_files", true)
    )
    val autoAcceptFiles: StateFlow<Boolean> = _autoAcceptFiles.asStateFlow()

    fun updateDeviceName(name: String) {
        _deviceName.value = name
        prefs.edit().putString("device_name", name).apply()
    }

    fun updatePreferredTransport(type: TransportType) {
        _preferredTransport.value = type
        prefs.edit().putString("preferred_transport", type.name).apply()
    }

    fun updateAutoAccept(enable: Boolean) {
        _autoAcceptFiles.value = enable
        prefs.edit().putBoolean("auto_accept_files", enable).apply()
    }

    fun getStorageLocation(): String {
        return FileManager.getBChatDownloadDir(getApplication()).absolutePath
    }

    fun getIdentityKeyFingerprint(): String {
        return CryptoEngine.getLocalFingerprint()
    }
}
