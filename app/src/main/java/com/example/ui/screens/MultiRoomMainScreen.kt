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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.data.model.CallInfo
import com.example.data.model.ConnectionStatus
import com.example.data.model.RoomOccupant
import com.example.data.model.UserPresence
import com.example.data.model.XmppRoom
import com.example.ui.XmppViewModel
import com.example.ui.components.ClientCapsBadge
import com.example.ui.components.ClientCapsDialog
import com.example.ui.components.ClientCapsIcon
import com.example.ui.components.getClientPlatformColor
import com.example.data.network.fcm.FcmPushManager
import com.example.ui.components.JoinCreateRoomDialog
import com.example.ui.components.MessageBubble
import com.example.ui.components.OccupantRosterSheet
import com.example.ui.components.RoomItemCard
import com.example.ui.components.RoomTopicBanner
import com.example.ui.components.StanzaConsoleSheet
import com.example.ui.theme.AwayAmber
import com.example.ui.theme.DndRed
import com.example.ui.theme.OfflineGray
import com.example.ui.theme.OnlineGreen
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiRoomMainScreen(
    viewModel: XmppViewModel,
    modifier: Modifier = Modifier
) {
    var activeNavIndex by remember { mutableIntStateOf(0) } // 0 = Rooms Chat, 1 = Roster Contacts, 2 = Directory, 3 = Account Settings
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showRoomMenu by remember { mutableStateOf(false) }
    var selectedCapsOccupant by remember { mutableStateOf<RoomOccupant?>(null) }

    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val currentAccount by viewModel.currentAccount.collectAsStateWithLifecycle()
    val allRooms by viewModel.allRooms.collectAsStateWithLifecycle()
    val filteredRooms by viewModel.filteredRooms.collectAsStateWithLifecycle()
    val activeRoom by viewModel.activeRoom.collectAsStateWithLifecycle()
    val messages by viewModel.activeRoomMessages.collectAsStateWithLifecycle()
    val occupants by viewModel.activeRoomOccupants.collectAsStateWithLifecycle()
    val stanzaLogs by viewModel.stanzaLogs.collectAsStateWithLifecycle()
    val directoryRooms by viewModel.directoryRooms.collectAsStateWithLifecycle()
    val isDirectoryLoading by viewModel.isDirectoryLoading.collectAsStateWithLifecycle()
    val whisperTarget by viewModel.whisperTargetOccupant.collectAsStateWithLifecycle()
    val currentInput by viewModel.currentInputText.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val isRosterOpen by viewModel.isRosterSheetOpen.collectAsStateWithLifecycle()
    val isStanzaConsoleOpen by viewModel.isStanzaConsoleOpen.collectAsStateWithLifecycle()
    val isJoinCreateOpen by viewModel.isJoinCreateDialogOpen.collectAsStateWithLifecycle()

    val rosterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val stanzaSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val filePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val cursor = context.contentResolver.query(it, null, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    val name = if (nameIndex >= 0) c.getString(nameIndex) else "file"
                    val size = if (sizeIndex >= 0) c.getLong(sizeIndex) else 0L
                    val mimeType = context.contentResolver.getType(it) ?: "application/octet-stream"
                    viewModel.sendFile(it, name, size, mimeType)
                }
            }
        }
    }

    // Auto scroll to latest message when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Keep active room in sync with FcmPushManager to suppress redundant notifications
    LaunchedEffect(activeNavIndex, activeRoom?.jid) {
        FcmPushManager.activeRoomJid = if (activeNavIndex == 0) activeRoom?.jid else null
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = activeNavIndex == 0,
                    onClick = { activeNavIndex = 0 },
                    icon = {
                        val totalUnread = allRooms.sumOf { it.unreadCount }
                        if (totalUnread > 0) {
                            BadgedBox(badge = { Badge { Text("$totalUnread") } }) {
                                Icon(Icons.Default.Forum, contentDescription = "Obrolan Room")
                            }
                        } else {
                            Icon(Icons.Default.Forum, contentDescription = "Obrolan Room")
                        }
                    },
                    label = { Text("Multi-Room") },
                    modifier = Modifier.testTag("nav_tab_rooms")
                )

                NavigationBarItem(
                    selected = activeNavIndex == 1,
                    onClick = {
                        activeNavIndex = 1
                        viewModel.refreshRoster()
                    },
                    icon = { Icon(Icons.Default.Contacts, contentDescription = "Roster Kontak") },
                    label = { Text("Kontak") },
                    modifier = Modifier.testTag("nav_tab_roster")
                )

                NavigationBarItem(
                    selected = activeNavIndex == 2,
                    onClick = {
                        activeNavIndex = 2
                        viewModel.loadDirectory()
                    },
                    icon = { Icon(Icons.Default.Explore, contentDescription = "Eksplorasi") },
                    label = { Text("Discover") },
                    modifier = Modifier.testTag("nav_tab_discover")
                )

                NavigationBarItem(
                    selected = activeNavIndex == 3,
                    onClick = { activeNavIndex = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Pengaturan") },
                    label = { Text("Akun & Server") },
                    modifier = Modifier.testTag("nav_tab_account")
                )
            }
        },
        topBar = {
            if (activeNavIndex == 0) {
                TopAppBar(
                    title = {
                        if (isSearchExpanded) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                placeholder = { Text("Cari ruangan atau topik...", fontSize = 14.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_rooms_input"),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        viewModel.updateSearchQuery("")
                                        isSearchExpanded = false
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Tutup Pencarian")
                                    }
                                }
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Connection Status Dot
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (connectionStatus) {
                                                ConnectionStatus.CONNECTED -> OnlineGreen
                                                ConnectionStatus.CONNECTING, ConnectionStatus.AUTHENTICATING -> AwayAmber
                                                ConnectionStatus.ERROR -> DndRed
                                                ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Column {
                                    Text(
                                        text = activeRoom?.name ?: "XMPP MultiRoom",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${occupants.size} online • ${activeRoom?.jid ?: currentAccount.domain}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (!isSearchExpanded) {
                            IconButton(onClick = { isSearchExpanded = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Cari Room")
                            }

                            // Roster Sheet button
                            IconButton(
                                onClick = { viewModel.isRosterSheetOpen.value = true },
                                modifier = Modifier.testTag("btn_open_roster")
                            ) {
                                BadgedBox(badge = {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ) {
                                        Text("${occupants.size}")
                                    }
                                }) {
                                    Icon(Icons.Default.Group, contentDescription = "Daftar Anggota")
                                }
                            }

                            // Stanza Console Shortcut
                            IconButton(
                                onClick = { viewModel.isStanzaConsoleOpen.value = true },
                                modifier = Modifier.testTag("btn_open_stanzas")
                            ) {
                                Icon(Icons.Default.Code, contentDescription = "Stanza XML Stream", tint = MaterialTheme.colorScheme.primary)
                            }

                            // More options
                            IconButton(onClick = { showRoomMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                            }

                            DropdownMenu(
                                expanded = showRoomMenu,
                                onDismissRequest = { showRoomMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Buat / Gabung Room Baru") },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                    onClick = {
                                        showRoomMenu = false
                                        viewModel.isJoinCreateDialogOpen.value = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Ubah Topik Room") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showRoomMenu = false
                                        activeRoom?.let { viewModel.isTopicModalOpen.value = true }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Tinggalkan Room Ini") },
                                    leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
                                    onClick = {
                                        showRoomMenu = false
                                        activeRoom?.let { viewModel.leaveRoom(it.jid) }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Bersihkan Riwayat Chat") },
                                    leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null) },
                                    onClick = {
                                        showRoomMenu = false
                                        activeRoom?.let { viewModel.clearRoomChatHistory(it.jid) }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Hapus Room dari Daftar") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showRoomMenu = false
                                        activeRoom?.let { viewModel.deleteRoom(it.jid) }
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (activeNavIndex) {
                0 -> {
                    // Multi-Room Chat View with dynamic horizontal multi-room switcher tabs
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Multi-Room Channel Switcher Tabs
                        MultiRoomTabHeader(
                            rooms = filteredRooms,
                            selectedRoomJid = activeRoom?.jid,
                            onSelectRoom = { jid -> viewModel.selectRoom(jid) },
                            onAddRoomClick = { viewModel.isJoinCreateDialogOpen.value = true }
                        )

                        // Active room topic banner
                        activeRoom?.let { room ->
                            RoomTopicBanner(
                                topic = room.topic,
                                roomJid = room.jid,
                                onTopicChange = { newTopic -> viewModel.updateTopic(newTopic) }
                            )
                        }

                        // Room Occupants Caps Strip (shows caps client icons of all active occupants in room)
                        if (occupants.isNotEmpty()) {
                            RoomOccupantsCapsBar(
                                occupants = occupants,
                                onOccupantClick = { occ -> selectedCapsOccupant = occ },
                                onOpenRosterClick = { viewModel.isRosterSheetOpen.value = true }
                            )
                        }

                        // Message Feed
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            if (messages.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Forum,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Belum ada pesan di ruang ${activeRoom?.name ?: ""}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Kirim pesan pertama untuk memulai obrolan dengan seluruh partisipan.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    items(messages, key = { it.id }) { msg ->
                                        MessageBubble(
                                            message = msg,
                                            onSenderClick = { senderNick ->
                                                val found = occupants.find { it.nickname == senderNick }
                                                viewModel.setWhisperTarget(found)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Whisper Target Banner (if whispering)
                        AnimatedVisibility(visible = whisperTarget != null) {
                            whisperTarget?.let { target ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF4C1D95).copy(alpha = 0.8f))
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = Color(0xFFE9D5FF),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        ClientCapsIcon(
                                            platform = target.clientCaps.platform,
                                            tint = Color(0xFFE9D5FF),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { selectedCapsOccupant = target }
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Mode Bisikan ke @${target.nickname} (${target.clientCaps.clientName})",
                                            color = Color(0xFFF3E8FF),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.setWhisperTarget(null) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Batalkan Bisikan",
                                            tint = Color(0xFFF3E8FF),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Quick action mention shortcuts
                        if (occupants.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item {
                                    Text(
                                        text = "Mention:",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                items(occupants.take(6)) { occ ->
                                    if (!occ.isMe) {
                                        FilterChip(
                                            selected = false,
                                            onClick = {
                                                viewModel.currentInputText.value = "${viewModel.currentInputText.value} @${occ.nickname} ".trimStart()
                                            },
                                            label = { Text("@${occ.nickname}", fontSize = 10.sp) }
                                        )
                                    }
                                }
                            }
                        }

                        // Input Bar
                        ChatInputBar(
                            inputText = currentInput,
                            onInputChange = { viewModel.currentInputText.value = it },
                            onSendMessage = { viewModel.sendMessage(it) },
                            onAttachClick = { filePicker.launch("*/*") },
                            onCallClick = { viewModel.initiateCall(it) },
                            whisperTarget = whisperTarget
                        )
                    }
                }

                1 -> {
                    // XMPP Roster & Contacts
                    RosterContactsScreen(
                        viewModel = viewModel
                    )
                }

                2 -> {
                    // Public MUC Directory
                    RoomDirectoryScreen(
                        directoryRooms = directoryRooms,
                        joinedRooms = allRooms,
                        isLoading = isDirectoryLoading,
                        onRefresh = { viewModel.loadDirectory() },
                        onJoinRoom = { dirRoom ->
                            viewModel.joinOrCreateRoom(
                                roomJid = dirRoom.jid,
                                roomName = dirRoom.name,
                                nickname = currentAccount.nickname,
                                topic = dirRoom.description
                            )
                            activeNavIndex = 0
                        },
                        onSelectJoinedRoom = { jid ->
                            viewModel.selectRoom(jid)
                            activeNavIndex = 0
                        }
                    )
                }

                3 -> {
                    // Account & Connection Settings
                    AccountConnectionScreen(
                        currentAccount = currentAccount,
                        connectionStatus = connectionStatus,
                        onConnect = { acc, pwd -> viewModel.connectAccount(acc, pwd) },
                        onDisconnect = { viewModel.disconnect() }
                    )
                }
            }
        }
    }

    // Modal Bottom Sheets & Dialogs
    if (isRosterOpen) {
        OccupantRosterSheet(
            occupants = occupants,
            roomName = activeRoom?.name ?: "Room",
            sheetState = rosterSheetState,
            onDismiss = { viewModel.isRosterSheetOpen.value = false },
            onWhisperClick = { occ -> viewModel.setWhisperTarget(occ) },
            onMentionClick = { occ ->
                viewModel.currentInputText.value = "${viewModel.currentInputText.value} @${occ.nickname} ".trimStart()
                viewModel.isRosterSheetOpen.value = false
            }
        )
    }

    if (isStanzaConsoleOpen) {
        StanzaConsoleSheet(
            logs = stanzaLogs,
            sheetState = stanzaSheetState,
            onDismiss = { viewModel.isStanzaConsoleOpen.value = false },
            onSendRawStanza = { xml -> viewModel.sendRawXmlStanza(xml) }
        )
    }

    if (isJoinCreateOpen) {
        JoinCreateRoomDialog(
            defaultNickname = currentAccount.nickname,
            onDismiss = { viewModel.isJoinCreateDialogOpen.value = false },
            onJoinOrCreate = { jid, name, nick, topic, pwd ->
                viewModel.joinOrCreateRoom(jid, name, nick, topic, pwd)
                activeNavIndex = 0
            }
        )
    }

    selectedCapsOccupant?.let { occ ->
        ClientCapsDialog(
            clientCaps = occ.clientCaps,
            userName = "${occ.nickname} (${occ.jid.ifBlank { "MUC Occupant" }})",
            onDismiss = { selectedCapsOccupant = null }
        )
    }

    PanggilanOverlay(viewModel = viewModel)
}

@Composable
fun RoomOccupantsCapsBar(
    occupants: List<RoomOccupant>,
    onOccupantClick: (RoomOccupant) -> Unit,
    onOpenRosterClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(occupants, key = { it.nickname }) { occupant ->
                    val presenceColor = when (occupant.presence) {
                        UserPresence.ONLINE, UserPresence.CHAT -> OnlineGreen
                        UserPresence.AWAY -> AwayAmber
                        UserPresence.DND -> DndRed
                        UserPresence.OFFLINE -> OfflineGray
                    }
                    val platformColor = getClientPlatformColor(occupant.clientCaps.platform)

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            0.8.dp,
                            platformColor.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onOccupantClick(occupant) }
                            .testTag("quick_occupant_${occupant.nickname}")
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Avatar with presence
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = occupant.nickname.take(1).uppercase(Locale.getDefault()),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(presenceColor)
                                )
                            }

                            Text(
                                text = occupant.nickname,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )

                            // Client Caps Icon
                            ClientCapsIcon(
                                platform = occupant.clientCaps.platform,
                                modifier = Modifier.size(12.dp),
                                tint = platformColor
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onOpenRosterClick,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = "Buka Roster Anggota",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun MultiRoomTabHeader(
    rooms: List<XmppRoom>,
    selectedRoomJid: String?,
    onSelectRoom: (String) -> Unit,
    onAddRoomClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (rooms.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "Memuat daftar room...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val selectedIndex = rooms.indexOfFirst { it.jid == selectedRoomJid }
                    .takeIf { it in rooms.indices } ?: 0

                ScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    edgePadding = 4.dp,
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = {}
                ) {
                    rooms.forEach { room ->
                        val isSelected = room.jid == selectedRoomJid
                        val tabColor = try {
                            Color(android.graphics.Color.parseColor(room.colorHex))
                        } catch (e: Exception) {
                            MaterialTheme.colorScheme.primary
                        }

                        Tab(
                            selected = isSelected,
                            onClick = { onSelectRoom(room.jid) },
                            modifier = Modifier
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) tabColor.copy(alpha = 0.18f) else Color.Transparent
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.5.dp,
                                    color = if (isSelected) tabColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .testTag("tab_room_${room.jid}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Channel dot
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(tabColor)
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                Text(
                                    text = room.name,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (room.unreadCount > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text("${room.unreadCount}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick add room button
            IconButton(
                onClick = onAddRoomClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .testTag("btn_quick_add_room")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Tambah Ruangan",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onAttachClick: () -> Unit,
    onCallClick: (String) -> Unit,
    whisperTarget: RoomOccupant?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onAttachClick) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Lampirkan File",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field"),
                    placeholder = {
                        Text(
                            text = if (whisperTarget != null) "Bisikkan ke @${whisperTarget.nickname}..." else "Ketik pesan MUC...",
                            fontSize = 13.sp
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText)
                        }
                    },
                    enabled = inputText.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (inputText.isNotBlank()) {
                                if (whisperTarget != null) Color(0xFF7C3AED) else MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .testTag("btn_send_message")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Kirim Pesan",
                        tint = if (inputText.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Quick Call Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { onCallClick("Audio") },
                    label = { Text("Panggilan Audio", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                AssistChip(
                    onClick = { onCallClick("Video") },
                    label = { Text("Panggilan Video", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.VideoCall,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}
