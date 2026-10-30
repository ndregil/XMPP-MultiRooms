package com.example.data.model

import java.util.UUID

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    ERROR
}

enum class MessageType {
    GROUPCHAT,
    WHISPER,
    SYSTEM_JOIN,
    SYSTEM_LEAVE,
    SYSTEM_TOPIC,
    ACTION,
    FILE,
    CALL_SIGNALLING
}

enum class DeliveryStatus {
    SENDING,
    SENT,
    DELIVERED,
    FAILED
}

enum class OccupantRole {
    OWNER,
    ADMIN,
    MEMBER,
    VISITOR,
    NONE
}

enum class OccupantAffiliation {
    OWNER,
    ADMIN,
    MEMBER,
    OUTCAST,
    NONE
}

enum class UserPresence {
    ONLINE,
    CHAT,
    AWAY,
    DND,
    OFFLINE
}

enum class StanzaDirection {
    INCOMING,
    OUTGOING
}

data class XmppAccount(
    val jid: String = "",
    val username: String = "",
    val domain: String = "",
    val nickname: String = "AndroidExplorer",
    val host: String = "",
    val port: Int = 5222,
    val useTls: Boolean = true,
    val isDemoMode: Boolean = false,
    val statusMessage: String = "Active on XMPP MultiRoom"
)

data class XmppRoom(
    val jid: String,
    val name: String,
    val topic: String = "",
    val myNickname: String = "AndroidUser",
    val occupantCount: Int = 1,
    val unreadCount: Int = 0,
    val lastMessageText: String = "",
    val lastMessageTime: Long = System.currentTimeMillis(),
    val isJoined: Boolean = true,
    val isBookmarked: Boolean = true,
    val myRole: OccupantRole = OccupantRole.MEMBER,
    val myAffiliation: OccupantAffiliation = OccupantAffiliation.MEMBER,
    val colorHex: String = "#0284C7"
)

enum class ClientPlatform {
    ANDROID,
    IOS,
    DESKTOP,
    WEB,
    BOT,
    UNKNOWN
}

data class ClientCaps(
    val clientName: String = "Conversations",
    val version: String = "2.16.2",
    val os: String = "Android",
    val platform: ClientPlatform = ClientPlatform.ANDROID,
    val nodeUri: String = "http://conversations.im",
    val hash: String = "sha-1",
    val ver: String = "kFwX23eY765qL90mN12aBcDeFgH=",
    val features: List<String> = listOf(
        "http://jabber.org/protocol/caps",
        "http://jabber.org/protocol/muc",
        "urn:xmpp:mam:2",
        "urn:xmpp:receipts",
        "urn:xmpp:chat-markers:0",
        "urn:xmpp:ping",
        "urn:xmpp:omemo:2",
        "urn:xmpp:carbons:2"
    )
) {
    val summaryText: String
        get() = "$clientName $version ($os)"

    val readableFeatures: List<Pair<String, String>>
        get() = features.map { feat ->
            val desc = when {
                feat.contains("caps") -> "XEP-0115: Entity Capabilities"
                feat.contains("muc") -> "XEP-0045: Multi-User Chat"
                feat.contains("mam") -> "XEP-0313: Message Archiving (MAM)"
                feat.contains("receipts") -> "XEP-0184: Message Delivery Receipts"
                feat.contains("chat-markers") -> "XEP-0333: Chat Markers"
                feat.contains("ping") -> "XEP-0199: XMPP Ping"
                feat.contains("omemo") || feat.contains("axolotl") -> "XEP-0384: OMEMO Encryption"
                feat.contains("carbons") -> "XEP-0280: Message Carbons"
                feat.contains("http:upload") -> "XEP-0363: HTTP File Upload"
                feat.contains("jingle") -> "XEP-0166: Jingle (Audio/Video)"
                feat.contains("disco") -> "XEP-0030: Service Discovery"
                feat.contains("version") -> "XEP-0092: Software Version"
                feat.contains("push") -> "XEP-0357: Push Notifications"
                feat.contains("roster") -> "RFC 6121: Contact List Roster"
                else -> feat
            }
            Pair(desc, feat)
        }

    companion object {
        fun resolveFor(name: String, jid: String = ""): ClientCaps {
            val lower = "$name $jid".lowercase()
            return when {
                lower.contains("bot") || lower.contains("admin") -> ClientCaps(
                    clientName = "Prosody Bot Service",
                    version = "0.12.4",
                    os = "Linux (Server)",
                    platform = ClientPlatform.BOT,
                    nodeUri = "http://prosody.im/caps",
                    ver = "bot_srv_9921_sha1",
                    features = listOf(
                        "http://jabber.org/protocol/caps",
                        "http://jabber.org/protocol/muc",
                        "http://jabber.org/protocol/disco#info",
                        "urn:xmpp:ping"
                    )
                )
                lower.contains("dino") || lower.contains("arch") || lower.contains("linux") || lower.contains("guru") -> ClientCaps(
                    clientName = "Dino",
                    version = "0.4.3",
                    os = "Linux (Debian/Fedora)",
                    platform = ClientPlatform.DESKTOP,
                    nodeUri = "https://dino.im",
                    ver = "dino_xep_0115_ver",
                    features = listOf(
                        "http://jabber.org/protocol/caps",
                        "http://jabber.org/protocol/muc",
                        "urn:xmpp:mam:2",
                        "urn:xmpp:receipts",
                        "urn:xmpp:omemo:2",
                        "urn:xmpp:carbons:2",
                        "urn:xmpp:ping"
                    )
                )
                lower.contains("gajim") || lower.contains("windows") || lower.contains("sec") -> ClientCaps(
                    clientName = "Gajim",
                    version = "1.9.0",
                    os = "Windows 11 (Desktop)",
                    platform = ClientPlatform.DESKTOP,
                    nodeUri = "http://gajim.org",
                    ver = "gajim_caps_hash_871",
                    features = listOf(
                        "http://jabber.org/protocol/caps",
                        "http://jabber.org/protocol/muc",
                        "urn:xmpp:mam:2",
                        "urn:xmpp:receipts",
                        "urn:xmpp:chat-markers:0",
                        "urn:xmpp:omemo:2",
                        "urn:xmpp:http:upload:0",
                        "urn:xmpp:ping"
                    )
                )
                lower.contains("siskin") || lower.contains("monal") || lower.contains("ios") || lower.contains("apple") || lower.contains("siti") -> ClientCaps(
                    clientName = "Siskin IM",
                    version = "7.3.1",
                    os = "iOS 18 (Apple)",
                    platform = ClientPlatform.IOS,
                    nodeUri = "https://siskin.im",
                    ver = "siskin_caps_v7",
                    features = listOf(
                        "http://jabber.org/protocol/caps",
                        "http://jabber.org/protocol/muc",
                        "urn:xmpp:mam:2",
                        "urn:xmpp:receipts",
                        "urn:xmpp:omemo:2",
                        "urn:xmpp:ping",
                        "urn:xmpp:push:0"
                    )
                )
                lower.contains("web") || lower.contains("converse") || lower.contains("kopi") || lower.contains("browser") -> ClientCaps(
                    clientName = "Converse.js",
                    version = "10.1.7",
                    os = "Web Browser (Chrome/Firefox)",
                    platform = ClientPlatform.WEB,
                    nodeUri = "https://conversejs.org",
                    ver = "converse_web_v10",
                    features = listOf(
                        "http://jabber.org/protocol/caps",
                        "http://jabber.org/protocol/muc",
                        "urn:xmpp:mam:2",
                        "urn:xmpp:receipts",
                        "urn:xmpp:ping"
                    )
                )
                lower.contains("android") || lower.contains("kotlin") || lower.contains("me") || lower.contains("kamu") || lower.contains("explore") -> ClientCaps(
                    clientName = "XMPP MultiRoom",
                    version = "1.0.0 (Smack 4.4)",
                    os = "Android 15",
                    platform = ClientPlatform.ANDROID,
                    nodeUri = "http://aistudio.google.com/xmpp",
                    ver = "xmpp_multiroom_smack44",
                    features = listOf(
                        "http://jabber.org/protocol/caps",
                        "http://jabber.org/protocol/muc",
                        "urn:xmpp:mam:2",
                        "urn:xmpp:receipts",
                        "urn:xmpp:chat-markers:0",
                        "urn:xmpp:ping",
                        "http://jabber.org/protocol/disco#info"
                    )
                )
                else -> ClientCaps(
                    clientName = "Conversations",
                    version = "2.16.2",
                    os = "Android",
                    platform = ClientPlatform.ANDROID,
                    nodeUri = "http://conversations.im",
                    ver = "conversations_caps_sha1",
                    features = listOf(
                        "http://jabber.org/protocol/caps",
                        "http://jabber.org/protocol/muc",
                        "urn:xmpp:mam:2",
                        "urn:xmpp:receipts",
                        "urn:xmpp:chat-markers:0",
                        "urn:xmpp:ping",
                        "urn:xmpp:omemo:2"
                    )
                )
            }
        }
    }
}

data class XmppMessage(
    val id: String = UUID.randomUUID().toString(),
    val roomJid: String,
    val senderJid: String,
    val senderNickname: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isMine: Boolean = false,
    val messageType: MessageType = MessageType.GROUPCHAT,
    val status: DeliveryStatus = DeliveryStatus.SENT,
    val recipientNickname: String? = null, // For 1-on-1 private whisper in MUC
    val fileUrl: String? = null,
    val fileMimeType: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val senderClientCaps: ClientCaps = ClientCaps.resolveFor(senderNickname, senderJid)
)

data class RoomOccupant(
    val nickname: String,
    val jid: String = "",
    val role: OccupantRole = OccupantRole.MEMBER,
    val affiliation: OccupantAffiliation = OccupantAffiliation.MEMBER,
    val presence: UserPresence = UserPresence.ONLINE,
    val statusText: String = "",
    val isMe: Boolean = false,
    val clientCaps: ClientCaps = ClientCaps.resolveFor(nickname, jid)
)


data class StanzaLog(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val direction: StanzaDirection,
    val rawXml: String,
    val stanzaType: String = "MESSAGE",
    val roomJid: String? = null
)

data class PublicDirectoryRoom(
    val jid: String,
    val name: String,
    val description: String,
    val occupantsCount: Int,
    val category: String = "General"
)

data class DiscoveredItem(
    val jid: String,
    val name: String = "",
    val node: String? = null
)

data class DiscoveredRoom(
    val jid: String,
    val name: String,
    val serviceJid: String = "",
    val description: String = "",
    val occupantsCount: Int = 0,
    val subject: String = "",
    val isPasswordProtected: Boolean = false,
    val isMembersOnly: Boolean = false,
    val isPersistent: Boolean = false,
    val features: List<String> = emptyList()
) {
    fun toPublicDirectoryRoom(): PublicDirectoryRoom {
        return PublicDirectoryRoom(
            jid = jid,
            name = name.ifBlank { jid.substringBefore("@") },
            description = if (description.isNotBlank()) description else if (subject.isNotBlank()) subject else "Ruang obrolan publik",
            occupantsCount = occupantsCount,
            category = if (isPasswordProtected) "Private / Password" else if (isMembersOnly) "Members Only" else "General"
        )
    }
}

data class RosterContact(
    val jid: String,
    val name: String,
    val presence: UserPresence = UserPresence.OFFLINE,
    val statusMessage: String = "",
    val groups: List<String> = emptyList(),
    val subscriptionType: String = "both",
    val isPendingApproval: Boolean = false,
    val clientCaps: ClientCaps = ClientCaps.resolveFor(name, jid)
) {
    val displayName: String
        get() = name.ifBlank { jid.substringBefore("@") }
}

