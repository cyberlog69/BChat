package com.praveen.bchat.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.praveen.bchat.data.local.dao.*
import com.praveen.bchat.data.local.entities.*
import com.praveen.bchat.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class BChatDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "bchat_database.db"
        private const val DATABASE_VERSION = 2

        @Volatile
        private var INSTANCE: BChatDatabase? = null

        fun getInstance(context: Context): BChatDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BChatDatabase(context).also { INSTANCE = it }
            }
        }
    }

    private val conversationsTrigger = MutableStateFlow(System.currentTimeMillis())
    private val messagesTrigger = MutableStateFlow(System.currentTimeMillis())
    private val transfersTrigger = MutableStateFlow(System.currentTimeMillis())
    private val peersTrigger = MutableStateFlow(System.currentTimeMillis())

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS conversations (
                conversationId TEXT PRIMARY KEY,
                peerId TEXT NOT NULL,
                peerName TEXT NOT NULL,
                lastMessageText TEXT NOT NULL,
                lastMessageTimestamp INTEGER NOT NULL,
                unreadCount INTEGER NOT NULL DEFAULT 0,
                preferredTransport TEXT NOT NULL,
                isEncrypted INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS messages (
                messageId TEXT PRIMARY KEY,
                conversationId TEXT NOT NULL,
                senderId TEXT NOT NULL,
                senderName TEXT NOT NULL,
                isOutgoing INTEGER NOT NULL,
                content TEXT NOT NULL,
                type TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                status TEXT NOT NULL,
                transportType TEXT NOT NULL,
                isEncrypted INTEGER NOT NULL DEFAULT 1,
                fileId TEXT,
                fileName TEXT,
                fileSize INTEGER,
                mimeType TEXT,
                localFilePath TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS file_transfers (
                transferId TEXT PRIMARY KEY,
                messageId TEXT,
                peerId TEXT NOT NULL,
                peerName TEXT NOT NULL,
                fileName TEXT NOT NULL,
                fileSize INTEGER NOT NULL,
                bytesTransferred INTEGER NOT NULL,
                mimeType TEXT NOT NULL,
                localUri TEXT,
                localFilePath TEXT,
                isIncoming INTEGER NOT NULL,
                status TEXT NOT NULL,
                transportType TEXT NOT NULL,
                transferSpeedBytesPerSec INTEGER NOT NULL,
                startTime INTEGER NOT NULL,
                completedTime INTEGER,
                errorMessage TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS peers (
                peerId TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                lastSeenTimestamp INTEGER NOT NULL,
                defaultTransport TEXT NOT NULL,
                ipAddress TEXT,
                bluetoothAddress TEXT,
                publicKey TEXT,
                safetyNumber TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS conversations")
        db.execSQL("DROP TABLE IF EXISTS messages")
        db.execSQL("DROP TABLE IF EXISTS file_transfers")
        db.execSQL("DROP TABLE IF EXISTS peers")
        onCreate(db)
    }

    // DAO Implementations
    private val chatDaoImpl = object : ChatDao {
        override fun getAllConversations(): Flow<List<ConversationEntity>> {
            return conversationsTrigger.asStateFlow().map {
                queryAllConversations()
            }
        }

        override suspend fun getConversationById(id: String): ConversationEntity? = withContext(Dispatchers.IO) {
            val db = readableDatabase
            val cursor = db.query("conversations", null, "conversationId = ?", arrayOf(id), null, null, null)
            cursor.use {
                if (it.moveToFirst()) cursorToConversation(it) else null
            }
        }

        override suspend fun insertOrUpdateConversation(conversation: ConversationEntity) = withContext(Dispatchers.IO) {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("conversationId", conversation.conversationId)
                put("peerId", conversation.peerId)
                put("peerName", conversation.peerName)
                put("lastMessageText", conversation.lastMessageText)
                put("lastMessageTimestamp", conversation.lastMessageTimestamp)
                put("unreadCount", conversation.unreadCount)
                put("preferredTransport", conversation.preferredTransport.name)
                put("isEncrypted", if (conversation.isEncrypted) 1 else 0)
            }
            db.insertWithOnConflict("conversations", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            conversationsTrigger.value = System.currentTimeMillis()
        }

        override suspend fun markConversationAsRead(id: String) = withContext(Dispatchers.IO) {
            val db = writableDatabase
            val cv = ContentValues().apply { put("unreadCount", 0) }
            db.update("conversations", cv, "conversationId = ?", arrayOf(id))
            conversationsTrigger.value = System.currentTimeMillis()
        }

        override suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
            val db = writableDatabase
            db.delete("conversations", "conversationId = ?", arrayOf(id))
            conversationsTrigger.value = System.currentTimeMillis()
        }
    }

    private val messageDaoImpl = object : MessageDao {
        override fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>> {
            return messagesTrigger.asStateFlow().map {
                queryMessagesForConversation(conversationId)
            }
        }

        override suspend fun insertMessage(message: MessageEntity) = withContext(Dispatchers.IO) {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("messageId", message.messageId)
                put("conversationId", message.conversationId)
                put("senderId", message.senderId)
                put("senderName", message.senderName)
                put("isOutgoing", if (message.isOutgoing) 1 else 0)
                put("content", message.content)
                put("type", message.type.name)
                put("timestamp", message.timestamp)
                put("status", message.status.name)
                put("transportType", message.transportType.name)
                put("isEncrypted", if (message.isEncrypted) 1 else 0)
                put("fileId", message.fileId)
                put("fileName", message.fileName)
                put("fileSize", message.fileSize)
                put("mimeType", message.mimeType)
                put("localFilePath", message.localFilePath)
            }
            db.insertWithOnConflict("messages", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            messagesTrigger.value = System.currentTimeMillis()
        }

        override suspend fun updateMessage(message: MessageEntity) = withContext(Dispatchers.IO) {
            insertMessage(message)
        }

        override suspend fun deleteMessagesForConversation(conversationId: String) = withContext(Dispatchers.IO) {
            val db = writableDatabase
            db.delete("messages", "conversationId = ?", arrayOf(conversationId))
            messagesTrigger.value = System.currentTimeMillis()
        }

        override suspend fun updateMessageStatus(messageId: String, status: MessageStatus) = withContext(Dispatchers.IO) {
            val db = writableDatabase
            val cv = ContentValues().apply { put("status", status.name) }
            db.update("messages", cv, "messageId = ?", arrayOf(messageId))
            messagesTrigger.value = System.currentTimeMillis()
        }
    }

    private val transferDaoImpl = object : TransferDao {
        override fun getAllTransfers(): Flow<List<TransferEntity>> {
            return transfersTrigger.asStateFlow().map {
                queryAllTransfers()
            }
        }

        override suspend fun getTransferById(id: String): TransferEntity? = withContext(Dispatchers.IO) {
            val db = readableDatabase
            val cursor = db.query("file_transfers", null, "transferId = ?", arrayOf(id), null, null, null)
            cursor.use {
                if (it.moveToFirst()) cursorToTransfer(it) else null
            }
        }

        override fun getTransferByMessageId(messageId: String): Flow<TransferEntity?> {
            return transfersTrigger.asStateFlow().map {
                queryTransferByMessageId(messageId)
            }
        }

        override suspend fun insertOrUpdateTransfer(transfer: TransferEntity) = withContext(Dispatchers.IO) {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("transferId", transfer.transferId)
                put("messageId", transfer.messageId)
                put("peerId", transfer.peerId)
                put("peerName", transfer.peerName)
                put("fileName", transfer.fileName)
                put("fileSize", transfer.fileSize)
                put("bytesTransferred", transfer.bytesTransferred)
                put("mimeType", transfer.mimeType)
                put("localUri", transfer.localUri)
                put("localFilePath", transfer.localFilePath)
                put("isIncoming", if (transfer.isIncoming) 1 else 0)
                put("status", transfer.status.name)
                put("transportType", transfer.transportType.name)
                put("transferSpeedBytesPerSec", transfer.transferSpeedBytesPerSec)
                put("startTime", transfer.startTime)
                put("completedTime", transfer.completedTime)
                put("errorMessage", transfer.errorMessage)
            }
            db.insertWithOnConflict("file_transfers", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            transfersTrigger.value = System.currentTimeMillis()
        }

        override suspend fun deleteTransfer(id: String) = withContext(Dispatchers.IO) {
            val db = writableDatabase
            db.delete("file_transfers", "transferId = ?", arrayOf(id))
            transfersTrigger.value = System.currentTimeMillis()
        }
    }

    private val peerDaoImpl = object : PeerDao {
        override fun getAllSavedPeers(): Flow<List<PeerEntity>> {
            return peersTrigger.asStateFlow().map {
                queryAllPeers()
            }
        }

        override suspend fun insertOrUpdatePeer(peer: PeerEntity) = withContext(Dispatchers.IO) {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("peerId", peer.peerId)
                put("name", peer.name)
                put("lastSeenTimestamp", peer.lastSeenTimestamp)
                put("defaultTransport", peer.defaultTransport.name)
                put("ipAddress", peer.ipAddress)
                put("bluetoothAddress", peer.bluetoothAddress)
                put("publicKey", peer.publicKey)
                put("safetyNumber", peer.safetyNumber)
            }
            db.insertWithOnConflict("peers", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            peersTrigger.value = System.currentTimeMillis()
        }

        override suspend fun getPeerById(peerId: String): PeerEntity? = withContext(Dispatchers.IO) {
            val db = readableDatabase
            val cursor = db.query("peers", null, "peerId = ?", arrayOf(peerId), null, null, null)
            cursor.use {
                if (it.moveToFirst()) cursorToPeer(it) else null
            }
        }
    }

    fun chatDao(): ChatDao = chatDaoImpl
    fun messageDao(): MessageDao = messageDaoImpl
    fun transferDao(): TransferDao = transferDaoImpl
    fun peerDao(): PeerDao = peerDaoImpl

    // Cursor helpers
    private fun queryAllConversations(): List<ConversationEntity> {
        val list = mutableListOf<ConversationEntity>()
        val db = readableDatabase
        val cursor = db.query("conversations", null, null, null, null, null, "lastMessageTimestamp DESC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToConversation(it))
            }
        }
        return list
    }

    private fun queryMessagesForConversation(convId: String): List<MessageEntity> {
        val list = mutableListOf<MessageEntity>()
        val db = readableDatabase
        val cursor = db.query("messages", null, "conversationId = ?", arrayOf(convId), null, null, "timestamp ASC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToMessage(it))
            }
        }
        return list
    }

    private fun queryAllTransfers(): List<TransferEntity> {
        val list = mutableListOf<TransferEntity>()
        val db = readableDatabase
        val cursor = db.query("file_transfers", null, null, null, null, null, "startTime DESC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToTransfer(it))
            }
        }
        return list
    }

    private fun queryTransferByMessageId(messageId: String): TransferEntity? {
        val db = readableDatabase
        val cursor = db.query("file_transfers", null, "messageId = ?", arrayOf(messageId), null, null, null, "1")
        cursor.use {
            return if (it.moveToFirst()) cursorToTransfer(it) else null
        }
    }

    private fun queryAllPeers(): List<PeerEntity> {
        val list = mutableListOf<PeerEntity>()
        val db = readableDatabase
        val cursor = db.query("peers", null, null, null, null, null, "lastSeenTimestamp DESC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToPeer(it))
            }
        }
        return list
    }

    private fun cursorToConversation(c: Cursor): ConversationEntity {
        return ConversationEntity(
            conversationId = c.getString(c.getColumnIndexOrThrow("conversationId")),
            peerId = c.getString(c.getColumnIndexOrThrow("peerId")),
            peerName = c.getString(c.getColumnIndexOrThrow("peerName")),
            lastMessageText = c.getString(c.getColumnIndexOrThrow("lastMessageText")),
            lastMessageTimestamp = c.getLong(c.getColumnIndexOrThrow("lastMessageTimestamp")),
            unreadCount = c.getInt(c.getColumnIndexOrThrow("unreadCount")),
            preferredTransport = try {
                TransportType.valueOf(c.getString(c.getColumnIndexOrThrow("preferredTransport")))
            } catch (e: Exception) {
                TransportType.NEARBY_SHARE
            },
            isEncrypted = c.getInt(c.getColumnIndexOrThrow("isEncrypted")) == 1
        )
    }

    private fun cursorToMessage(c: Cursor): MessageEntity {
        val typeStr = c.getString(c.getColumnIndexOrThrow("type"))
        val statusStr = c.getString(c.getColumnIndexOrThrow("status"))
        val transportStr = c.getString(c.getColumnIndexOrThrow("transportType"))

        return MessageEntity(
            messageId = c.getString(c.getColumnIndexOrThrow("messageId")),
            conversationId = c.getString(c.getColumnIndexOrThrow("conversationId")),
            senderId = c.getString(c.getColumnIndexOrThrow("senderId")),
            senderName = c.getString(c.getColumnIndexOrThrow("senderName")),
            isOutgoing = c.getInt(c.getColumnIndexOrThrow("isOutgoing")) == 1,
            content = c.getString(c.getColumnIndexOrThrow("content")),
            type = try { MessageType.valueOf(typeStr) } catch (e: Exception) { MessageType.TEXT },
            timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp")),
            status = try { MessageStatus.valueOf(statusStr) } catch (e: Exception) { MessageStatus.SENT },
            transportType = try { TransportType.valueOf(transportStr) } catch (e: Exception) { TransportType.NEARBY_SHARE },
            isEncrypted = c.getInt(c.getColumnIndexOrThrow("isEncrypted")) == 1,
            fileId = c.getString(c.getColumnIndexOrThrow("fileId")),
            fileName = c.getString(c.getColumnIndexOrThrow("fileName")),
            fileSize = if (c.isNull(c.getColumnIndexOrThrow("fileSize"))) null else c.getLong(c.getColumnIndexOrThrow("fileSize")),
            mimeType = c.getString(c.getColumnIndexOrThrow("mimeType")),
            localFilePath = c.getString(c.getColumnIndexOrThrow("localFilePath"))
        )
    }

    private fun cursorToTransfer(c: Cursor): TransferEntity {
        val statusStr = c.getString(c.getColumnIndexOrThrow("status"))
        val transportStr = c.getString(c.getColumnIndexOrThrow("transportType"))

        return TransferEntity(
            transferId = c.getString(c.getColumnIndexOrThrow("transferId")),
            messageId = c.getString(c.getColumnIndexOrThrow("messageId")),
            peerId = c.getString(c.getColumnIndexOrThrow("peerId")),
            peerName = c.getString(c.getColumnIndexOrThrow("peerName")),
            fileName = c.getString(c.getColumnIndexOrThrow("fileName")),
            fileSize = c.getLong(c.getColumnIndexOrThrow("fileSize")),
            bytesTransferred = c.getLong(c.getColumnIndexOrThrow("bytesTransferred")),
            mimeType = c.getString(c.getColumnIndexOrThrow("mimeType")),
            localUri = c.getString(c.getColumnIndexOrThrow("localUri")),
            localFilePath = c.getString(c.getColumnIndexOrThrow("localFilePath")),
            isIncoming = c.getInt(c.getColumnIndexOrThrow("isIncoming")) == 1,
            status = try { TransferStatus.valueOf(statusStr) } catch (e: Exception) { TransferStatus.PENDING },
            transportType = try { TransportType.valueOf(transportStr) } catch (e: Exception) { TransportType.NEARBY_SHARE },
            transferSpeedBytesPerSec = c.getLong(c.getColumnIndexOrThrow("transferSpeedBytesPerSec")),
            startTime = c.getLong(c.getColumnIndexOrThrow("startTime")),
            completedTime = if (c.isNull(c.getColumnIndexOrThrow("completedTime"))) null else c.getLong(c.getColumnIndexOrThrow("completedTime")),
            errorMessage = c.getString(c.getColumnIndexOrThrow("errorMessage"))
        )
    }

    private fun cursorToPeer(c: Cursor): PeerEntity {
        val transportStr = c.getString(c.getColumnIndexOrThrow("defaultTransport"))
        return PeerEntity(
            peerId = c.getString(c.getColumnIndexOrThrow("peerId")),
            name = c.getString(c.getColumnIndexOrThrow("name")),
            lastSeenTimestamp = c.getLong(c.getColumnIndexOrThrow("lastSeenTimestamp")),
            defaultTransport = try { TransportType.valueOf(transportStr) } catch (e: Exception) { TransportType.NEARBY_SHARE },
            ipAddress = c.getString(c.getColumnIndexOrThrow("ipAddress")),
            bluetoothAddress = c.getString(c.getColumnIndexOrThrow("bluetoothAddress")),
            publicKey = c.getString(c.getColumnIndexOrThrow("publicKey")),
            safetyNumber = c.getString(c.getColumnIndexOrThrow("safetyNumber"))
        )
    }
}
