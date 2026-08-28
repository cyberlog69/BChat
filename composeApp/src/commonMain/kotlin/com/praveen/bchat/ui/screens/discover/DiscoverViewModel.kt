package com.praveen.bchat.ui.screens.discover

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.praveen.bchat.BChatApplication
import com.praveen.bchat.domain.model.PeerDevice
import com.praveen.bchat.domain.model.TransportType
import com.praveen.bchat.util.NetworkUtils
import com.praveen.bchat.util.QrPeerPayload
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DiscoverViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as BChatApplication).chatRepository
    private val p2pManager = (application as BChatApplication).p2pManager

    val discoveredPeers: StateFlow<List<PeerDevice>> = repository.discoveredPeers
    val connectedPeers: StateFlow<List<PeerDevice>> = repository.connectedPeers
    val activeTransportFilter: StateFlow<TransportType?> = repository.activeTransportFilter
    val isScanning: StateFlow<Boolean> = repository.isScanning
    val isAdvertising: StateFlow<Boolean> = repository.isAdvertising

    val statusEvents: SharedFlow<String> = p2pManager.statusEvents

    fun toggleScan() {
        if (isScanning.value) {
            repository.stopDiscovery()
        } else {
            repository.startDiscovery()
        }
    }

    fun toggleAdvertising() {
        if (isAdvertising.value) {
            repository.stopAdvertising()
        } else {
            repository.startAdvertising()
        }
    }

    fun setTransportFilter(type: TransportType?) {
        repository.setTransportFilter(type)
    }

    fun connectToPeer(peer: PeerDevice) {
        repository.connectToPeer(peer)
    }

    fun disconnectPeer(peerId: String) {
        repository.disconnectPeer(peerId)
    }

    fun getLocalQrPayload(): QrPeerPayload {
        val app = getApplication<Application>()
        val deviceName = NetworkUtils.getDeviceName(app)
        val ip = NetworkUtils.getLocalIpAddress()
        return QrPeerPayload(
            deviceName = deviceName,
            transport = "ALL",
            ipAddress = ip,
            port = 8888
        )
    }

    fun connectViaQrPayload(payload: QrPeerPayload) {
        if (payload.ipAddress != null) {
            val peer = PeerDevice(
                id = "${payload.ipAddress}:${payload.port ?: 8888}",
                name = payload.deviceName,
                transportType = TransportType.HOTSPOT_WIFI,
                ipAddress = payload.ipAddress,
                port = payload.port ?: 8888
            )
            repository.connectToPeer(peer)
        }
    }
}
