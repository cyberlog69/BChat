package com.praveen.bchat.data.transport.nearby

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.collection.SimpleArrayMap
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.praveen.bchat.data.transport.P2PTransport
import com.praveen.bchat.data.transport.P2PTransportListener
import com.praveen.bchat.domain.model.*
import com.praveen.bchat.util.FileManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

class NearbyConnectionsTransport(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : P2PTransport {

    companion object {
        private const val TAG = "NearbyTransport"
        private const val SERVICE_ID = "com.praveen.bchat.NEARBY"
        private val STRATEGY = Strategy.P2P_CLUSTER
    }

    override val transportType: TransportType = TransportType.NEARBY_SHARE

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private var listener: P2PTransportListener? = null

    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    override val connectedPeers: StateFlow<List<PeerDevice>> = _connectedPeers.asStateFlow()

    // Map: endpointId -> PeerDevice
    private val peerMap = mutableMapOf<String, PeerDevice>()

    // Map: payloadId -> FileTransfer in progress
    private val activeTransfers = mutableMapOf<Long, FileTransfer>()

    // Map: payloadId -> Payload (for incoming files)
    private val incomingPayloads = SimpleArrayMap<Long, Payload>()
    // Map: payloadId -> FileAttachmentMeta (metadata received right before payload)
    private val pendingIncomingMeta = SimpleArrayMap<Long, Pair<String, FileAttachmentMeta>>()
    private val pendingNextFileMeta = mutableMapOf<String, FileAttachmentMeta>()

    override fun setListener(listener: P2PTransportListener) {
        this.listener = listener
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint found: $endpointId (${info.endpointName})")
            val peer = PeerDevice(
                id = endpointId,
                name = info.endpointName,
                transportType = TransportType.NEARBY_SHARE,
                connectionStatus = ConnectionStatus.DISCOVERING,
                nearbyEndpointId = endpointId
            )
            peerMap[endpointId] = peer
            listener?.onPeerDiscovered(peer)
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
            peerMap.remove(endpointId)
            listener?.onPeerLost(endpointId)
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with: $endpointId (${connectionInfo.endpointName})")
            val peer = PeerDevice(
                id = endpointId,
                name = connectionInfo.endpointName,
                transportType = TransportType.NEARBY_SHARE,
                connectionStatus = ConnectionStatus.CONNECTING,
                nearbyEndpointId = endpointId
            )
            peerMap[endpointId] = peer
            listener?.onConnectionInitiated(peer, connectionInfo.authenticationDigits)
            // Auto accept or trigger UI callback
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Connected to: $endpointId")
                    val existing = peerMap[endpointId]
                    val updatedPeer = (existing ?: PeerDevice(
                        id = endpointId,
                        name = "Nearby Peer",
                        transportType = TransportType.NEARBY_SHARE,
                        nearbyEndpointId = endpointId
                    )).copy(connectionStatus = ConnectionStatus.CONNECTED)

                    peerMap[endpointId] = updatedPeer
                    updateConnectedPeersList()
                    listener?.onConnected(updatedPeer)
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.d(TAG, "Connection rejected by: $endpointId")
                    peerMap.remove(endpointId)
                    updateConnectedPeersList()
                    listener?.onDisconnected(endpointId)
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    Log.e(TAG, "Connection error with: $endpointId")
                    peerMap.remove(endpointId)
                    updateConnectedPeersList()
                    listener?.onError(TransportType.NEARBY_SHARE, "Connection error with $endpointId")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from: $endpointId")
            peerMap.remove(endpointId)
            updateConnectedPeersList()
            listener?.onDisconnected(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes() ?: return
                    val packet = ProtocolPacket.fromByteArray(bytes) ?: return
                    Log.d(TAG, "Received packet type: ${packet.type} from $endpointId")

                    if (packet.type == PacketType.FILE_OFFER && packet.fileAttachment != null) {
                        // Store pending metadata for next incoming payload from this endpoint
                        pendingNextFileMeta[endpointId] = packet.fileAttachment
                    }

                    listener?.onPacketReceived(endpointId, packet)
                }
                Payload.Type.FILE -> {
                    Log.d(TAG, "Receiving FILE payload id: ${payload.id} from $endpointId")
                    incomingPayloads.put(payload.id, payload)
                    val meta = pendingNextFileMeta.remove(endpointId)
                    if (meta != null) {
                        pendingIncomingMeta.put(payload.id, Pair(endpointId, meta))
                        val transfer = FileTransfer(
                            id = payload.id.toString(),
                            messageId = null,
                            peerId = endpointId,
                            peerName = peerMap[endpointId]?.name ?: "Nearby Peer",
                            fileName = meta.fileName,
                            fileSize = meta.fileSize,
                            mimeType = meta.mimeType,
                            isIncoming = true,
                            status = TransferStatus.IN_PROGRESS,
                            transportType = TransportType.NEARBY_SHARE
                        )
                        activeTransfers[payload.id] = transfer
                        listener?.onFileTransferProgress(transfer)
                    }
                }
                Payload.Type.STREAM -> {
                    Log.d(TAG, "Stream payload received: ${payload.id}")
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val payloadId = update.payloadId
            val existingTransfer = activeTransfers[payloadId]

            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    if (existingTransfer != null && update.totalBytes > 0) {
                        val prevBytes = existingTransfer.bytesTransferred
                        val currentBytes = update.bytesTransferred
                        val delta = currentBytes - prevBytes
                        val speed = delta * 2 // Approx calculation
                        val updated = existingTransfer.copy(
                            bytesTransferred = currentBytes,
                            transferSpeedBytesPerSec = speed,
                            status = TransferStatus.IN_PROGRESS
                        )
                        activeTransfers[payloadId] = updated
                        listener?.onFileTransferProgress(updated)
                    }
                }
                PayloadTransferUpdate.Status.SUCCESS -> {
                    Log.d(TAG, "Payload transfer SUCCESS: $payloadId")
                    val payload = incomingPayloads.remove(payloadId)
                    val metaPair = pendingIncomingMeta.remove(payloadId)

                    if (payload != null && payload.type == Payload.Type.FILE && metaPair != null) {
                        scope.launch(Dispatchers.IO) {
                            processReceivedFile(payload, metaPair.first, metaPair.second)
                        }
                    } else if (existingTransfer != null) {
                        val completed = existingTransfer.copy(
                            bytesTransferred = existingTransfer.fileSize,
                            status = TransferStatus.COMPLETED,
                            completedTime = System.currentTimeMillis()
                        )
                        activeTransfers.remove(payloadId)
                        listener?.onFileTransferProgress(completed)
                    }
                }
                PayloadTransferUpdate.Status.FAILURE,
                PayloadTransferUpdate.Status.CANCELED -> {
                    Log.e(TAG, "Payload transfer failed/canceled: $payloadId (status: ${update.status})")
                    if (existingTransfer != null) {
                        val failed = existingTransfer.copy(
                            status = if (update.status == PayloadTransferUpdate.Status.CANCELED)
                                TransferStatus.CANCELLED else TransferStatus.FAILED
                        )
                        activeTransfers.remove(payloadId)
                        listener?.onFileTransferProgress(failed)
                    }
                    incomingPayloads.remove(payloadId)
                    pendingIncomingMeta.remove(payloadId)
                }
            }
        }
    }

    private suspend fun processReceivedFile(
        payload: Payload,
        endpointId: String,
        meta: FileAttachmentMeta
    ) = withContext(Dispatchers.IO) {
        try {
            val payloadFile = payload.asFile()?.asJavaFile()
            val bChatDir = FileManager.getBChatDownloadDir(context)
            var targetFile = File(bChatDir, meta.fileName)

            // Handle filename duplicate
            var counter = 1
            val baseName = meta.fileName.substringBeforeLast(".")
            val ext = meta.fileName.substringAfterLast(".", "")
            while (targetFile.exists()) {
                val newName = if (ext.isNotEmpty()) "${baseName}_$counter.$ext" else "${baseName}_$counter"
                targetFile = File(bChatDir, newName)
                counter++
            }

            if (payloadFile != null && payloadFile.exists()) {
                payloadFile.copyTo(targetFile, overwrite = true)
                payloadFile.delete()
            } else {
                val pfd = payload.asFile()?.asParcelFileDescriptor()
                if (pfd != null) {
                    FileInputStream(pfd.fileDescriptor).use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            val completedTransfer = FileTransfer(
                id = payload.id.toString(),
                messageId = null,
                peerId = endpointId,
                peerName = peerMap[endpointId]?.name ?: "Nearby Peer",
                fileName = targetFile.name,
                fileSize = targetFile.length(),
                bytesTransferred = targetFile.length(),
                mimeType = meta.mimeType,
                localFilePath = targetFile.absolutePath,
                isIncoming = true,
                status = TransferStatus.COMPLETED,
                transportType = TransportType.NEARBY_SHARE,
                completedTime = System.currentTimeMillis()
            )
            activeTransfers.remove(payload.id)
            withContext(Dispatchers.Main) {
                listener?.onFileReceived(completedTransfer)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving received file", e)
        }
    }

    private fun updateConnectedPeersList() {
        _connectedPeers.value = peerMap.values.filter { it.isConnected }
    }

    override fun startAdvertising(localDeviceName: String) {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(
            localDeviceName,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Advertising started as $localDeviceName")
            _isAdvertising.value = true
        }.addOnFailureListener { e ->
            Log.e(TAG, "Advertising failed", e)
            _isAdvertising.value = false
            listener?.onError(TransportType.NEARBY_SHARE, "Failed to start advertising: ${e.localizedMessage}")
        }
    }

    override fun stopAdvertising() {
        connectionsClient.stopAdvertising()
        _isAdvertising.value = false
        Log.d(TAG, "Advertising stopped")
    }

    override fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Discovery started")
            _isDiscovering.value = true
        }.addOnFailureListener { e ->
            Log.e(TAG, "Discovery failed", e)
            _isDiscovering.value = false
            listener?.onError(TransportType.NEARBY_SHARE, "Failed to start discovery: ${e.localizedMessage}")
        }
    }

    override fun stopDiscovery() {
        connectionsClient.stopDiscovery()
        _isDiscovering.value = false
        Log.d(TAG, "Discovery stopped")
    }

    override fun connect(peer: PeerDevice) {
        val endpointId = peer.nearbyEndpointId ?: peer.id
        val localName = "BChat User"
        connectionsClient.requestConnection(localName, endpointId, connectionLifecycleCallback)
            .addOnSuccessListener {
                Log.d(TAG, "Connection requested to $endpointId")
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to request connection to $endpointId", e)
                listener?.onError(TransportType.NEARBY_SHARE, "Connection request failed: ${e.localizedMessage}")
            }
    }

    override fun acceptConnection(peerId: String) {
        connectionsClient.acceptConnection(peerId, payloadCallback)
    }

    override fun rejectConnection(peerId: String) {
        connectionsClient.rejectConnection(peerId)
    }

    override fun disconnect(peerId: String) {
        connectionsClient.disconnectFromEndpoint(peerId)
        peerMap.remove(peerId)
        updateConnectedPeersList()
    }

    override fun disconnectAll() {
        connectionsClient.stopAllEndpoints()
        peerMap.clear()
        updateConnectedPeersList()
    }

    override fun sendPacket(peerId: String, packet: ProtocolPacket) {
        val payload = Payload.fromBytes(packet.toByteArray())
        connectionsClient.sendPayload(peerId, payload)
    }

    override fun broadcastPacket(packet: ProtocolPacket) {
        val endpoints = _connectedPeers.value.map { it.nearbyEndpointId ?: it.id }
        if (endpoints.isNotEmpty()) {
            val payload = Payload.fromBytes(packet.toByteArray())
            connectionsClient.sendPayload(endpoints, payload)
        }
    }

    override fun sendFile(
        peerId: String,
        fileUri: Uri,
        messageId: String,
        onProgress: (FileTransfer) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val meta = FileManager.resolveFileMetaFromUri(context, fileUri) ?: return@launch
                val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(fileUri, "r")
                    ?: return@launch

                // 1. Send FILE_OFFER packet first so recipient knows fileName, size, mimeType
                val offerPacket = ProtocolPacket(
                    type = PacketType.FILE_OFFER,
                    senderId = "me",
                    senderName = "Me",
                    messageId = messageId,
                    fileAttachment = meta
                )
                sendPacket(peerId, offerPacket)

                // Small delay to ensure packet arrives before stream/file
                delay(100)

                // 2. Send File Payload
                val filePayload = Payload.fromFile(pfd)
                val transfer = FileTransfer(
                    id = filePayload.id.toString(),
                    messageId = messageId,
                    peerId = peerId,
                    peerName = peerMap[peerId]?.name ?: "Nearby Peer",
                    fileName = meta.fileName,
                    fileSize = meta.fileSize,
                    mimeType = meta.mimeType,
                    localUri = fileUri.toString(),
                    isIncoming = false,
                    status = TransferStatus.IN_PROGRESS,
                    transportType = TransportType.NEARBY_SHARE
                )
                activeTransfers[filePayload.id] = transfer
                onProgress(transfer)

                connectionsClient.sendPayload(peerId, filePayload)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending file via Nearby", e)
                withContext(Dispatchers.Main) {
                    listener?.onError(TransportType.NEARBY_SHARE, "Failed to send file: ${e.localizedMessage}")
                }
            }
        }
    }

    override fun release() {
        stopAdvertising()
        stopDiscovery()
        disconnectAll()
    }
}
