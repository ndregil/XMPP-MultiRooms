package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.RoomConfigEntity
import com.example.data.local.entity.RoomEntity
import com.example.data.model.CallInfo
import com.example.data.model.ConnectionStatus
import com.example.data.model.DeliveryStatus
import com.example.data.model.DiscoveredRoom
import com.example.data.model.MessageType
import com.example.data.model.OccupantAffiliation
import com.example.data.model.OccupantRole
import com.example.data.model.PublicDirectoryRoom
import com.example.data.model.RoomOccupant
import com.example.data.model.StanzaLog
import com.example.data.model.XmppAccount
import com.example.data.model.XmppMessage
import com.example.data.model.XmppRoom
import com.example.data.model.RosterContact
import com.example.data.model.UserPresence
import com.example.data.network.HttpFileUploadManager
import com.example.data.network.JingleCallManager
import com.example.data.network.MultiUserChatManager
import com.example.data.network.RosterManager
import com.example.data.network.ServiceDiscoveryManager
import com.example.data.network.XmppConnectionManager
import com.example.data.network.XmppProtocolEngine
import org.jxmpp.jid.impl.JidCreate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class XmppRepository(
    private val context: android.content.Context,
    private val database: AppDatabase,
    private val scope: CoroutineScope
) {
    val engine = XmppProtocolEngine(scope)
    val smackConnectionManager = XmppConnectionManager()
    val smackMucManager = MultiUserChatManager(smackConnectionManager)
    val smackDiscoManager = ServiceDiscoveryManager(smackConnectionManager)
    val smackRosterManager = RosterManager(smackConnectionManager, scope)
    val smackFileUploadManager = HttpFileUploadManager(context)
    val smackCallManager = JingleCallManager(smackConnectionManager, scope)

    val connectionStatus: StateFlow<ConnectionStatus> = engine.connectionStatus
    val currentAccount: StateFlow<XmppAccount> = engine.currentAccount
    val stanzaLogs: StateFlow<List<StanzaLog>> = engine.stanzaLogs
    val roomOccupants: StateFlow<Map<String, List<RoomOccupant>>> = engine.roomOccupants
    val currentCall: StateFlow<CallInfo?> = smackCallManager.currentCall
    val discoveredRooms: StateFlow<List<DiscoveredRoom>> = smackDiscoManager.discoveredRooms
    val isDiscovering: StateFlow<Boolean> = smackDiscoManager.isDiscovering
    val mucServiceDomains: StateFlow<List<String>> = smackDiscoManager.mucServiceDomains
    val rosterContacts: StateFlow<List<RosterContact>> = smackRosterManager.contacts
    val rosterPresenceMap: StateFlow<Map<String, UserPresence>> = smackRosterManager.presenceMap
    val isRosterLoading: StateFlow<Boolean> = smackRosterManager.isLoading

    private val _userPresence = kotlinx.coroutines.flow.MutableStateFlow(UserPresence.ONLINE)
    val userPresence: StateFlow<UserPresence> = _userPresence.asStateFlow()

    private val _userStatusMessage = kotlinx.coroutines.flow.MutableStateFlow("Active on XMPP MultiRoom")
    val userStatusMessage: StateFlow<String> = _userStatusMessage.asStateFlow()

    val roomDao = database.roomDao()
    val messageDao = database.messageDao()
    val chatMessageDao = database.chatMessageDao()
    val roomConfigDao = database.roomConfigDao()

    val rooms: Flow<List<XmppRoom>> = database.roomDao().getAllRooms().map { entities ->
        entities.map { it.toModel() }
    }

    val roomConfigs: Flow<List<RoomConfigEntity>> = database.roomConfigDao().getAllRoomConfigs()


    init {
        // Collect incoming network messages from engine and store into Room database
        scope.launch {
            try {
                engine.incomingMessages.collect { msg ->
                    processIncomingMessage(msg)
                }
            } catch (e: Exception) {
                // Handled safely
            }
        }

        // Collect incoming messages from Smack MultiUserChatManager MessageListener
        scope.launch {
            try {
                smackMucManager.incomingMessages.collect { msg ->
                    processIncomingMessage(msg)
                }
            } catch (e: Exception) {
                // Handled safely
            }
        }

        // Collect MUC room topic updates
        scope.launch {
            try {
                smackMucManager.topicUpdates.collect { (roomJid, newTopic) ->
                    database.roomDao().updateTopic(roomJid, newTopic)
                }
            } catch (e: Exception) {
                // Handled safely
            }
        }

        // Initialize default rooms if empty
        scope.launch {
            try {
                seedDefaultRoomsIfEmpty()
            } catch (e: Exception) {
                // Handled safely
            }
        }
    }

    private suspend fun seedDefaultRoomsIfEmpty() {
        val count = database.roomDao().getRoomByJid("general@conference.xmpp.today")
        if (count == null) {
            val defaultRooms = listOf(
                RoomEntity(
                    jid = "general@conference.xmpp.today",
                    name = "# Umum (General)",
                    topic = "Ruang obrolan umum multi-user XMPP",
                    myNickname = "AndroidUser",
                    occupantCount = 5,
                    unreadCount = 0,
                    lastMessageText = "Selamat datang di XMPP MultiRoom!",
                    lastMessageTime = System.currentTimeMillis() - 60000,
                    isJoined = true,
                    isBookmarked = true,
                    myRole = OccupantRole.MEMBER,
                    myAffiliation = OccupantAffiliation.MEMBER,
                    colorHex = "#0284C7"
                ),
                RoomEntity(
                    jid = "tech-id@conference.xmpp.today",
                    name = "# Tech Indonesia",
                    topic = "Diskusi teknologi, cloud, server Jabber & modern computing",
                    myNickname = "AndroidUser",
                    occupantCount = 4,
                    unreadCount = 1,
                    lastMessageText = "Prosody 0.12 siap digunakan",
                    lastMessageTime = System.currentTimeMillis() - 120000,
                    isJoined = true,
                    isBookmarked = true,
                    myRole = OccupantRole.MEMBER,
                    myAffiliation = OccupantAffiliation.MEMBER,
                    colorHex = "#0D9488"
                ),
                RoomEntity(
                    jid = "android-kotlin@conference.xmpp.today",
                    name = "# Android & Kotlin",
                    topic = "Seputar Jetpack Compose, Coroutines, dan M3 Design",
                    myNickname = "AndroidUser",
                    occupantCount = 4,
                    unreadCount = 0,
                    lastMessageText = "Room database terintegrasi sempurna",
                    lastMessageTime = System.currentTimeMillis() - 300000,
                    isJoined = true,
                    isBookmarked = true,
                    myRole = OccupantRole.MEMBER,
                    myAffiliation = OccupantAffiliation.MEMBER,
                    colorHex = "#7C3AED"
                ),
                RoomEntity(
                    jid = "random-lounge@conference.xmpp.today",
                    name = "# Santai & Kopi",
                    topic = "Lounge santai, games & ngobrol bebas",
                    myNickname = "AndroidUser",
                    occupantCount = 3,
                    unreadCount = 0,
                    lastMessageText = "Selamat sore semuanya!",
                    lastMessageTime = System.currentTimeMillis() - 500000,
                    isJoined = true,
                    isBookmarked = true,
                    myRole = OccupantRole.MEMBER,
                    myAffiliation = OccupantAffiliation.MEMBER,
                    colorHex = "#D97706"
                )
            )
            database.roomDao().insertRooms(defaultRooms)

            // Seed initial room configurations so room settings survive app restarts
            val defaultConfigs = listOf(
                RoomConfigEntity(
                    roomJid = "general@conference.xmpp.today",
                    roomName = "# Umum (General)",
                    nickname = "AndroidUser",
                    isAutoJoin = true,
                    notificationsEnabled = true,
                    subject = "Ruang obrolan umum multi-user XMPP",
                    colorHex = "#0284C7"
                ),
                RoomConfigEntity(
                    roomJid = "tech-id@conference.xmpp.today",
                    roomName = "# Tech Indonesia",
                    nickname = "AndroidUser",
                    isAutoJoin = true,
                    notificationsEnabled = true,
                    subject = "Diskusi teknologi, cloud, server Jabber & modern computing",
                    colorHex = "#0D9488"
                ),
                RoomConfigEntity(
                    roomJid = "android-kotlin@conference.xmpp.today",
                    roomName = "# Android & Kotlin",
                    nickname = "AndroidUser",
                    isAutoJoin = true,
                    notificationsEnabled = true,
                    subject = "Seputar Jetpack Compose, Coroutines, dan M3 Design",
                    colorHex = "#7C3AED"
                ),
                RoomConfigEntity(
                    roomJid = "random-lounge@conference.xmpp.today",
                    roomName = "# Santai & Kopi",
                    nickname = "AndroidUser",
                    isAutoJoin = true,
                    notificationsEnabled = true,
                    subject = "Lounge santai, games & ngobrol bebas",
                    colorHex = "#D97706"
                )
            )
            database.roomConfigDao().insertOrUpdateConfigs(defaultConfigs)

            // Seed initial welcome messages
            val welcomeMessages = listOf(
                MessageEntity(
                    id = "init_1",
                    roomJid = "general@conference.xmpp.today",
                    senderJid = "general@conference.xmpp.today/XmppAdmin",
                    senderNickname = "XmppAdmin",
                    body = "Selamat datang di XMPP Multi-Room Chat! Ruang ini mendukung obrolan multi-kanal terdesentralisasi, live stanza XML, dan roster anggota.",
                    timestamp = System.currentTimeMillis() - 100000,
                    isMine = false,
                    messageType = MessageType.GROUPCHAT,
                    status = DeliveryStatus.DELIVERED
                ),
                MessageEntity(
                    id = "init_2",
                    roomJid = "tech-id@conference.xmpp.today",
                    senderJid = "tech-id@conference.xmpp.today/ArchGuru",
                    senderNickname = "ArchGuru",
                    body = "Protokol XEP-0045 memungkinkan pembuatan kamar obrolan publik dan privat dengan pengaturan role moderasi.",
                    timestamp = System.currentTimeMillis() - 120000,
                    isMine = false,
                    messageType = MessageType.GROUPCHAT,
                    status = DeliveryStatus.DELIVERED
                ),
                MessageEntity(
                    id = "init_3",
                    roomJid = "android-kotlin@conference.xmpp.today",
                    senderJid = "android-kotlin@conference.xmpp.today/KotlinMaster",
                    senderNickname = "KotlinMaster",
                    body = "Halo teman-teman! Aplikasi ini dibangun dengan Kotlin & Jetpack Compose modern.",
                    timestamp = System.currentTimeMillis() - 300000,
                    isMine = false,
                    messageType = MessageType.GROUPCHAT,
                    status = DeliveryStatus.DELIVERED
                )
            )
            database.messageDao().insertMessages(welcomeMessages)
        }
    }

    fun getMessagesForRoom(roomJid: String): Flow<List<XmppMessage>> {
        return database.messageDao().getMessagesForRoom(roomJid).map { list ->
            list.map { it.toModel() }
        }
    }

    suspend fun markRoomAsRead(roomJid: String) {
        database.roomDao().markRoomAsRead(roomJid)
    }

    suspend fun connect(account: XmppAccount, password: String = "") {
        database.accountDao().saveAccount(account.toEntity())
        
        // 1. Connect via Smack (Real XMPP)
        val smackResult = smackConnectionManager.connectAndLogin(
            com.example.data.network.XmppCredentials.fromAccount(account, password)
        )
        
        // 2. Connect via Engine (Logs & raw stream)
        engine.connect(account, password)

        if (smackResult.isSuccess) {
            // Auto-join configured rooms upon successful connection
            scope.launch {
                try {
                    autoJoinConfiguredRooms()
                } catch (e: Exception) {
                    // Handled safely
                }
            }
        }
    }


    suspend fun disconnect() {
        engine.disconnect()
    }

    private suspend fun processIncomingMessage(msg: XmppMessage) {
        try {
            database.messageDao().insertMessage(msg.toEntity())

            // Update room last message and unread count
            val existing = database.roomDao().getRoomByJid(msg.roomJid)
            if (existing != null) {
                val updated = existing.copy(
                    lastMessageText = if (msg.messageType == MessageType.WHISPER) "[Bisikan] ${msg.body}" else msg.body,
                    lastMessageTime = msg.timestamp,
                    unreadCount = if (msg.isMine) existing.unreadCount else existing.unreadCount + 1
                )
                database.roomDao().insertOrUpdateRoom(updated)
            }
        } catch (e: Exception) {
            // Handled safely
        }
    }

    suspend fun joinRoom(roomJid: String, name: String, nickname: String, topic: String = "", password: String = "") {
        val existing = database.roomDao().getRoomByJid(roomJid)
        val roomEntity = existing?.copy(
            isJoined = true,
            myNickname = nickname,
            topic = if (topic.isNotBlank()) topic else existing.topic,
            password = if (password.isNotBlank()) password else existing.password
        ) ?: RoomEntity(
            jid = roomJid,
            name = if (name.isNotBlank()) name else "# " + roomJid.substringBefore("@"),
            topic = topic,
            myNickname = nickname,
            occupantCount = 1,
            unreadCount = 0,
            lastMessageText = "Baru saja bergabung",
            lastMessageTime = System.currentTimeMillis(),
            isJoined = true,
            isBookmarked = true,
            myRole = OccupantRole.MEMBER,
            myAffiliation = OccupantAffiliation.MEMBER,
            colorHex = randomColorForRoom(roomJid),
            password = if (password.isNotBlank()) password else null,
            isAutoJoin = true
        )
        database.roomDao().insertOrUpdateRoom(roomEntity)

        // Persist room configuration so room settings and credentials survive app restarts
        val existingConfig = database.roomConfigDao().getRoomConfigSync(roomJid)
        val config = existingConfig?.copy(
            roomName = if (name.isNotBlank()) name else existingConfig.roomName,
            nickname = nickname,
            password = if (password.isNotBlank()) password else existingConfig.password,
            subject = if (topic.isNotBlank()) topic else existingConfig.subject,
            lastJoinedAt = System.currentTimeMillis()
        ) ?: RoomConfigEntity(
            roomJid = roomJid,
            roomName = if (name.isNotBlank()) name else "# " + roomJid.substringBefore("@"),
            nickname = nickname,
            password = if (password.isNotBlank()) password else null,
            isAutoJoin = true,
            notificationsEnabled = true,
            subject = topic,
            colorHex = randomColorForRoom(roomJid),
            lastJoinedAt = System.currentTimeMillis()
        )
        database.roomConfigDao().insertOrUpdateConfig(config)

        // Join via Smack MUC if Smack connection is established
        if (smackConnectionManager.isConnected()) {
            try {
                smackMucManager.joinRoom(roomJid, nickname, password)
            } catch (e: Exception) {
                // Handled gracefully
            }
        }

        engine.joinRoom(roomJid, nickname, password)
    }

    suspend fun leaveRoom(roomJid: String, nickname: String) {
        val existing = database.roomDao().getRoomByJid(roomJid)
        if (existing != null) {
            database.roomDao().insertOrUpdateRoom(existing.copy(isJoined = false))
        }

        if (smackConnectionManager.isConnected()) {
            try {
                smackMucManager.leaveRoom(roomJid)
            } catch (e: Exception) {
                // Handled gracefully
            }
        }

        engine.leaveRoom(roomJid, nickname)
    }

    suspend fun deleteRoom(roomJid: String) {
        database.roomDao().deleteRoom(roomJid)
        database.roomConfigDao().deleteConfig(roomJid)
        database.messageDao().clearRoomMessages(roomJid)
    }

    /**
     * Automatically rejoins rooms configured with isAutoJoin = true using persisted credentials.
     */
    suspend fun autoJoinConfiguredRooms() {
        val autoJoinConfigs = database.roomConfigDao().getAutoJoinRoomConfigsList()
        for (cfg in autoJoinConfigs) {
            joinRoom(
                roomJid = cfg.roomJid,
                name = cfg.roomName,
                nickname = cfg.nickname.ifBlank { "AndroidUser" },
                topic = cfg.subject,
                password = cfg.password.orEmpty()
            )
        }
    }

    fun getRoomConfig(roomJid: String): Flow<RoomConfigEntity?> {
        return database.roomConfigDao().getRoomConfig(roomJid)
    }

    suspend fun getRoomConfigSync(roomJid: String): RoomConfigEntity? {
        return database.roomConfigDao().getRoomConfigSync(roomJid)
    }

    suspend fun saveRoomConfig(config: RoomConfigEntity) {
        database.roomConfigDao().insertOrUpdateConfig(config)
        database.roomDao().updateAutoJoin(config.roomJid, config.isAutoJoin)
        if (config.password != null) {
            database.roomDao().updatePassword(config.roomJid, config.password)
        }
    }

    suspend fun updateRoomAutoJoin(roomJid: String, isAutoJoin: Boolean) {
        database.roomConfigDao().updateAutoJoin(roomJid, isAutoJoin)
        database.roomDao().updateAutoJoin(roomJid, isAutoJoin)
    }

    suspend fun updateRoomPassword(roomJid: String, password: String?) {
        database.roomConfigDao().updatePassword(roomJid, password)
        database.roomDao().updatePassword(roomJid, password)
    }

    suspend fun updateRoomNickname(roomJid: String, nickname: String) {
        database.roomConfigDao().updateNickname(roomJid, nickname)
    }

    suspend fun clearRoomChatHistory(roomJid: String) {
        database.messageDao().clearRoomMessages(roomJid)
    }

    fun searchRoomChatHistory(roomJid: String, query: String): Flow<List<XmppMessage>> {
        return database.messageDao().searchMessages(roomJid, query).map { list ->
            list.map { it.toModel() }
        }
    }

    suspend fun updateMessageDeliveryStatus(messageId: String, status: DeliveryStatus) {
        database.messageDao().updateMessageStatus(messageId, status)
    }


    suspend fun sendMessage(roomJid: String, text: String, myNickname: String) {
        // If Smack MUC is connected and room joined, send via Smack MUC
        if (smackConnectionManager.isConnected() && smackMucManager.isRoomJoined(roomJid)) {
            try {
                smackMucManager.sendMessage(roomJid, text)
            } catch (e: Exception) {
                // Handled gracefully
            }
        }

        val msg = engine.sendGroupMessage(roomJid, text, myNickname)
        database.messageDao().insertMessage(msg.toEntity())
        
        val existing = database.roomDao().getRoomByJid(roomJid)
        if (existing != null) {
            database.roomDao().insertOrUpdateRoom(
                existing.copy(
                    lastMessageText = text,
                    lastMessageTime = msg.timestamp
                )
            )
        }
    }

    suspend fun sendWhisper(roomJid: String, targetNickname: String, text: String, myNickname: String) {
        // If Smack MUC is connected and room joined, send whisper via Smack
        if (smackConnectionManager.isConnected() && smackMucManager.isRoomJoined(roomJid)) {
            try {
                smackMucManager.sendWhisper(roomJid, targetNickname, text)
            } catch (e: Exception) {
                // Handled gracefully
            }
        }

        val msg = engine.sendPrivateWhisper(roomJid, targetNickname, text, myNickname)
        database.messageDao().insertMessage(msg.toEntity())
    }

    suspend fun updateRoomTopic(roomJid: String, newTopic: String, myNickname: String) {
        database.roomDao().updateTopic(roomJid, newTopic)

        if (smackConnectionManager.isConnected() && smackMucManager.isRoomJoined(roomJid)) {
            try {
                smackMucManager.changeSubject(roomJid, newTopic)
            } catch (e: Exception) {
                // Handled gracefully
            }
        }

    engine.changeRoomTopic(roomJid, newTopic, myNickname)
    }

    suspend fun sendFile(
        roomJid: String,
        uri: android.net.Uri,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        myNickname: String
    ) {
        // 1. If real connection, upload via XEP-0363
        var finalUrl: String? = null
        if (smackConnectionManager.isConnected()) {
            val connection = smackConnectionManager.getRawConnection()
            if (connection != null) {
                val uploadResult = smackFileUploadManager.uploadFile(connection, uri, fileName, fileSize, mimeType)
                finalUrl = uploadResult.getOrNull()
            }
        }

        // 2. If upload failed or in demo mode, use a simulated URL (XMPP servers often use this format)
        val url = finalUrl ?: "https://xmpp.today/upload/${java.util.UUID.randomUUID()}/$fileName"

        // 3. Send message via engine
        val msg = engine.sendGroupFile(roomJid, url, fileName, mimeType, fileSize, myNickname)
        database.messageDao().insertMessage(msg.toEntity())

        // 4. Send via Smack MUC if connected
        if (smackConnectionManager.isConnected() && smackMucManager.isRoomJoined(roomJid)) {
            try {
                smackMucManager.sendMessage(roomJid, url)
            } catch (e: Exception) {
                // Handled gracefully
            }
        }
    }

    suspend fun initiateCall(roomJid: String, callType: String, myNickname: String, targetNickname: String? = null) {
        val msgBody = if (targetNickname != null) {
            "Memulai panggilan $callType ke @$targetNickname..."
        } else {
            "Memulai panggilan $callType grup..."
        }
        
        val msg = engine.sendCallSignalling(roomJid, msgBody, myNickname)
        database.messageDao().insertMessage(msg.toEntity())
        
        if (smackConnectionManager.isConnected()) {
            val targetJid = if (targetNickname != null) "$roomJid/$targetNickname" else roomJid
            smackCallManager.initiateCall(targetJid, targetNickname ?: "Group", callType)
            
            if (smackMucManager.isRoomJoined(roomJid)) {
                try {
                    smackMucManager.sendMessage(roomJid, msgBody)
                } catch (e: Exception) {
                    // Handled gracefully
                }
            }
        }
    }

    suspend fun initiate1on1Call(targetJid: String, nickname: String, callType: String) {
        if (smackConnectionManager.isConnected()) {
            smackCallManager.initiateCall(targetJid, nickname, callType)
        }
    }

    suspend fun sendFileToContact(targetJid: String, uri: android.net.Uri) {
        if (smackConnectionManager.isConnected()) {
            val connection = smackConnectionManager.getRawConnection() ?: return
            
            // In a real app we'd get file details from content resolver
            val fileName = "file_${System.currentTimeMillis()}"
            val fileSize = 1024L // Placeholder
            val mimeType = "application/octet-stream"

            val uploadResult = smackFileUploadManager.uploadFile(connection, uri, fileName, fileSize, mimeType)
            val url = uploadResult.getOrNull() ?: return
            
            // Send URL as message to contact
            val message = org.jivesoftware.smack.packet.Message(JidCreate.from(targetJid), org.jivesoftware.smack.packet.Message.Type.chat)
            message.body = "Membagikan file: $url"
            connection.sendStanza(message)
        }
    }

    suspend fun queryDirectory(): List<PublicDirectoryRoom> {
        val discoRooms = smackDiscoManager.discoverRooms().getOrNull()
        if (!discoRooms.isNullOrEmpty()) {
            return discoRooms.map { it.toPublicDirectoryRoom() }
        }
        return engine.queryPublicDirectory()
    }

    suspend fun discoverRooms(serviceJid: String? = null): List<DiscoveredRoom> {
        val result = smackDiscoManager.discoverRooms(serviceJid)
        return result.getOrElse { emptyList() }
    }

    suspend fun loadRosterContacts(): List<RosterContact> {
        return smackRosterManager.loadContacts().getOrElse { smackRosterManager.contacts.value }
    }

    suspend fun addRosterContact(jid: String, name: String, group: String? = null): Result<Unit> {
        return smackRosterManager.addContact(jid, name, group)
    }

    suspend fun removeRosterContact(jid: String): Result<Unit> {
        return smackRosterManager.removeContact(jid)
    }

    suspend fun renameRosterContact(jid: String, newName: String): Result<Unit> {
        return smackRosterManager.renameContact(jid, newName)
    }

    suspend fun addRosterContactToGroup(jid: String, group: String): Result<Unit> {
        return smackRosterManager.addContactToGroup(jid, group)
    }

    fun getContactPresence(jid: String): UserPresence {
        return smackRosterManager.getContactPresence(jid)
    }

    suspend fun updateUserPresence(presence: UserPresence, statusMessage: String) {
        _userPresence.value = presence
        _userStatusMessage.value = statusMessage

        val myNick = currentAccount.value.nickname

        // 1. Update Smack TCP Connection presence (broadcasts to all roster contacts via XMPP server)
        try {
            smackConnectionManager.updatePresence(presence, statusMessage)
        } catch (e: Exception) {
            // Handled safely
        }

        // 2. Broadcast presence to all joined MUC rooms in Smack
        try {
            smackMucManager.updatePresenceInRooms(presence, statusMessage)
        } catch (e: Exception) {
            // Handled safely
        }

        // 3. Update protocol engine (multi-room occupant simulation and raw stanzas)
        try {
            engine.updateUserPresence(presence, statusMessage, myNick)
        } catch (e: Exception) {
            // Handled safely
        }
    }

    fun sendRawStanza(xml: String) {
        engine.sendRawStanza(xml)
    }

    private fun randomColorForRoom(roomJid: String): String {
        val colors = listOf("#0284C7", "#0D9488", "#7C3AED", "#D97706", "#DC2626", "#2563EB", "#059669")
        val index = kotlin.math.abs(roomJid.hashCode()) % colors.size
        return colors[index]
    }
}

private fun AccountEntity.toModel(): XmppAccount = XmppAccount(
    jid = jid,
    username = username,
    domain = domain,
    nickname = nickname,
    host = host,
    port = port,
    useTls = useTls,
    isDemoMode = isDemoMode,
    statusMessage = statusMessage
)

private fun XmppAccount.toEntity(): AccountEntity = AccountEntity(
    jid = jid,
    username = username,
    domain = domain,
    nickname = nickname,
    host = host,
    port = port,
    useTls = useTls,
    isDemoMode = isDemoMode,
    statusMessage = statusMessage
)

private fun RoomEntity.toModel(): XmppRoom = XmppRoom(
    jid = jid,
    name = name,
    topic = topic,
    myNickname = myNickname,
    occupantCount = occupantCount,
    unreadCount = unreadCount,
    lastMessageText = lastMessageText,
    lastMessageTime = lastMessageTime,
    isJoined = isJoined,
    isBookmarked = isBookmarked,
    myRole = myRole,
    myAffiliation = myAffiliation,
    colorHex = colorHex
)

private fun MessageEntity.toModel(): XmppMessage = XmppMessage(
    id = id,
    roomJid = roomJid,
    senderJid = senderJid,
    senderNickname = senderNickname,
    body = body,
    timestamp = timestamp,
    isMine = isMine,
    messageType = messageType,
    status = status,
    recipientNickname = recipientNickname,
    fileUrl = fileUrl,
    fileMimeType = fileMimeType,
    fileName = fileName,
    fileSize = fileSize
)

private fun XmppMessage.toEntity(): MessageEntity = MessageEntity(
    id = id,
    roomJid = roomJid,
    senderJid = senderJid,
    senderNickname = senderNickname,
    body = body,
    timestamp = timestamp,
    isMine = isMine,
    messageType = messageType,
    status = status,
    recipientNickname = recipientNickname,
    fileUrl = fileUrl,
    fileMimeType = fileMimeType,
    fileName = fileName,
    fileSize = fileSize
)
