package com.praveen.bchat.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class MessageType {
    TEXT,
    FILE,
    IMAGE,
    AUDIO,
    SYSTEM
}

@Serializable
enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

@Serializable
data class FileAttachmentMeta(
    val fileId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val uriString: String? = null,
    val localFilePath: String? = null,
    val sha256Checksum: String? = null
)

@Serializable
data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val isOutgoing: Boolean,
    val content: String,
    val type: MessageType = MessageType.TEXT,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val transportType: TransportType = TransportType.NEARBY_SHARE,
    val isEncrypted: Boolean = true,
    val fileAttachment: FileAttachmentMeta? = null
)
