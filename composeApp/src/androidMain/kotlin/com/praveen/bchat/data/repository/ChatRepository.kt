package com.praveen.bchat.data.repository

import android.net.Uri
import com.praveen.bchat.data.local.entities.ConversationEntity
import com.praveen.bchat.data.local.entities.MessageEntity
import com.praveen.bchat.data.local.entities.PeerEntity
import com.praveen.bchat.data.local.entities.TransferEntity
import com.praveen.bchat.domain.model.ChatMessage
import com.praveen.bchat.domain.model.FileTransfer
import com.praveen.bchat.domain.model.PeerDevice
import com.praveen.bchat.domain.model.TransportType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ChatRepository {
    val discoveredPeers: StateFlow<List<PeerDevice>>
    val connectedPeers: StateFlow<List<PeerDevice>>
    val activeTransportFilter: StateFlow<TransportType?>
    val isScanning: StateFlow<Boolean>
    val isAdvertising: StateFlow<Boolean>

    fun getAllConversations(): Flow<List<ConversationEntity>>
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>
    fun getAllTransfers(): Flow<List<TransferEntity>>
    fun getTransferByMessageId(messageId: String): Flow<TransferEntity?>
    fun getPeerSafetyNumber(peerId: String): String?
    fun getPeerPublicKey(peerId: String): String?

    fun startDiscovery()
    fun stopDiscovery()
    fun startAdvertising(name: String? = null)
    fun stopAdvertising()
    fun setTransportFilter(type: TransportType?)
    fun connectToPeer(peer: PeerDevice)
    fun disconnectPeer(peerId: String)

    suspend fun sendTextMessage(peerId: String, text: String, peerName: String): MessageEntity
    suspend fun broadcastTextMessage(text: String): MessageEntity
    suspend fun sendFile(peerId: String, peerName: String, fileUri: Uri): MessageEntity
    suspend fun markConversationAsRead(conversationId: String)
    suspend fun deleteConversation(conversationId: String)
}
