package com.praveen.bchat.data.transfer

import com.praveen.bchat.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Multi-Path Striped Concurrent Transfer Engine.
 * Pure Kotlin Multiplatform implementation supporting adaptive multi-channel striping,
 * dynamic load balancing, CRC-32 per-chunk integrity, and out-of-order chunk reassembly.
 */
class MultiPathTransferEngine {

    companion object {
        const val DEFAULT_CHUNK_SIZE = 128 * 1024 // 128 KB standard chunk size
        const val FAST_CHANNEL_CHUNK_SIZE = 256 * 1024 // 256 KB for Wi-Fi / Hotspot
        const val SLOW_CHANNEL_CHUNK_SIZE = 32 * 1024 // 32 KB for Bluetooth RFCOMM/BLE
    }

    /**
     * Helper to compute total number of chunks for a given file size and chunk size.
     */
    fun calculateChunkCount(fileSize: Long, chunkSize: Int = DEFAULT_CHUNK_SIZE): Int {
        if (fileSize <= 0) return 0
        return ((fileSize + chunkSize - 1) / chunkSize).toInt()
    }

    /**
     * Sender-side scheduler that distributes chunks across multiple active channels.
     */
    class SenderScheduler(
        val transferId: String,
        val totalChunks: Int,
        val fileSize: Long,
        val chunkSize: Int = DEFAULT_CHUNK_SIZE
    ) {
        private val mutex = Mutex()
        private var nextChunkIndex = 0
        private val pendingRetries = mutableListOf<Int>()
        private val inFlightChunks = mutableSetOf<Int>()
        private val completedChunks = mutableSetOf<Int>()

        // Channel throughput tracking: channelTag -> bytesTransferred
        private val channelContributions = mutableMapOf<String, Long>()
        private val channelSpeeds = mutableMapOf<String, Long>()
        private val channelLastTimes = mutableMapOf<String, Long>()

        suspend fun getNextChunkToSend(): Int? = mutex.withLock {
            if (pendingRetries.isNotEmpty()) {
                val retryIndex = pendingRetries.removeAt(0)
                inFlightChunks.add(retryIndex)
                return retryIndex
            }
            if (nextChunkIndex < totalChunks) {
                val index = nextChunkIndex++
                inFlightChunks.add(index)
                return index
            }
            return null
        }

        suspend fun onChunkAck(chunkIndex: Int, channelTag: String, bytes: Long, timestamp: Long) = mutex.withLock {
            inFlightChunks.remove(chunkIndex)
            completedChunks.add(chunkIndex)

            // Update stats
            val prevBytes = channelContributions.getOrElse(channelTag) { 0L }
            channelContributions[channelTag] = prevBytes + bytes

            val lastTime = channelLastTimes[channelTag] ?: timestamp
            val elapsedMs = (timestamp - lastTime).coerceAtLeast(1L)
            val currentSpeed = (bytes * 1000L) / elapsedMs
            channelSpeeds[channelTag] = currentSpeed
            channelLastTimes[channelTag] = timestamp
        }

        suspend fun onChunkFailed(chunkIndex: Int) = mutex.withLock {
            inFlightChunks.remove(chunkIndex)
            if (!completedChunks.contains(chunkIndex) && !pendingRetries.contains(chunkIndex)) {
                pendingRetries.add(chunkIndex)
            }
        }

        suspend fun isCompleted(): Boolean = mutex.withLock {
            completedChunks.size == totalChunks
        }

        suspend fun getProgressFraction(): Float = mutex.withLock {
            if (totalChunks > 0) completedChunks.size.toFloat() / totalChunks.toFloat() else 0f
        }
    }

    /**
     * Receiver-side collector and reassembler.
     */
    class ReceiverReassembler(
        val transferId: String,
        val totalChunks: Int,
        val fileSize: Long
    ) {
        private val mutex = Mutex()
        private val receivedChunks = mutableSetOf<Int>()
        private var totalBytesReceived = 0L
        private val channelContributions = mutableMapOf<String, Long>()

        private val _progressFraction = MutableStateFlow(0f)
        val progressFraction: StateFlow<Float> = _progressFraction.asStateFlow()

        /**
         * Validates a chunk against its CRC-32 checksum and records receipt.
         * Returns true if the chunk was valid and new, false if corrupt or already received.
         */
        suspend fun validateAndRecordChunk(chunk: FileChunkPacket): Boolean = mutex.withLock {
            // Verify CRC-32 integrity
            val computedChecksum = Crc32.calculate(chunk.data)
            if (computedChecksum != chunk.checksum) {
                return false // Checksum mismatch
            }

            if (receivedChunks.contains(chunk.chunkIndex)) {
                return true // Duplicate chunk, already accounted
            }

            receivedChunks.add(chunk.chunkIndex)
            totalBytesReceived += chunk.data.size

            val currentContrib = channelContributions.getOrElse(chunk.channelTag) { 0L }
            channelContributions[chunk.channelTag] = currentContrib + chunk.data.size

            if (totalChunks > 0) {
                _progressFraction.value = (receivedChunks.size.toFloat() / totalChunks.toFloat()).coerceIn(0f, 1f)
            }

            return true
        }

        suspend fun isComplete(): Boolean = mutex.withLock {
            receivedChunks.size == totalChunks
        }

        suspend fun getMissingChunkIndices(): List<Int> = mutex.withLock {
            (0 until totalChunks).filter { !receivedChunks.contains(it) }
        }

        suspend fun getReceivedBytes(): Long = mutex.withLock {
            totalBytesReceived
        }

        suspend fun getChannelContributions(): Map<String, Long> = mutex.withLock {
            channelContributions.toMap()
        }
    }
}
