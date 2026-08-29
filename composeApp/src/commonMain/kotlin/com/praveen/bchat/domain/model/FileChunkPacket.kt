package com.praveen.bchat.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FileChunkPacket(
    val transferId: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val data: ByteArray,
    val checksum: Long,                  // IEEE 802.3 CRC-32 checksum
    val channelTag: String = "primary",  // e.g., "wifi", "bluetooth", "multipeer"
    val isLastChunk: Boolean = false,
    val iv: String? = null               // For per-chunk E2EE if enabled
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as FileChunkPacket
        if (transferId != other.transferId) return false
        if (chunkIndex != other.chunkIndex) return false
        if (totalChunks != other.totalChunks) return false
        if (!data.contentEquals(other.data)) return false
        if (checksum != other.checksum) return false
        if (channelTag != other.channelTag) return false
        if (isLastChunk != other.isLastChunk) return false
        if (iv != other.iv) return false
        return true
    }

    override fun hashCode(): Int {
        var result = transferId.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + totalChunks
        result = 31 * result + data.contentHashCode()
        result = 31 * result + checksum.hashCode()
        result = 31 * result + channelTag.hashCode()
        result = 31 * result + isLastChunk.hashCode()
        result = 31 * result + (iv?.hashCode() ?: 0)
        return result
    }
}
