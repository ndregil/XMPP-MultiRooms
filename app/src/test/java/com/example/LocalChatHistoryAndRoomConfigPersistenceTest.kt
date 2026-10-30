package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.RoomConfigEntity
import com.example.data.model.DeliveryStatus
import com.example.data.model.MessageType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalChatHistoryAndRoomConfigPersistenceTest {

    private lateinit var database: AppDatabase
    private val generalRoom = "general@conference.xmpp.today"
    private val techRoom = "tech-id@conference.xmpp.today"

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testInsertAndQueryChatMessagesOrderedByTimestamp() = runBlocking {
        val messageDao = database.messageDao()

        val msg1 = MessageEntity(
            id = "msg_1",
            roomJid = generalRoom,
            senderJid = "$generalRoom/Alice",
            senderNickname = "Alice",
            body = "Pesan pertama",
            timestamp = 1000L,
            isMine = false,
            messageType = MessageType.GROUPCHAT,
            status = DeliveryStatus.DELIVERED
        )
        val msg2 = MessageEntity(
            id = "msg_2",
            roomJid = generalRoom,
            senderJid = "$generalRoom/Bob",
            senderNickname = "Bob",
            body = "Pesan kedua",
            timestamp = 2000L,
            isMine = false,
            messageType = MessageType.GROUPCHAT,
            status = DeliveryStatus.DELIVERED
        )
        val msg3 = MessageEntity(
            id = "msg_3",
            roomJid = generalRoom,
            senderJid = "$generalRoom/Me",
            senderNickname = "Me",
            body = "Pesan ketiga dari saya",
            timestamp = 3000L,
            isMine = true,
            messageType = MessageType.GROUPCHAT,
            status = DeliveryStatus.SENT
        )

        messageDao.insertMessages(listOf(msg2, msg1, msg3))

        val retrieved = messageDao.getMessagesForRoom(generalRoom).first()
        assertEquals(3, retrieved.size)
        // Verify ascending timestamp order
        assertEquals("msg_1", retrieved[0].id)
        assertEquals("msg_2", retrieved[1].id)
        assertEquals("msg_3", retrieved[2].id)
        assertEquals("Pesan pertama", retrieved[0].body)
    }

    @Test
    fun testGetRecentMessagesWithLimit() = runBlocking {
        val messageDao = database.messageDao()

        val messages = (1..5).map { index ->
            MessageEntity(
                id = "m_$index",
                roomJid = generalRoom,
                senderJid = "$generalRoom/User$index",
                senderNickname = "User$index",
                body = "Message $index",
                timestamp = 1000L * index,
                isMine = false,
                messageType = MessageType.GROUPCHAT,
                status = DeliveryStatus.DELIVERED
            )
        }
        messageDao.insertMessages(messages)

        val recent = messageDao.getRecentMessages(generalRoom, limit = 2).first()
        assertEquals(2, recent.size)
        // Recent query is ordered DESC by timestamp
        assertEquals("m_5", recent[0].id)
        assertEquals("m_4", recent[1].id)
    }

    @Test
    fun testSearchChatMessages() = runBlocking {
        val messageDao = database.messageDao()

        val msg1 = MessageEntity(
            id = "s_1",
            roomJid = generalRoom,
            senderJid = "$generalRoom/Dev1",
            senderNickname = "Dev1",
            body = "Halo dunia XMPP MUC multi-room!",
            timestamp = 1000L,
            isMine = false,
            messageType = MessageType.GROUPCHAT,
            status = DeliveryStatus.DELIVERED
        )
        val msg2 = MessageEntity(
            id = "s_2",
            roomJid = generalRoom,
            senderJid = "$generalRoom/Dev2",
            senderNickname = "Dev2",
            body = "Kopi santai sore",
            timestamp = 2000L,
            isMine = false,
            messageType = MessageType.GROUPCHAT,
            status = DeliveryStatus.DELIVERED
        )

        messageDao.insertMessages(listOf(msg1, msg2))

        val searchResults = messageDao.searchMessages(generalRoom, "XMPP").first()
        assertEquals(1, searchResults.size)
        assertEquals("s_1", searchResults[0].id)
        assertTrue(searchResults[0].body.contains("XMPP"))
    }

    @Test
    fun testUpdateDeliveryStatus() = runBlocking {
        val messageDao = database.messageDao()

        val msg = MessageEntity(
            id = "status_msg",
            roomJid = generalRoom,
            senderJid = "$generalRoom/Me",
            senderNickname = "Me",
            body = "Pengiriman pesan",
            timestamp = 1500L,
            isMine = true,
            messageType = MessageType.GROUPCHAT,
            status = DeliveryStatus.SENDING
        )
        messageDao.insertMessage(msg)

        val initial = messageDao.getMessageByIdSync("status_msg")
        assertNotNull(initial)
        assertEquals(DeliveryStatus.SENDING, initial?.status)

        messageDao.updateMessageStatus("status_msg", DeliveryStatus.DELIVERED)

        val updated = messageDao.getMessageByIdSync("status_msg")
        assertEquals(DeliveryStatus.DELIVERED, updated?.status)
    }

    @Test
    fun testClearRoomMessagesLeavesOtherRoomsIntact() = runBlocking {
        val messageDao = database.messageDao()

        val msgGen = MessageEntity(
            id = "gen_1",
            roomJid = generalRoom,
            senderJid = "$generalRoom/Alice",
            senderNickname = "Alice",
            body = "Umum",
            timestamp = 1000L,
            isMine = false,
            messageType = MessageType.GROUPCHAT,
            status = DeliveryStatus.DELIVERED
        )
        val msgTech = MessageEntity(
            id = "tech_1",
            roomJid = techRoom,
            senderJid = "$techRoom/Bob",
            senderNickname = "Bob",
            body = "Tech",
            timestamp = 2000L,
            isMine = false,
            messageType = MessageType.GROUPCHAT,
            status = DeliveryStatus.DELIVERED
        )

        messageDao.insertMessages(listOf(msgGen, msgTech))

        messageDao.clearRoomMessages(generalRoom)

        val genList = messageDao.getMessagesForRoom(generalRoom).first()
        val techList = messageDao.getMessagesForRoom(techRoom).first()

        assertEquals(0, genList.size)
        assertEquals(1, techList.size)
        assertEquals("tech_1", techList[0].id)
    }

    @Test
    fun testRoomConfigInsertAndRetrieve() = runBlocking {
        val configDao = database.roomConfigDao()

        val config = RoomConfigEntity(
            roomJid = generalRoom,
            roomName = "# Umum",
            nickname = "AndroidTester",
            password = "secret_password",
            isAutoJoin = true,
            notificationsEnabled = true,
            subject = "Diskusi Umum",
            isBookmarked = true,
            colorHex = "#0284C7"
        )

        configDao.insertOrUpdateConfig(config)

        val loaded = configDao.getRoomConfigSync(generalRoom)
        assertNotNull(loaded)
        assertEquals(generalRoom, loaded?.roomJid)
        assertEquals("# Umum", loaded?.roomName)
        assertEquals("AndroidTester", loaded?.nickname)
        assertEquals("secret_password", loaded?.password)
        assertTrue(loaded?.isAutoJoin == true)
        assertTrue(loaded?.notificationsEnabled == true)
        assertEquals("Diskusi Umum", loaded?.subject)
    }

    @Test
    fun testAutoJoinRoomConfigsQuery() = runBlocking {
        val configDao = database.roomConfigDao()

        val cfg1 = RoomConfigEntity(
            roomJid = "room1@conference.xmpp.today",
            roomName = "Room 1",
            nickname = "User1",
            isAutoJoin = true
        )
        val cfg2 = RoomConfigEntity(
            roomJid = "room2@conference.xmpp.today",
            roomName = "Room 2",
            nickname = "User2",
            isAutoJoin = false
        )
        val cfg3 = RoomConfigEntity(
            roomJid = "room3@conference.xmpp.today",
            roomName = "Room 3",
            nickname = "User3",
            isAutoJoin = true
        )

        configDao.insertOrUpdateConfigs(listOf(cfg1, cfg2, cfg3))

        val autoJoinList = configDao.getAutoJoinRoomConfigsList()
        assertEquals(2, autoJoinList.size)
        assertTrue(autoJoinList.all { it.isAutoJoin })
        val jids = autoJoinList.map { it.roomJid }
        assertTrue(jids.contains("room1@conference.xmpp.today"))
        assertTrue(jids.contains("room3@conference.xmpp.today"))
        assertFalse(jids.contains("room2@conference.xmpp.today"))
    }

    @Test
    fun testUpdateRoomConfigFields() = runBlocking {
        val configDao = database.roomConfigDao()

        val cfg = RoomConfigEntity(
            roomJid = generalRoom,
            roomName = "# General",
            nickname = "OldNick",
            password = null,
            isAutoJoin = true
        )
        configDao.insertOrUpdateConfig(cfg)

        // Update nickname
        configDao.updateNickname(generalRoom, "NewNick")
        assertEquals("NewNick", configDao.getRoomConfigSync(generalRoom)?.nickname)

        // Update auto-join
        configDao.updateAutoJoin(generalRoom, false)
        assertFalse(configDao.getRoomConfigSync(generalRoom)?.isAutoJoin == true)

        // Update password
        configDao.updatePassword(generalRoom, "pass123")
        assertEquals("pass123", configDao.getRoomConfigSync(generalRoom)?.password)

        // Update subject
        configDao.updateSubject(generalRoom, "Topik Baru")
        assertEquals("Topik Baru", configDao.getRoomConfigSync(generalRoom)?.subject)
    }

    @Test
    fun testDeleteRoomConfig() = runBlocking {
        val configDao = database.roomConfigDao()

        val cfg = RoomConfigEntity(
            roomJid = techRoom,
            roomName = "Tech Room",
            nickname = "Geek",
            isAutoJoin = true
        )
        configDao.insertOrUpdateConfig(cfg)
        assertNotNull(configDao.getRoomConfigSync(techRoom))

        configDao.deleteConfig(techRoom)
        assertNull(configDao.getRoomConfigSync(techRoom))
    }

    @Test
    fun testChatMessageDaoTypealiasCompatibility() = runBlocking {
        // AppDatabase provides both messageDao() and chatMessageDao()
        val chatMessageDao = database.chatMessageDao()
        val countBefore = chatMessageDao.getMessageCount(generalRoom).first()
        assertEquals(0, countBefore)

        val msg = MessageEntity(
            id = "test_compat",
            roomJid = generalRoom,
            senderJid = "$generalRoom/Tester",
            senderNickname = "Tester",
            body = "Compatibility check",
            timestamp = 5000L,
            isMine = true,
            messageType = MessageType.GROUPCHAT,
            status = DeliveryStatus.SENT
        )
        chatMessageDao.insertMessage(msg)

        val countAfter = chatMessageDao.getMessageCount(generalRoom).first()
        assertEquals(1, countAfter)
    }
}
