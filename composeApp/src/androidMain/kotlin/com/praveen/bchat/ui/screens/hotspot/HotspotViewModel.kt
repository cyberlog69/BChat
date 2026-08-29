package com.praveen.bchat.ui.screens.hotspot

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.praveen.bchat.BChatApplication
import com.praveen.bchat.domain.model.PeerDevice
import com.praveen.bchat.domain.model.TransportType
import com.praveen.bchat.util.NetworkUtils
import com.praveen.bchat.util.QrPeerPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HotspotViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as BChatApplication).chatRepository
    private val p2pManager = (application as BChatApplication).p2pManager

    private val _localIp = MutableStateFlow(NetworkUtils.getLocalIpAddress())
    val localIp: StateFlow<String?> = _localIp.asStateFlow()

    val isServerRunning: StateFlow<Boolean> = p2pManager.hotspotTransport.isAdvertising

    fun refreshNetwork() {
        _localIp.value = NetworkUtils.getLocalIpAddress()
    }

    fun startHotspotServer() {
        val name = NetworkUtils.getDeviceName(getApplication())
        p2pManager.hotspotTransport.startAdvertising(name)
    }

    fun stopHotspotServer() {
        p2pManager.hotspotTransport.stopAdvertising()
    }

    fun connectToIp(ip: String, port: Int = 8888, peerName: String = "Wi-Fi Peer") {
        val peer = PeerDevice(
            id = "$ip:$port",
            name = peerName,
            transportType = TransportType.HOTSPOT_WIFI,
            ipAddress = ip,
            port = port
        )
        repository.connectToPeer(peer)
    }

    fun getHotspotQrPayload(): QrPeerPayload {
        val app = getApplication<Application>()
        return QrPeerPayload(
            deviceName = NetworkUtils.getDeviceName(app),
            transport = "HOTSPOT_WIFI",
            ipAddress = _localIp.value,
            port = 8888
        )
    }
}
