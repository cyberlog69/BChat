package com.praveen.bchat.data.transport

import android.content.Context
import android.net.Uri
import android.util.Log
import com.praveen.bchat.data.transfer.MultiPathFileManager
import com.praveen.bchat.data.transport.bluetooth.BluetoothClassicTransport
import com.praveen.bchat.data.transport.hotspot.HotspotSocketTransport
import com.praveen.bchat.data.transport.nearby.NearbyConnectionsTransport
import com.praveen.bchat.domain.model.*
import com.praveen.bchat.util.CryptoEngine
import com.praveen.bchat.util.NetworkUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey

class P2PManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : P2PTransportListener {

    companion object {
        private const val TAG = "P2PManager"

        @Volatile
        private var INSTANCE: P2PManager? = null

        fun getInstance(context: Context): P2PManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: P2PManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    val nearbyTransport = NearbyConnectionsTransport(context, scope)
    val hotspotTransport = HotspotSocketTransport(context, scope)
    val bluetoothTransport = BluetoothClassicTransport(context, scope)

    val multiPathFileManager = MultiPathFileManager(context, scope)

    private val allTransports: List<P2PTransport> = listOf(
        nearbyTransport,
        hotspotTransport,
        bluetoothTransport
    )

    private val _activeTransportFilter = MutableStateFlow<TransportType?>(null)
    val activeTransportFilter: StateFlow<TransportType?> = _activeTransportFilter.asStateFlow()

    private val _discoveredPeersMap = ConcurrentHashMap<String, PeerDevice>()
    private val _discoveredPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<PeerDevice>> = _discoveredPeers.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val connectedPeers: StateFlow<List<PeerDevice>> = _connectedPeers.asStateFlow()

    // E2EE Session Key Store: peerId -> SecretKey
    private val sessionKeys = ConcurrentHashMap<String, SecretKey>()
    private val peerPublicKeys = ConcurrentHashMap<String, String>()

    private val _incomingMessages = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<ChatMessage> = _incomingMessages.asSharedFlow()

    private val _transferUpdates = MutableSharedFlow<FileTransfer>(extraBufferCapacity = 64)
    val transferUpdates: SharedFlow<FileTransfer> = _transferUpdates.asSharedFlow()

    private val _receivedFiles = MutableSharedFlow<FileTransfer>(extraBufferCapacity = 64)
    val receivedFiles: SharedFlow<FileTransfer> = _receivedFiles.asSharedFlow()

    private val _statusEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val statusEvents: SharedFlow<String> = _statusEvents.asSharedFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    init {
        nearbyTransport.setListener(this)
        hotspotTransport.setListener(this)
        bluetoothTransport.setListener(this)
    }

    fun setTransportFilter(type: TransportType?) {
        _activeTransportFilter.value = type
        updatePeersFlow()
    }

    fun startAllAdvertising(localDeviceName: String) {
        val filter = _activeTransportFilter.value
        if (filter == null) {
            allTransports.forEach { it.startAdvertising(localDeviceName) }
        } else {
            getTransport(filter)?.startAdvertising(localDeviceName)
        }
        _isAdvertising.value = true
        scope.launch { _statusEvents.emit("Advertising as '$localDeviceName' on active channels") }
    }

    fun stopAllAdvertising() {
        allTransports.forEach { it.stopAdvertising() }
        _isAdvertising.value = false
        scope.launch { _statusEvents.emit("Stopped advertising") }
    }

    fun startAllDiscovery() {
        val filter = _activeTransportFilter.value
        _discoveredPeersMap.clear()
        updatePeersFlow()

        if (filter == null) {
            allTransports.forEach { it.startDiscovery() }
        } else {
            getTransport(filter)?.startDiscovery()
        }
        _isScanning.value = true
        scope.launch { _statusEvents.emit("Scanning for nearby peers...") }
    }

    fun stopAllDiscovery() {
        allTransports.forEach { it.stopDiscovery() }
        _isScanning.value = false
        scope.launch { _statusEvents.emit("Scanning stopped") }
    }

    fun connectToPeer(peer: PeerDevice) {
        val transport = getTransport(peer.transportType)
        if (transport == null) {
            scope.launch { _statusEvents.emit("Unknown transport: ${peer.transportType}") }
            return
        }
        val connectingPeer = peer.copy(connectionStatus = ConnectionStatus.CONNECTING)
        _discoveredPeersMap[peer.id] = connectingPeer
        updatePeersFlow()

        transport.connect(peer)
        scope.launch { _statusEvents.emit("Connecting to ${peer.name} via ${peer.transportType.displayName}...") }
    }

    fun disconnectPeer(peer: PeerDevice) {
        val transport = getTransport(peer.transportType)
        transport?.disconnect(peer.id)
        sessionKeys.remove(peer.id)
        peerPublicKeys.remove(peer.id)
        _discoveredPeersMap.remove(peer.id)
        updatePeersFlow()
        updateConnectedPeersFlow()
    }

    fun sendTextMessage(
        peerId: String,
        content: String,
        conversationId: String,
        senderName: String
    ): ChatMessage {
        val messageId = UUID.randomUUID().toString()
        val peer = _discoveredPeersMap[peerId] ?: _connectedPeers.value.find { it.id == peerId }
        val transportType = peer?.transportType ?: TransportType.NEARBY_SHARE

        val sessionKey = sessionKeys[peerId]
        val packet: ProtocolPacket

        if (sessionKey != null) {
            // E2EE Encrypt text payload
            val encryptedPayload = CryptoEngine.encrypt(content, sessionKey)
            if (encryptedPayload != null) {
                packet = ProtocolPacket(
                    type = PacketType.TEXT_MESSAGE,
                    senderId = "me",
                    senderName = senderName,
                    messageId = messageId,
                    conversationId = conversationId,
                    isEncrypted = true,
                    cipherText = encryptedPayload.cipherText,
                    iv = encryptedPayload.iv
                )
            } else {
                packet = ProtocolPacket(
                    type = PacketType.TEXT_MESSAGE,
                    senderId = "me",
                    senderName = senderName,
                    messageId = messageId,
                    textContent = content,
                    conversationId = conversationId,
                    isEncrypted = false
                )
            }
        } else {
            packet = ProtocolPacket(
                type = PacketType.TEXT_MESSAGE,
                senderId = "me",
                senderName = senderName,
                messageId = messageId,
                textContent = content,
                conversationId = conversationId,
                isEncrypted = false
            )
        }

        getTransport(transportType)?.sendPacket(peerId, packet)

        return ChatMessage(
            id = messageId,
            conversationId = conversationId,
            senderId = "me",
            senderName = "Me",
            isOutgoing = true,
            content = content,
            type = MessageType.TEXT,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENT,
            transportType = transportType,
            isEncrypted = (sessionKey != null)
        )
    }

    fun broadcastTextMessage(
        content: String,
        conversationId: String,
        senderName: String
    ): ChatMessage {
        val messageId = UUID.randomUUID().toString()
        val packet = ProtocolPacket(
            type = PacketType.TEXT_MESSAGE,
            senderId = "me",
            senderName = senderName,
            messageId = messageId,
            textContent = content,
            conversationId = conversationId,
            isEncrypted = false
        )

        allTransports.forEach { it.broadcastPacket(packet) }

        return ChatMessage(
            id = messageId,
            conversationId = conversationId,
            senderId = "me",
            senderName = "Me",
            isOutgoing = true,
            content = content,
            type = MessageType.TEXT,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENT,
            transportType = TransportType.NEARBY_SHARE,
            isEncrypted = false
        )
    }

    fun sendFile(
        peerId: String,
        fileUri: Uri,
        conversationId: String,
        meta: FileAttachmentMeta,
        onProgress: (FileTransfer) -> Unit
    ): ChatMessage {
        val messageId = UUID.randomUUID().toString()
        val peer = _discoveredPeersMap[peerId] ?: _connectedPeers.value.find { it.id == peerId }
        val transportType = peer?.transportType ?: TransportType.NEARBY_SHARE

        val transport = getTransport(transportType)
        transport?.sendFile(peerId, fileUri, messageId) { transfer ->
            scope.launch {
                _transferUpdates.emit(transfer)
                onProgress(transfer)
            }
        }

        return ChatMessage(
            id = messageId,
            conversationId = conversationId,
            senderId = "me",
            senderName = "Me",
            isOutgoing = true,
            content = "Sent file: ${meta.fileName}",
            type = if (meta.mimeType.startsWith("image/")) MessageType.IMAGE else MessageType.FILE,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING,
            transportType = transportType,
            isEncrypted = true,
            fileAttachment = meta
        )
    }

    /**
     * Sends a large file using concurrent Multi-Path striping across all connected channels.
     */
    fun sendFileMultiPath(
        peerId: String,
        fileUri: Uri,
        conversationId: String,
        meta: FileAttachmentMeta,
        onProgress: (FileTransfer) -> Unit
    ): ChatMessage {
        val messageId = UUID.randomUUID().toString()
        val peer = _discoveredPeersMap[peerId] ?: _connectedPeers.value.find { it.id == peerId }
        val peerName = peer?.name ?: "Peer"

        val availableTransports = allTransports.filter { transport ->
            transport.connectedPeers.value.any { it.id == peerId } || transport.transportType == peer?.transportType
        }.ifEmpty {
            listOfNotNull(peer?.transportType?.let { getTransport(it) } ?: hotspotTransport)
        }

        multiPathFileManager.sendFileMultiPath(
            transports = availableTransports,
            peerId = peerId,
            peerName = peerName,
            fileUri = fileUri,
            messageId = messageId
        ) { transfer ->
            scope.launch {
                _transferUpdates.emit(transfer)
                onProgress(transfer)
            }
        }

        return ChatMessage(
            id = messageId,
            conversationId = conversationId,
            senderId = "me",
            senderName = "Me",
            isOutgoing = true,
            content = "⚡ Multi-Path file: ${meta.fileName}",
            type = if (meta.mimeType.startsWith("image/")) MessageType.IMAGE else MessageType.FILE,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING,
            transportType = peer?.transportType ?: TransportType.HOTSPOT_WIFI,
            isEncrypted = true,
            fileAttachment = meta
        )
    }

    private fun getTransport(type: TransportType): P2PTransport? {
        return when (type) {
            TransportType.NEARBY_SHARE -> nearbyTransport
            TransportType.HOTSPOT_WIFI -> hotspotTransport
            TransportType.BLUETOOTH_CLASSIC -> bluetoothTransport
        }
    }

    // P2PTransportListener callbacks
    override fun onPeerDiscovered(peer: PeerDevice) {
        _discoveredPeersMap[peer.id] = peer
        updatePeersFlow()
    }

    override fun onPeerLost(peerId: String) {
        _discoveredPeersMap.remove(peerId)
        sessionKeys.remove(peerId)
        peerPublicKeys.remove(peerId)
        updatePeersFlow()
    }

    override fun onConnectionInitiated(peer: PeerDevice, authVerificationCode: String?) {
        _discoveredPeersMap[peer.id] = peer
        updatePeersFlow()
        scope.launch {
            _statusEvents.emit("Connecting to ${peer.name}...")
        }
    }

    override fun onConnected(peer: PeerDevice) {
        val updated = peer.copy(connectionStatus = ConnectionStatus.CONNECTED)
        _discoveredPeersMap[peer.id] = updated
        updatePeersFlow()
        updateConnectedPeersFlow()

        // Send E2EE Handshake packet containing our EC Public Key
        try {
            val myPubKey = CryptoEngine.getLocalPublicKeyBase64()
            val handshake = ProtocolPacket(
                type = PacketType.HANDSHAKE,
                senderId = "me",
                senderName = NetworkUtils.getDeviceName(context),
                publicKey = myPubKey
            )
            getTransport(peer.transportType)?.sendPacket(peer.id, handshake)
            Log.d(TAG, "Sent E2EE Handshake with public key to ${peer.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating E2EE Handshake", e)
        }

        scope.launch {
            _statusEvents.emit("Connected to ${peer.name} (${peer.transportType.displayName})")
        }
    }

    override fun onDisconnected(peerId: String) {
        val existing = _discoveredPeersMap[peerId]
        if (existing != null) {
            _discoveredPeersMap[peerId] = existing.copy(connectionStatus = ConnectionStatus.DISCONNECTED)
        }
        sessionKeys.remove(peerId)
        peerPublicKeys.remove(peerId)
        updatePeersFlow()
        updateConnectedPeersFlow()
        scope.launch {
            _statusEvents.emit("Disconnected from ${existing?.name ?: peerId}")
        }
    }

    override fun onPacketReceived(peerId: String, packet: ProtocolPacket) {
        val peer = _discoveredPeersMap[peerId]
        val senderName = packet.senderName.ifBlank { peer?.name ?: "Peer" }
        val transportType = peer?.transportType ?: TransportType.NEARBY_SHARE

        when (packet.type) {
            PacketType.HANDSHAKE -> {
                // Peer sent their public key in HANDSHAKE
                val peerPubKey = packet.publicKey
                if (peerPubKey != null) {
                    handlePeerPublicKey(peerId, peerPubKey)
                    // Reply with HANDSHAKE_ACK containing our public key
                    try {
                        val myPubKey = CryptoEngine.getLocalPublicKeyBase64()
                        val ack = ProtocolPacket(
                            type = PacketType.HANDSHAKE_ACK,
                            senderId = "me",
                            senderName = NetworkUtils.getDeviceName(context),
                            publicKey = myPubKey
                        )
                        getTransport(transportType)?.sendPacket(peerId, ack)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error replying to HANDSHAKE", e)
                    }
                }
            }

            PacketType.HANDSHAKE_ACK -> {
                // Peer replied with their public key in HANDSHAKE_ACK
                val peerPubKey = packet.publicKey
                if (peerPubKey != null) {
                    handlePeerPublicKey(peerId, peerPubKey)
                }
            }

            PacketType.TEXT_MESSAGE -> {
                val resolvedText: String
                val isEncrypted: Boolean

                val sessionKey = sessionKeys[peerId]
                if (packet.isEncrypted && packet.cipherText != null && packet.iv != null && sessionKey != null) {
                    val decrypted = CryptoEngine.decrypt(packet.cipherText, packet.iv, sessionKey)
                    resolvedText = decrypted ?: "[⚠️ Failed to decrypt message]"
                    isEncrypted = true
                } else {
                    resolvedText = packet.textContent ?: ""
                    isEncrypted = false
                }

                val msg = ChatMessage(
                    id = packet.messageId ?: UUID.randomUUID().toString(),
                    conversationId = packet.conversationId ?: peerId,
                    senderId = peerId,
                    senderName = senderName,
                    isOutgoing = false,
                    content = resolvedText,
                    type = MessageType.TEXT,
                    timestamp = packet.timestamp,
                    status = MessageStatus.DELIVERED,
                    transportType = transportType,
                    isEncrypted = isEncrypted
                )
                scope.launch { _incomingMessages.emit(msg) }
            }

            PacketType.FILE_OFFER -> {
                if (packet.fileAttachment != null) {
                    val msg = ChatMessage(
                        id = packet.messageId ?: UUID.randomUUID().toString(),
                        conversationId = packet.conversationId ?: peerId,
                        senderId = peerId,
                        senderName = senderName,
                        isOutgoing = false,
                        content = "Incoming file: ${packet.fileAttachment.fileName}",
                        type = if (packet.fileAttachment.mimeType.startsWith("image/")) MessageType.IMAGE else MessageType.FILE,
                        timestamp = packet.timestamp,
                        status = MessageStatus.SENDING,
                        transportType = transportType,
                        isEncrypted = true,
                        fileAttachment = packet.fileAttachment
                    )
                    scope.launch { _incomingMessages.emit(msg) }
                }
            }

            PacketType.MULTIPATH_HANDSHAKE -> {
                val handshake = packet.multipathHandshake
                if (handshake != null) {
                    multiPathFileManager.startIncomingSession(
                        handshake = handshake,
                        peerId = peerId,
                        peerName = senderName,
                        onProgress = { transfer ->
                            scope.launch { _transferUpdates.emit(transfer) }
                        },
                        onComplete = { transfer ->
                            scope.launch {
                                _receivedFiles.emit(transfer)
                                _statusEvents.emit("⚡ Received multi-path file: ${transfer.fileName}")
                            }
                        }
                    )
                }
            }

            PacketType.FILE_CHUNK -> {
                val chunk = packet.chunkPacket
                if (chunk != null) {
                    multiPathFileManager.handleIncomingChunk(chunk, transportType)
                }
            }

            else -> {
                // Other packets
            }
        }
    }

    private fun handlePeerPublicKey(peerId: String, peerPubKey: String) {
        try {
            peerPublicKeys[peerId] = peerPubKey
            val sessionKey = CryptoEngine.deriveSessionKey(peerPubKey)
            if (sessionKey != null) {
                sessionKeys[peerId] = sessionKey
                val myPubKey = CryptoEngine.getLocalPublicKeyBase64()
                val safetyNumber = CryptoEngine.computeSafetyNumber(myPubKey, peerPubKey)

                val existing = _discoveredPeersMap[peerId]
                if (existing != null) {
                    val updated = existing.copy(
                        publicKey = peerPubKey,
                        safetyNumber = safetyNumber
                    )
                    _discoveredPeersMap[peerId] = updated
                    updatePeersFlow()
                    updateConnectedPeersFlow()
                }

                Log.d(TAG, "🔒 E2EE Session Key established with $peerId. Safety Number: $safetyNumber")
                scope.launch {
                    _statusEvents.emit("🔒 E2EE Established with ${existing?.name ?: peerId}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling peer public key", e)
        }
    }

    override fun onFileTransferProgress(transfer: FileTransfer) {
        scope.launch { _transferUpdates.emit(transfer) }
    }

    override fun onFileReceived(transfer: FileTransfer) {
        scope.launch {
            _receivedFiles.emit(transfer)
            _statusEvents.emit("Received file: ${transfer.fileName}")
        }
    }

    override fun onError(transport: TransportType, message: String) {
        Log.e(TAG, "Error in ${transport.name}: $message")
        scope.launch {
            _statusEvents.emit("[${transport.displayName}] $message")
        }
    }

    private fun updatePeersFlow() {
        val filter = _activeTransportFilter.value
        val list = _discoveredPeersMap.values.toList()
        _discoveredPeers.value = if (filter == null) list else list.filter { it.transportType == filter }
    }

    private fun updateConnectedPeersFlow() {
        val allConnected = mutableListOf<PeerDevice>()
        allConnected.addAll(nearbyTransport.connectedPeers.value)
        allConnected.addAll(hotspotTransport.connectedPeers.value)
        allConnected.addAll(bluetoothTransport.connectedPeers.value)
        _connectedPeers.value = allConnected.distinctBy { it.id }.map {
            // enrich with E2EE metadata
            it.copy(
                publicKey = peerPublicKeys[it.id],
                safetyNumber = _discoveredPeersMap[it.id]?.safetyNumber
            )
        }
    }

    fun release() {
        allTransports.forEach { it.release() }
    }
}
