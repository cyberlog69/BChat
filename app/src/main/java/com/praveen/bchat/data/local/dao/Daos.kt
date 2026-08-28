package com.praveen.bchat.data.local.dao

import com.praveen.bchat.data.local.entities.ConversationEntity
import com.praveen.bchat.data.local.entities.MessageEntity
import com.praveen.bchat.data.local.entities.PeerEntity
import com.praveen.bchat.data.local.entities.TransferEntity
import com.praveen.bchat.domain.model.MessageStatus
import kotlinx.coroutines.flow.Flow

interface ChatDao {
    fun getAllConversations(): Flow<List<ConversationEntity>>
    suspend fun getConversationById(id: String): ConversationEntity?
    suspend fun insertOrUpdateConversation(conversation: ConversationEntity)
    suspend fun markConversationAsRead(id: String)
    suspend fun deleteConversation(id: String)
}

interface MessageDao {
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>
    suspend fun insertMessage(message: MessageEntity)
    suspend fun updateMessage(message: MessageEntity)
    suspend fun deleteMessagesForConversation(conversationId: String)
    suspend fun updateMessageStatus(messageId: String, status: MessageStatus)
}

interface TransferDao {
    fun getAllTransfers(): Flow<List<TransferEntity>>
    suspend fun getTransferById(id: String): TransferEntity?
    fun getTransferByMessageId(messageId: String): Flow<TransferEntity?>
    suspend fun insertOrUpdateTransfer(transfer: TransferEntity)
    suspend fun deleteTransfer(id: String)
}

interface PeerDao {
    fun getAllSavedPeers(): Flow<List<PeerEntity>>
    suspend fun insertOrUpdatePeer(peer: PeerEntity)
    suspend fun getPeerById(peerId: String): PeerEntity?
}
