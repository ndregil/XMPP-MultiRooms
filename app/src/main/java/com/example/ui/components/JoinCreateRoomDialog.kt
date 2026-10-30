package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun JoinCreateRoomDialog(
    defaultNickname: String,
    onDismiss: () -> Unit,
    onJoinOrCreate: (roomJid: String, roomName: String, nickname: String, topic: String, password: String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Buat Room, 1 = Gabung Room
    var roomName by remember { mutableStateOf("") }
    var roomJid by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf(defaultNickname) }
    var topic by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (selectedTab == 0) Icons.Default.Add else Icons.Default.GroupAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (selectedTab == 0) "Buat Ruang Obrolan Baru" else "Gabung Ruang MUC")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Buat Room") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Gabung Room") }
                    )
                }

                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = roomName,
                        onValueChange = {
                            roomName = it
                            if (roomJid.isBlank() || roomJid.endsWith("@conference.xmpp.today")) {
                                val clean = it.lowercase().replace(" ", "-").replace("[^a-z0-9-]".toRegex(), "")
                                roomJid = if (clean.isNotBlank()) "$clean@conference.xmpp.today" else ""
                            }
                        },
                        label = { Text("Nama Ruangan (Contoh: Mobile Dev)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_room_name"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = roomJid,
                        onValueChange = { roomJid = it },
                        label = { Text("JID Ruangan (MUC Conference)") },
                        placeholder = { Text("room-name@conference.domain.org") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_room_jid"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = topic,
                        onValueChange = { topic = it },
                        label = { Text("Topik Ruangan (Opsional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = roomJid,
                        onValueChange = { roomJid = it },
                        label = { Text("JID Ruang Obrolan") },
                        placeholder = { Text("contoh: lounge@conference.xmpp.today") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_room_jid"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Rekomendasi server MUC publik:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("cyber-hub", "developer", "komunitas").forEach { preset ->
                            FilterChip(
                                selected = roomJid.startsWith(preset),
                                onClick = {
                                    roomJid = "$preset@conference.xmpp.today"
                                    roomName = "# " + preset.replaceFirstChar { it.uppercase() }
                                },
                                label = { Text(preset, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Nickname Anda di Room") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Kata Sandi Room (Jika Room Privat)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (roomJid.isNotBlank()) {
                        val finalName = if (roomName.isNotBlank()) roomName else "# " + roomJid.substringBefore("@")
                        onJoinOrCreate(roomJid.trim(), finalName.trim(), nickname.trim(), topic.trim(), password.trim())
                    }
                },
                enabled = roomJid.isNotBlank(),
                modifier = Modifier.testTag("btn_confirm_room_dialog")
            ) {
                Text(if (selectedTab == 0) "Buat & Masuk" else "Gabung Room")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
