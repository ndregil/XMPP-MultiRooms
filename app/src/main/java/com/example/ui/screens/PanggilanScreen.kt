package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallInfo
import com.example.data.model.CallState
import com.example.ui.XmppViewModel
import com.example.ui.theme.DndRed
import com.example.ui.theme.OnlineGreen

@Composable
fun PanggilanOverlay(viewModel: XmppViewModel) {
    val currentCall by viewModel.currentCall.collectAsState()

    AnimatedVisibility(
        visible = currentCall != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        currentCall?.let { call ->
            PanggilanScreen(
                call = call,
                onAccept = { viewModel.acceptCall() },
                onReject = { viewModel.rejectCall() },
                onEnd = { viewModel.endCall() },
                onToggleMute = { viewModel.toggleMute() },
                onToggleVideo = { viewModel.toggleVideo() }
            )
        }
    }
}

@Composable
fun PanggilanScreen(
    call: CallInfo,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF1A1A1A) // Dark background for calls
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Call Info Top
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Surface(
                    modifier = Modifier.size(100.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = call.remoteNickname.take(1).uppercase(),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = call.remoteNickname,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = call.remoteJid,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                val statusText = when (call.state) {
                    CallState.OUTGOING_RINGING -> "Menghubungi..."
                    CallState.INCOMING_RINGING -> "Panggilan Masuk (${call.callType})"
                    CallState.CONNECTED -> formatDuration(call.durationSeconds)
                    CallState.ENDED -> "Panggilan Berakhir"
                    CallState.IDLE -> ""
                }
                
                Text(
                    text = statusText,
                    color = if (call.state == CallState.CONNECTED) OnlineGreen else Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Video Preview Placeholder (If Video Call)
            if (call.callType == "Video" && call.state == CallState.CONNECTED) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .background(Color.Black, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (call.isVideoEnabled) {
                        Text("Pratinjau Video Aktif", color = Color.White.copy(alpha = 0.4f))
                    } else {
                        Icon(
                            Icons.Default.VideocamOff,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // Controls Bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (call.state == CallState.INCOMING_RINGING) {
                    CallActionButton(
                        icon = Icons.Default.Close,
                        color = DndRed,
                        onClick = onReject,
                        label = "Tolak"
                    )
                    
                    CallActionButton(
                        icon = if (call.callType == "Video") Icons.Default.VideoCall else Icons.Default.Call,
                        color = OnlineGreen,
                        onClick = onAccept,
                        label = "Terima"
                    )
                } else {
                    CallActionButton(
                        icon = if (call.isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        color = if (call.isAudioMuted) Color.Gray else Color.White.copy(alpha = 0.2f),
                        onClick = onToggleMute,
                        label = "Mute"
                    )
                    
                    CallActionButton(
                        icon = Icons.Default.CallEnd,
                        color = DndRed,
                        onClick = onEnd,
                        label = "Akhiri"
                    )
                    
                    if (call.callType == "Video") {
                        CallActionButton(
                            icon = if (call.isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            color = if (call.isVideoEnabled) Color.White.copy(alpha = 0.2f) else Color.Gray,
                            onClick = onToggleVideo,
                            label = "Video"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CallActionButton(
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(color)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (color == Color.White.copy(alpha = 0.2f)) Color.White else Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, color = Color.White, fontSize = 12.sp)
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}
