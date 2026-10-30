package com.example

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.RoomEntity
import com.example.data.model.OccupantAffiliation
import com.example.data.model.OccupantRole
import com.example.data.network.fcm.FcmPushManager
import com.example.data.network.fcm.NotificationActionReceiver
import com.example.data.network.fcm.PushMessagePayload
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FcmPushNotificationTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val testRoomJid = "general@conference.xmpp.today"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        FcmPushManager.init(context)
        FcmPushManager.activeRoomJid = null
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testNotificationChannelsCreated() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val mucChannel = notificationManager.getNotificationChannel(FcmPushManager.CHANNEL_MUC)
        val whisperChannel = notificationManager.getNotificationChannel(FcmPushManager.CHANNEL_WHISPER)

        assertNotNull("MUC notification channel should exist", mucChannel)
        assertNotNull("Whisper notification channel should exist", whisperChannel)
        assertEquals("XMPP Room & Group Chat", mucChannel.name)
        assertEquals("Bisikan Pribadi XMPP", whisperChannel.name)
    }

    @Test
    fun testTokenUpdateAndPreferences() {
        val testToken = "test_fcm_token_12345"
        FcmPushManager.updateToken(context, testToken)

        assertEquals(testToken, FcmPushManager.fcmToken.value)

        val prefs = context.getSharedPreferences("xmpp_fcm_prefs", Context.MODE_PRIVATE)
        assertEquals(testToken, prefs.getString("fcm_token", null))
    }

    @Test
    fun testPushEnableDisableToggle() {
        FcmPushManager.setPushEnabled(context, false)
        assertFalse(FcmPushManager.isPushEnabled.value)

        FcmPushManager.setPushEnabled(context, true)
        assertTrue(FcmPushManager.isPushEnabled.value)
    }

    @Test
    fun testSystemNotificationDispatchedForBackgroundMessage() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNotificationManager = shadowOf(notificationManager)

        val payload = PushMessagePayload(
            roomJid = testRoomJid,
            senderNickname = "ServerBot",
            body = "Pengujian pesan FCM di latar belakang",
            isWhisper = false
        )

        FcmPushManager.showSystemNotification(context, payload)

        val notifications = shadowNotificationManager.allNotifications
        assertTrue("Notification should be posted to system", notifications.isNotEmpty())

        val notification = notifications.last()
        val title = notification.extras.getString("android.title")
        val text = notification.extras.getCharSequence("android.text")?.toString()

        assertTrue(title?.contains("ServerBot") == true)
        assertEquals("Pengujian pesan FCM di latar belakang", text)
    }

    @Test
    fun testNotificationSuppressedWhenRoomIsActive() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNotificationManager = shadowOf(notificationManager)
        val initialCount = shadowNotificationManager.allNotifications.size

        // Set the active room
        FcmPushManager.activeRoomJid = testRoomJid

        val payload = PushMessagePayload(
            roomJid = testRoomJid,
            senderNickname = "ServerBot",
            body = "Pesan ini seharusnya tidak memunculkan notifikasi sistem",
            isWhisper = false
        )

        FcmPushManager.showSystemNotification(context, payload)

        assertEquals("No new notification should be posted when room is active", initialCount, shadowNotificationManager.allNotifications.size)
    }

    @Test
    fun testWhisperPushNotificationUsesWhisperChannel() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNotificationManager = shadowOf(notificationManager)

        val payload = PushMessagePayload(
            roomJid = testRoomJid,
            senderNickname = "Alice",
            body = "Pesan rahasia bisikan",
            isWhisper = true
        )

        FcmPushManager.showSystemNotification(context, payload)

        val notification = shadowNotificationManager.allNotifications.last()
        assertEquals(FcmPushManager.CHANNEL_WHISPER, notification.channelId)
        val title = notification.extras.getString("android.title")
        assertTrue(title?.contains("Bisikan dari Alice") == true)
    }

    @Test
    fun testNotificationActionReceiverMarksRoomAsRead() = runBlocking {
        val appDb = AppDatabase.getDatabase(context)
        val roomDao = appDb.roomDao()
        val initialRoom = RoomEntity(
            jid = testRoomJid,
            name = "Ruang Umum",
            topic = "Topik",
            myNickname = "MyNick",
            occupantCount = 3,
            unreadCount = 5,
            lastMessageText = "Pesan lama",
            lastMessageTime = System.currentTimeMillis() - 5000,
            isJoined = true,
            isBookmarked = false,
            myRole = OccupantRole.MEMBER,
            myAffiliation = OccupantAffiliation.MEMBER,
            colorHex = "#4F46E5"
        )
        roomDao.insertOrUpdateRoom(initialRoom)

        val notificationId = (testRoomJid.hashCode() and 0x7FFFFFFF)

        val intent = Intent(FcmPushManager.ACTION_MARK_AS_READ).apply {
            putExtra(FcmPushManager.EXTRA_ROOM_JID, testRoomJid)
            putExtra(FcmPushManager.EXTRA_NOTIFICATION_ID, notificationId)
        }

        val receiver = NotificationActionReceiver()
        receiver.onReceive(context, intent)

        // Give coroutine brief moment to execute
        kotlinx.coroutines.delay(200)

        // Check unread count is reset in the DB
        val updatedRoom = roomDao.getRoomByJid(testRoomJid)
        assertNotNull(updatedRoom)
        assertEquals(0, updatedRoom?.unreadCount)
    }
}
