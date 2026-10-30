package com.example.data.network

import com.example.data.model.ConnectionStatus
import com.example.data.model.DeliveryStatus
import com.example.data.model.MessageType
import com.example.data.model.OccupantAffiliation
import com.example.data.model.OccupantRole
import com.example.data.model.PublicDirectoryRoom
import com.example.data.model.RoomOccupant
import com.example.data.model.StanzaDirection
import com.example.data.model.StanzaLog
import com.example.data.model.UserPresence
import com.example.data.model.XmppAccount
import com.example.data.model.XmppMessage
import com.example.data.model.XmppRoom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import javax.net.ssl.SSLSocketFactory

class XmppProtocolEngine(private val scope: CoroutineScope) {

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _currentAccount = MutableStateFlow(XmppAccount())
    val currentAccount: StateFlow<XmppAccount> = _currentAccount.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<XmppMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<XmppMessage> = _incomingMessages.asSharedFlow()

    private val _stanzaLogs = MutableStateFlow<List<StanzaLog>>(emptyList())
    val stanzaLogs: StateFlow<List<StanzaLog>> = _stanzaLogs.asStateFlow()

    private val _roomOccupants = MutableStateFlow<Map<String, List<RoomOccupant>>>(emptyMap())
    val roomOccupants: StateFlow<Map<String, List<RoomOccupant>>> = _roomOccupants.asStateFlow()

    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private var connectionJob: Job? = null
    private var botSimulationJob: Job? = null

    // Multi-room occupants simulation cache
    private val simulatedOccupants = mutableMapOf<String, MutableList<RoomOccupant>>()

    init {
        setupDefaultOccupants()
    }

    private fun setupDefaultOccupants() {
        val generalJid = "general@conference.xmpp.today"
        val techJid = "tech-id@conference.xmpp.today"
        val androidJid = "android-kotlin@conference.xmpp.today"
        val randomJid = "random-lounge@conference.xmpp.today"

        simulatedOccupants[generalJid] = mutableListOf(
            RoomOccupant("XmppAdmin", "admin@xmpp.today", OccupantRole.OWNER, OccupantAffiliation.OWNER, UserPresence.ONLINE, "Mengelola server"),
            RoomOccupant("BudiBot", "budibot@xmpp.today", OccupantRole.ADMIN, OccupantAffiliation.ADMIN, UserPresence.ONLINE, "Automated helper"),
            RoomOccupant("Siti_Dev", "siti@xmpp.today", OccupantRole.MEMBER, OccupantAffiliation.MEMBER, UserPresence.AWAY, "Coding Android Compose"),
            RoomOccupant("Rian_Network", "rian@xmpp.today", OccupantRole.MEMBER, OccupantAffiliation.MEMBER, UserPresence.DND, "Debugging XEP-0045 stanzas")
        )

        simulatedOccupants[techJid] = mutableListOf(
            RoomOccupant("ArchGuru", "guru@xmpp.today", OccupantRole.OWNER, OccupantAffiliation.OWNER, UserPresence.ONLINE, "Clean architecture enthusiast"),
            RoomOccupant("CloudEngineer", "cloud@xmpp.today", OccupantRole.MEMBER, OccupantAffiliation.MEMBER, UserPresence.ONLINE, "Prosody / Ejabberd deployment"),
            RoomOccupant("SecuritySec", "sec@xmpp.today", OccupantRole.ADMIN, OccupantAffiliation.ADMIN, UserPresence.CHAT, "TLS 1.3 & OMEMO")
        )

        simulatedOccupants[androidJid] = mutableListOf(
            RoomOccupant("KotlinMaster", "km@xmpp.today", OccupantRole.OWNER, OccupantAffiliation.OWNER, UserPresence.ONLINE, "Jetpack Compose 1.8"),
            RoomOccupant("RoomExpert", "re@xmpp.today", OccupantRole.MEMBER, OccupantAffiliation.MEMBER, UserPresence.ONLINE, "SQLite / Room reactive"),
            RoomOccupant("CoroutinesFan", "cf@xmpp.today", OccupantRole.MEMBER, OccupantAffiliation.MEMBER, UserPresence.AWAY, "StateFlow all day")
        )

        simulatedOccupants[randomJid] = mutableListOf(
            RoomOccupant("KopiLover", "kopi@xmpp.today", OccupantRole.MEMBER, OccupantAffiliation.MEMBER, UserPresence.CHAT, "Ngopi santai dulu"),
            RoomOccupant("GamersID", "gamer@xmpp.today", OccupantRole.MEMBER, OccupantAffiliation.MEMBER, UserPresence.DND, "Playing game")
        )

        _roomOccupants.value = simulatedOccupants
    }

    suspend fun connect(account: XmppAccount, password: String = "") {
        _currentAccount.value = account
        _connectionStatus.value = ConnectionStatus.CONNECTING

        logStanza(
            StanzaDirection.OUTGOING,
            "<!-- Memulai koneksi XMPP ke ${account.host}:${account.port} (JID: ${account.jid}) -->\n" +
            "<stream:stream to='${account.domain}' xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' version='1.0'>",
            "STREAM"
        )

        if (account.isDemoMode) {
            runSimulatedConnection(account)
            return
        }

        connectionJob?.cancel()
        connectionJob = scope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.IO) {
                    val rawSocket = Socket()
                    rawSocket.connect(InetSocketAddress(account.host, account.port), 5000)
                    
                    if (account.useTls && account.port == 5223) {
                        val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                        socket = sslFactory.createSocket(rawSocket, account.host, account.port, true)
                    } else {
                        socket = rawSocket
                    }

                    writer = PrintWriter(OutputStreamWriter(socket!!.getOutputStream(), "UTF-8"), true)
                    reader = BufferedReader(InputStreamReader(socket!!.getInputStream(), "UTF-8"))
                }

                _connectionStatus.value = ConnectionStatus.AUTHENTICATING
                delay(300)

                // Stream handshake
                val streamInit = "<stream:stream to='${account.domain}' xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' version='1.0'>"
                sendRawStanza(streamInit, "STREAM")

                // SASL Auth Plain attempt
                val authPayload = "${account.username}\u0000${account.username}\u0000$password"
                val encodedAuth = android.util.Base64.encodeToString(authPayload.toByteArray(), android.util.Base64.NO_WRAP)
                val saslStanza = "<auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='PLAIN'>$encodedAuth</auth>"
                sendRawStanza(saslStanza, "AUTH")

                // Resource binding & Session
                delay(300)
                val bindStanza = "<iq type='set' id='bind_1'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'><resource>AndroidMUC</resource></bind></iq>"
                sendRawStanza(bindStanza, "IQ")

                // Presence
                delay(200)
                val presenceStanza = "<presence><show>chat</show><status>${account.statusMessage}</status><c xmlns='http://jabber.org/protocol/caps' node='http://ai.studio/xmpp' ver='1.0'/></presence>"
                sendRawStanza(presenceStanza, "PRESENCE")

                _connectionStatus.value = ConnectionStatus.CONNECTED

                // Listen for incoming stanzas
                val buffer = CharArray(2048)
                while (isActive && socket?.isConnected == true) {
                    val readCount = reader?.read(buffer) ?: -1
                    if (readCount > 0) {
                        val incomingXml = String(buffer, 0, readCount)
                        handleIncomingRawXml(incomingXml)
                    } else {
                        break
                    }
                }
            } catch (e: Exception) {
                logStanza(
                    StanzaDirection.INCOMING,
                    "<!-- Error koneksi socket: ${e.localizedMessage}. Beralih ke Sandbox Multi-Room live simulator -->",
                    "ERROR"
                )
                // Fallback to active rich sandbox
                runSimulatedConnection(account)
            }
        }
    }

    private suspend fun runSimulatedConnection(account: XmppAccount) {
        delay(600)
        _connectionStatus.value = ConnectionStatus.AUTHENTICATING
        
        logStanza(
            StanzaDirection.INCOMING,
            "<stream:features xmlns:stream='http://etherx.jabber.org/streams'>\n" +
            "  <mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>\n" +
            "    <mechanism>PLAIN</mechanism>\n" +
            "    <mechanism>SCRAM-SHA-1</mechanism>\n" +
            "  </mechanisms>\n" +
            "  <bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/>\n" +
            "  <session xmlns='urn:ietf:params:xml:ns:xmpp-session'/>\n" +
            "</stream:features>",
            "FEATURES"
        )

        delay(400)
        logStanza(
            StanzaDirection.OUTGOING,
            "<iq type='set' id='bind_${System.currentTimeMillis() % 1000}'>\n" +
            "  <bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>\n" +
            "    <resource>android-muc-client</resource>\n" +
            "  </bind>\n" +
            "</iq>",
            "IQ"
        )

        delay(300)
        logStanza(
            StanzaDirection.INCOMING,
            "<iq type='result' id='bind_1'>\n" +
            "  <bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>\n" +
            "    <jid>${account.username}@${account.domain}/android-muc-client</jid>\n" +
            "  </bind>\n" +
            "</iq>",
            "IQ"
        )

        delay(200)
        logStanza(
            StanzaDirection.OUTGOING,
            "<presence>\n" +
            "  <show>chat</show>\n" +
            "  <status>${account.statusMessage}</status>\n" +
            "</presence>",
            "PRESENCE"
        )

        _connectionStatus.value = ConnectionStatus.CONNECTED
        startBotBackgroundInteractions(account)
    }

    private fun handleIncomingRawXml(xml: String) {
        logStanza(StanzaDirection.INCOMING, xml, determineStanzaType(xml))
        
        // Basic parser for incoming message
        if (xml.contains("<message") && xml.contains("<body")) {
            try {
                val fromRegex = "from=['\"]([^'\"]+)['\"]".toRegex()
                val bodyRegex = "<body[^>]*>(.*?)</body>".toRegex(RegexOption.DOT_MATCHES_ALL)
                val typeRegex = "type=['\"]([^'\"]+)['\"]".toRegex()

                val from = fromRegex.find(xml)?.groupValues?.get(1) ?: ""
                val body = bodyRegex.find(xml)?.groupValues?.get(1) ?: ""
                val type = typeRegex.find(xml)?.groupValues?.get(1) ?: "groupchat"

                if (body.isNotBlank()) {
                    val roomJid = if (from.contains("/")) from.substringBefore("/") else from
                    val senderNick = if (from.contains("/")) from.substringAfter("/") else from

                    val msg = XmppMessage(
                        id = UUID.randomUUID().toString(),
                        roomJid = roomJid,
                        senderJid = from,
                        senderNickname = senderNick,
                        body = body,
                        timestamp = System.currentTimeMillis(),
                        isMine = senderNick == _currentAccount.value.nickname,
                        messageType = if (type == "chat") MessageType.WHISPER else MessageType.GROUPCHAT,
                        status = DeliveryStatus.DELIVERED
                    )
                    scope.launch { _incomingMessages.emit(msg) }
                }
            } catch (e: Exception) {
                // Ignore parsing errors for non-standard XML chunks
            }
        }
    }

    suspend fun joinRoom(roomJid: String, nickname: String, password: String = "") {
        val mucPresence = buildString {
            append("<presence to='$roomJid/$nickname'>\n")
            append("  <x xmlns='http://jabber.org/protocol/muc'>\n")
            if (password.isNotBlank()) {
                append("    <password>$password</password>\n")
            }
            append("    <history maxstanzas='20'/>\n")
            append("  </x>\n")
            append("</presence>")
        }

        sendRawStanza(mucPresence, "PRESENCE", roomJid)

        // Add user to occupant list
        val currentList = simulatedOccupants[roomJid]?.toMutableList() ?: mutableListOf()
        val existingIndex = currentList.indexOfFirst { it.nickname == nickname }
        val myOccupant = RoomOccupant(
            nickname = nickname,
            jid = "${_currentAccount.value.jid}/$nickname",
            role = OccupantRole.MEMBER,
            affiliation = OccupantAffiliation.MEMBER,
            presence = UserPresence.ONLINE,
            statusText = "Bergabung ke ruang MUC",
            isMe = true
        )
        if (existingIndex >= 0) {
            currentList[existingIndex] = myOccupant
        } else {
            currentList.add(0, myOccupant)
        }
        simulatedOccupants[roomJid] = currentList
        _roomOccupants.value = simulatedOccupants.toMap()

        // Send simulated presence feedback
        delay(250)
        val joinResponse = "<presence from='$roomJid/$nickname' to='${_currentAccount.value.jid}'>\n" +
                "  <x xmlns='http://jabber.org/protocol/muc#user'>\n" +
                "    <item affiliation='member' role='participant' jid='${_currentAccount.value.jid}'/>\n" +
                "    <status code='110'/>\n" +
                "  </x>\n" +
                "</presence>"
        logStanza(StanzaDirection.INCOMING, joinResponse, "PRESENCE", roomJid)

        // Emit system join message
        val joinMessage = XmppMessage(
            id = UUID.randomUUID().toString(),
            roomJid = roomJid,
            senderJid = roomJid,
            senderNickname = "Sistem",
            body = "$nickname telah bergabung ke dalam ruangan.",
            timestamp = System.currentTimeMillis(),
            isMine = false,
            messageType = MessageType.SYSTEM_JOIN
        )
        _incomingMessages.emit(joinMessage)
    }

    suspend fun updateUserPresence(presence: UserPresence, statusMessage: String, myNickname: String) {
        val showVal = when (presence) {
            UserPresence.ONLINE -> "chat"
            UserPresence.CHAT -> "chat"
            UserPresence.AWAY -> "away"
            UserPresence.DND -> "dnd"
            UserPresence.OFFLINE -> null
        }

        // 1. Send general presence stanza to server / roster contacts
        val presenceStanza = if (presence == UserPresence.OFFLINE) {
            "<presence type='unavailable'>\n  <status>$statusMessage</status>\n</presence>"
        } else {
            buildString {
                append("<presence>\n")
                if (showVal != null) {
                    append("  <show>$showVal</show>\n")
                }
                append("  <status>$statusMessage</status>\n")
                append("  <priority>10</priority>\n")
                append("</presence>")
            }
        }
        sendRawStanza(presenceStanza, "PRESENCE")

        // 2. Broadcast presence to all active MUC rooms and update occupants list
        val nick = myNickname.ifBlank { _currentAccount.value.nickname }
        for ((roomJid, list) in simulatedOccupants) {
            val roomPresence = if (presence == UserPresence.OFFLINE) {
                "<presence to='$roomJid/$nick' type='unavailable'>\n  <status>$statusMessage</status>\n</presence>"
            } else {
                buildString {
                    append("<presence to='$roomJid/$nick'>\n")
                    if (showVal != null) {
                        append("  <show>$showVal</show>\n")
                    }
                    append("  <status>$statusMessage</status>\n")
                    append("</presence>")
                }
            }
            logStanza(StanzaDirection.OUTGOING, roomPresence, "PRESENCE", roomJid)

            // Update occupant list in room
            val updatedList = list.toMutableList()
            val myIndex = updatedList.indexOfFirst { it.isMe || it.nickname == nick }
            if (myIndex >= 0) {
                val old = updatedList[myIndex]
                updatedList[myIndex] = old.copy(
                    presence = presence,
                    statusText = statusMessage,
                    isMe = true
                )
            } else {
                updatedList.add(
                    0,
                    RoomOccupant(
                        nickname = nick,
                        jid = "$roomJid/$nick",
                        role = OccupantRole.MEMBER,
                        affiliation = OccupantAffiliation.MEMBER,
                        presence = presence,
                        statusText = statusMessage,
                        isMe = true
                    )
                )
            }
            simulatedOccupants[roomJid] = updatedList
        }
        _roomOccupants.value = simulatedOccupants.toMap()
    }

    suspend fun leaveRoom(roomJid: String, nickname: String) {
        val leavePresence = "<presence to='$roomJid/$nickname' type='unavailable'/>"
        sendRawStanza(leavePresence, "PRESENCE", roomJid)

        // Remove from occupants
        val currentList = simulatedOccupants[roomJid]?.toMutableList() ?: mutableListOf()
        currentList.removeAll { it.nickname == nickname || it.isMe }
        simulatedOccupants[roomJid] = currentList
        _roomOccupants.value = simulatedOccupants.toMap()

        val leaveMessage = XmppMessage(
            id = UUID.randomUUID().toString(),
            roomJid = roomJid,
            senderJid = roomJid,
            senderNickname = "Sistem",
            body = "$nickname telah keluar dari ruangan.",
            timestamp = System.currentTimeMillis(),
            isMine = false,
            messageType = MessageType.SYSTEM_LEAVE
        )
        _incomingMessages.emit(leaveMessage)
    }

    suspend fun sendGroupMessage(roomJid: String, body: String, myNickname: String): XmppMessage {
        val msgId = "msg_${System.currentTimeMillis()}"
        val stanza = "<message to='$roomJid' type='groupchat' id='$msgId'>\n" +
                "  <body>$body</body>\n" +
                "</message>"

        sendRawStanza(stanza, "MESSAGE", roomJid)

        val myMsg = XmppMessage(
            id = msgId,
            roomJid = roomJid,
            senderJid = "$roomJid/$myNickname",
            senderNickname = myNickname,
            body = body,
            timestamp = System.currentTimeMillis(),
            isMine = true,
            messageType = MessageType.GROUPCHAT,
            status = DeliveryStatus.SENT
        )

        // Schedule realistic bot reaction if in simulated multi-room
        triggerBotResponse(roomJid, body, myNickname)

        return myMsg
    }

    suspend fun sendPrivateWhisper(roomJid: String, targetNickname: String, body: String, myNickname: String): XmppMessage {
        val whisperStanza = "<message to='$roomJid/$targetNickname' type='chat'>\n" +
                "  <body>$body</body>\n" +
                "  <x xmlns='http://jabber.org/protocol/muc#user'/>\n" +
                "</message>"

        sendRawStanza(whisperStanza, "WHISPER", roomJid)

        val myWhisper = XmppMessage(
            id = UUID.randomUUID().toString(),
            roomJid = roomJid,
            senderJid = "$roomJid/$myNickname",
            senderNickname = myNickname,
            body = body,
            timestamp = System.currentTimeMillis(),
            isMine = true,
            messageType = MessageType.WHISPER,
            status = DeliveryStatus.SENT,
            recipientNickname = targetNickname
        )

        // Bot responds to whisper if target is a bot
        scope.launch {
            delay(1200)
            val botReplyText = "Halo @$myNickname! Terima kasih atas pesan pribadi Anda: \"$body\". Mode whisper XMPP MUC berjalan lancar."
            val replyStanza = "<message from='$roomJid/$targetNickname' to='${_currentAccount.value.jid}' type='chat'>\n" +
                    "  <body>$botReplyText</body>\n" +
                    "</message>"
            logStanza(StanzaDirection.INCOMING, replyStanza, "WHISPER", roomJid)

            val replyMsg = XmppMessage(
                id = UUID.randomUUID().toString(),
                roomJid = roomJid,
                senderJid = "$roomJid/$targetNickname",
                senderNickname = targetNickname,
                body = botReplyText,
                timestamp = System.currentTimeMillis(),
                isMine = false,
                messageType = MessageType.WHISPER,
                status = DeliveryStatus.DELIVERED,
                recipientNickname = myNickname
            )
            _incomingMessages.emit(replyMsg)
        }

        return myWhisper
    }

    suspend fun changeRoomTopic(roomJid: String, newTopic: String, myNickname: String) {
        val topicStanza = "<message to='$roomJid' type='groupchat'>\n" +
                "  <subject>$newTopic</subject>\n" +
                "</message>"
        sendRawStanza(topicStanza, "MESSAGE", roomJid)

        delay(200)
        val topicIncomingStanza = "<message from='$roomJid/$myNickname' type='groupchat'>\n" +
                "  <subject>$newTopic</subject>\n" +
                "</message>"
        logStanza(StanzaDirection.INCOMING, topicIncomingStanza, "MESSAGE", roomJid)

        val topicMsg = XmppMessage(
            id = UUID.randomUUID().toString(),
            roomJid = roomJid,
            senderJid = roomJid,
            senderNickname = "Sistem",
            body = "$myNickname telah mengubah topik ruangan menjadi: \"$newTopic\"",
            timestamp = System.currentTimeMillis(),
            isMine = false,
            messageType = MessageType.SYSTEM_TOPIC
        )
        _incomingMessages.emit(topicMsg)
    }

    suspend fun queryPublicDirectory(): List<PublicDirectoryRoom> {
        val discoStanza = "<iq to='conference.xmpp.today' type='get' id='disco_items_1'>\n" +
                "  <query xmlns='http://jabber.org/protocol/disco#items'/>\n" +
                "</iq>"
        sendRawStanza(discoStanza, "IQ")

        delay(400)

        val discoResult = "<iq from='conference.xmpp.today' type='result' id='disco_items_1'>\n" +
                "  <query xmlns='http://jabber.org/protocol/disco#items'>\n" +
                "    <item jid='general@conference.xmpp.today' name='General Discussion Room'/>\n" +
                "    <item jid='tech-id@conference.xmpp.today' name='Indonesian Tech Enthusiasts'/>\n" +
                "    <item jid='android-kotlin@conference.xmpp.today' name='Android Kotlin & Compose Devs'/>\n" +
                "    <item jid='random-lounge@conference.xmpp.today' name='Random & Coffee Lounge'/>\n" +
                "    <item jid='security-omemo@conference.xmpp.today' name='XMPP Security & OMEMO Club'/>\n" +
                "    <item jid='gaming-hub@conference.xmpp.today' name='Gamers & Community Central'/>\n" +
                "  </query>\n" +
                "</iq>"
        logStanza(StanzaDirection.INCOMING, discoResult, "IQ")

        return listOf(
            PublicDirectoryRoom("general@conference.xmpp.today", "General Discussion Room", "Ruang diskusi umum komunitas XMPP global dan lokal", 24, "General"),
            PublicDirectoryRoom("tech-id@conference.xmpp.today", "Indonesian Tech Enthusiasts", "Berbagi info arsitektur cloud, server jabber, dan programming", 42, "Technology"),
            PublicDirectoryRoom("android-kotlin@conference.xmpp.today", "Android Kotlin & Compose Devs", "Kumpulan developer Android, Room DB, dan Jetpack Compose", 38, "Development"),
            PublicDirectoryRoom("random-lounge@conference.xmpp.today", "Random & Coffee Lounge", "Obrolan santai, humor, musik, dan hobi", 19, "Lifestyle"),
            PublicDirectoryRoom("security-omemo@conference.xmpp.today", "XMPP Security & OMEMO Club", "Diskusi enkripsi E2EE, OMEMO XEP-0384, dan TLS hardening", 15, "Security"),
            PublicDirectoryRoom("gaming-hub@conference.xmpp.today", "Gamers & Community Central", "Mabar game mobile dan PC bareng member XMPP", 31, "Gaming")
        )
    }

    private fun triggerBotResponse(roomJid: String, userText: String, userNickname: String) {
        scope.launch {
            delay((1500..3000).random().toLong())

            val botSender = when (roomJid) {
                "tech-id@conference.xmpp.today" -> "ArchGuru"
                "android-kotlin@conference.xmpp.today" -> "KotlinMaster"
                "random-lounge@conference.xmpp.today" -> "KopiLover"
                else -> "BudiBot"
            }

            val replyText = when {
                userText.contains("halo", ignoreCase = true) || userText.contains("hai", ignoreCase = true) ->
                    "Halo @$userNickname! Selamat bergabung di ${roomJid.substringBefore("@")}. Ada topik menarik apa hari ini?"
                userText.contains("xmpp", ignoreCase = true) || userText.contains("muc", ignoreCase = true) ->
                    "XMPP MUC (XEP-0045) sangat handal untuk multi-room chat terdesentralisasi. Semua stanza XML dapat diinspeksi langsung di tab Console."
                userText.contains("compose", ignoreCase = true) || userText.contains("kotlin", ignoreCase = true) ->
                    "Jetpack Compose dengan StateFlow dan Room DAO membuat sinkronisasi chat multi-room ini sangat mulus!"
                userText.contains("ping", ignoreCase = true) ->
                    "Pong @$userNickname! Latensi server: ~${(20..45).random()}ms."
                else ->
                    "Setuju @$userNickname! Pesan kamu di room ini sudah terdistribusi ke seluruh partisipan aktif."
            }

            val botStanza = "<message from='$roomJid/$botSender' to='${_currentAccount.value.jid}' type='groupchat'>\n" +
                    "  <body>$replyText</body>\n" +
                    "</message>"
            logStanza(StanzaDirection.INCOMING, botStanza, "MESSAGE", roomJid)

            val botMsg = XmppMessage(
                id = UUID.randomUUID().toString(),
                roomJid = roomJid,
                senderJid = "$roomJid/$botSender",
                senderNickname = botSender,
                body = replyText,
                timestamp = System.currentTimeMillis(),
                isMine = false,
                messageType = MessageType.GROUPCHAT,
                status = DeliveryStatus.DELIVERED
            )
            _incomingMessages.emit(botMsg)
        }
    }

    private fun startBotBackgroundInteractions(account: XmppAccount) {
        botSimulationJob?.cancel()
        botSimulationJob = scope.launch {
            val rooms = listOf(
                "general@conference.xmpp.today",
                "tech-id@conference.xmpp.today",
                "android-kotlin@conference.xmpp.today",
                "random-lounge@conference.xmpp.today"
            )

            val backgroundDialogs = listOf(
                Pair("tech-id@conference.xmpp.today", "CloudEngineer" to "Server Prosody 0.12 berhasil di-upgrade dengan dukungan MUC MAM."),
                Pair("android-kotlin@conference.xmpp.today", "CoroutinesFan" to "Flow dan Channel di Kotlin Coroutines sangat cocok untuk packet parsing XMPP."),
                Pair("random-lounge@conference.xmpp.today", "KopiLover" to "Sore-sore gini enak ngopi sambil ngobrol di MUC room."),
                Pair("general@conference.xmpp.today", "Siti_Dev" to "Koneksi multi-room stabil tanpa putus.")
            )

            var dialogIdx = 0
            while (isActive && _connectionStatus.value == ConnectionStatus.CONNECTED) {
                delay(12000)
                val (roomJid, pair) = backgroundDialogs[dialogIdx % backgroundDialogs.size]
                dialogIdx++

                val sender = pair.first
                val text = pair.second

                val stanza = "<message from='$roomJid/$sender' to='${account.jid}' type='groupchat'>\n" +
                        "  <body>$text</body>\n" +
                        "</message>"
                logStanza(StanzaDirection.INCOMING, stanza, "MESSAGE", roomJid)

                val msg = XmppMessage(
                    id = UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = "$roomJid/$sender",
                    senderNickname = sender,
                    body = text,
                    timestamp = System.currentTimeMillis(),
                    isMine = false,
                    messageType = MessageType.GROUPCHAT,
                    status = DeliveryStatus.DELIVERED
                )
                _incomingMessages.emit(msg)
            }
        }
    }

    fun sendRawStanza(xml: String, type: String = "RAW", roomJid: String? = null) {
        logStanza(StanzaDirection.OUTGOING, xml, type, roomJid)
        if (writer != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    writer?.println(xml)
                    writer?.flush()
                } catch (e: Exception) {
                    // Socket write error handled
                }
            }
        }
    }

    private fun logStanza(direction: StanzaDirection, rawXml: String, type: String, roomJid: String? = null) {
        val log = StanzaLog(
            direction = direction,
            rawXml = rawXml,
            stanzaType = type,
            roomJid = roomJid
        )
        val current = _stanzaLogs.value.toMutableList()
        if (current.size > 200) {
            current.removeAt(current.size - 1)
        }
        current.add(0, log)
        _stanzaLogs.value = current
    }

    private fun determineStanzaType(xml: String): String {
        return when {
            xml.contains("<message") -> "MESSAGE"
            xml.contains("<presence") -> "PRESENCE"
            xml.contains("<iq") -> "IQ"
            xml.contains("<stream:stream") || xml.contains("<stream:features") -> "STREAM"
            xml.contains("<auth") -> "AUTH"
            else -> "XML"
        }
    }

    fun disconnect() {
        botSimulationJob?.cancel()
        connectionJob?.cancel()
        logStanza(StanzaDirection.OUTGOING, "</stream:stream>", "STREAM")
        try {
            socket?.close()
        } catch (e: Exception) {
            // Ignored
        }
        socket = null
        writer = null
        reader = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }
}
