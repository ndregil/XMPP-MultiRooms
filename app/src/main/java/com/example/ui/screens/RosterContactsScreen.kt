package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.RosterContact
import com.example.data.model.UserPresence
import com.example.data.model.XmppAccount
import com.example.ui.XmppViewModel
import com.example.ui.components.ClientCapsBadge
import com.example.ui.components.ClientCapsDialog
import com.example.ui.components.ClientCapsIcon
import com.example.ui.theme.AwayAmber
import com.example.ui.theme.DndRed
import com.example.ui.theme.OfflineGray
import com.example.ui.theme.OnlineGreen
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RosterContactsScreen(
    viewModel: XmppViewModel,
    modifier: Modifier = Modifier
) {
    val contacts by viewModel.rosterContacts.collectAsStateWithLifecycle()
    val isRosterLoading by viewModel.isRosterLoading.collectAsStateWithLifecycle()
    val currentAccount by viewModel.currentAccount.collectAsStateWithLifecycle()
    val myPresence by viewModel.myPresence.collectAsStateWithLifecycle()
    val myStatusMessage by viewModel.myStatusMessage.collectAsStateWithLifecycle()
    val allRooms by viewModel.allRooms.collectAsStateWithLifecycle()

    var selectedFileTargetJid by remember { mutableStateOf<String?>(null) }
    
    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            // In a real app, we'd need more info, but for this demo:
            viewModel.sendFileToContact(selectedFileTargetJid ?: return@let, it)
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, ONLINE, OFFLINE
    var showAddDialog by remember { mutableStateOf(false) }
    var contactToRename by remember { mutableStateOf<RosterContact?>(null) }
    var contactToDelete by remember { mutableStateOf<RosterContact?>(null) }
    var selectedCapsContact by remember { mutableStateOf<RosterContact?>(null) }

    // Filter contacts based on search and status
    val filteredContacts = remember(contacts, searchQuery, selectedFilter) {
        contacts.filter { contact ->
            val matchesQuery = searchQuery.isBlank() ||
                contact.name.contains(searchQuery, ignoreCase = true) ||
                contact.jid.contains(searchQuery, ignoreCase = true) ||
                contact.groups.any { it.contains(searchQuery, ignoreCase = true) }

            val matchesFilter = when (selectedFilter) {
                "ONLINE" -> contact.presence != UserPresence.OFFLINE
                "OFFLINE" -> contact.presence == UserPresence.OFFLINE
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "XMPP Roster & Kontak",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "${contacts.count { it.presence != UserPresence.OFFLINE }} online dari ${contacts.size} kontak",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshRoster() },
                        modifier = Modifier.testTag("refresh_roster_button")
                    ) {
                        if (isRosterLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Muat Ulang Roster")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_contact_fab")
            ) {
                Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "Tambah Kontak")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // User Presence Status & Broadcast Card (Updates status across all contacts & MUC rooms)
            UserPresenceControlCard(
                currentPresence = myPresence,
                statusMessage = myStatusMessage,
                currentAccount = currentAccount,
                contactsCount = contacts.size,
                roomsCount = allRooms.size,
                onPresenceChange = { newPresence, newStatusMsg ->
                    viewModel.updateMyPresence(newPresence, newStatusMsg)
                },
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )

            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari berdasarkan nama, JID, atau grup...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Hapus teks")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("roster_search_input")
            )

            // Presence Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" },
                        label = { Text("Semua (${contacts.size})") },
                        modifier = Modifier.testTag("filter_all_contacts")
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == "ONLINE",
                        onClick = { selectedFilter = "ONLINE" },
                        label = { Text("Online (${contacts.count { it.presence != UserPresence.OFFLINE }})") },
                        modifier = Modifier.testTag("filter_online_contacts")
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == "OFFLINE",
                        onClick = { selectedFilter = "OFFLINE" },
                        label = { Text("Offline (${contacts.count { it.presence == UserPresence.OFFLINE }})") },
                        modifier = Modifier.testTag("filter_offline_contacts")
                    )
                }
            }

            // Contact List or Empty State
            if (filteredContacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Kontak tidak ditemukan" else "Belum ada kontak di Roster",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Coba kata kunci lain" else "Tekan tombol + untuk menambahkan kontak baru menggunakan bare JID.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .testTag("roster_contact_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredContacts, key = { it.jid }) { contact ->
                        RosterContactCard(
                            contact = contact,
                            onRenameClick = { contactToRename = contact },
                            onDeleteClick = { contactToDelete = contact },
                            onCapsClick = { selectedCapsContact = contact },
                            onCallClick = { viewModel.initiate1on1Call(contact.jid, contact.displayName, "Voice") },
                            onVideoCallClick = { viewModel.initiate1on1Call(contact.jid, contact.displayName, "Video") },
                            onSendFileClick = {
                                selectedFileTargetJid = contact.jid
                                filePickerLauncher.launch("*/*")
                            }
                        )
                    }
                }
            }
        }
    }

    // Client Caps Inspector Dialog
    selectedCapsContact?.let { contact ->
        ClientCapsDialog(
            clientCaps = contact.clientCaps,
            userName = "${contact.displayName} (${contact.jid})",
            onDismiss = { selectedCapsContact = null }
        )
    }

    // Add Contact Dialog
    if (showAddDialog) {
        AddContactDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { jid, name, group ->
                viewModel.addRosterContact(jid, name, group)
                showAddDialog = false
            }
        )
    }

    // Rename Contact Dialog
    contactToRename?.let { contact ->
        RenameContactDialog(
            currentContact = contact,
            onDismiss = { contactToRename = null },
            onConfirm = { newName ->
                viewModel.renameRosterContact(contact.jid, newName)
                contactToRename = null
            }
        )
    }

    // Delete Confirmation Dialog
    contactToDelete?.let { contact ->
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            title = { Text("Hapus Kontak Roster?") },
            text = { Text("Apakah Anda yakin ingin menghapus '${contact.displayName}' (${contact.jid}) dari daftar kontak XMPP?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeRosterContact(contact.jid)
                        contactToDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_contact_button")
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { contactToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun RosterContactCard(
    contact: RosterContact,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCapsClick: () -> Unit = {},
    onCallClick: () -> Unit,
    onVideoCallClick: () -> Unit,
    onSendFileClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val presenceColor = when (contact.presence) {
        UserPresence.ONLINE, UserPresence.CHAT -> OnlineGreen
        UserPresence.AWAY -> AwayAmber
        UserPresence.DND -> DndRed
        UserPresence.OFFLINE -> OfflineGray
    }

    val presenceLabel = when (contact.presence) {
        UserPresence.ONLINE -> "Online"
        UserPresence.CHAT -> "Free to Chat"
        UserPresence.AWAY -> "Away"
        UserPresence.DND -> "Do Not Disturb"
        UserPresence.OFFLINE -> "Offline"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("contact_item_${contact.jid}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with Presence indicator
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contact.displayName.take(2).uppercase(Locale.getDefault()),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 16.sp
                    )
                }

                // Presence Dot
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(presenceColor)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details Column
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = contact.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = presenceColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = presenceLabel,
                            color = presenceColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = contact.jid,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Client Caps Icon & Badge (Clickable to view full capabilities)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 3.dp)
                ) {
                    ClientCapsBadge(
                        clientCaps = contact.clientCaps,
                        onClick = onCapsClick
                    )
                    Text(
                        text = contact.clientCaps.version,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }

                if (contact.statusMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "\"${contact.statusMessage}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Groups chips
                if (contact.groups.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        contact.groups.take(3).forEach { groupName ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = groupName,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quick Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onCallClick,
                    enabled = contact.presence != UserPresence.OFFLINE,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Panggil Suara",
                        tint = if (contact.presence != UserPresence.OFFLINE) OnlineGreen else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onVideoCallClick,
                    enabled = contact.presence != UserPresence.OFFLINE,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Panggil Video",
                        tint = if (contact.presence != UserPresence.OFFLINE) OnlineGreen else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onSendFileClick,
                    enabled = contact.presence != UserPresence.OFFLINE,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Kirim File",
                        tint = if (contact.presence != UserPresence.OFFLINE) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // More Options Menu
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("contact_options_${contact.jid}")
                ) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Opsi kontak")
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Lihat Caps & Klien") },
                        leadingIcon = {
                            ClientCapsIcon(
                                platform = contact.clientCaps.platform,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onCapsClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Ubah Nama") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRenameClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus Kontak") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDeleteClick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AddContactDialog(
    onDismiss: () -> Unit,
    onConfirm: (jid: String, name: String, group: String?) -> Unit
) {
    var jidInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var groupInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Kontak XMPP Baru") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Masukkan bare JID kontak (misal: user@xmpp.chat.org) untuk menambahkan ke Roster Smack.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = jidInput,
                    onValueChange = {
                        jidInput = it
                        errorMessage = null
                    },
                    label = { Text("Bare JID *") },
                    placeholder = { Text("teman@xmpp.org") },
                    singleLine = true,
                    isError = errorMessage != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_contact_jid_input")
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Nama Kontak (Opsional)") },
                    placeholder = { Text("Nama panggilan") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_contact_name_input")
                )

                OutlinedTextField(
                    value = groupInput,
                    onValueChange = { groupInput = it },
                    label = { Text("Grup (Opsional)") },
                    placeholder = { Text("misal: Teman, Kerja") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_contact_group_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanJid = jidInput.trim()
                    if (cleanJid.isBlank() || !cleanJid.contains("@")) {
                        errorMessage = "Format JID tidak valid. Harus menyertakan username@domain"
                    } else {
                        onConfirm(cleanJid, nameInput.trim(), groupInput.trim().ifBlank { null })
                    }
                },
                modifier = Modifier.testTag("submit_add_contact_button")
            ) {
                Text("Tambah Kontak")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun RenameContactDialog(
    currentContact: RosterContact,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit
) {
    var newNameInput by remember { mutableStateOf(currentContact.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubah Nama Kontak") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "JID: ${currentContact.jid}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = newNameInput,
                    onValueChange = { newNameInput = it },
                    label = { Text("Nama Tampilan Baru") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_contact_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newNameInput.isNotBlank()) {
                        onConfirm(newNameInput.trim())
                    }
                },
                modifier = Modifier.testTag("confirm_rename_contact_button")
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPresenceControlCard(
    currentPresence: UserPresence,
    statusMessage: String,
    currentAccount: XmppAccount,
    contactsCount: Int,
    roomsCount: Int,
    onPresenceChange: (UserPresence, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var customMessageInput by remember(statusMessage) { mutableStateOf(statusMessage) }
    var updateFeedback by remember { mutableStateOf<String?>(null) }

    val presenceColor = when (currentPresence) {
        UserPresence.ONLINE -> OnlineGreen
        UserPresence.CHAT -> Color(0xFF0D9488)
        UserPresence.AWAY -> AwayAmber
        UserPresence.DND -> DndRed
        UserPresence.OFFLINE -> OfflineGray
    }

    val presenceTitle = when (currentPresence) {
        UserPresence.ONLINE -> "Online"
        UserPresence.CHAT -> "Free to Chat"
        UserPresence.AWAY -> "Away"
        UserPresence.DND -> "Do Not Disturb"
        UserPresence.OFFLINE -> "Offline"
    }

    val presenceOptions = listOf(
        Triple(UserPresence.ONLINE, "Online", OnlineGreen),
        Triple(UserPresence.CHAT, "Chat", Color(0xFF0D9488)),
        Triple(UserPresence.AWAY, "Away", AwayAmber),
        Triple(UserPresence.DND, "DND", DndRed),
        Triple(UserPresence.OFFLINE, "Offline", OfflineGray)
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("user_presence_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            presenceColor.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Avatar, Name, Presence Badge, Expand Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User Avatar with Presence dot
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentAccount.nickname.take(2).uppercase(Locale.getDefault()),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 16.sp
                        )
                    }

                    // Presence indicator dot
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = currentAccount.nickname,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = presenceColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = presenceTitle,
                                color = presenceColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = if (statusMessage.isNotBlank()) "\"$statusMessage\"" else "Tidak ada pesan status",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "📡 Disiarkan ke $contactsCount kontak & $roomsCount ruang MUC",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.testTag("toggle_presence_edit_btn")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Buka pengaturan status",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Presence Option Selector Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("presence_selector_row"),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(presenceOptions) { (presence, label, color) ->
                    val isSelected = currentPresence == presence
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onPresenceChange(presence, customMessageInput)
                            updateFeedback = "Status disiarkan: $label"
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("presence_select_${label.lowercase(Locale.getDefault())}")
                    )
                }
            }

            // Expandable Custom Status Message Input & Quick Presets
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Text(
                        text = "Pesan Status Pribadi XMPP:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = customMessageInput,
                        onValueChange = { customMessageInput = it },
                        placeholder = { Text("Tulis pesan status Anda...", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    onPresenceChange(currentPresence, customMessageInput)
                                    updateFeedback = "Status pesan berhasil diperbarui & disiarkan!"
                                },
                                modifier = Modifier.testTag("apply_presence_status_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Terapkan status",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_status_message_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset Chips
                    Text(
                        text = "Pilih Preset Cepat:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val presets = listOf(
                        "Tersedia untuk obrolan 👋",
                        "Sedang fokus coding 💻",
                        "Sedang meeting 📞",
                        "Makan siang 🥪",
                        "AFK sebentar ⏳",
                        "Jangan diganggu ⛔"
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(presets) { preset ->
                            FilterChip(
                                selected = customMessageInput == preset,
                                onClick = {
                                    customMessageInput = preset
                                    onPresenceChange(currentPresence, preset)
                                    updateFeedback = "Status disiarkan: \"$preset\""
                                },
                                label = { Text(preset, fontSize = 10.sp) },
                                modifier = Modifier.testTag("preset_${preset.take(8).trim()}")
                            )
                        }
                    }
                }
            }

            // Brief Feedback Message
            updateFeedback?.let { feedback ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = OnlineGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = feedback,
                        fontSize = 11.sp,
                        color = OnlineGreen,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

