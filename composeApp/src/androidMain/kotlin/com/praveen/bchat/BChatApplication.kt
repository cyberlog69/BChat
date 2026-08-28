package com.praveen.bchat

import android.app.Application
import com.praveen.bchat.data.local.BChatDatabase
import com.praveen.bchat.data.repository.ChatRepository
import com.praveen.bchat.data.repository.ChatRepositoryImpl
import com.praveen.bchat.data.transport.P2PManager
import com.praveen.bchat.util.CryptoEngine

class BChatApplication : Application() {

    lateinit var database: BChatDatabase
        private set

    lateinit var p2pManager: P2PManager
        private set

    lateinit var chatRepository: ChatRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize End-to-End Encryption identity keypair
        CryptoEngine.initialize(this)

        database = BChatDatabase.getInstance(this)
        p2pManager = P2PManager.getInstance(this)
        chatRepository = ChatRepositoryImpl(this, database, p2pManager)
    }

    companion object {
        lateinit var instance: BChatApplication
            private set
    }
}
