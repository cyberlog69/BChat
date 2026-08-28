package com.praveen.bchat.ui.components

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.praveen.bchat.domain.model.*
import com.praveen.bchat.ui.theme.*
import com.praveen.bchat.util.FileManager
import com.praveen.bchat.util.QrCodeHelper
import com.praveen.bchat.util.QrPeerPayload
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TransportBadge(transportType: TransportType, modifier: Modifier = Modifier) {
    val (bgColor, textColor, icon) = when (transportType) {
        TransportType.NEARBY_SHARE -> Triple(CyanNeon.copy(alpha = 0.15f), CyanNeon, Icons.Default.NearMe)
        TransportType.HOTSPOT_WIFI -> Triple(PurpleNeon.copy(alpha = 0.15f), PurpleNeon, Icons.Default.WifiTethering)
        TransportType.BLUETOOTH_CLASSIC -> Triple(BlueElectric.copy(alpha = 0.15f), BlueElectric, Icons.Default.Bluetooth)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = transportType.displayName,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun RadarAnimation(
    isScanning: Boolean,
    modifier: Modifier = Modifier,
    discoveredCount: Int = 0
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarAngle"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(240.dp)
            .padding(16.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2

            // Concentric radar circles
            for (i in 1..3) {
                drawCircle(
                    color = CyanNeon.copy(alpha = 0.15f),
                    radius = radius * (i / 3f),
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // Crosshair lines
            drawLine(
                color = CyanNeon.copy(alpha = 0.1f),
                start = Offset(center.x - radius, center.y),
                end = Offset(center.x + radius, center.y),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = CyanNeon.copy(alpha = 0.1f),
                start = Offset(center.x, center.y - radius),
                end = Offset(center.x, center.y + radius),
                strokeWidth = 1.dp.toPx()
            )

            if (isScanning) {
                // Expanding pulse wave
                drawCircle(
                    color = CyanNeon.copy(alpha = (1f - pulseScale) * 0.4f),
                    radius = radius * pulseScale,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Rotating radar beam
                val rad = Math.toRadians(angle.toDouble())
                val beamEnd = Offset(
                    (center.x + radius * cos(rad)).toFloat(),
                    (center.y + radius * sin(rad)).toFloat()
                )
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(CyanNeon.copy(alpha = 0.8f), Color.Transparent),
                        start = center,
                        end = beamEnd
                    ),
                    start = center,
                    end = beamEnd,
                    strokeWidth = 3.dp.toPx()
                )
            }
        }

        // Center status badge
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isScanning) Icons.Default.Sensors else Icons.Default.SensorsOff,
                contentDescription = null,
                tint = if (isScanning) CyanNeon else TextSecondary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isScanning) "Searching..." else "Idle",
                color = if (isScanning) CyanNeon else TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            if (discoveredCount > 0) {
                Text(
                    text = "$discoveredCount nearby",
                    color = AccentGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TransferProgressCard(
    transfer: FileTransfer,
    onOpen: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (transfer.mimeType.startsWith("image/")) Icons.Default.Image
                        else if (transfer.mimeType.startsWith("video/")) Icons.Default.VideoFile
                        else if (transfer.mimeType.startsWith("audio/")) Icons.Default.AudioFile
                        else Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = CyanNeon,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = transfer.fileName,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${FileManager.formatFileSize(transfer.bytesTransferred)} / ${FileManager.formatFileSize(transfer.fileSize)}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                TransportBadge(transportType = transfer.transportType)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { transfer.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = when (transfer.status) {
                    TransferStatus.COMPLETED -> AccentGreen
                    TransferStatus.FAILED -> AccentRed
                    else -> CyanNeon
                },
                trackColor = DarkBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = when (transfer.status) {
                        TransferStatus.COMPLETED -> "Completed"
                        TransferStatus.IN_PROGRESS -> "Speed: ${transfer.formattedSpeed}"
                        TransferStatus.FAILED -> "Failed"
                        TransferStatus.CANCELLED -> "Cancelled"
                        TransferStatus.PENDING -> "Waiting..."
                        TransferStatus.PAUSED -> "Paused"
                    },
                    color = when (transfer.status) {
                        TransferStatus.COMPLETED -> AccentGreen
                        TransferStatus.FAILED -> AccentRed
                        else -> TextSecondary
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "${transfer.progressPercent}%",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (transfer.status == TransferStatus.COMPLETED && transfer.localFilePath != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (onShare != null) {
                        TextButton(onClick = onShare) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share", fontSize = 12.sp)
                        }
                    }
                    if (onOpen != null) {
                        Button(
                            onClick = onOpen,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DarkBackground),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QrDisplayDialog(
    payload: QrPeerPayload,
    onDismiss: () -> Unit
) {
    val qrText = remember(payload) { QrCodeHelper.encodePeerPayload(payload) }
    val qrBitmap = remember(qrText) { QrCodeHelper.generateQrBitmap(qrText, 600) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Quick Connect QR",
                    style = Typography.titleLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Scan with another BChat device to connect instantly",
                    style = Typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (qrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(12.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Device: ${payload.deviceName}",
                    color = CyanNeon,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                if (payload.ipAddress != null) {
                    Text(
                        text = "IP: ${payload.ipAddress}:${payload.port ?: 8888}",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated)
                ) {
                    Text("Close", color = TextPrimary)
                }
            }
        }
    }
}

@Composable
fun QrScannerDialog(
    onQrScanned: (QrPeerPayload) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Scan Peer QR Code",
                        style = Typography.titleLarge,
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                val barcodeScanner = BarcodeScanning.getClient()
                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null) {
                                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                        barcodeScanner.process(image)
                                            .addOnSuccessListener { barcodes ->
                                                for (barcode in barcodes) {
                                                    val rawValue = barcode.rawValue
                                                    if (rawValue != null) {
                                                        val peerPayload = QrCodeHelper.decodePeerPayload(rawValue)
                                                        if (peerPayload != null) {
                                                            onQrScanned(peerPayload)
                                                            break
                                                        }
                                                    }
                                                }
                                            }
                                            .addOnCompleteListener {
                                                imageProxy.close()
                                            }
                                    } else {
                                        imageProxy.close()
                                    }
                                }

                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageAnalysis
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }, ContextCompat.getMainExecutor(ctx))

                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Target scanning box overlay
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .align(Alignment.Center)
                            .border(2.dp, CyanNeon, RoundedCornerShape(12.dp))
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Point camera at another device's BChat QR code",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}
