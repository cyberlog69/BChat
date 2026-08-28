package com.praveen.bchat.ui.screens.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.praveen.bchat.BChatApplication
import com.praveen.bchat.data.local.entities.ConversationEntity
import com.praveen.bchat.data.local.entities.MessageEntity
import com.praveen.bchat.data.local.entities.TransferEntity
import com.praveen.bchat.domain.model.FileTransfer
import com.praveen.bchat.domain.model.PeerDevice
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as BChatApplication).chatRepository
    private val p2pManager = (application as BChatApplication).p2pManager

    val conversations: Flow<List<ConversationEntity>> = repository.getAllConversations()
    val connectedPeers: StateFlow<List<PeerDevice>> = repository.connectedPeers
    val transferUpdates: SharedFlow<FileTransfer> = p2pManager.transferUpdates

    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>> {
        return repository.getMessagesForConversation(conversationId)
    }

    fun getTransferByMessageId(messageId: String): Flow<TransferEntity?> {
        return repository.getTransferByMessageId(messageId)
    }

    fun getPeerSafetyNumber(peerId: String): String? {
        return repository.getPeerSafetyNumber(peerId)
    }

    fun getPeerPublicKey(peerId: String): String? {
        return repository.getPeerPublicKey(peerId)
    }

    fun sendMessage(peerId: String, text: String, peerName: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            if (peerId == "all" || peerId == "mesh_broadcast_group") {
                repository.broadcastTextMessage(text)
            } else {
                repository.sendTextMessage(peerId, text, peerName)
            }
        }
    }

    fun sendFile(peerId: String, peerName: String, fileUri: Uri) {
        viewModelScope.launch {
            repository.sendFile(peerId, peerName, fileUri)
        }
    }

    fun markConversationAsRead(conversationId: String) {
        viewModelScope.launch {
            repository.markConversationAsRead(conversationId)
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            repository.deleteConversation(conversationId)
        }
    }
}
