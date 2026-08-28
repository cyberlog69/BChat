package com.praveen.bchat.data.transport.hotspot

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.Uri
import android.util.Log
import com.praveen.bchat.data.transport.P2PTransport
import com.praveen.bchat.data.transport.P2PTransportListener
import com.praveen.bchat.domain.model.*
import com.praveen.bchat.util.FileManager
import com.praveen.bchat.util.NetworkUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class HotspotSocketTransport(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : P2PTransport {

    companion object {
        private const val TAG = "HotspotTransport"
        const val DEFAULT_PORT = 8888
        private const val SERVICE_TYPE = "_bchat._tcp."
        private const val TYPE_JSON_PACKET = 0
        private const val TYPE_FILE_DATA = 1
        private const val CHUNK_SIZE = 64 * 1024 // 64 KB
    }

    override val transportType: TransportType = TransportType.HOTSPOT_WIFI

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var listener: P2PTransportListener? = null

    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    override val connectedPeers: StateFlow<List<PeerDevice>> = _connectedPeers.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var isServerRunning = false

    // Map: peerId -> SocketHandler
    private val clientSockets = ConcurrentHashMap<String, SocketHandler>()
    private val discoveredPeers = ConcurrentHashMap<String, PeerDevice>()

    override fun setListener(listener: P2PTransportListener) {
        this.listener = listener
    }

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    override fun startAdvertising(localDeviceName: String) {
        if (_isAdvertising.value) return
        startServer()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = localDeviceName.replace(" ", "_")
            serviceType = SERVICE_TYPE
            port = DEFAULT_PORT
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                Log.d(TAG, "NSD Service registered: ${NsdServiceInfo.serviceName}")
                _isAdvertising.value = true
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD Registration failed: $errorCode")
                _isAdvertising.value = false
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.d(TAG, "NSD Service unregistered")
                _isAdvertising.value = false
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD Unregistration failed: $errorCode")
            }
        }

        try {
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting NSD registration", e)
        }
    }

    override fun stopAdvertising() {
        try {
            registrationListener?.let { nsdManager?.unregisterService(it) }
            registrationListener = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping NSD advertising", e)
        }
        _isAdvertising.value = false
        stopServer()
    }

    override fun startDiscovery() {
        if (_isDiscovering.value) return

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "NSD Service discovery started")
                _isDiscovering.value = true
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "NSD Service found: ${service.serviceName}")
                if (service.serviceType == SERVICE_TYPE || service.serviceType == "$SERVICE_TYPE.") {
                    resolveService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "NSD Service lost: ${service.serviceName}")
                val peerId = service.serviceName
                discoveredPeers.remove(peerId)
                listener?.onPeerLost(peerId)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "NSD Discovery stopped")
                _isDiscovering.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "NSD Start Discovery failed: $errorCode")
                _isDiscovering.value = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "NSD Stop Discovery failed: $errorCode")
            }
        }

        try {
            nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting NSD discovery", e)
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed: $errorCode")
            }

            override fun onServiceResolved(resolvedService: NsdServiceInfo) {
                val host = resolvedService.host?.hostAddress ?: return
                val port = resolvedService.port
                val peerId = "$host:$port"
                val name = resolvedService.serviceName.replace("_", " ")

                val peer = PeerDevice(
                    id = peerId,
                    name = name,
                    transportType = TransportType.HOTSPOT_WIFI,
                    connectionStatus = ConnectionStatus.DISCOVERING,
                    ipAddress = host,
                    port = port
                )
                discoveredPeers[peerId] = peer
                scope.launch(Dispatchers.Main) {
                    listener?.onPeerDiscovered(peer)
                }
            }
        })
    }

    override fun stopDiscovery() {
        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
            discoveryListener = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping discovery", e)
        }
        _isDiscovering.value = false
    }

    private fun startServer() {
        if (isServerRunning) return
        isServerRunning = true

        scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(DEFAULT_PORT).apply {
                    reuseAddress = true
                }
                Log.d(TAG, "Hotspot Server started on port $DEFAULT_PORT")

                while (isServerRunning && serverSocket?.isClosed == false) {
                    try {
                        val socket = serverSocket!!.accept()
                        val remoteIp = socket.inetAddress.hostAddress ?: "unknown"
                        val peerId = "$remoteIp:${socket.port}"
                        Log.d(TAG, "Accepted connection from $peerId")

                        val handler = SocketHandler(socket, peerId)
                        clientSockets[peerId] = handler
                        handler.start()
                    } catch (e: Exception) {
                        if (!isServerRunning) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server socket error", e)
            }
        }
    }

    private fun stopServer() {
        isServerRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
    }

    override fun connect(peer: PeerDevice) {
        val ip = peer.ipAddress ?: return
        val port = peer.port ?: DEFAULT_PORT
        val peerId = peer.id

        scope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Connecting to socket at $ip:$port")
                val socket = Socket(ip, port)
                val handler = SocketHandler(socket, peerId, peer.name)
                clientSockets[peerId] = handler
                handler.start()

                // Send initial handshake
                val handshake = ProtocolPacket(
                    type = PacketType.HANDSHAKE,
                    senderId = NetworkUtils.getLocalIpAddress() ?: "me",
                    senderName = NetworkUtils.getDeviceName(context)
                )
                handler.sendPacket(handshake)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to $ip:$port", e)
                withContext(Dispatchers.Main) {
                    listener?.onError(TransportType.HOTSPOT_WIFI, "Connection to $ip:$port failed")
                }
            }
        }
    }

    override fun acceptConnection(peerId: String) {
        // TCP socket automatically accepts
    }

    override fun rejectConnection(peerId: String) {
        disconnect(peerId)
    }

    override fun disconnect(peerId: String) {
        clientSockets[peerId]?.close()
        clientSockets.remove(peerId)
        updateConnectedPeers()
    }

    override fun disconnectAll() {
        clientSockets.values.forEach { it.close() }
        clientSockets.clear()
        updateConnectedPeers()
    }

    override fun sendPacket(peerId: String, packet: ProtocolPacket) {
        clientSockets[peerId]?.sendPacket(packet)
    }

    override fun broadcastPacket(packet: ProtocolPacket) {
        clientSockets.values.forEach { it.sendPacket(packet) }
    }

    override fun sendFile(
        peerId: String,
        fileUri: Uri,
        messageId: String,
        onProgress: (FileTransfer) -> Unit
    ) {
        val handler = clientSockets[peerId]
        if (handler == null) {
            listener?.onError(TransportType.HOTSPOT_WIFI, "Peer $peerId not connected")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val meta = FileManager.resolveFileMetaFromUri(context, fileUri) ?: return@launch
                val inputStream = context.contentResolver.openInputStream(fileUri) ?: return@launch

                // 1. Send FILE_OFFER packet
                val offer = ProtocolPacket(
                    type = PacketType.FILE_OFFER,
                    senderId = NetworkUtils.getLocalIpAddress() ?: "me",
                    senderName = NetworkUtils.getDeviceName(context),
                    messageId = messageId,
                    fileAttachment = meta
                )
                handler.sendPacket(offer)

                val transfer = FileTransfer(
                    id = UUID.randomUUID().toString(),
                    messageId = messageId,
                    peerId = peerId,
                    peerName = handler.peerName ?: "Wi-Fi Peer",
                    fileName = meta.fileName,
                    fileSize = meta.fileSize,
                    mimeType = meta.mimeType,
                    localUri = fileUri.toString(),
                    isIncoming = false,
                    status = TransferStatus.IN_PROGRESS,
                    transportType = TransportType.HOTSPOT_WIFI
                )
                withContext(Dispatchers.Main) { onProgress(transfer) }

                // 2. Stream file data frames
                handler.sendFileStream(transfer, inputStream) { updatedTransfer ->
                    scope.launch(Dispatchers.Main) {
                        onProgress(updatedTransfer)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending file over socket", e)
                withContext(Dispatchers.Main) {
                    listener?.onError(TransportType.HOTSPOT_WIFI, "File transfer error: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun updateConnectedPeers() {
        val list = clientSockets.values.map {
            PeerDevice(
                id = it.peerId,
                name = it.peerName ?: "Wi-Fi Peer",
                transportType = TransportType.HOTSPOT_WIFI,
                connectionStatus = ConnectionStatus.CONNECTED,
                ipAddress = it.peerId.substringBefore(":")
            )
        }
        _connectedPeers.value = list
    }

    override fun release() {
        stopAdvertising()
        stopDiscovery()
        disconnectAll()
    }

    // Inner Socket Connection Handler
    private inner class SocketHandler(
        private val socket: Socket,
        val peerId: String,
        var peerName: String? = null
    ) {
        private val dis = DataInputStream(BufferedInputStream(socket.getInputStream()))
        private val dos = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
        private var isRunning = true
        private var currentIncomingFile: File? = null
        private var currentIncomingMeta: FileAttachmentMeta? = null
        private var currentIncomingFos: FileOutputStream? = null
        private var currentBytesReceived = 0L

        fun start() {
            scope.launch(Dispatchers.IO) {
                try {
                    val peer = PeerDevice(
                        id = peerId,
                        name = peerName ?: "Wi-Fi Peer",
                        transportType = TransportType.HOTSPOT_WIFI,
                        connectionStatus = ConnectionStatus.CONNECTED,
                        ipAddress = socket.inetAddress.hostAddress
                    )
                    withContext(Dispatchers.Main) {
                        listener?.onConnected(peer)
                        updateConnectedPeers()
                    }

                    while (isRunning && !socket.isClosed) {
                        val frameType = dis.readInt() // 0 = Packet, 1 = File Chunk
                        val length = dis.readInt()

                        if (length < 0 || length > 100 * 1024 * 1024) {
                            Log.w(TAG, "Invalid frame length: $length")
                            break
                        }

                        val buffer = ByteArray(length)
                        dis.readFully(buffer)

                        if (frameType == TYPE_JSON_PACKET) {
                            val packet = ProtocolPacket.fromByteArray(buffer)
                            if (packet != null) {
                                if (packet.type == PacketType.HANDSHAKE) {
                                    peerName = packet.senderName
                                    withContext(Dispatchers.Main) { updateConnectedPeers() }
                                } else if (packet.type == PacketType.FILE_OFFER && packet.fileAttachment != null) {
                                    handleIncomingFileOffer(packet.fileAttachment)
                                }
                                withContext(Dispatchers.Main) {
                                    listener?.onPacketReceived(peerId, packet)
                                }
                            }
                        } else if (frameType == TYPE_FILE_DATA) {
                            handleIncomingFileChunk(buffer)
                        }
                    }
                } catch (e: Exception) {
                    if (isRunning) Log.d(TAG, "Socket handler closed: ${e.message}")
                } finally {
                    close()
                    withContext(Dispatchers.Main) {
                        listener?.onDisconnected(peerId)
                        updateConnectedPeers()
                    }
                }
            }
        }

        private fun handleIncomingFileOffer(meta: FileAttachmentMeta) {
            try {
                val bChatDir = FileManager.getBChatDownloadDir(context)
                var file = File(bChatDir, meta.fileName)
                var count = 1
                val base = meta.fileName.substringBeforeLast(".")
                val ext = meta.fileName.substringAfterLast(".", "")
                while (file.exists()) {
                    file = File(bChatDir, "${base}_$count.$ext")
                    count++
                }
                currentIncomingFile = file
                currentIncomingMeta = meta
                currentIncomingFos = FileOutputStream(file)
                currentBytesReceived = 0L

                val transfer = FileTransfer(
                    id = meta.fileId,
                    peerId = peerId,
                    peerName = peerName ?: "Wi-Fi Peer",
                    fileName = file.name,
                    fileSize = meta.fileSize,
                    mimeType = meta.mimeType,
                    localFilePath = file.absolutePath,
                    isIncoming = true,
                    status = TransferStatus.IN_PROGRESS,
                    transportType = TransportType.HOTSPOT_WIFI
                )
                scope.launch(Dispatchers.Main) {
                    listener?.onFileTransferProgress(transfer)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error preparing incoming file", e)
            }
        }

        private fun handleIncomingFileChunk(chunk: ByteArray) {
            try {
                currentIncomingFos?.write(chunk)
                currentBytesReceived += chunk.size
                val meta = currentIncomingMeta
                val file = currentIncomingFile

                if (meta != null && file != null) {
                    val isDone = currentBytesReceived >= meta.fileSize
                    val transfer = FileTransfer(
                        id = meta.fileId,
                        peerId = peerId,
                        peerName = peerName ?: "Wi-Fi Peer",
                        fileName = file.name,
                        fileSize = meta.fileSize,
                        bytesTransferred = currentBytesReceived,
                        mimeType = meta.mimeType,
                        localFilePath = file.absolutePath,
                        isIncoming = true,
                        status = if (isDone) TransferStatus.COMPLETED else TransferStatus.IN_PROGRESS,
                        transportType = TransportType.HOTSPOT_WIFI,
                        completedTime = if (isDone) System.currentTimeMillis() else null
                    )

                    scope.launch(Dispatchers.Main) {
                        listener?.onFileTransferProgress(transfer)
                        if (isDone) {
                            currentIncomingFos?.flush()
                            currentIncomingFos?.close()
                            currentIncomingFos = null
                            listener?.onFileReceived(transfer)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing file chunk", e)
            }
        }

        fun sendPacket(packet: ProtocolPacket) {
            scope.launch(Dispatchers.IO) {
                try {
                    val bytes = packet.toByteArray()
                    synchronized(dos) {
                        dos.writeInt(TYPE_JSON_PACKET)
                        dos.writeInt(bytes.size)
                        dos.write(bytes)
                        dos.flush()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending socket packet", e)
                }
            }
        }

        fun sendFileStream(
            transfer: FileTransfer,
            inputStream: InputStream,
            onProgress: (FileTransfer) -> Unit
        ) {
            try {
                val buffer = ByteArray(CHUNK_SIZE)
                var bytesRead: Int
                var totalSent = 0L
                var lastSpeedCalcTime = System.currentTimeMillis()
                var lastBytesSent = 0L

                inputStream.use { stream ->
                    while (stream.read(buffer).also { bytesRead = it } != -1) {
                        synchronized(dos) {
                            dos.writeInt(TYPE_FILE_DATA)
                            dos.writeInt(bytesRead)
                            dos.write(buffer, 0, bytesRead)
                            dos.flush()
                        }
                        totalSent += bytesRead

                        val now = System.currentTimeMillis()
                        val dt = now - lastSpeedCalcTime
                        val speed = if (dt >= 500) {
                            val sp = (totalSent - lastBytesSent) * 1000 / dt
                            lastSpeedCalcTime = now
                            lastBytesSent = totalSent
                            sp
                        } else transfer.transferSpeedBytesPerSec

                        onProgress(
                            transfer.copy(
                                bytesTransferred = totalSent,
                                transferSpeedBytesPerSec = speed,
                                status = if (totalSent >= transfer.fileSize) TransferStatus.COMPLETED else TransferStatus.IN_PROGRESS,
                                completedTime = if (totalSent >= transfer.fileSize) System.currentTimeMillis() else null
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during file streaming", e)
                onProgress(transfer.copy(status = TransferStatus.FAILED, errorMessage = e.localizedMessage))
            }
        }

        fun close() {
            isRunning = false
            try {
                currentIncomingFos?.close()
                dis.close()
                dos.close()
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
