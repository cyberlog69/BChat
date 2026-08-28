package com.praveen.bchat.ui.screens.transfers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.praveen.bchat.BChatApplication
import com.praveen.bchat.data.local.entities.TransferEntity
import com.praveen.bchat.domain.model.PeerDevice
import com.praveen.bchat.util.FileManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class TransfersViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as BChatApplication).chatRepository

    val allTransfers: Flow<List<TransferEntity>> = repository.getAllTransfers()
    val connectedPeers: StateFlow<List<PeerDevice>> = repository.connectedPeers

    fun getReceivedFiles(): List<File> {
        val dir = FileManager.getBChatDownloadDir(getApplication())
        return dir.listFiles()?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
