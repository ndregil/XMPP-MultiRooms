package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.RoomConfigEntity
import com.example.data.local.entity.RoomEntity
import com.example.data.model.DeliveryStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY lastActiveTime DESC LIMIT 1")
    fun getActiveAccount(): Flow<AccountEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAccount(account: AccountEntity)

    @Query("DELETE FROM accounts")
    suspend fun clearAccounts()
}

@Dao
interface RoomDao {
    @Query("SELECT * FROM rooms ORDER BY lastMessageTime DESC")
    fun getAllRooms(): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms WHERE jid = :roomJid")
    suspend fun getRoomByJid(roomJid: String): RoomEntity?

    @Query("SELECT * FROM rooms WHERE jid = :roomJid")
    fun observeRoomByJid(roomJid: String): Flow<RoomEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRoom(room: RoomEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRooms(rooms: List<RoomEntity>)

    @Query("UPDATE rooms SET unreadCount = 0 WHERE jid = :roomJid")
    suspend fun markRoomAsRead(roomJid: String)

    @Query("UPDATE rooms SET unreadCount = 0 WHERE jid = :roomJid")
    suspend fun resetUnreadCount(roomJid: String)

    @Query("UPDATE rooms SET topic = :newTopic WHERE jid = :roomJid")
    suspend fun updateTopic(roomJid: String, newTopic: String)

    @Query("UPDATE rooms SET isJoined = :isJoined WHERE jid = :roomJid")
    suspend fun updateJoinStatus(roomJid: String, isJoined: Boolean)

    @Query("UPDATE rooms SET isAutoJoin = :isAutoJoin WHERE jid = :roomJid")
    suspend fun updateAutoJoin(roomJid: String, isAutoJoin: Boolean)

    @Query("UPDATE rooms SET password = :password WHERE jid = :roomJid")
    suspend fun updatePassword(roomJid: String, password: String?)

    @Query("DELETE FROM rooms WHERE jid = :roomJid")
    suspend fun deleteRoom(roomJid: String)

    @Query("DELETE FROM rooms")
    suspend fun clearAllRooms()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE roomJid = :roomJid ORDER BY timestamp ASC")
    fun getMessagesForRoom(roomJid: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE roomJid = :roomJid ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMessages(roomJid: String, limit: Int = 100): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE roomJid = :roomJid AND body LIKE '%' || :query || '%' ORDER BY timestamp ASC")
    fun searchMessages(roomJid: String, query: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    fun getMessageById(id: String): Flow<MessageEntity?>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageByIdSync(id: String): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE roomJid = :roomJid")
    fun getMessageCount(roomJid: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateMessageStatus(id: String, status: DeliveryStatus)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM messages WHERE roomJid = :roomJid")
    suspend fun clearRoomMessages(roomJid: String)

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()
}

typealias ChatMessageDao = MessageDao

@Dao
interface RoomConfigDao {
    @Query("SELECT * FROM room_configs ORDER BY roomName ASC")
    fun getAllRoomConfigs(): Flow<List<RoomConfigEntity>>

    @Query("SELECT * FROM room_configs WHERE isAutoJoin = 1")
    fun getAutoJoinRoomConfigs(): Flow<List<RoomConfigEntity>>

    @Query("SELECT * FROM room_configs WHERE isAutoJoin = 1")
    suspend fun getAutoJoinRoomConfigsList(): List<RoomConfigEntity>

    @Query("SELECT * FROM room_configs WHERE roomJid = :roomJid")
    fun getRoomConfig(roomJid: String): Flow<RoomConfigEntity?>

    @Query("SELECT * FROM room_configs WHERE roomJid = :roomJid")
    suspend fun getRoomConfigSync(roomJid: String): RoomConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConfig(config: RoomConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConfigs(configs: List<RoomConfigEntity>)

    @Query("UPDATE room_configs SET isAutoJoin = :isAutoJoin WHERE roomJid = :roomJid")
    suspend fun updateAutoJoin(roomJid: String, isAutoJoin: Boolean)

    @Query("UPDATE room_configs SET nickname = :nickname WHERE roomJid = :roomJid")
    suspend fun updateNickname(roomJid: String, nickname: String)

    @Query("UPDATE room_configs SET password = :password WHERE roomJid = :roomJid")
    suspend fun updatePassword(roomJid: String, password: String?)

    @Query("UPDATE room_configs SET subject = :subject WHERE roomJid = :roomJid")
    suspend fun updateSubject(roomJid: String, subject: String)

    @Query("UPDATE room_configs SET notificationsEnabled = :enabled WHERE roomJid = :roomJid")
    suspend fun updateNotifications(roomJid: String, enabled: Boolean)

    @Query("UPDATE room_configs SET isBookmarked = :isBookmarked WHERE roomJid = :roomJid")
    suspend fun updateBookmark(roomJid: String, isBookmarked: Boolean)

    @Query("UPDATE room_configs SET lastJoinedAt = :time WHERE roomJid = :roomJid")
    suspend fun updateLastJoinedAt(roomJid: String, time: Long)

    @Query("DELETE FROM room_configs WHERE roomJid = :roomJid")
    suspend fun deleteConfig(roomJid: String)

    @Query("DELETE FROM room_configs")
    suspend fun clearAllConfigs()
}

