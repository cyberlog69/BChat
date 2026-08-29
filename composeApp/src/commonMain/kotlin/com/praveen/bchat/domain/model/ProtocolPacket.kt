package com.praveen.bchat.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class PacketType {
    HANDSHAKE,
    HANDSHAKE_ACK,
    TEXT_MESSAGE,
    FILE_OFFER,
    FILE_ACCEPT,
    FILE_REJECT,
    DELIVERY_ACK,
    READ_ACK,
    TYPING_STATUS,
    DISCONNECT
}

@Serializable
data class ProtocolPacket(
    val type: PacketType,
    val senderId: String,
    val senderName: String,
    val messageId: String? = null,
    val textContent: String? = null,
    val fileAttachment: FileAttachmentMeta? = null,
    val timestamp: Long = 0L,
    val isTyping: Boolean = false,
    val conversationId: String? = null,
    // E2EE fields
    val publicKey: String? = null,       // Base64 public key during HANDSHAKE / HANDSHAKE_ACK
    val isEncrypted: Boolean = false,    // True if textContent or payload is encrypted
    val cipherText: String? = null,      // Base64 ciphertext
    val iv: String? = null               // Base64 AES-GCM IV
) {
    fun toByteArray(): ByteArray {
        return jsonConfig.encodeToString(this).encodeToByteArray()
    }

    companion object {
        private val jsonConfig = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromByteArray(bytes: ByteArray): ProtocolPacket? {
            return try {
                val str = bytes.decodeToString()
                jsonConfig.decodeFromString<ProtocolPacket>(str)
            } catch (e: Exception) {
                null
            }
        }
    }
}
