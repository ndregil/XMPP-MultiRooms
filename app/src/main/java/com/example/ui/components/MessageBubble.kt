package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DeliveryStatus
import com.example.data.model.MessageType
import com.example.data.model.XmppMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubble(
    message: XmppMessage,
    onSenderClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showCapsDialog by remember { mutableStateOf(false) }

    if (message.messageType == MessageType.SYSTEM_JOIN ||
        message.messageType == MessageType.SYSTEM_LEAVE ||
        message.messageType == MessageType.SYSTEM_TOPIC
    ) {
        SystemMessagePill(message = message, modifier = modifier)
        return
    }

    val isMine = message.isMine
    val isWhisper = message.messageType == MessageType.WHISPER
    val senderColor = getSenderColor(message.senderNickname)

    val bubbleBg = when {
        isWhisper && isMine -> Color(0xFF4C1D95)
        isWhisper && !isMine -> Color(0xFF581C87)
        isMine -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = when {
        isWhisper -> Color(0xFFF3E8FF)
        isMine -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val bubbleShape = if (isMine) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("message_bubble_${message.id}"),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // Other's avatar
        if (!isMine) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(senderColor.copy(alpha = 0.2f))
                    .border(1.dp, senderColor, CircleShape)
                    .clickable { showCapsDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = message.senderNickname.take(1).uppercase(Locale.getDefault()),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = senderColor
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            // Sender name, client caps icon & whisper indicator
            if (!isMine || isWhisper) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 2.dp, start = if (!isMine) 4.dp else 0.dp)
                ) {
                    if (isWhisper) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Private Whisper",
                            tint = Color(0xFFC084FC),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (isMine) "Bisikan ke @${message.recipientNickname ?: "user"}" else "Bisikan dari ${message.senderNickname}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFC084FC)
                        )
                    } else {
                        Text(
                            text = message.senderNickname,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = senderColor,
                            modifier = Modifier.clickable { onSenderClick(message.senderNickname) }
                        )
                    }

                    // Client Caps Icon (Clickable to inspect client capabilities)
                    ClientCapsIcon(
                        platform = message.senderClientCaps.platform,
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .clickable { showCapsDialog = true }
                            .testTag("caps_icon_msg_${message.id}"),
                        contentDescription = "Caps: ${message.senderClientCaps.clientName}"
                    )
                }
            }


            // Message Bubble Box
            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(bubbleBg)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    Text(
                        text = message.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(message.timestamp),
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.7f)
                        )

                        if (isMine) {
                            Spacer(modifier = Modifier.width(4.dp))
                            when (message.status) {
                                DeliveryStatus.SENDING -> Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Mengirim",
                                    tint = textColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(11.dp)
                                )
                                DeliveryStatus.SENT -> Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Terkirim",
                                    tint = textColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(11.dp)
                                )
                                DeliveryStatus.DELIVERED -> Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Sampai",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(12.dp)
                                )
                                DeliveryStatus.FAILED -> Text(
                                    text = "!",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCapsDialog) {
        ClientCapsDialog(
            clientCaps = message.senderClientCaps,
            userName = "${message.senderNickname} (${if (message.isMine) "Kamu" else message.senderJid.ifBlank { "User" }})",
            onDismiss = { showCapsDialog = false }
        )
    }
}


@Composable
fun SystemMessagePill(message: XmppMessage, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = message.body,
                style = MaterialTheme.typography.labelSmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun getSenderColor(nickname: String): Color {
    val palette = listOf(
        Color(0xFF0284C7),
        Color(0xFF0D9488),
        Color(0xFF8B5CF6),
        Color(0xFFD97706),
        Color(0xFFE11D48),
        Color(0xFF059669),
        Color(0xFF7C3AED),
        Color(0xFF2563EB)
    )
    val hash = kotlin.math.abs(nickname.hashCode())
    return palette[hash % palette.size]
}
