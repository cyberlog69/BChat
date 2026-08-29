package com.praveen.bchat.ui.screens.transfers

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.praveen.bchat.domain.model.FileTransfer
import com.praveen.bchat.domain.model.TransferStatus
import com.praveen.bchat.ui.components.TransferProgressCard
import com.praveen.bchat.ui.theme.*
import com.praveen.bchat.util.FileManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(
    onNavigateToChat: (String, String) -> Unit,
    viewModel: TransfersViewModel = viewModel()
) {
    val context = LocalContext.current
    val transfers by viewModel.allTransfers.collectAsState(initial = emptyList())
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Active & History, 1 = Received Files

    var receivedFiles by remember { mutableStateOf(viewModel.getReceivedFiles()) }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            receivedFiles = viewModel.getReceivedFiles()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Turbo File Sharing", style = Typography.headlineMedium, color = TextPrimary)
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
        ) {
            // Speed Highlight Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(CyanNeon.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            tint = CyanNeon,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Ultra-Fast Offline Sharing",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Nearby Share & Wi-Fi Direct speeds up to 40+ MB/s",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = DarkSurface,
                contentColor = CyanNeon,
                divider = {},
                indicator = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "Transfers (${transfers.size})",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) CyanNeon else TextSecondary
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "Saved Files (${receivedFiles.size})",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) CyanNeon else TextSecondary
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                if (transfers.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SwapVert,
                                contentDescription = null,
                                tint = TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No file transfers yet",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Send photos, videos, apps or docs to any connected peer",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(transfers, key = { it.transferId }) { transfer ->
                            val model = FileTransfer(
                                id = transfer.transferId,
                                messageId = transfer.messageId,
                                peerId = transfer.peerId,
                                peerName = transfer.peerName,
                                fileName = transfer.fileName,
                                fileSize = transfer.fileSize,
                                bytesTransferred = transfer.bytesTransferred,
                                mimeType = transfer.mimeType,
                                localFilePath = transfer.localFilePath,
                                isIncoming = transfer.isIncoming,
                                status = transfer.status,
                                transportType = transfer.transportType,
                                transferSpeedBytesPerSec = transfer.transferSpeedBytesPerSec
                            )

                            TransferProgressCard(
                                transfer = model,
                                onOpen = {
                                    if (transfer.localFilePath != null) {
                                        FileManager.openFile(context, File(transfer.localFilePath))
                                    }
                                },
                                onShare = {
                                    if (transfer.localFilePath != null) {
                                        FileManager.shareFile(context, File(transfer.localFilePath))
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                // Saved Files library
                if (receivedFiles.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Text("No received files in BChat directory", color = TextSecondary)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(receivedFiles, key = { it.absolutePath }) { file ->
                            SavedFileItem(
                                file = file,
                                onOpen = { FileManager.openFile(context, file) },
                                onShare = { FileManager.shareFile(context, file) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SavedFileItem(
    file: File,
    onOpen: () -> Unit,
    onShare: () -> Unit
) {
    val mimeType = remember(file.name) { FileManager.getMimeTypeFromFileName(file.name) }
    val formattedDate = remember(file.lastModified()) {
        SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date(file.lastModified()))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = if (mimeType.startsWith("image/")) Icons.Default.Image
                else if (mimeType.startsWith("video/")) Icons.Default.VideoFile
                else if (mimeType.startsWith("audio/")) Icons.Default.AudioFile
                else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = CyanNeon,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${FileManager.formatFileSize(file.length())} • $formattedDate",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = TextSecondary)
            }
        }
    }
}
