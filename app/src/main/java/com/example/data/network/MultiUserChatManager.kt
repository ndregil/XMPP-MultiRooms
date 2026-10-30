package com.example.data.network

import android.util.Log
import com.example.data.model.DeliveryStatus
import com.example.data.model.MessageType
import com.example.data.model.OccupantAffiliation
import com.example.data.model.OccupantRole
import com.example.data.model.PublicDirectoryRoom
import com.example.data.model.RoomOccupant
import com.example.data.model.UserPresence
import com.example.data.model.XmppMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jivesoftware.smack.MessageListener
import org.jivesoftware.smack.PresenceListener
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.packet.Presence
import org.jivesoftware.smack.packet.PresenceBuilder
import org.jivesoftware.smackx.muc.MUCAffiliation
import org.jivesoftware.smackx.muc.MUCRole
import org.jivesoftware.smackx.muc.MultiUserChat
import org.jivesoftware.smackx.muc.MultiUserChatManager as SmackMultiUserChatManager
import org.jivesoftware.smackx.muc.Occupant
import org.jivesoftware.smackx.muc.RoomInfo
import org.jivesoftware.smackx.muc.SubjectUpdatedListener
import org.jxmpp.jid.EntityFullJid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.parts.Resourcepart
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager class responsible for handling Multi-User Chat (MUC) operations:
 * - Joining existing MUC rooms with nickname and enter configuration
 * - Creating and configuring new MUC rooms (XEP-0045)
 * - Leaving MUC rooms
 * - Configuring room options (title, topic, persistence, password)
 * - Sending groupchat messages and private whispers
 * - Managing occupant rosters and presence in rooms
 * - Updating and listening to room subject/topic changes
 * - Discovering hosted public MUC rooms
 */
class MultiUserChatManager(
    private val connectionManager: XmppConnectionManager
) {
    companion object {
        private const val TAG = "MultiUserChatManager"
        private const val DEFAULT_HISTORY_STANZAS = 50
    }

    // Active joined MUC instances mapped by Bare JID string
    private val activeMucs = ConcurrentHashMap<String, MultiUserChat>()

    // Nicknames used when entering rooms (mapped by room JID string)
    private val roomNicknames = ConcurrentHashMap<String, String>()

    // MessageListener instances registered per room
    private val roomMessageListeners = ConcurrentHashMap<String, MutableList<MessageListener>>()

    // State flow of joined room JIDs
    private val _joinedRooms = MutableStateFlow<Set<String>>(emptySet())
    val joinedRooms: StateFlow<Set<String>> = _joinedRooms.asStateFlow()

    // Shared flow of incoming messages across all joined MUC rooms
    private val _incomingMessages = MutableSharedFlow<XmppMessage>(extraBufferCapacity = 128)
    val incomingMessages: SharedFlow<XmppMessage> = _incomingMessages.asSharedFlow()

    // State flow of occupants mapped by room JID string
    private val _roomOccupants = MutableStateFlow<Map<String, List<RoomOccupant>>>(emptyMap())
    val roomOccupants: StateFlow<Map<String, List<RoomOccupant>>> = _roomOccupants.asStateFlow()

    // Flow of room topic updates: Pair(roomJid, newTopic)
    private val _topicUpdates = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 32)
    val topicUpdates: SharedFlow<Pair<String, String>> = _topicUpdates.asSharedFlow()

    /**
     * Helper to obtain the underlying Smack MultiUserChatManager.
     */
    private fun getSmackMucManager(): SmackMultiUserChatManager {
        val conn = connectionManager.getRawConnection()
            ?: throw IllegalStateException("XMPP connection is not established. Please connect first.")
        if (!conn.isAuthenticated) {
            throw IllegalStateException("XMPP user is not authenticated. Please log in first.")
        }
        return SmackMultiUserChatManager.getInstanceFor(conn)
    }

    /**
     * Joins an existing MUC room, or creates it if it does not yet exist.
     *
     * @param roomJid Bare JID of the room, e.g. "tech@conference.example.org"
     * @param nickname User's display nickname inside this MUC room
     * @param password Optional room password for password-protected rooms
     * @param maxHistoryStanzas Number of past messages to request upon entering
     */
    suspend fun joinRoom(
        roomJid: String,
        nickname: String,
        password: String? = null,
        maxHistoryStanzas: Int = DEFAULT_HISTORY_STANZAS
    ): Result<MultiUserChat> = withContext(Dispatchers.IO) {
        try {
            val smackMucManager = getSmackMucManager()
            val bareJid = JidCreate.entityBareFrom(roomJid.trim())
            val nick = Resourcepart.from(nickname.trim().ifBlank { "AndroidUser" })

            val muc = smackMucManager.getMultiUserChat(bareJid)

            // If already joined, return existing instance
            if (muc.isJoined) {
                Log.d(TAG, "Already joined room $roomJid")
                activeMucs[roomJid] = muc
                roomNicknames[roomJid] = nick.toString()
                _joinedRooms.value = activeMucs.keys.toSet()
                updateOccupantsForRoom(roomJid, muc)
                return@withContext Result.success(muc)
            }

            // Build enter configuration
            val enterConfigBuilder = muc.getEnterConfigurationBuilder(nick)
            if (!password.isNullOrBlank()) {
                enterConfigBuilder.withPassword(password)
            }
            if (maxHistoryStanzas > 0) {
                enterConfigBuilder.requestMaxStanzasHistory(maxHistoryStanzas)
            } else {
                enterConfigBuilder.requestNoHistory()
            }

            Log.i(TAG, "Joining MUC room $roomJid as '$nick'...")
            muc.join(enterConfigBuilder.build())

            // Register listeners
            registerMucListeners(roomJid, muc, nick.toString())

            activeMucs[roomJid] = muc
            roomNicknames[roomJid] = nick.toString()
            _joinedRooms.value = activeMucs.keys.toSet()

            updateOccupantsForRoom(roomJid, muc)
            Log.i(TAG, "Successfully joined room $roomJid")

            Result.success(muc)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to join MUC room $roomJid: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Creates a new MUC room and configures it with initial settings.
     *
     * @param roomJid Bare JID for the new room (e.g. "newroom@conference.example.org")
     * @param nickname Creator's nickname inside the room
     * @param roomName Natural title / human-readable name of the room
     * @param topic Initial room description or subject
     * @param isPersistent Whether the room should persist after all users leave
     * @param isPublic Whether the room should be listed in public discovery
     * @param password Optional password protection for the room
     */
    suspend fun createAndConfigureRoom(
        roomJid: String,
        nickname: String,
        roomName: String? = null,
        topic: String? = null,
        isPersistent: Boolean = true,
        isPublic: Boolean = true,
        password: String? = null
    ): Result<MultiUserChat> = withContext(Dispatchers.IO) {
        try {
            val smackMucManager = getSmackMucManager()
            val bareJid = JidCreate.entityBareFrom(roomJid.trim())
            val nick = Resourcepart.from(nickname.trim().ifBlank { "AndroidUser" })

            val muc = smackMucManager.getMultiUserChat(bareJid)

            Log.i(TAG, "Creating new MUC room $roomJid with nickname '$nick'...")
            val createHandle = muc.create(nick)

            // Submit room configuration form to unlock room for other users
            try {
                val configForm = muc.configurationForm
                val answerForm = configForm.fillableForm

                if (!roomName.isNullOrBlank()) {
                    answerForm.setAnswer("muc#roomconfig_roomname", roomName)
                }
                if (!topic.isNullOrBlank()) {
                    answerForm.setAnswer("muc#roomconfig_roomdesc", topic)
                }

                answerForm.setAnswer("muc#roomconfig_persistentroom", isPersistent)
                answerForm.setAnswer("muc#roomconfig_publicroom", isPublic)

                if (!password.isNullOrBlank()) {
                    answerForm.setAnswer("muc#roomconfig_passwordprotectedroom", true)
                    answerForm.setAnswer("muc#roomconfig_roomsecret", password)
                }

                muc.sendConfigurationForm(answerForm)
                Log.i(TAG, "Configuration form submitted successfully for room $roomJid")
            } catch (formException: Exception) {
                Log.w(TAG, "Could not apply custom config form, falling back to instant room: ${formException.message}")
                try {
                    createHandle.makeInstant()
                } catch (e: Exception) {
                    Log.w(TAG, "Instant room fallback: ${e.message}")
                }
            }

            // Set initial topic if specified
            if (!topic.isNullOrBlank()) {
                try {
                    muc.changeSubject(topic)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set initial topic: ${e.message}")
                }
            }

            registerMucListeners(roomJid, muc, nick.toString())

            activeMucs[roomJid] = muc
            roomNicknames[roomJid] = nick.toString()
            _joinedRooms.value = activeMucs.keys.toSet()

            updateOccupantsForRoom(roomJid, muc)
            Log.i(TAG, "Room $roomJid successfully created and configured.")

            Result.success(muc)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create and configure room $roomJid: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Configures an existing MUC room (title, description, persistence, password).
     */
    suspend fun configureRoom(
        roomJid: String,
        roomName: String? = null,
        topic: String? = null,
        isPersistent: Boolean = true,
        isPublic: Boolean = true,
        password: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(
            IllegalStateException("Room $roomJid is not joined.")
        )
        try {
            val configForm = muc.configurationForm
            val answerForm = configForm.fillableForm

            if (!roomName.isNullOrBlank()) {
                answerForm.setAnswer("muc#roomconfig_roomname", roomName)
            }
            if (!topic.isNullOrBlank()) {
                answerForm.setAnswer("muc#roomconfig_roomdesc", topic)
            }
            answerForm.setAnswer("muc#roomconfig_persistentroom", isPersistent)
            answerForm.setAnswer("muc#roomconfig_publicroom", isPublic)

            if (!password.isNullOrBlank()) {
                answerForm.setAnswer("muc#roomconfig_passwordprotectedroom", true)
                answerForm.setAnswer("muc#roomconfig_roomsecret", password)
            }

            muc.sendConfigurationForm(answerForm)

            if (!topic.isNullOrBlank()) {
                try {
                    muc.changeSubject(topic)
                    _topicUpdates.tryEmit(Pair(roomJid, topic))
                } catch (e: Exception) {
                    Log.w(TAG, "Could not update subject during configure: ${e.message}")
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure room $roomJid: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Leaves an active MUC room and cleans up listeners and occupant state.
     */
    suspend fun leaveRoom(roomJid: String): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = activeMucs[roomJid]
        if (muc == null) {
            Log.w(TAG, "Room $roomJid is not in active joined rooms list.")
            return@withContext Result.success(Unit)
        }

        try {
            Log.i(TAG, "Leaving MUC room $roomJid...")
            if (muc.isJoined) {
                muc.leave()
            }
            // Remove registered listeners
            roomMessageListeners.remove(roomJid)?.forEach { listener ->
                try {
                    muc.removeMessageListener(listener)
                } catch (e: Exception) {
                    // Ignore listener cleanup errors on leave
                }
            }
            activeMucs.remove(roomJid)
            roomNicknames.remove(roomJid)
            _joinedRooms.value = activeMucs.keys.toSet()

            // Remove room from occupants map
            val currentOccupants = _roomOccupants.value.toMutableMap()
            currentOccupants.remove(roomJid)
            _roomOccupants.value = currentOccupants

            Log.i(TAG, "Left room $roomJid successfully.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error leaving room $roomJid: ${e.message}", e)
            // Even if network exception occurs, unregister locally
            activeMucs.remove(roomJid)
            roomNicknames.remove(roomJid)
            _joinedRooms.value = activeMucs.keys.toSet()
            Result.failure(e)
        }
    }

    /**
     * Changes or updates the room topic / subject.
     */
    suspend fun changeSubject(roomJid: String, newTopic: String): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(
            IllegalStateException("Room $roomJid is not joined.")
        )
        try {
            muc.changeSubject(newTopic)
            _topicUpdates.tryEmit(Pair(roomJid, newTopic))
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change room subject for $roomJid: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Sends a groupchat message to all participants in the MUC room.
     */
    suspend fun sendMessage(roomJid: String, body: String): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(
            IllegalStateException("Room $roomJid is not joined.")
        )
        try {
            muc.sendMessage(body)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message to $roomJid: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Sends a private whisper message to a specific occupant inside the MUC room (XEP-0045 7.5).
     */
    suspend fun sendWhisper(
        roomJid: String,
        targetNickname: String,
        body: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val conn = connectionManager.getRawConnection()
            ?: return@withContext Result.failure(IllegalStateException("No active XMPP connection."))

        try {
            val targetFullJid: EntityFullJid = JidCreate.entityFullFrom("$roomJid/$targetNickname")
            val message = Message(targetFullJid, Message.Type.chat).apply {
                this.body = body
            }
            conn.sendStanza(message)
            Log.d(TAG, "Sent whisper to $targetNickname in $roomJid: $body")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send whisper to $targetNickname in $roomJid: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Broadcasts updated presence to all active joined MUC rooms.
     */
    suspend fun updatePresenceInRooms(
        presence: UserPresence,
        statusMessage: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val conn = connectionManager.getRawConnection()
        if (conn == null || !conn.isAuthenticated) {
            return@withContext Result.success(Unit)
        }

        try {
            val presenceMode = when (presence) {
                UserPresence.ONLINE -> Presence.Mode.available
                UserPresence.CHAT -> Presence.Mode.chat
                UserPresence.AWAY -> Presence.Mode.away
                UserPresence.DND -> Presence.Mode.dnd
                UserPresence.OFFLINE -> null
            }

            for ((roomJid, muc) in activeMucs) {
                if (muc.isJoined) {
                    val nick = roomNicknames[roomJid] ?: conn.user?.resourceOrEmpty?.toString() ?: "AndroidUser"
                    val roomFullJid = JidCreate.entityFullFrom("$roomJid/$nick")
                    val pres = if (presence == UserPresence.OFFLINE) {
                        PresenceBuilder.buildPresence()
                            .ofType(Presence.Type.unavailable)
                            .to(roomFullJid)
                            .setStatus(statusMessage)
                            .build()
                    } else {
                        val builder = PresenceBuilder.buildPresence()
                            .ofType(Presence.Type.available)
                            .to(roomFullJid)
                            .setStatus(statusMessage)
                        if (presenceMode != null) {
                            builder.setMode(presenceMode)
                        }
                        builder.build()
                    }
                    conn.sendStanza(pres)
                    Log.d(TAG, "Broadcasted presence to MUC room $roomJid: $presence")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed updating presence in MUC rooms: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Queries detailed room information from the server (subject, occupants count, etc.).
     */
    suspend fun getRoomInfo(roomJid: String): Result<RoomInfo> = withContext(Dispatchers.IO) {
        try {
            val smackMucManager = getSmackMucManager()
            val bareJid = JidCreate.entityBareFrom(roomJid.trim())
            val info = smackMucManager.getRoomInfo(bareJid)
            Result.success(info)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get room info for $roomJid: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Discovers hosted public MUC rooms from a conference service domain.
     * Defaults to "conference.<domain>" if domain is not provided.
     */
    suspend fun discoverPublicRooms(
        mucServiceDomain: String? = null
    ): Result<List<PublicDirectoryRoom>> = withContext(Dispatchers.IO) {
        try {
            val smackMucManager = getSmackMucManager()
            val domain = mucServiceDomain?.trim() ?: run {
                val accountDomain = connectionManager.credentials.value.domain
                "conference.$accountDomain"
            }

            val domainBareJid = JidCreate.domainBareFrom(domain)
            val hostedRoomsMap = smackMucManager.getRoomsHostedBy(domainBareJid)

            val directoryRooms = hostedRoomsMap.values.map { hosted ->
                val jidStr = hosted.jid.toString()
                var occupantCount = 0
                var topic = ""
                try {
                    val info = smackMucManager.getRoomInfo(hosted.jid)
                    occupantCount = info.occupantsCount
                    topic = info.description ?: info.subject ?: ""
                } catch (e: Exception) {
                    // Ignore individual room info query failures
                }

                PublicDirectoryRoom(
                    jid = jidStr,
                    name = hosted.name ?: jidStr.substringBefore("@"),
                    description = topic.ifBlank { "Ruang obrolan publik di $domain" },
                    occupantsCount = occupantCount
                )
            }

            Log.i(TAG, "Discovered ${directoryRooms.size} hosted rooms on $domain")
            Result.success(directoryRooms)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to discover public rooms on $mucServiceDomain: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Leaves all active MUC rooms gracefully.
     */
    suspend fun leaveAllRooms() = withContext(Dispatchers.IO) {
        for ((jid, muc) in activeMucs) {
            try {
                if (muc.isJoined) {
                    muc.leave()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error leaving room $jid during teardown: ${e.message}")
            }
        }
        activeMucs.clear()
        roomNicknames.clear()
        roomMessageListeners.clear()
        _joinedRooms.value = emptySet()
        _roomOccupants.value = emptyMap()
    }

    /**
     * Checks if a room is currently joined.
     */
    fun isRoomJoined(roomJid: String): Boolean = activeMucs[roomJid]?.isJoined == true

    /**
     * Retrieves the active [MultiUserChat] instance for a room, or null if not joined.
     */
    fun getMultiUserChat(roomJid: String): MultiUserChat? = activeMucs[roomJid]

    /**
     * Returns current cached occupant list for a room.
     */
    fun getOccupants(roomJid: String): List<RoomOccupant> = _roomOccupants.value[roomJid] ?: emptyList()

    /**
     * Attaches a [MessageListener] implementation to a joined MUC room.
     *
     * @param roomJid Room bare JID
     * @param listener The MessageListener instance
     * @return true if successfully attached to the room
     */
    fun addMessageListener(roomJid: String, listener: MessageListener): Boolean {
        val muc = activeMucs[roomJid] ?: return false
        val success = muc.addMessageListener(listener)
        if (success) {
            roomMessageListeners.getOrPut(roomJid) { mutableListOf() }.add(listener)
        }
        return success
    }

    /**
     * Detaches a [MessageListener] from a joined MUC room.
     */
    fun removeMessageListener(roomJid: String, listener: MessageListener): Boolean {
        val muc = activeMucs[roomJid] ?: return false
        val success = muc.removeMessageListener(listener)
        roomMessageListeners[roomJid]?.remove(listener)
        return success
    }

    /**
     * Hooks a new [MucMessageListener] into a joined MUC room to process incoming messages.
     *
     * @param roomJid Room bare JID
     * @param onMessageReceived Callback receiving parsed [XmppMessage] stanzas
     * @return The configured [MucMessageListener], or null if room is not joined
     */
    fun hookMessageListener(
        roomJid: String,
        onMessageReceived: (XmppMessage) -> Unit
    ): MucMessageListener? {
        val muc = activeMucs[roomJid] ?: return null
        val listener = MucMessageListener(
            roomJid = roomJid,
            myNicknameProvider = { roomNicknames[roomJid] ?: "" },
            onMessageProcessed = onMessageReceived
        )
        if (muc.addMessageListener(listener)) {
            roomMessageListeners.getOrPut(roomJid) { mutableListOf() }.add(listener)
            return listener
        }
        return null
    }

    /**
     * Attaches message, presence, and subject listeners to a [MultiUserChat] instance.
     */
    private fun registerMucListeners(roomJid: String, muc: MultiUserChat, myNickname: String) {
        // Instantiate and hook our dedicated MucMessageListener implementation
        val mucMessageListener = MucMessageListener(
            roomJid = roomJid,
            myNicknameProvider = { roomNicknames[roomJid] ?: myNickname },
            onMessageProcessed = { xmppMessage ->
                _incomingMessages.tryEmit(xmppMessage)
            },
            onTopicUpdated = { jid, newTopic ->
                _topicUpdates.tryEmit(Pair(jid, newTopic))
            }
        )
        muc.addMessageListener(mucMessageListener)
        roomMessageListeners.getOrPut(roomJid) { mutableListOf() }.add(mucMessageListener)

        // Participant presence listener
        muc.addParticipantListener(PresenceListener { presence ->
            Log.d(TAG, "MUC [$roomJid] Participant presence update: ${presence.from} -> ${presence.type}")
            updateOccupantsForRoom(roomJid, muc)
        })

        // Subject / Topic change listener
        muc.addSubjectUpdatedListener(SubjectUpdatedListener { newSubject, from ->
            if (newSubject != null) {
                Log.i(TAG, "MUC [$roomJid] Subject updated to '$newSubject' by $from")
                _topicUpdates.tryEmit(Pair(roomJid, newSubject))
            }
        })
    }

    /**
     * Refreshes the cached list of occupants for a specific room.
     */
    private fun updateOccupantsForRoom(roomJid: String, muc: MultiUserChat) {
        try {
            val myNickname = roomNicknames[roomJid] ?: ""
            val occupantsList = mutableListOf<RoomOccupant>()

            for (occupantJid in muc.occupants) {
                val occupant: Occupant? = muc.getOccupant(occupantJid)
                val nickname = occupant?.nick?.toString()
                    ?: occupantJid.resourceOrNull?.toString()
                    ?: occupantJid.toString().substringAfterLast("/")

                val isMe = nickname.equals(myNickname, ignoreCase = true)

                val role = when (occupant?.role) {
                    MUCRole.moderator -> OccupantRole.ADMIN
                    MUCRole.participant -> OccupantRole.MEMBER
                    MUCRole.visitor -> OccupantRole.VISITOR
                    else -> OccupantRole.NONE
                }

                val affiliation = when (occupant?.affiliation) {
                    MUCAffiliation.owner -> OccupantAffiliation.OWNER
                    MUCAffiliation.admin -> OccupantAffiliation.ADMIN
                    MUCAffiliation.member -> OccupantAffiliation.MEMBER
                    MUCAffiliation.outcast -> OccupantAffiliation.OUTCAST
                    else -> OccupantAffiliation.NONE
                }

                occupantsList.add(
                    RoomOccupant(
                        nickname = nickname,
                        jid = occupant?.jid?.toString() ?: occupantJid.toString(),
                        role = role,
                        affiliation = affiliation,
                        presence = UserPresence.ONLINE,
                        statusText = if (isMe) "Anda" else "Online",
                        isMe = isMe
                    )
                )
            }

            val currentMap = _roomOccupants.value.toMutableMap()
            currentMap[roomJid] = occupantsList
            _roomOccupants.value = currentMap
        } catch (e: Exception) {
            Log.w(TAG, "Error updating occupants for $roomJid: ${e.message}")
        }
    }
}
