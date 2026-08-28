package com.praveen.bchat.data.local.entities

import com.praveen.bchat.domain.model.MessageType
import com.praveen.bchat.domain.model.MessageStatus
import com.praveen.bchat.domain.model.TransferStatus
import com.praveen.bchat.domain.model.TransportType

data class ConversationEntity(
    val conversationId: String,
    val peerId: String,
    val peerName: String,
    val lastMessageText: String,
    val lastMessageTimestamp: Long,
    val unreadCount: Int = 0,
    val preferredTransport: TransportType = TransportType.NEARBY_SHARE,
    val isEncrypted: Boolean = true
)

data class MessageEntity(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val isOutgoing: Boolean,
    val content: String,
    val type: MessageType,
    val timestamp: Long,
    val status: MessageStatus,
    val transportType: TransportType,
    val isEncrypted: Boolean = true,
    val fileId: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val mimeType: String? = null,
    val localFilePath: String? = null
)

data class TransferEntity(
    val transferId: String,
    val messageId: String?,
    val peerId: String,
    val peerName: String,
    val fileName: String,
    val fileSize: Long,
    val bytesTransferred: Long,
    val mimeType: String,
    val localUri: String?,
    val localFilePath: String?,
    val isIncoming: Boolean,
    val status: TransferStatus,
    val transportType: TransportType,
    val transferSpeedBytesPerSec: Long,
    val startTime: Long,
    val completedTime: Long?,
    val errorMessage: String?
)

data class PeerEntity(
    val peerId: String,
    val name: String,
    val lastSeenTimestamp: Long,
    val defaultTransport: TransportType,
    val ipAddress: String? = null,
    val bluetoothAddress: String? = null,
    val publicKey: String? = null,
    val safetyNumber: String? = null
)
