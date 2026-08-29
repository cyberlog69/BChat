package com.praveen.bchat.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class TransferStatus {
    PENDING,
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    CANCELLED,
    FAILED
}

@Serializable
data class FileTransfer(
    val id: String,
    val messageId: String? = null,
    val peerId: String,
    val peerName: String,
    val fileName: String,
    val fileSize: Long,
    val bytesTransferred: Long = 0,
    val mimeType: String = "*/*",
    val localUri: String? = null,
    val localFilePath: String? = null,
    val isIncoming: Boolean,
    val status: TransferStatus = TransferStatus.PENDING,
    val transportType: TransportType = TransportType.NEARBY_SHARE,
    val transferSpeedBytesPerSec: Long = 0,
    val startTime: Long = 0L,
    val completedTime: Long? = null,
    val errorMessage: String? = null
) {
    val progressFraction: Float
        get() = if (fileSize > 0) (bytesTransferred.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f) else 0f

    val progressPercent: Int
        get() = (progressFraction * 100).toInt()

    val formattedSpeed: String
        get() {
            val speedMb = transferSpeedBytesPerSec / (1024.0 * 1024.0)
            return if (speedMb >= 1.0) {
                "${((speedMb * 10).toLong() / 10.0)} MB/s"
            } else {
                val speedKb = transferSpeedBytesPerSec / 1024.0
                "${((speedKb * 10).toLong() / 10.0)} KB/s"
            }
        }

    val formattedSize: String
        get() {
            val sizeMb = fileSize / (1024.0 * 1024.0)
            return if (sizeMb >= 1.0) {
                "${((sizeMb * 10).toLong() / 10.0)} MB"
            } else {
                val sizeKb = fileSize / 1024.0
                "${((sizeKb * 10).toLong() / 10.0)} KB"
            }
        }
}
