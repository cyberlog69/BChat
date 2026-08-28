package com.praveen.bchat.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.praveen.bchat.data.local.entities.ConversationEntity
import com.praveen.bchat.domain.model.PeerDevice
import com.praveen.bchat.ui.components.TransportBadge
import com.praveen.bchat.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onNavigateToChat: (String, String) -> Unit,
    onNavigateToDiscover: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val conversations by viewModel.conversations.collectAsState(initial = emptyList())
    val connectedPeers by viewModel.connectedPeers.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Messages", style = Typography.headlineMedium, color = TextPrimary)
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
            // Connected Peers Horizontal Bar
            if (connectedPeers.isNotEmpty()) {
                Text(
                    text = "Connected Peers (${connectedPeers.size})",
                    style = Typography.titleMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(connectedPeers, key = { it.id }) { peer ->
                        ConnectedPeerAvatar(
                            peer = peer,
                            onClick = { onNavigateToChat(peer.id, peer.name) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Mesh Broadcast Channel Card
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToChat("mesh_broadcast_group", "📢 Mesh Broadcast") }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(PurpleNeon)
                    ) {
                        Icon(Icons.Default.Campaign, contentDescription = null, tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "📢 Mesh Broadcast Group",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Broadcast message to all connected peers",
                            color = CyanNeon,
                            fontSize = 12.sp
                        )
                    }

                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Conversations",
                style = Typography.titleMedium,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (conversations.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = TextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No offline conversations yet",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Connect with nearby peers to start chatting",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateToDiscover,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DarkBackground)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Discover Peers", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(conversations, key = { it.conversationId }) { conv ->
                        ConversationItem(
                            conversation = conv,
                            onClick = { onNavigateToChat(conv.conversationId, conv.peerName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectedPeerAvatar(
    peer: PeerDevice,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .width(64.dp)
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
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
            // Online green indicator dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(AccentGreen)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = peer.name,
            color = TextPrimary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ConversationItem(
    conversation: ConversationEntity,
    onClick: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val formattedTime = remember(conversation.lastMessageTimestamp) {
        timeFormat.format(Date(conversation.lastMessageTimestamp))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(conversation.peerName.hashCode() or 0xFF000000.toInt()))
            ) {
                Text(
                    text = conversation.peerName.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = conversation.peerName,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formattedTime,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = conversation.lastMessageText,
                        color = if (conversation.unreadCount > 0) TextPrimary else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (conversation.unreadCount > 0) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(CyanNeon)
                        ) {
                            Text(
                                text = conversation.unreadCount.toString(),
                                color = DarkBackground,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        TransportBadge(transportType = conversation.preferredTransport)
                    }
                }
            }
        }
    }
}
