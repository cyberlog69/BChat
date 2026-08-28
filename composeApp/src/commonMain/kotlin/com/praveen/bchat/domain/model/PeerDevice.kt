package com.praveen.bchat.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PeerDevice(
    val id: String, // Unique endpoint ID, MAC, or IP:Port
    val name: String,
    val transportType: TransportType,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val rssi: Int = 0, // Signal strength (optional)
    val ipAddress: String? = null,
    val port: Int? = null,
    val bluetoothAddress: String? = null,
    val nearbyEndpointId: String? = null,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val avatarColorSeed: Int = name.hashCode(),
    val publicKey: String? = null,       // Base64 EC Public Key
    val safetyNumber: String? = null     // 6-digit E2EE safety number
) {
    val isConnected: Boolean
        get() = connectionStatus == ConnectionStatus.CONNECTED

    val isEncrypted: Boolean
        get() = publicKey != null
}
