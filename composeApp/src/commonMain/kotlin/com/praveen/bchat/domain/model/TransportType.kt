package com.praveen.bchat.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class TransportType(val displayName: String, val speedRating: String) {
    NEARBY_SHARE("Nearby Share", "Turbo (Up to 40+ MB/s)"),
    HOTSPOT_WIFI("Wi-Fi Hotspot / LAN", "High Speed (Up to 20+ MB/s)"),
    BLUETOOTH_CLASSIC("Bluetooth Classic", "Standard (~2 MB/s)")
}

@Serializable
enum class ConnectionStatus {
    DISCONNECTED,
    ADVERTISING,
    DISCOVERING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED
}
