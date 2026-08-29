package com.praveen.bchat.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MultiPathHandshake(
    val transferId: String,
    val fileName: String,
    val fileSize: Long,
    val chunkSize: Int,
    val totalChunks: Int,
    val mimeType: String,
    val sha256Checksum: String? = null,
    val supportedTransports: List<TransportType> = emptyList()
)

@Serializable
data class ChunkAck(
    val transferId: String,
    val chunkIndex: Int,
    val success: Boolean,
    val receivedBytes: Long = 0L
)

@Serializable
data class MultiPathTransferStats(
    val transferId: String,
    val totalBytes: Long,
    val transferredBytes: Long,
    val aggregateSpeedBytesPerSec: Long,
    val channelSpeeds: Map<TransportType, Long> = emptyMap(),
    val channelContributions: Map<TransportType, Long> = emptyMap(),
    val activeChannelsCount: Int = 1
)
