package com.praveen.bchat.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.praveen.bchat.data.local.BChatDatabase
import com.praveen.bchat.data.local.entities.ConversationEntity
import com.praveen.bchat.data.local.entities.MessageEntity
import com.praveen.bchat.data.local.entities.PeerEntity
import com.praveen.bchat.data.local.entities.TransferEntity
import com.praveen.bchat.data.transport.P2PManager
import com.praveen.bchat.domain.model.*
import com.praveen.bchat.util.FileManager
import com.praveen.bchat.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatRepositoryImpl(
    private val context: Context,
    private val database: BChatDatabase,
    private val p2pManager: P2PManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : ChatRepository {

    companion object {
        private const val TAG = "ChatRepository"
        const val BROADCAST_CONVERSATION_ID = "mesh_broadcast_group"
    }

    override val discoveredPeers: StateFlow<List<PeerDevice>> = p2pManager.discoveredPeers
    override val connectedPeers: StateFlow<List<PeerDevice>> = p2pManager.connectedPeers
    override val activeTransportFilter: StateFlow<TransportType?> = p2pManager.activeTransportFilter
    override val isScanning: StateFlow<Boolean> = p2pManager.isScanning
    override val isAdvertising: StateFlow<Boolean> = p2pManager.isAdvertising

    init {
        // Listen to incoming messages and save to Room
        scope.launch {
            p2pManager.incomingMessages.collect { msg ->
                handleIncomingMessage(msg)
            }
        }

        // Listen to transfer updates and save to Room
        scope.launch {
            p2pManager.transferUpdates.collect { transfer ->
                handleTransferUpdate(transfer)
            }
        }

        // Listen to completed received files and update corresponding message & transfer
        scope.launch {
            p2pManager.receivedFiles.collect { transfer ->
                handleFileReceived(transfer)
            }
        }
    }

    private suspend fun handleIncomingMessage(msg: ChatMessage) {
        val convId = msg.conversationId.ifBlank { msg.senderId }
        val msgEntity = MessageEntity(
            messageId = msg.id,
            conversationId = convId,
            senderId = msg.senderId,
            senderName = msg.senderName,
            isOutgoing = false,
            content = msg.content,
            type = msg.type,
            timestamp = msg.timestamp,
            status = MessageStatus.DELIVERED,
            transportType = msg.transportType,
            isEncrypted = msg.isEncrypted,
            fileId = msg.fileAttachment?.fileId,
            fileName = msg.fileAttachment?.fileName,
            fileSize = msg.fileAttachment?.fileSize,
            mimeType = msg.fileAttachment?.mimeType,
            localFilePath = msg.fileAttachment?.localFilePath
        )
        database.messageDao().insertMessage(msgEntity)

        val existingConv = database.chatDao().getConversationById(convId)
        val unread = (existingConv?.unreadCount ?: 0) + 1
        val convEntity = ConversationEntity(
            conversationId = convId,
            peerId = msg.senderId,
            peerName = msg.senderName,
            lastMessageText = if (msg.type == MessageType.TEXT) msg.content else "📎 ${msg.fileAttachment?.fileName ?: "File"}",
            lastMessageTimestamp = msg.timestamp,
            unreadCount = unread,
            preferredTransport = msg.transportType,
            isEncrypted = msg.isEncrypted
        )
        database.chatDao().insertOrUpdateConversation(convEntity)

        // Save peer with public key if known
        val pubKey = p2pManager.getPeerPublicKey(msg.senderId)
        val safety = p2pManager.getPeerSafetyNumber(msg.senderId)
        database.peerDao().insertOrUpdatePeer(
            PeerEntity(
                peerId = msg.senderId,
                name = msg.senderName,
                lastSeenTimestamp = System.currentTimeMillis(),
                defaultTransport = msg.transportType,
                publicKey = pubKey,
                safetyNumber = safety
            )
        )
    }

    private suspend fun handleTransferUpdate(transfer: FileTransfer) {
        val entity = TransferEntity(
            transferId = transfer.id,
            messageId = transfer.messageId,
            peerId = transfer.peerId,
            peerName = transfer.peerName,
            fileName = transfer.fileName,
            fileSize = transfer.fileSize,
            bytesTransferred = transfer.bytesTransferred,
            mimeType = transfer.mimeType,
            localUri = transfer.localUri,
            localFilePath = transfer.localFilePath,
            isIncoming = transfer.isIncoming,
            status = transfer.status,
            transportType = transfer.transportType,
            transferSpeedBytesPerSec = transfer.transferSpeedBytesPerSec,
            startTime = transfer.startTime,
            completedTime = transfer.completedTime,
            errorMessage = transfer.errorMessage
        )
        database.transferDao().insertOrUpdateTransfer(entity)
    }

    private suspend fun handleFileReceived(transfer: FileTransfer) {
        handleTransferUpdate(transfer)
        if (transfer.messageId != null) {
            database.messageDao().updateMessageStatus(transfer.messageId, MessageStatus.DELIVERED)
        }
    }

    override fun getAllConversations(): Flow<List<ConversationEntity>> {
        return database.chatDao().getAllConversations()
    }

    override fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>> {
        return database.messageDao().getMessagesForConversation(conversationId)
    }

    override fun getAllTransfers(): Flow<List<TransferEntity>> {
        return database.transferDao().getAllTransfers()
    }

    override fun getTransferByMessageId(messageId: String): Flow<TransferEntity?> {
        return database.transferDao().getTransferByMessageId(messageId)
    }

    override fun getPeerSafetyNumber(peerId: String): String? {
        return p2pManager.getPeerSafetyNumber(peerId)
    }

    override fun getPeerPublicKey(peerId: String): String? {
        return p2pManager.getPeerPublicKey(peerId)
    }

    override fun startDiscovery() {
        p2pManager.startDiscovery()
    }

    override fun stopDiscovery() {
        p2pManager.stopDiscovery()
    }

    override fun startAdvertising(name: String?) {
        p2pManager.startAdvertising(name)
    }

    override fun stopAdvertising() {
        p2pManager.stopAdvertising()
    }

    override fun setTransportFilter(type: TransportType?) {
        p2pManager.setTransportFilter(type)
    }

    override fun connectToPeer(peer: PeerDevice) {
        p2pManager.connectToPeer(peer)
    }

    override fun disconnectPeer(peerId: String) {
        p2pManager.disconnectPeer(peerId)
    }

    override suspend fun sendTextMessage(peerId: String, text: String, peerName: String): MessageEntity {
        val myName = NetworkUtils.getDeviceName(context)
        val msg = p2pManager.sendTextMessage(
            peerId = peerId,
            content = text,
            conversationId = peerId,
            senderName = myName
        )

        val entity = MessageEntity(
            messageId = msg.id,
            conversationId = peerId,
            senderId = "me",
            senderName = "Me",
            isOutgoing = true,
            content = text,
            type = MessageType.TEXT,
            timestamp = msg.timestamp,
            status = MessageStatus.SENT,
            transportType = msg.transportType,
            isEncrypted = msg.isEncrypted
        )
        database.messageDao().insertMessage(entity)

        val conv = ConversationEntity(
            conversationId = peerId,
            peerId = peerId,
            peerName = peerName,
            lastMessageText = text,
            lastMessageTimestamp = msg.timestamp,
            unreadCount = 0,
            preferredTransport = msg.transportType,
            isEncrypted = msg.isEncrypted
        )
        database.chatDao().insertOrUpdateConversation(conv)

        return entity
    }

    override suspend fun broadcastTextMessage(text: String): MessageEntity {
        val myName = NetworkUtils.getDeviceName(context)
        val msg = p2pManager.broadcastTextMessage(
            content = text,
            conversationId = BROADCAST_CONVERSATION_ID,
            senderName = myName
        )

        val entity = MessageEntity(
            messageId = msg.id,
            conversationId = BROADCAST_CONVERSATION_ID,
            senderId = "me",
            senderName = "Me (Broadcast)",
            isOutgoing = true,
            content = text,
            type = MessageType.TEXT,
            timestamp = msg.timestamp,
            status = MessageStatus.SENT,
            transportType = msg.transportType,
            isEncrypted = false
        )
        database.messageDao().insertMessage(entity)

        val conv = ConversationEntity(
            conversationId = BROADCAST_CONVERSATION_ID,
            peerId = "all",
            peerName = "📢 Mesh Broadcast",
            lastMessageText = text,
            lastMessageTimestamp = msg.timestamp,
            unreadCount = 0,
            preferredTransport = TransportType.NEARBY_SHARE,
            isEncrypted = false
        )
        database.chatDao().insertOrUpdateConversation(conv)

        return entity
    }

    override suspend fun sendFile(peerId: String, peerName: String, fileUri: Uri): MessageEntity {
        val meta = FileManager.resolveFileMetaFromUri(context, fileUri)
            ?: throw IllegalArgumentException("Could not resolve file metadata")

        val msg = p2pManager.sendFile(
            peerId = peerId,
            fileUri = fileUri,
            conversationId = peerId,
            meta = meta
        ) { progressTransfer ->
            scope.launch {
                handleTransferUpdate(progressTransfer)
            }
        }

        val entity = MessageEntity(
            messageId = msg.id,
            conversationId = peerId,
            senderId = "me",
            senderName = "Me",
            isOutgoing = true,
            content = "Sent file: ${meta.fileName}",
            type = if (meta.mimeType.startsWith("image/")) MessageType.IMAGE else MessageType.FILE,
            timestamp = msg.timestamp,
            status = MessageStatus.SENDING,
            transportType = msg.transportType,
            isEncrypted = true,
            fileId = meta.fileId,
            fileName = meta.fileName,
            fileSize = meta.fileSize,
            mimeType = meta.mimeType,
            localFilePath = meta.localFilePath
        )
        database.messageDao().insertMessage(entity)

        val conv = ConversationEntity(
            conversationId = peerId,
            peerId = peerId,
            peerName = peerName,
            lastMessageText = "📎 ${meta.fileName}",
            lastMessageTimestamp = msg.timestamp,
            unreadCount = 0,
            preferredTransport = msg.transportType,
            isEncrypted = true
        )
        database.chatDao().insertOrUpdateConversation(conv)

        return entity
    }

    override suspend fun markConversationAsRead(conversationId: String) {
        database.chatDao().markConversationAsRead(conversationId)
    }

    override suspend fun deleteConversation(conversationId: String) {
        database.chatDao().deleteConversation(conversationId)
        database.messageDao().deleteMessagesForConversation(conversationId)
    }
}
