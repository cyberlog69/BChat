package com.praveen.bchat.data.transport

import android.net.Uri
import com.praveen.bchat.domain.model.*
import kotlinx.coroutines.flow.StateFlow

interface P2PTransportListener {
    fun onPeerDiscovered(peer: PeerDevice)
    fun onPeerLost(peerId: String)
    fun onConnectionInitiated(peer: PeerDevice, authVerificationCode: String?)
    fun onConnected(peer: PeerDevice)
    fun onDisconnected(peerId: String)
    fun onPacketReceived(peerId: String, packet: ProtocolPacket)
    fun onFileTransferProgress(transfer: FileTransfer)
    fun onFileReceived(transfer: FileTransfer)
    fun onError(transport: TransportType, message: String)
}

interface P2PTransport {
    val transportType: TransportType
    val isAdvertising: StateFlow<Boolean>
    val isDiscovering: StateFlow<Boolean>
    val connectedPeers: StateFlow<List<PeerDevice>>

    fun setListener(listener: P2PTransportListener)
    fun startAdvertising(localDeviceName: String)
    fun stopAdvertising()
    fun startDiscovery()
    fun stopDiscovery()
    fun connect(peer: PeerDevice)
    fun acceptConnection(peerId: String)
    fun rejectConnection(peerId: String)
    fun disconnect(peerId: String)
    fun disconnectAll()
    fun sendPacket(peerId: String, packet: ProtocolPacket)
    fun broadcastPacket(packet: ProtocolPacket)
    fun sendFile(peerId: String, fileUri: Uri, messageId: String, onProgress: (FileTransfer) -> Unit)
    fun sendChunk(peerId: String, chunk: FileChunkPacket): Boolean {
        sendPacket(
            peerId,
            ProtocolPacket(
                type = PacketType.FILE_CHUNK,
                senderId = "me",
                senderName = "BChat",
                chunkPacket = chunk
            )
        )
        return true
    }
    fun release()
}
