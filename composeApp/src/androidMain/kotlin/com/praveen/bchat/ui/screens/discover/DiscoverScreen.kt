package com.praveen.bchat.ui.screens.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.praveen.bchat.domain.model.ConnectionStatus
import com.praveen.bchat.domain.model.PeerDevice
import com.praveen.bchat.domain.model.TransportType
import com.praveen.bchat.ui.components.*
import com.praveen.bchat.ui.theme.*
import com.praveen.bchat.util.QrPeerPayload
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onNavigateToChat: (String, String) -> Unit,
    viewModel: DiscoverViewModel = viewModel()
) {
    val discoveredPeers by viewModel.discoveredPeers.collectAsState()
    val connectedPeers by viewModel.connectedPeers.collectAsState()
    val activeFilter by viewModel.activeTransportFilter.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isAdvertising by viewModel.isAdvertising.collectAsState()

    var showMyQrDialog by remember { mutableStateOf(false) }
    var showScanQrDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.statusEvents.collectLatest { event ->
            snackbarHostState.showSnackbar(event)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Discover Nearby", style = Typography.headlineMedium, color = TextPrimary)
                        Text(
                            if (isScanning) "Searching for peers..." else "Scan stopped",
                            style = Typography.labelSmall,
                            color = if (isScanning) CyanNeon else TextSecondary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMyQrDialog = true }) {
                        Icon(Icons.Default.QrCode, contentDescription = "My QR Code", tint = CyanNeon)
                    }
                    IconButton(onClick = { showScanQrDialog = true }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR Code", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Advertising & Scanning Switch Bar
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isAdvertising) AccentGreen else TextSecondary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Make Device Discoverable",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isAdvertising) "Broadcasting to nearby devices" else "Hidden from peers",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Switch(
                        checked = isAdvertising,
                        onCheckedChange = { viewModel.toggleAdvertising() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkBackground,
                            checkedTrackColor = CyanNeon,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = DarkSurfaceElevated
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Radar Scan View
            RadarAnimation(
                isScanning = isScanning,
                discoveredCount = discoveredPeers.size,
                modifier = Modifier.clickable { viewModel.toggleScan() }
            )

            // Scan Toggle Button
            Button(
                onClick = { viewModel.toggleScan() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning) DarkSurfaceElevated else CyanNeon,
                    contentColor = if (isScanning) TextPrimary else DarkBackground
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isScanning) "Stop Scanning" else "Scan for Peers",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Transport Filters Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = activeFilter == null,
                    onClick = { viewModel.setTransportFilter(null) },
                    label = { Text("All") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanNeon,
                        selectedLabelColor = DarkBackground,
                        containerColor = DarkSurface
                    )
                )

                TransportType.values().forEach { transport ->
                    FilterChip(
                        selected = activeFilter == transport,
                        onClick = { viewModel.setTransportFilter(transport) },
                        label = { Text(transport.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanNeon,
                            selectedLabelColor = DarkBackground,
                            containerColor = DarkSurface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Discovered Peers List
            Text(
                text = "Discovered Devices (${discoveredPeers.size})",
                style = Typography.titleMedium,
                color = TextSecondary,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(vertical = 4.dp)
            )

            if (discoveredPeers.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.DevicesOther,
                            contentDescription = null,
                            tint = TextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isScanning) "Listening for nearby devices..." else "Tap 'Scan for Peers' to start",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(discoveredPeers, key = { it.id }) { peer ->
                        DiscoveredPeerItem(
                            peer = peer,
                            onConnect = { viewModel.connectToPeer(peer) },
                            onDisconnect = { viewModel.disconnectPeer(peer.id) },
                            onChat = { onNavigateToChat(peer.id, peer.name) }
                        )
                    }
                }
            }
        }
    }

    if (showMyQrDialog) {
        QrDisplayDialog(
            payload = viewModel.getLocalQrPayload(),
            onDismiss = { showMyQrDialog = false }
        )
    }

    if (showScanQrDialog) {
        QrScannerDialog(
            onQrScanned = { payload ->
                showScanQrDialog = false
                viewModel.connectViaQrPayload(payload)
            },
            onDismiss = { showScanQrDialog = false }
        )
    }
}

@Composable
fun DiscoveredPeerItem(
    peer: PeerDevice,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onChat: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Device Avatar
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(peer.avatarColorSeed or 0xFF000000.toInt()))
                ) {
                    Text(
                        text = peer.name.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = peer.name,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    TransportBadge(transportType = peer.transportType)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (peer.isConnected) {
                    Button(
                        onClick = onChat,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DarkBackground),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Chat", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = onDisconnect) {
                        Icon(Icons.Default.Close, contentDescription = "Disconnect", tint = AccentRed)
                    }
                } else if (peer.connectionStatus == ConnectionStatus.CONNECTING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = CyanNeon,
                        strokeWidth = 2.dp
                    )
                } else {
                    OutlinedButton(
                        onClick = onConnect,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Connect", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
