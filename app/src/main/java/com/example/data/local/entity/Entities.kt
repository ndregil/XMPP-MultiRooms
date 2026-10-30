package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.DeliveryStatus
import com.example.data.model.MessageType
import com.example.data.model.OccupantAffiliation
import com.example.data.model.OccupantRole

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val jid: String,
    val username: String,
    val domain: String,
    val nickname: String,
    val host: String,
    val port: Int,
    val useTls: Boolean,
    val isDemoMode: Boolean,
    val statusMessage: String,
    val lastActiveTime: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "rooms",
    indices = [
        Index(value = ["isJoined"]),
        Index(value = ["lastMessageTime"])
    ]
)
data class RoomEntity(
    @PrimaryKey val jid: String,
    val name: String,
    val topic: String,
    val myNickname: String,
    val occupantCount: Int,
    val unreadCount: Int,
    val lastMessageText: String,
    val lastMessageTime: Long,
    val isJoined: Boolean,
    val isBookmarked: Boolean,
    val myRole: OccupantRole,
    val myAffiliation: OccupantAffiliation,
    val colorHex: String,
    val password: String? = null,
    val isAutoJoin: Boolean = true
)

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["roomJid"]),
        Index(value = ["roomJid", "timestamp"]),
        Index(value = ["timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val roomJid: String,
    val senderJid: String,
    val senderNickname: String,
    val body: String,
    val timestamp: Long,
    val isMine: Boolean,
    val messageType: MessageType,
    val status: DeliveryStatus,
    val recipientNickname: String? = null,
    val serverMsgId: String? = null
)

typealias ChatMessageEntity = MessageEntity

@Entity(
    tableName = "room_configs",
    indices = [
        Index(value = ["isAutoJoin"]),
        Index(value = ["isBookmarked"])
    ]
)
data class RoomConfigEntity(
    @PrimaryKey val roomJid: String,
    val roomName: String,
    val nickname: String,
    val password: String? = null,
    val isAutoJoin: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val subject: String = "",
    val isBookmarked: Boolean = true,
    val colorHex: String = "#0284C7",
    val createdAt: Long = System.currentTimeMillis(),
    val lastJoinedAt: Long = 0L
)

