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

/**
 * Low-level XMPP engine that manages a raw socket connection for XML stream inspection.
 * In a production app, this usually acts as a secondary stream or is integrated into Smack.
 * Here we use it to provide the "XML Console" (Stanza Logs) feature.
 */
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

    // Room occupants cache from real XMPP events
    private val occupantsMap = mutableMapOf<String, MutableList<RoomOccupant>>()

    suspend fun connect(account: XmppAccount, password: String = "") {
        _currentAccount.value = account
        _connectionStatus.value = ConnectionStatus.CONNECTING

        logStanza(
            StanzaDirection.OUTGOING,
            "<!-- Memulai koneksi XMPP raw socket ke ${account.host}:${account.port} -->\n" +
            "<stream:stream to='${account.domain}' xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' version='1.0'>",
            "STREAM"
        )

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
                
                // Stream handshake
                sendRawStanza("<stream:stream to='${account.domain}' xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' version='1.0'>", "STREAM")

                // SASL Auth Plain attempt (Minimal implementation for raw stream)
                val authPayload = "${account.username}\u0000${account.username}\u0000$password"
                val encodedAuth = android.util.Base64.encodeToString(authPayload.toByteArray(), android.util.Base64.NO_WRAP)
                sendRawStanza("<auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='PLAIN'>$encodedAuth</auth>", "AUTH")

                // Resource binding
                delay(300)
                sendRawStanza("<iq type='set' id='bind_1'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'><resource>AndroidMUC-Raw</resource></bind></iq>", "IQ")

                // Presence
                delay(200)
                sendRawStanza("<presence><show>chat</show><status>${account.statusMessage}</status></presence>", "PRESENCE")

                _connectionStatus.value = ConnectionStatus.CONNECTED

                // Listen for incoming stanzas
                val buffer = CharArray(4096)
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
                logStanza(StanzaDirection.INCOMING, "<!-- Error koneksi raw socket: ${e.localizedMessage} -->", "ERROR")
                _connectionStatus.value = ConnectionStatus.ERROR
            }
        }
    }

    private fun handleIncomingRawXml(xml: String) {
        logStanza(StanzaDirection.INCOMING, xml, determineStanzaType(xml))
        
        // Basic parser for incoming message to allow some interactivity even in raw mode
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

                    val isUrl = body.trim().startsWith("http")
                    val mType = when {
                        type == "chat" -> MessageType.WHISPER
                        isUrl -> MessageType.FILE
                        else -> MessageType.GROUPCHAT
                    }

                    val msg = XmppMessage(
                        id = UUID.randomUUID().toString(),
                        roomJid = roomJid,
                        senderJid = from,
                        senderNickname = senderNick,
                        body = body,
                        timestamp = System.currentTimeMillis(),
                        isMine = senderNick == _currentAccount.value.nickname,
                        messageType = mType,
                        status = DeliveryStatus.DELIVERED,
                        fileUrl = if (isUrl) body.trim() else null
                    )
                    scope.launch { _incomingMessages.emit(msg) }
                }
            } catch (e: Exception) {}
        }
    }

    suspend fun joinRoom(roomJid: String, nickname: String, password: String = "") {
        val mucPresence = buildString {
            append("<presence to='$roomJid/$nickname'>\n")
            append("  <x xmlns='http://jabber.org/protocol/muc'>\n")
            if (password.isNotBlank()) append("    <password>$password</password>\n")
            append("    <history maxstanzas='20'/>\n")
            append("  </x>\n")
            append("</presence>")
        }
        sendRawStanza(mucPresence, "PRESENCE", roomJid)
    }

    suspend fun updateUserPresence(presence: UserPresence, statusMessage: String, myNickname: String) {
        val showVal = when (presence) {
            UserPresence.ONLINE, UserPresence.CHAT -> "chat"
            UserPresence.AWAY -> "away"
            UserPresence.DND -> "dnd"
            UserPresence.OFFLINE -> null
        }

        val presenceStanza = if (presence == UserPresence.OFFLINE) {
            "<presence type='unavailable'><status>$statusMessage</status></presence>"
        } else {
            buildString {
                append("<presence>")
                if (showVal != null) append("<show>$showVal</show>")
                append("<status>$statusMessage</status>")
                append("</presence>")
            }
        }
        sendRawStanza(presenceStanza, "PRESENCE")
    }

    suspend fun leaveRoom(roomJid: String, nickname: String) {
        val leavePresence = "<presence to='$roomJid/$nickname' type='unavailable'/>"
        sendRawStanza(leavePresence, "PRESENCE", roomJid)
    }

    suspend fun sendGroupMessage(roomJid: String, body: String, myNickname: String): XmppMessage {
        val msgId = "raw_${System.currentTimeMillis()}"
        val stanza = "<message to='$roomJid' type='groupchat' id='$msgId'><body>$body</body></message>"
        sendRawStanza(stanza, "MESSAGE", roomJid)

        return XmppMessage(
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
    }

    suspend fun sendGroupFile(roomJid: String, url: String, fileName: String, mimeType: String, fileSize: Long, myNickname: String): XmppMessage {
        val msgId = "raw_file_${System.currentTimeMillis()}"
        val body = "File: $fileName\n$url"
        val stanza = "<message to='$roomJid' type='groupchat' id='$msgId'><body>$body</body><x xmlns='jabber:x:oob'><url>$url</url></x></message>"
        sendRawStanza(stanza, "MESSAGE", roomJid)

        return XmppMessage(
            id = msgId,
            roomJid = roomJid,
            senderJid = "$roomJid/$myNickname",
            senderNickname = myNickname,
            body = body,
            timestamp = System.currentTimeMillis(),
            isMine = true,
            messageType = MessageType.FILE,
            status = DeliveryStatus.SENT,
            fileUrl = url,
            fileName = fileName,
            fileMimeType = mimeType,
            fileSize = fileSize
        )
    }

    suspend fun sendCallSignalling(roomJid: String, callAction: String, myNickname: String): XmppMessage {
        val msgId = "raw_call_${System.currentTimeMillis()}"
        val body = "Call Action: $callAction"
        val stanza = "<message to='$roomJid' type='groupchat' id='$msgId'><body>$body</body><jingle xmlns='urn:xmpp:jingle:1' action='$callAction'/></message>"
        sendRawStanza(stanza, "MESSAGE", roomJid)

        return XmppMessage(
            id = msgId,
            roomJid = roomJid,
            senderJid = "$roomJid/$myNickname",
            senderNickname = myNickname,
            body = body,
            timestamp = System.currentTimeMillis(),
            isMine = true,
            messageType = MessageType.CALL_SIGNALLING,
            status = DeliveryStatus.SENT
        )
    }

    suspend fun sendPrivateWhisper(roomJid: String, targetNickname: String, body: String, myNickname: String): XmppMessage {
        val whisperStanza = "<message to='$roomJid/$targetNickname' type='chat'><body>$body</body></message>"
        sendRawStanza(whisperStanza, "WHISPER", roomJid)

        return XmppMessage(
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
    }

    suspend fun changeRoomTopic(roomJid: String, newTopic: String, myNickname: String) {
        val topicStanza = "<message to='$roomJid' type='groupchat'><subject>$newTopic</subject></message>"
        sendRawStanza(topicStanza, "MESSAGE", roomJid)
    }

    suspend fun queryPublicDirectory(): List<PublicDirectoryRoom> {
        val discoStanza = "<iq to='conference.xmpp.today' type='get' id='disco_items_1'><query xmlns='http://jabber.org/protocol/disco#items'/></iq>"
        sendRawStanza(discoStanza, "IQ")
        return emptyList()
    }

    fun sendRawStanza(xml: String, type: String = "RAW", roomJid: String? = null) {
        logStanza(StanzaDirection.OUTGOING, xml, type, roomJid)
        if (writer != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    writer?.println(xml)
                    writer?.flush()
                } catch (e: Exception) {}
            }
        }
    }

    private fun logStanza(direction: StanzaDirection, rawXml: String, type: String, roomJid: String? = null) {
        val log = StanzaLog(direction = direction, rawXml = rawXml, stanzaType = type, roomJid = roomJid)
        val current = _stanzaLogs.value.toMutableList()
        if (current.size > 200) current.removeAt(current.size - 1)
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
        connectionJob?.cancel()
        logStanza(StanzaDirection.OUTGOING, "</stream:stream>", "STREAM")
        try { socket?.close() } catch (e: Exception) {}
        socket = null
        writer = null
        reader = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }
}
