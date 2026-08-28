package com.praveen.bchat.data.transport.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BluetoothClassicTransport(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : P2PTransport {

    companion object {
        private const val TAG = "BluetoothTransport"
        private const val SERVICE_NAME = "BChat_BT"
        private val BCHAT_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
        private const val TYPE_JSON_PACKET = 0
        private const val TYPE_FILE_DATA = 1
        private const val CHUNK_SIZE = 16 * 1024 // 16 KB for Bluetooth RFCOMM buffer
    }

    override val transportType: TransportType = TransportType.BLUETOOTH_CLASSIC

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var listener: P2PTransportListener? = null

    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    override val connectedPeers: StateFlow<List<PeerDevice>> = _connectedPeers.asStateFlow()

    private var serverSocket: BluetoothServerSocket? = null
    private var isServerRunning = false

    private val activeConnections = ConcurrentHashMap<String, BtConnection>()
    private val discoveredDevices = ConcurrentHashMap<String, PeerDevice>()

    override fun setListener(listener: P2PTransportListener) {
        this.listener = listener
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) {
                        val name = device.name ?: "Bluetooth Device"
                        val address = device.address
                        val peer = PeerDevice(
                            id = address,
                            name = name,
                            transportType = TransportType.BLUETOOTH_CLASSIC,
                            connectionStatus = ConnectionStatus.DISCOVERING,
                            bluetoothAddress = address
                        )
                        discoveredDevices[address] = peer
                        listener?.onPeerDiscovered(peer)
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isDiscovering.value = false
                    Log.d(TAG, "Bluetooth Discovery finished")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun startAdvertising(localDeviceName: String) {
        if (_isAdvertising.value) return
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            listener?.onError(TransportType.BLUETOOTH_CLASSIC, "Bluetooth is not enabled")
            return
        }

        isServerRunning = true
        scope.launch(Dispatchers.IO) {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, BCHAT_UUID)
                _isAdvertising.value = true
                Log.d(TAG, "Bluetooth Server listening on UUID: $BCHAT_UUID")

                while (isServerRunning && serverSocket != null) {
                    try {
                        val socket = serverSocket!!.accept()
                        val remoteDevice = socket.remoteDevice
                        val peerId = remoteDevice.address
                        val peerName = remoteDevice.name ?: "BT Peer"
                        Log.d(TAG, "Accepted BT connection from $peerId ($peerName)")

                        val conn = BtConnection(socket, peerId, peerName)
                        activeConnections[peerId] = conn
                        conn.start()
                    } catch (e: Exception) {
                        if (!isServerRunning) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "BT Server error", e)
                _isAdvertising.value = false
            }
        }
    }

    override fun stopAdvertising() {
        isServerRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
        _isAdvertising.value = false
    }

    @SuppressLint("MissingPermission")
    override fun startDiscovery() {
        if (_isDiscovering.value) return
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            listener?.onError(TransportType.BLUETOOTH_CLASSIC, "Bluetooth is not enabled")
            return
        }

        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            context.registerReceiver(discoveryReceiver, filter)

            // Also check already bonded/paired devices
            bluetoothAdapter.bondedDevices?.forEach { device ->
                val peer = PeerDevice(
                    id = device.address,
                    name = device.name ?: "Paired Device",
                    transportType = TransportType.BLUETOOTH_CLASSIC,
                    connectionStatus = ConnectionStatus.DISCOVERING,
                    bluetoothAddress = device.address
                )
                discoveredDevices[device.address] = peer
                listener?.onPeerDiscovered(peer)
            }

            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
            bluetoothAdapter.startDiscovery()
            _isDiscovering.value = true
            Log.d(TAG, "Bluetooth discovery started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BT discovery", e)
            _isDiscovering.value = false
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopDiscovery() {
        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
            context.unregisterReceiver(discoveryReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
        _isDiscovering.value = false
    }

    @SuppressLint("MissingPermission")
    override fun connect(peer: PeerDevice) {
        val address = peer.bluetoothAddress ?: peer.id
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return

        scope.launch(Dispatchers.IO) {
            try {
                if (bluetoothAdapter.isDiscovering) {
                    bluetoothAdapter.cancelDiscovery()
                }
                Log.d(TAG, "Connecting to BT device: $address")
                val socket = device.createRfcommSocketToServiceRecord(BCHAT_UUID)
                socket.connect()

                val conn = BtConnection(socket, address, peer.name)
                activeConnections[address] = conn
                conn.start()

                // Send handshake
                val handshake = ProtocolPacket(
                    type = PacketType.HANDSHAKE,
                    senderId = bluetoothAdapter.address ?: "me",
                    senderName = NetworkUtils.getDeviceName(context)
                )
                conn.sendPacket(handshake)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to BT device $address", e)
                withContext(Dispatchers.Main) {
                    listener?.onError(TransportType.BLUETOOTH_CLASSIC, "Failed to connect to ${peer.name}")
                }
            }
        }
    }

    override fun acceptConnection(peerId: String) {}

    override fun rejectConnection(peerId: String) {
        disconnect(peerId)
    }

    override fun disconnect(peerId: String) {
        activeConnections[peerId]?.close()
        activeConnections.remove(peerId)
        updateConnectedPeers()
    }

    override fun disconnectAll() {
        activeConnections.values.forEach { it.close() }
        activeConnections.clear()
        updateConnectedPeers()
    }

    override fun sendPacket(peerId: String, packet: ProtocolPacket) {
        activeConnections[peerId]?.sendPacket(packet)
    }

    override fun broadcastPacket(packet: ProtocolPacket) {
        activeConnections.values.forEach { it.sendPacket(packet) }
    }

    override fun sendFile(
        peerId: String,
        fileUri: Uri,
        messageId: String,
        onProgress: (FileTransfer) -> Unit
    ) {
        val conn = activeConnections[peerId]
        if (conn == null) {
            listener?.onError(TransportType.BLUETOOTH_CLASSIC, "Peer $peerId not connected")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val meta = FileManager.resolveFileMetaFromUri(context, fileUri) ?: return@launch
                val inputStream = context.contentResolver.openInputStream(fileUri) ?: return@launch

                // 1. Send FILE_OFFER packet
                val offer = ProtocolPacket(
                    type = PacketType.FILE_OFFER,
                    senderId = "me",
                    senderName = NetworkUtils.getDeviceName(context),
                    messageId = messageId,
                    fileAttachment = meta
                )
                conn.sendPacket(offer)

                val transfer = FileTransfer(
                    id = UUID.randomUUID().toString(),
                    messageId = messageId,
                    peerId = peerId,
                    peerName = conn.peerName ?: "BT Peer",
                    fileName = meta.fileName,
                    fileSize = meta.fileSize,
                    mimeType = meta.mimeType,
                    localUri = fileUri.toString(),
                    isIncoming = false,
                    status = TransferStatus.IN_PROGRESS,
                    transportType = TransportType.BLUETOOTH_CLASSIC
                )
                withContext(Dispatchers.Main) { onProgress(transfer) }

                // 2. Stream chunked file data
                conn.sendFileStream(transfer, inputStream) { updated ->
                    scope.launch(Dispatchers.Main) { onProgress(updated) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending file over BT", e)
                withContext(Dispatchers.Main) {
                    listener?.onError(TransportType.BLUETOOTH_CLASSIC, "Bluetooth file transfer error: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun updateConnectedPeers() {
        val list = activeConnections.values.map {
            PeerDevice(
                id = it.peerId,
                name = it.peerName ?: "BT Peer",
                transportType = TransportType.BLUETOOTH_CLASSIC,
                connectionStatus = ConnectionStatus.CONNECTED,
                bluetoothAddress = it.peerId
            )
        }
        _connectedPeers.value = list
    }

    override fun release() {
        stopAdvertising()
        stopDiscovery()
        disconnectAll()
    }

    // Inner Bluetooth Connection Handler
    private inner class BtConnection(
        private val socket: BluetoothSocket,
        val peerId: String,
        var peerName: String? = null
    ) {
        private val dis = DataInputStream(BufferedInputStream(socket.inputStream))
        private val dos = DataOutputStream(BufferedOutputStream(socket.outputStream))
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
                        name = peerName ?: "BT Peer",
                        transportType = TransportType.BLUETOOTH_CLASSIC,
                        connectionStatus = ConnectionStatus.CONNECTED,
                        bluetoothAddress = peerId
                    )
                    withContext(Dispatchers.Main) {
                        listener?.onConnected(peer)
                        updateConnectedPeers()
                    }

                    while (isRunning && socket.isConnected) {
                        val frameType = dis.readInt()
                        val length = dis.readInt()

                        if (length < 0 || length > 100 * 1024 * 1024) break

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
                    if (isRunning) Log.d(TAG, "BT connection closed: ${e.message}")
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
                    peerName = peerName ?: "BT Peer",
                    fileName = file.name,
                    fileSize = meta.fileSize,
                    mimeType = meta.mimeType,
                    localFilePath = file.absolutePath,
                    isIncoming = true,
                    status = TransferStatus.IN_PROGRESS,
                    transportType = TransportType.BLUETOOTH_CLASSIC
                )
                scope.launch(Dispatchers.Main) { listener?.onFileTransferProgress(transfer) }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling BT file offer", e)
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
                        peerName = peerName ?: "BT Peer",
                        fileName = file.name,
                        fileSize = meta.fileSize,
                        bytesTransferred = currentBytesReceived,
                        mimeType = meta.mimeType,
                        localFilePath = file.absolutePath,
                        isIncoming = true,
                        status = if (isDone) TransferStatus.COMPLETED else TransferStatus.IN_PROGRESS,
                        transportType = TransportType.BLUETOOTH_CLASSIC,
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
                Log.e(TAG, "Error writing BT file chunk", e)
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
                    Log.e(TAG, "Error sending BT packet", e)
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
                Log.e(TAG, "Error during BT streaming", e)
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
