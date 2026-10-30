package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OccupantRole
import com.example.data.model.RoomOccupant
import com.example.data.model.UserPresence
import com.example.ui.theme.AwayAmber
import com.example.ui.theme.DndRed
import com.example.ui.theme.OfflineGray
import com.example.ui.theme.OnlineGreen
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OccupantRosterSheet(
    occupants: List<RoomOccupant>,
    roomName: String,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onWhisperClick: (RoomOccupant) -> Unit,
    onMentionClick: (RoomOccupant) -> Unit
) {
    var selectedCapsOccupant by remember { mutableStateOf<RoomOccupant?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Anggota Ruangan ($roomName)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                }
            }

            Text(
                text = "${occupants.size} Partisipan aktif di room MUC ini (klik ikon caps klien untuk detail)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .padding(vertical = 8.dp)
            ) {
                items(occupants) { occupant ->
                    OccupantItemRow(
                        occupant = occupant,
                        onWhisper = { onWhisperClick(occupant) },
                        onMention = { onMentionClick(occupant) },
                        onViewCaps = { selectedCapsOccupant = occupant }
                    )
                }
            }
        }
    }

    selectedCapsOccupant?.let { occupant ->
        ClientCapsDialog(
            clientCaps = occupant.clientCaps,
            userName = "${occupant.nickname} (${occupant.jid.ifBlank { "MUC Occupant" }})",
            onDismiss = { selectedCapsOccupant = null }
        )
    }
}

@Composable
fun OccupantItemRow(
    occupant: RoomOccupant,
    onWhisper: () -> Unit,
    onMention: () -> Unit,
    onViewCaps: () -> Unit = {}
) {
    val presenceColor = when (occupant.presence) {
        UserPresence.ONLINE, UserPresence.CHAT -> OnlineGreen
        UserPresence.AWAY -> AwayAmber
        UserPresence.DND -> DndRed
        UserPresence.OFFLINE -> OfflineGray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("occupant_${occupant.nickname}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar + presence status dot
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = occupant.nickname.take(2).uppercase(Locale.getDefault()),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(presenceColor)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = occupant.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (occupant.isMe) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(Kamu)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Role badge
                Spacer(modifier = Modifier.width(6.dp))
                when (occupant.role) {
                    OccupantRole.OWNER -> {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFEF3C7))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(text = "Owner", fontSize = 10.sp, color = Color(0xFF92400E), fontWeight = FontWeight.Bold)
                        }
                    }
                    OccupantRole.ADMIN -> {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFE0E7FF))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(text = "Admin", fontSize = 10.sp, color = Color(0xFF3730A3), fontWeight = FontWeight.Bold)
                        }
                    }
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Client Caps Badge (Clickable to view XEP-0115 info)
                ClientCapsBadge(
                    clientCaps = occupant.clientCaps,
                    onClick = onViewCaps
                )

                Text(
                    text = occupant.statusText.ifBlank {
                        when (occupant.presence) {
                            UserPresence.ONLINE, UserPresence.CHAT -> "Online"
                            UserPresence.AWAY -> "Away"
                            UserPresence.DND -> "DND"
                            UserPresence.OFFLINE -> "Offline"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }


        if (!occupant.isMe) {
            // Mention button
            IconButton(
                onClick = onMention,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AlternateEmail,
                    contentDescription = "Mention @${occupant.nickname}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Whisper private chat button
            IconButton(
                onClick = onWhisper,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Bisikan ke ${occupant.nickname}",
                    tint = Color(0xFFC084FC),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
