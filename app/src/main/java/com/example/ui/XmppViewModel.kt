package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.RoomConfigEntity
import com.example.data.model.ConnectionStatus
import com.example.data.model.DiscoveredRoom
import com.example.data.model.PublicDirectoryRoom
import com.example.data.model.RoomOccupant
import com.example.data.model.RosterContact
import com.example.data.model.StanzaLog
import com.example.data.model.UserPresence
import com.example.data.model.XmppAccount
import com.example.data.model.XmppMessage
import com.example.data.model.XmppRoom
import com.example.data.repository.XmppRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class XmppViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    val repository = XmppRepository(application, database, viewModelScope)

    val connectionStatus: StateFlow<ConnectionStatus> = repository.connectionStatus
    val currentAccount: StateFlow<XmppAccount> = repository.currentAccount
    val stanzaLogs: StateFlow<List<StanzaLog>> = repository.stanzaLogs
    val allRoomOccupants: StateFlow<Map<String, List<RoomOccupant>>> = repository.roomOccupants
    val rosterContacts: StateFlow<List<RosterContact>> = repository.rosterContacts
    val rosterPresenceMap: StateFlow<Map<String, UserPresence>> = repository.rosterPresenceMap
    val isRosterLoading: StateFlow<Boolean> = repository.isRosterLoading
    val myPresence: StateFlow<UserPresence> = repository.userPresence
    val myStatusMessage: StateFlow<String> = repository.userStatusMessage
    val currentCall: StateFlow<com.example.data.model.CallInfo?> = repository.currentCall

    // Search filter query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Raw rooms list
    val allRooms: StateFlow<List<XmppRoom>> = repository.rooms.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Room Configurations (persisted in Room DB)
    val roomConfigs: StateFlow<List<RoomConfigEntity>> = repository.roomConfigs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Filtered rooms
    val filteredRooms: StateFlow<List<XmppRoom>> = combine(allRooms, _searchQuery) { rooms, query ->
        if (query.isBlank()) {
            rooms
        } else {
            rooms.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.jid.contains(query, ignoreCase = true) ||
                it.topic.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Currently selected / active room JID
    private val _selectedRoomJid = MutableStateFlow<String?>("general@conference.xmpp.today")
    val selectedRoomJid: StateFlow<String?> = _selectedRoomJid.asStateFlow()

    // Active room object with graceful fallback
    val activeRoom: StateFlow<XmppRoom?> = combine(allRooms, _selectedRoomJid) { rooms, jid ->
        if (jid == null) rooms.firstOrNull() else (rooms.find { it.jid == jid } ?: rooms.firstOrNull())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // Messages for active room
    val activeRoomMessages: StateFlow<List<XmppMessage>> = activeRoom.flatMapLatest { room ->
        if (room != null) {
            repository.getMessagesForRoom(room.jid)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Occupants for active room
    val activeRoomOccupants: StateFlow<List<RoomOccupant>> = combine(allRoomOccupants, activeRoom) { map, room ->
        if (room != null) map[room.jid] ?: emptyList() else emptyList()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Public Directory & XEP-0030 Service Discovery
    private val _directoryRooms = MutableStateFlow<List<PublicDirectoryRoom>>(emptyList())
    val directoryRooms: StateFlow<List<PublicDirectoryRoom>> = _directoryRooms.asStateFlow()

    private val _isDirectoryLoading = MutableStateFlow(false)
    val isDirectoryLoading: StateFlow<Boolean> = _isDirectoryLoading.asStateFlow()

    val discoveredRooms: StateFlow<List<DiscoveredRoom>> = repository.discoveredRooms
    val isDiscovering: StateFlow<Boolean> = repository.isDiscovering
    val mucServiceDomains: StateFlow<List<String>> = repository.mucServiceDomains

    // UI state flags
    val isRosterSheetOpen = MutableStateFlow(false)
    val isTopicModalOpen = MutableStateFlow(false)
    val isJoinCreateDialogOpen = MutableStateFlow(false)
    val isStanzaConsoleOpen = MutableStateFlow(false)
    val whisperTargetOccupant = MutableStateFlow<RoomOccupant?>(null)

    // Active input text
    val currentInputText = MutableStateFlow("")

    init {
        // App will wait for user to configure account in AccountConnectionScreen
    }

    fun selectRoom(jid: String) {
        _selectedRoomJid.value = jid
        viewModelScope.launch {
            repository.markRoomAsRead(jid)
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        val room = activeRoom.value ?: return
        if (trimmed.isBlank()) return

        val myNick = room.myNickname.ifBlank { currentAccount.value.nickname }

        viewModelScope.launch {
            val whisperTarget = whisperTargetOccupant.value
            if (whisperTarget != null) {
                repository.sendWhisper(room.jid, whisperTarget.nickname, trimmed, myNick)
                whisperTargetOccupant.value = null
            } else {
                repository.sendMessage(room.jid, trimmed, myNick)
            }
            currentInputText.value = ""
        }
    }

    fun sendFile(uri: android.net.Uri, fileName: String, fileSize: Long, mimeType: String) {
        val room = activeRoom.value ?: return
        val myNick = room.myNickname.ifBlank { currentAccount.value.nickname }
        viewModelScope.launch {
            repository.sendFile(room.jid, uri, fileName, fileSize, mimeType, myNick)
        }
    }

    fun initiateCall(callType: String) {
        val room = activeRoom.value ?: return
        val myNick = room.myNickname.ifBlank { currentAccount.value.nickname }
        val targetNick = whisperTargetOccupant.value?.nickname
        
        viewModelScope.launch {
            repository.initiateCall(room.jid, callType, myNick, targetNick)
        }
    }

    fun initiate1on1Call(targetJid: String, nickname: String, callType: String) {
        viewModelScope.launch {
            repository.initiate1on1Call(targetJid, nickname, callType)
        }
    }

    fun sendFileToContact(targetJid: String, uri: android.net.Uri) {
        viewModelScope.launch {
            repository.sendFileToContact(targetJid, uri)
        }
    }

    fun acceptCall() {
        repository.smackCallManager.acceptCall()
    }

    fun rejectCall() {
        repository.smackCallManager.rejectCall()
    }

    fun endCall() {
        repository.smackCallManager.endCall()
    }

    fun toggleMute() {
        repository.smackCallManager.toggleMute()
    }

    fun toggleVideo() {
        repository.smackCallManager.toggleVideo()
    }

    fun joinOrCreateRoom(
        roomJid: String,
        roomName: String,
        nickname: String,
        topic: String = "",
        password: String = ""
    ) {
        viewModelScope.launch {
            val validJid = if (roomJid.contains("@")) roomJid else "$roomJid@conference.xmpp.today"
            val validNick = if (nickname.isNotBlank()) nickname else currentAccount.value.nickname
            repository.joinRoom(validJid, roomName, validNick, topic, password)
            _selectedRoomJid.value = validJid
            isJoinCreateDialogOpen.value = false
        }
    }

    fun leaveRoom(roomJid: String) {
        viewModelScope.launch {
            val room = allRooms.value.find { it.jid == roomJid }
            val nick = room?.myNickname ?: currentAccount.value.nickname
            repository.leaveRoom(roomJid, nick)
            if (_selectedRoomJid.value == roomJid) {
                val other = allRooms.value.firstOrNull { it.jid != roomJid && it.isJoined }
                _selectedRoomJid.value = other?.jid
            }
        }
    }

    fun deleteRoom(roomJid: String) {
        viewModelScope.launch {
            repository.deleteRoom(roomJid)
            if (_selectedRoomJid.value == roomJid) {
                _selectedRoomJid.value = allRooms.value.firstOrNull { it.jid != roomJid }?.jid
            }
        }
    }

    fun updateTopic(newTopic: String) {
        val room = activeRoom.value ?: return
        viewModelScope.launch {
            val myNick = room.myNickname.ifBlank { currentAccount.value.nickname }
            repository.updateRoomTopic(room.jid, newTopic, myNick)
            isTopicModalOpen.value = false
        }
    }

    fun loadDirectory(serviceJid: String? = null) {
        viewModelScope.launch {
            _isDirectoryLoading.value = true
            try {
                val rooms = repository.discoverRooms(serviceJid)
                if (rooms.isNotEmpty()) {
                    _directoryRooms.value = rooms.map { it.toPublicDirectoryRoom() }
                } else {
                    _directoryRooms.value = repository.queryDirectory()
                }
            } finally {
                _isDirectoryLoading.value = false
            }
        }
    }

    /**
     * Triggers XEP-0030 Service Discovery on the server or specific MUC service.
     */
    fun discoverRooms(serviceJid: String? = null) {
        loadDirectory(serviceJid)
    }


    fun connectAccount(account: XmppAccount, password: String = "") {
        viewModelScope.launch {
            repository.connect(account, password)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            repository.disconnect()
        }
    }

    fun sendRawXmlStanza(xml: String) {
        repository.sendRawStanza(xml)
    }

    fun setWhisperTarget(occupant: RoomOccupant?) {
        whisperTargetOccupant.value = occupant
        isRosterSheetOpen.value = false
    }

    /**
     * Hooks a custom MessageListener callback to receive incoming MUC messages directly in UI.
     */
    fun hookMucMessageListener(
        roomJid: String,
        onMessage: (XmppMessage) -> Unit
    ): com.example.data.network.MucMessageListener? {
        return repository.smackMucManager.hookMessageListener(roomJid, onMessage)
    }

    /**
     * Roster & Presence methods (Smack Roster API)
     */
    fun refreshRoster() {
        viewModelScope.launch {
            repository.loadRosterContacts()
        }
    }

    fun addRosterContact(jid: String, name: String, group: String? = null) {
        viewModelScope.launch {
            repository.addRosterContact(jid, name, group)
        }
    }

    fun removeRosterContact(jid: String) {
        viewModelScope.launch {
            repository.removeRosterContact(jid)
        }
    }

    fun renameRosterContact(jid: String, newName: String) {
        viewModelScope.launch {
            repository.renameRosterContact(jid, newName)
        }
    }

    fun addRosterContactToGroup(jid: String, group: String) {
        viewModelScope.launch {
            repository.addRosterContactToGroup(jid, group)
        }
    }

    fun getContactPresence(jid: String): UserPresence = repository.getContactPresence(jid)

    fun updateMyPresence(presence: UserPresence, statusMessage: String? = null) {
        viewModelScope.launch {
            val msg = statusMessage ?: myStatusMessage.value
            repository.updateUserPresence(presence, msg)
        }
    }

    /**
     * Room Configuration & Chat History Persistence operations
     */
    fun updateRoomAutoJoin(roomJid: String, isAutoJoin: Boolean) {
        viewModelScope.launch {
            repository.updateRoomAutoJoin(roomJid, isAutoJoin)
        }
    }

    fun updateRoomPassword(roomJid: String, password: String?) {
        viewModelScope.launch {
            repository.updateRoomPassword(roomJid, password)
        }
    }

    fun updateRoomNickname(roomJid: String, nickname: String) {
        viewModelScope.launch {
            repository.updateRoomNickname(roomJid, nickname)
        }
    }

    fun saveRoomConfig(config: RoomConfigEntity) {
        viewModelScope.launch {
            repository.saveRoomConfig(config)
        }
    }

    fun clearRoomChatHistory(roomJid: String) {
        viewModelScope.launch {
            repository.clearRoomChatHistory(roomJid)
        }
    }

    fun autoJoinConfiguredRooms() {
        viewModelScope.launch {
            repository.autoJoinConfiguredRooms()
        }
    }
}

