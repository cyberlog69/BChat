package com.praveen.bchat.data.transfer

import com.praveen.bchat.data.transport.IosMultipeerTransport
import com.praveen.bchat.domain.model.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.*
import platform.posix.memcpy

/**
 * iOS Multi-Path File Manager using Apple Native Foundation file handles.
 */
@OptIn(ExperimentalForeignApi::class)
class IosMultiPathFileManager(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    private val engine = MultiPathTransferEngine()
    private val incomingSessions = mutableMapOf<String, IosIncomingSession>()
    private val sessionMutex = Mutex()

    private class IosIncomingSession(
        val transferId: String,
        val filePath: String,
        val fileHandle: NSFileHandle,
        val reassembler: MultiPathTransferEngine.ReceiverReassembler,
        val fileName: String,
        val fileSize: Long,
        val mimeType: String,
        val peerId: String,
        val peerName: String,
        val onProgress: (FileTransfer) -> Unit,
        val onComplete: (FileTransfer) -> Unit
    ) {
        val mutex = Mutex()
    }

    suspend fun startIncomingSession(
        handshake: MultiPathHandshake,
        peerId: String,
        peerName: String,
        onProgress: (FileTransfer) -> Unit,
        onComplete: (FileTransfer) -> Unit
    ) = sessionMutex.withLock {
        val tempDir = NSTemporaryDirectory()
        val filePath = "$tempDir/${handshake.fileName}"

        val fileManager = NSFileManager.defaultManager
        fileManager.createFileAtPath(filePath, contents = null, attributes = null)

        val fileHandle = NSFileHandle.fileHandleForWritingAtPath(filePath) ?: return@withLock
        fileHandle.truncateFileAtOffset(handshake.fileSize.toULong())

        val reassembler = MultiPathTransferEngine.ReceiverReassembler(
            transferId = handshake.transferId,
            totalChunks = handshake.totalChunks,
            fileSize = handshake.fileSize
        )

        val session = IosIncomingSession(
            transferId = handshake.transferId,
            filePath = filePath,
            fileHandle = fileHandle,
            reassembler = reassembler,
            fileName = handshake.fileName,
            fileSize = handshake.fileSize,
            mimeType = handshake.mimeType,
            peerId = peerId,
            peerName = peerName,
            onProgress = onProgress,
            onComplete = onComplete
        )

        incomingSessions[handshake.transferId] = session
    }

    suspend fun handleIncomingChunk(chunk: FileChunkPacket) {
        val session = sessionMutex.withLock { incomingSessions[chunk.transferId] } ?: return

        session.mutex.withLock {
            val isValid = session.reassembler.validateAndRecordChunk(chunk)
            if (!isValid) return@withLock

            val offset = chunk.chunkIndex.toULong() * MultiPathTransferEngine.DEFAULT_CHUNK_SIZE.toULong()
            session.fileHandle.seekToFileOffset(offset)

            val data = chunk.data.toNSData()
            session.fileHandle.writeData(data)

            val receivedBytes = session.reassembler.getReceivedBytes()
            val transfer = FileTransfer(
                id = session.transferId,
                peerId = session.peerId,
                peerName = session.peerName,
                fileName = session.fileName,
                fileSize = session.fileSize,
                bytesTransferred = receivedBytes,
                mimeType = session.mimeType,
                localFilePath = session.filePath,
                isIncoming = true,
                status = if (session.reassembler.isComplete()) TransferStatus.COMPLETED else TransferStatus.IN_PROGRESS,
                transportType = TransportType.NEARBY_SHARE
            )

            session.onProgress(transfer)

            if (session.reassembler.isComplete()) {
                session.fileHandle.closeFile()
                sessionMutex.withLock { incomingSessions.remove(session.transferId) }
                session.onComplete(transfer)
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.dataWithBytes(pinned.addressOf(0), this.size.toULong())
}
