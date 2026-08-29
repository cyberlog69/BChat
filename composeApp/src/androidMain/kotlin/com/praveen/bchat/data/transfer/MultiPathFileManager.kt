package com.praveen.bchat.data.transfer

import android.content.Context
import android.net.Uri
import android.util.Log
import com.praveen.bchat.data.transport.P2PTransport
import com.praveen.bchat.domain.model.*
import com.praveen.bchat.util.FileManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Android Multi-Path File Manager.
 * Orchestrates high-speed concurrent file transmission across multiple radios (Wi-Fi + Bluetooth).
 */
class MultiPathFileManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    companion object {
        private const val TAG = "MultiPathFileManager"
        const val CHUNK_SIZE = 128 * 1024 // 128 KB
    }

    private val engine = MultiPathTransferEngine()

    // Active incoming transfers: transferId -> IncomingTransferSession
    private val incomingSessions = ConcurrentHashMap<String, IncomingTransferSession>()

    // Active outgoing transfers: transferId -> OutgoingTransferSession
    private val outgoingSessions = ConcurrentHashMap<String, OutgoingTransferSession>()

    private class IncomingTransferSession(
        val transferId: String,
        val targetFile: File,
        val randomAccessFile: RandomAccessFile,
        val reassembler: MultiPathTransferEngine.ReceiverReassembler,
        val meta: FileAttachmentMeta,
        val peerId: String,
        val peerName: String,
        val onProgress: (FileTransfer) -> Unit,
        val onComplete: (FileTransfer) -> Unit
    ) {
        val mutex = Mutex()
        var startTime: Long = System.currentTimeMillis()
        var lastReportTime: Long = System.currentTimeMillis()
        var bytesTransferredSinceLastReport: Long = 0L
    }

    private class OutgoingTransferSession(
        val transferId: String,
        val scheduler: MultiPathTransferEngine.SenderScheduler,
        val fileUri: Uri,
        val peerId: String,
        val meta: FileAttachmentMeta,
        val onProgress: (FileTransfer) -> Unit
    )

    /**
     * Prepares an incoming multi-path transfer session.
     */
    fun startIncomingSession(
        handshake: MultiPathHandshake,
        peerId: String,
        peerName: String,
        onProgress: (FileTransfer) -> Unit,
        onComplete: (FileTransfer) -> Unit
    ) {
        try {
            val downloadDir = FileManager.getBChatDownloadDir(context)
            var file = File(downloadDir, handshake.fileName)
            var count = 1
            val base = handshake.fileName.substringBeforeLast(".")
            val ext = handshake.fileName.substringAfterLast(".", "")
            while (file.exists()) {
                file = File(downloadDir, if (ext.isNotEmpty()) "${base}_$count.$ext" else "${base}_$count")
                count++
            }

            val raf = RandomAccessFile(file, "rw")
            raf.setLength(handshake.fileSize) // Pre-allocate file size on disk for zero fragmentation

            val reassembler = MultiPathTransferEngine.ReceiverReassembler(
                transferId = handshake.transferId,
                totalChunks = handshake.totalChunks,
                fileSize = handshake.fileSize
            )

            val meta = FileAttachmentMeta(
                fileId = handshake.transferId,
                fileName = file.name,
                fileSize = handshake.fileSize,
                mimeType = handshake.mimeType,
                localFilePath = file.absolutePath
            )

            val session = IncomingTransferSession(
                transferId = handshake.transferId,
                targetFile = file,
                randomAccessFile = raf,
                reassembler = reassembler,
                meta = meta,
                peerId = peerId,
                peerName = peerName,
                onProgress = onProgress,
                onComplete = onComplete
            )

            incomingSessions[handshake.transferId] = session
            Log.d(TAG, "Started incoming multi-path transfer for ${file.name} (${handshake.fileSize} bytes, ${handshake.totalChunks} chunks)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start incoming session", e)
        }
    }

    /**
     * Handles an incoming FileChunkPacket from any transport channel.
     */
    fun handleIncomingChunk(chunk: FileChunkPacket, channelType: TransportType = TransportType.NEARBY_SHARE) {
        val session = incomingSessions[chunk.transferId] ?: return

        scope.launch(Dispatchers.IO) {
            session.mutex.withLock {
                val isValid = session.reassembler.validateAndRecordChunk(chunk)
                if (!isValid) {
                    Log.w(TAG, "Corrupt chunk ${chunk.chunkIndex} for transfer ${chunk.transferId}")
                    return@withLock
                }

                // Write chunk to target file at computed offset
                try {
                    val offset = chunk.chunkIndex.toLong() * CHUNK_SIZE.toLong()
                    session.randomAccessFile.seek(offset)
                    session.randomAccessFile.write(chunk.data)

                    val now = System.currentTimeMillis()
                    val receivedBytes = session.reassembler.getReceivedBytes()

                    // Calculate speed
                    val elapsedTotal = (now - session.startTime).coerceAtLeast(1L)
                    val speed = (receivedBytes * 1000L) / elapsedTotal

                    val transfer = FileTransfer(
                        id = session.transferId,
                        peerId = session.peerId,
                        peerName = session.peerName,
                        fileName = session.meta.fileName,
                        fileSize = session.meta.fileSize,
                        bytesTransferred = receivedBytes,
                        mimeType = session.meta.mimeType,
                        localFilePath = session.targetFile.absolutePath,
                        isIncoming = true,
                        status = if (session.reassembler.isComplete()) TransferStatus.COMPLETED else TransferStatus.IN_PROGRESS,
                        transportType = channelType,
                        transferSpeedBytesPerSec = speed,
                        startTime = session.startTime,
                        completedTime = if (session.reassembler.isComplete()) now else null
                    )

                    withContext(Dispatchers.Main) {
                        session.onProgress(transfer)
                    }

                    if (session.reassembler.isComplete()) {
                        session.randomAccessFile.close()
                        incomingSessions.remove(session.transferId)
                        Log.d(TAG, "Multi-path transfer completed: ${session.targetFile.name} (${receivedBytes} bytes)")
                        withContext(Dispatchers.Main) {
                            session.onComplete(transfer)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing chunk ${chunk.chunkIndex}", e)
                }
            }
        }
    }

    /**
     * Sends a file across multiple active transports concurrently.
     */
    fun sendFileMultiPath(
        transports: List<P2PTransport>,
        peerId: String,
        peerName: String,
        fileUri: Uri,
        messageId: String,
        onProgress: (FileTransfer) -> Unit
    ) {
        if (transports.isEmpty()) {
            Log.e(TAG, "No transports available for multi-path send")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val meta = FileManager.resolveFileMetaFromUri(context, fileUri) ?: return@launch
                val transferId = UUID.randomUUID().toString()
                val totalChunks = engine.calculateChunkCount(meta.fileSize, CHUNK_SIZE)

                val scheduler = MultiPathTransferEngine.SenderScheduler(
                    transferId = transferId,
                    totalChunks = totalChunks,
                    fileSize = meta.fileSize,
                    chunkSize = CHUNK_SIZE
                )

                // 1. Send MultiPathHandshake across all active transports
                val handshake = MultiPathHandshake(
                    transferId = transferId,
                    fileName = meta.fileName,
                    fileSize = meta.fileSize,
                    chunkSize = CHUNK_SIZE,
                    totalChunks = totalChunks,
                    mimeType = meta.mimeType,
                    supportedTransports = transports.map { it.transportType }
                )

                val handshakePacket = ProtocolPacket(
                    type = PacketType.MULTIPATH_HANDSHAKE,
                    senderId = "me",
                    senderName = "BChat",
                    messageId = messageId,
                    multipathHandshake = handshake
                )

                transports.forEach { transport ->
                    try {
                        transport.sendPacket(peerId, handshakePacket)
                    } catch (e: Exception) {
                        Log.w(TAG, "Handshake failed on ${transport.transportType}: ${e.message}")
                    }
                }

                val startTime = System.currentTimeMillis()
                var totalBytesSent = 0L

                // 2. Launch concurrent workers for each transport channel
                val workerJobs = transports.map { transport ->
                    launch(Dispatchers.IO) {
                        val channelTag = transport.transportType.name
                        var input: InputStream? = null
                        try {
                            input = context.contentResolver.openInputStream(fileUri)
                            if (input == null) return@launch

                            var currentStreamPos = 0L

                            while (isActive) {
                                val chunkIndex = scheduler.getNextChunkToSend() ?: break

                                val targetOffset = chunkIndex.toLong() * CHUNK_SIZE.toLong()
                                val expectedChunkLen = if (chunkIndex == totalChunks - 1) {
                                    (meta.fileSize - targetOffset).toInt()
                                } else {
                                    CHUNK_SIZE
                                }

                                // Reposition stream if out of order
                                if (currentStreamPos != targetOffset) {
                                    input.close()
                                    input = context.contentResolver.openInputStream(fileUri) ?: break
                                    input.skip(targetOffset)
                                    currentStreamPos = targetOffset
                                }

                                val buffer = ByteArray(expectedChunkLen)
                                var bytesRead = 0
                                while (bytesRead < expectedChunkLen) {
                                    val r = input.read(buffer, bytesRead, expectedChunkLen - bytesRead)
                                    if (r == -1) break
                                    bytesRead += r
                                }
                                currentStreamPos += bytesRead

                                val checksum = Crc32.calculate(buffer, 0, bytesRead)
                                val chunkPacket = FileChunkPacket(
                                    transferId = transferId,
                                    chunkIndex = chunkIndex,
                                    totalChunks = totalChunks,
                                    data = if (bytesRead == buffer.size) buffer else buffer.copyOf(bytesRead),
                                    checksum = checksum,
                                    channelTag = channelTag,
                                    isLastChunk = (chunkIndex == totalChunks - 1)
                                )

                                val packet = ProtocolPacket(
                                    type = PacketType.FILE_CHUNK,
                                    senderId = "me",
                                    senderName = "BChat",
                                    messageId = messageId,
                                    chunkPacket = chunkPacket
                                )

                                transport.sendPacket(peerId, packet)
                                val now = System.currentTimeMillis()
                                scheduler.onChunkAck(chunkIndex, channelTag, bytesRead.toLong(), now)

                                synchronized(this@MultiPathFileManager) {
                                    totalBytesSent += bytesRead
                                }

                                val elapsedTotal = (now - startTime).coerceAtLeast(1L)
                                val speed = (totalBytesSent * 1000L) / elapsedTotal

                                val transfer = FileTransfer(
                                    id = transferId,
                                    messageId = messageId,
                                    peerId = peerId,
                                    peerName = peerName,
                                    fileName = meta.fileName,
                                    fileSize = meta.fileSize,
                                    bytesTransferred = totalBytesSent.coerceAtMost(meta.fileSize),
                                    mimeType = meta.mimeType,
                                    localUri = fileUri.toString(),
                                    isIncoming = false,
                                    status = if (totalBytesSent >= meta.fileSize) TransferStatus.COMPLETED else TransferStatus.IN_PROGRESS,
                                    transportType = transport.transportType,
                                    transferSpeedBytesPerSec = speed,
                                    startTime = startTime,
                                    completedTime = if (totalBytesSent >= meta.fileSize) now else null
                                )

                                withContext(Dispatchers.Main) {
                                    onProgress(transfer)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Worker failed on channel $channelTag", e)
                        } finally {
                            input?.close()
                        }
                    }
                }

                workerJobs.joinAll()
                Log.d(TAG, "Multi-path file send completed for ${meta.fileName}")
            } catch (e: Exception) {
                Log.e(TAG, "Error during multi-path send", e)
            }
        }
    }
}
