package com.praveen.bchat.ui.screens.hotspot

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.praveen.bchat.ui.theme.*
import com.praveen.bchat.util.QrCodeHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotspotScreen(
    onNavigateToChat: (String, String) -> Unit,
    viewModel: HotspotViewModel = viewModel()
) {
    val localIp by viewModel.localIp.collectAsState()
    val isServerRunning by viewModel.isServerRunning.collectAsState()

    var manualIpInput by remember { mutableStateOf("") }
    var manualPortInput by remember { mutableStateOf("8888") }

    val qrPayload = remember(localIp) { viewModel.getHotspotQrPayload() }
    val qrText = remember(qrPayload) { QrCodeHelper.encodePeerPayload(qrPayload) }
    val qrBitmap = remember(qrText) { QrCodeHelper.generateQrBitmap(qrText, 500) }

    LaunchedEffect(Unit) {
        viewModel.refreshNetwork()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Hotspot & Wi-Fi LAN", style = Typography.headlineMedium, color = TextPrimary)
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshNetwork() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = CyanNeon)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Hotspot Server Status Card
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "Local Socket Server",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (localIp != null) "IP: $localIp (Port 8888)" else "Not connected to Wi-Fi/Hotspot",
                                color = if (localIp != null) CyanNeon else AccentOrange,
                                fontSize = 13.sp
                            )
                        }

                        Switch(
                            checked = isServerRunning,
                            onCheckedChange = {
                                if (it) viewModel.startHotspotServer() else viewModel.stopHotspotServer()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBackground,
                                checkedTrackColor = CyanNeon
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // QR Code for Instant Hotspot Pairing
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Hotspot Join QR Code",
                        style = Typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Have another peer scan this to join your offline network",
                        style = Typography.bodyMedium,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (qrBitmap != null) {
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(10.dp)
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Hotspot QR",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Direct IP Connect Card
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Direct Socket Connect",
                        style = Typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Connect directly by entering a peer's local Wi-Fi or Hotspot IP",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = manualIpInput,
                            onValueChange = { manualIpInput = it },
                            label = { Text("IP Address (e.g. 192.168.43.1)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanNeon,
                                unfocusedBorderColor = DarkSurfaceElevated,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.weight(2f)
                        )

                        OutlinedTextField(
                            value = manualPortInput,
                            onValueChange = { manualPortInput = it },
                            label = { Text("Port") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanNeon,
                                unfocusedBorderColor = DarkSurfaceElevated,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (manualIpInput.isNotBlank()) {
                                val port = manualPortInput.toIntOrNull() ?: 8888
                                viewModel.connectToIp(manualIpInput.trim(), port)
                                onNavigateToChat("${manualIpInput.trim()}:$port", "Wi-Fi Peer ($manualIpInput)")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DarkBackground),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Connect Direct Socket", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
