package com.praveen.bchat.util

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.serialization.Serializable
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class EncryptedPayload(
    val cipherText: String, // Base64
    val iv: String          // Base64
)

object CryptoEngine {

    private const val TAG = "CryptoEngine"
    private const val PREFS_NAME = "bchat_crypto_keys"
    private const val KEY_PRIV = "ec_private_key"
    private const val KEY_PUB = "ec_public_key"

    private const val EC_CURVE = "secp256r1"
    private const val GCM_TAG_LENGTH = 128 // bits
    private const val GCM_IV_LENGTH = 12   // bytes (96 bits)

    private var localKeyPair: KeyPair? = null

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val privBase64 = prefs.getString(KEY_PRIV, null)
        val pubBase64 = prefs.getString(KEY_PUB, null)

        if (privBase64 != null && pubBase64 != null) {
            try {
                val keyFactory = KeyFactory.getInstance("EC")
                val privBytes = Base64.decode(privBase64, Base64.NO_WRAP)
                val pubBytes = Base64.decode(pubBase64, Base64.NO_WRAP)

                val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))
                localKeyPair = KeyPair(publicKey, privateKey)
                Log.d(TAG, "Loaded existing EC identity KeyPair")
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load saved keypair, generating new one", e)
            }
        }

        // Generate fresh EC KeyPair
        try {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(ECGenParameterSpec(EC_CURVE))
            val pair = kpg.generateKeyPair()
            localKeyPair = pair

            val privStr = Base64.encodeToString(pair.private.encoded, Base64.NO_WRAP)
            val pubStr = Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)

            prefs.edit()
                .putString(KEY_PRIV, privStr)
                .putString(KEY_PUB, pubStr)
                .apply()

            Log.d(TAG, "Generated and saved new EC identity KeyPair")
        } catch (e: Exception) {
            Log.e(TAG, "Error generating EC keypair", e)
        }
    }

    fun getLocalPublicKeyBase64(): String {
        val pub = localKeyPair?.public ?: throw IllegalStateException("CryptoEngine not initialized")
        return Base64.encodeToString(pub.encoded, Base64.NO_WRAP)
    }

    fun deriveSessionKey(peerPublicKeyBase64: String): SecretKey? {
        return try {
            val keyFactory = KeyFactory.getInstance("EC")
            val pubBytes = Base64.decode(peerPublicKeyBase64, Base64.NO_WRAP)
            val peerPubKey = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))

            val keyAgreement = KeyAgreement.getInstance("ECDH")
            keyAgreement.init(localKeyPair?.private)
            keyAgreement.doPhase(peerPubKey, true)
            val sharedSecret = keyAgreement.generateSecret()

            // Derive 256-bit AES key via SHA-256 of the shared secret
            val md = MessageDigest.getInstance("SHA-256")
            val derivedKeyBytes = md.digest(sharedSecret)
            SecretKeySpec(derivedKeyBytes, "AES")
        } catch (e: Exception) {
            Log.e(TAG, "Error deriving ECDH session key", e)
            null
        }
    }

    fun encrypt(plaintext: String, secretKey: SecretKey): EncryptedPayload {
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return EncryptedPayload(
            cipherText = Base64.encodeToString(cipherBytes, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    fun decrypt(cipherTextBase64: String, ivBase64: String, secretKey: SecretKey): String? {
        return try {
            val cipherBytes = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting ciphertext", e)
            null
        }
    }

    fun computeSafetyNumber(myPubKeyBase64: String, peerPubKeyBase64: String): String {
        return try {
            // Sort keys deterministically so both sides calculate the exact same 6-digit number
            val sorted = listOf(myPubKeyBase64, peerPubKeyBase64).sorted()
            val combined = sorted[0] + ":" + sorted[1]

            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(combined.toByteArray(Charsets.UTF_8))

            // Derive 6-digit security code from hash
            val intVal = ((hash[0].toInt() and 0xFF) shl 24) or
                    ((hash[1].toInt() and 0xFF) shl 16) or
                    ((hash[2].toInt() and 0xFF) shl 8) or
                    (hash[3].toInt() and 0xFF)

            val code = Math.abs(intVal % 1_000_000)
            String.format("%06d", code)
        } catch (e: Exception) {
            "000000"
        }
    }

    fun getLocalFingerprint(): String {
        return try {
            val pub = getLocalPublicKeyBase64()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(pub.toByteArray(Charsets.UTF_8))
            digest.take(8).joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }
}
