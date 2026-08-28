package com.praveen.bchat.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class QrPeerPayload(
    val deviceName: String,
    val transport: String,
    val ipAddress: String? = null,
    val port: Int? = null,
    val bluetoothAddress: String? = null,
    val nearbyEndpoint: String? = null,
    val hotspotSsid: String? = null,
    val hotspotPass: String? = null
)

object QrCodeHelper {

    private val json = Json { ignoreUnknownKeys = true }

    fun generateQrBitmap(content: String, size: Int = 512): Bitmap? {
        return try {
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun encodePeerPayload(payload: QrPeerPayload): String {
        return "BCHAT:" + json.encodeToString(payload)
    }

    fun decodePeerPayload(qrRaw: String): QrPeerPayload? {
        return try {
            val content = if (qrRaw.startsWith("BCHAT:")) qrRaw.substring(6) else qrRaw
            json.decodeFromString<QrPeerPayload>(content)
        } catch (e: Exception) {
            null
        }
    }
}
