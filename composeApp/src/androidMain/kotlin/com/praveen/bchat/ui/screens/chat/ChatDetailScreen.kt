package com.praveen.bchat.ui.screens.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.praveen.bchat.data.local.entities.MessageEntity
import com.praveen.bchat.domain.model.FileTransfer
import com.praveen.bchat.domain.model.MessageStatus
import com.praveen.bchat.domain.model.MessageType
import com.praveen.bchat.domain.model.TransferStatus
import com.praveen.bchat.ui.components.SafetyNumberDialog
import com.praveen.bchat.ui.components.TransferProgressCard
import com.praveen.bchat.ui.theme.*
import com.praveen.bchat.util.FileManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    conversationId: String,
    peerName: String,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val messages by viewModel.getMessagesForConversation(conversationId).collectAsState(initial = emptyList())
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    var showSafetyDialog by remember { mutableStateOf(false) }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.sendFile(conversationId, peerName, uri)
        }
    }

    LaunchedEffect(conversationId) {
        viewModel.markConversationAsRead(conversationId)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            if (conversationId != "mesh_broadcast_group") {
                                showSafetyDialog = true
                            }
                        }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(peerName.hashCode() or 0xFF000000.toInt()))
                        ) {
                            Text(
                                text = peerName.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = peerName,
                                style = Typography.titleMedium,
                                color = TextPrimary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (conversationId != "mesh_broadcast_group") {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Encrypted",
                                        tint = AccentGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "End-to-End Encrypted",
                                        style = Typography.labelSmall,
                                        color = AccentGreen
                                    )
                                } else {
                                    Text(
                                        text = "Mesh Broadcast",
                                        style = Typography.labelSmall,
                                        color = CyanNeon
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (conversationId != "mesh_broadcast_group") {
                        IconButton(onClick = { showSafetyDialog = true }) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = "Verify Encryption", tint = CyanNeon)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Messages stream
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                items(messages, key = { it.messageId }) { message ->
                    val transfer by viewModel.getTransferByMessageId(message.messageId).collectAsState(initial = null)

                    ChatMessageBubble(
                        message = message,
                        transfer = transfer,
                        onOpenFile = {
                            val path = message.localFilePath ?: transfer?.localFilePath
                            if (path != null) {
                                FileManager.openFile(context, File(path))
                            }
                        },
                        onShareFile = {
                            val path = message.localFilePath ?: transfer?.localFilePath
                            if (path != null) {
                                FileManager.shareFile(context, File(path))
                            }
                        }
                    )
                }
            }

            // Chat Input Bar
            Surface(
                color = DarkSurface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    IconButton(
                        onClick = { filePickerLauncher.launch("*/*") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach File",
                            tint = CyanNeon
                        )
                    }

                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Type encrypted message...", color = TextSecondary, fontSize = 14.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceElevated,
                            unfocusedContainerColor = DarkSurfaceElevated,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    )

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(conversationId, inputText.trim(), peerName)
                                inputText = ""
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(CyanNeon)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = DarkBackground,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSafetyDialog) {
        val safetyNumber = viewModel.getPeerSafetyNumber(conversationId)
        val peerPubKey = viewModel.getPeerPublicKey(conversationId)
        SafetyNumberDialog(
            peerName = peerName,
            safetyNumber = safetyNumber,
            peerPublicKey = peerPubKey,
            onDismiss = { showSafetyDialog = false }
        )
    }
}

@Composable
fun ChatMessageBubble(
    message: MessageEntity,
    transfer: com.praveen.bchat.data.local.entities.TransferEntity?,
    onOpenFile: () -> Unit,
    onShareFile: () -> Unit
) {
    val isOutgoing = message.isOutgoing
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }

    val bubbleBg = if (isOutgoing) BlueElectric else DarkSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Surface(
                color = bubbleBg,
                shape = bubbleShape,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    if (message.type == MessageType.FILE || message.type == MessageType.IMAGE) {
                        val activeTransfer = transfer?.let {
                            FileTransfer(
                                id = it.transferId,
                                messageId = it.messageId,
                                peerId = it.peerId,
                                peerName = it.peerName,
                                fileName = it.fileName,
                                fileSize = it.fileSize,
                                bytesTransferred = it.bytesTransferred,
                                mimeType = it.mimeType,
                                localFilePath = it.localFilePath,
                                isIncoming = it.isIncoming,
                                status = it.status,
                                transportType = it.transportType,
                                transferSpeedBytesPerSec = it.transferSpeedBytesPerSec
                            )
                        } ?: FileTransfer(
                            id = message.fileId ?: message.messageId,
                            messageId = message.messageId,
                            peerId = message.senderId,
                            peerName = message.senderName,
                            fileName = message.fileName ?: "File",
                            fileSize = message.fileSize ?: 0L,
                            bytesTransferred = message.fileSize ?: 0L,
                            mimeType = message.mimeType ?: "*/*",
                            localFilePath = message.localFilePath,
                            isIncoming = !isOutgoing,
                            status = if (message.status == MessageStatus.DELIVERED) TransferStatus.COMPLETED else TransferStatus.IN_PROGRESS,
                            transportType = message.transportType
                        )

                        TransferProgressCard(
                            transfer = activeTransfer,
                            onOpen = onOpenFile,
                            onShare = onShareFile
                        )
                    } else {
                        Text(
                            text = message.content,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        if (message.isEncrypted) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Encrypted",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                        }

                        Text(
                            text = formattedTime,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )

                        if (isOutgoing) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = when (message.status) {
                                    MessageStatus.READ -> Icons.Default.DoneAll
                                    MessageStatus.DELIVERED -> Icons.Default.DoneAll
                                    MessageStatus.SENT -> Icons.Default.Done
                                    MessageStatus.SENDING -> Icons.Default.Schedule
                                    MessageStatus.FAILED -> Icons.Default.ErrorOutline
                                },
                                contentDescription = null,
                                tint = if (message.status == MessageStatus.READ) CyanNeon else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
