package com.example

import com.example.data.model.MessageType
import com.example.data.model.XmppMessage
import com.example.data.network.MucMessageListener
import com.example.data.network.MultiUserChatManager
import com.example.data.network.XmppChatMessageCallback
import com.example.data.network.XmppConnectionManager
import org.jivesoftware.smack.packet.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.jxmpp.jid.impl.JidCreate
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MucMessageListenerTest {

    private val roomJid = "tech-talk@conference.xmpp.today"
    private val myNick = "AndroidDev"

    @Test
    fun testProcessGroupchatMessageFromOtherUser() {
        var receivedMessage: XmppMessage? = null

        val listener = MucMessageListener(
            roomJid = roomJid,
            myNicknameProvider = { myNick },
            onMessageProcessed = { receivedMessage = it }
        )

        val smackMessage = Message(
            JidCreate.from("androiddev@xmpp.today"),
            Message.Type.groupchat
        ).apply {
            from = JidCreate.from("$roomJid/Budi")
            body = "Halo rekan-rekan pengembang!"
            stanzaId = "msg_1001"
        }

        listener.processMessage(smackMessage)

        assertNotNull("Message should be processed and dispatched", receivedMessage)
        assertEquals("msg_1001", receivedMessage?.id)
        assertEquals(roomJid, receivedMessage?.roomJid)
        assertEquals("Budi", receivedMessage?.senderNickname)
        assertEquals("Halo rekan-rekan pengembang!", receivedMessage?.body)
        assertFalse("Message from Budi should not be marked as mine", receivedMessage!!.isMine)
        assertEquals(MessageType.GROUPCHAT, receivedMessage?.messageType)
    }

    @Test
    fun testProcessMessageFromLocalUser() {
        var receivedMessage: XmppMessage? = null

        val listener = MucMessageListener(
            roomJid = roomJid,
            myNickname = myNick,
            onMessageProcessed = { receivedMessage = it }
        )

        val smackMessage = Message(
            JidCreate.from("$roomJid"),
            Message.Type.groupchat
        ).apply {
            from = JidCreate.from("$roomJid/$myNick")
            body = "Pesan terkirim dari saya sendiri"
            stanzaId = "msg_local_1"
        }

        listener.processMessage(smackMessage)

        assertNotNull(receivedMessage)
        assertTrue("Message with sender matching myNick should be isMine=true", receivedMessage!!.isMine)
        assertEquals(myNick, receivedMessage?.senderNickname)
    }

    @Test
    fun testProcessWhisperMessage() {
        var receivedMessage: XmppMessage? = null

        val listener = MucMessageListener(
            roomJid = roomJid,
            myNicknameProvider = { myNick },
            onMessageProcessed = { receivedMessage = it }
        )

        val smackMessage = Message(
            JidCreate.from("$roomJid/$myNick"),
            Message.Type.chat
        ).apply {
            from = JidCreate.from("$roomJid/SecretUser")
            body = "Ini pesan bisikan rahasia"
            stanzaId = "whisper_42"
        }

        listener.processMessage(smackMessage)

        assertNotNull(receivedMessage)
        assertEquals(MessageType.WHISPER, receivedMessage?.messageType)
        assertEquals("SecretUser", receivedMessage?.senderNickname)
        assertEquals(myNick, receivedMessage?.recipientNickname)
        assertEquals("Ini pesan bisikan rahasia", receivedMessage?.body)
    }

    @Test
    fun testTopicChangeDetection() {
        var updatedTopic: String? = null
        var receivedMessage: XmppMessage? = null

        val listener = MucMessageListener(
            roomJid = roomJid,
            myNicknameProvider = { myNick },
            onMessageProcessed = { receivedMessage = it },
            onTopicUpdated = { _, newTopic -> updatedTopic = newTopic }
        )

        val topicStanza = Message(
            JidCreate.from(roomJid),
            Message.Type.groupchat
        ).apply {
            from = JidCreate.from("$roomJid/Admin")
            subject = "Topik baru: Rilis Smack 4.4.8 dan Jetpack Compose"
            // body is left null in standard subject broadcast stanzas
        }

        listener.processMessage(topicStanza)

        assertNull("Subject-only stanza should not produce chat message", receivedMessage)
        assertEquals("Topik baru: Rilis Smack 4.4.8 dan Jetpack Compose", updatedTopic)
    }

    @Test
    fun testEmptyBodyStanzasIgnored() {
        var messageDispatched = false

        val listener = MucMessageListener(
            roomJid = roomJid,
            myNickname = myNick,
            onMessageProcessed = { messageDispatched = true }
        )

        val emptyStanza = Message().apply {
            from = JidCreate.from("$roomJid/Nobody")
            body = "   "
        }

        listener.processMessage(emptyStanza)

        assertFalse("Whitespace body stanzas should not be dispatched", messageDispatched)
    }

    @Test
    fun testCallbackInterfaceImplementation() {
        var callbackReceived = false
        var topicChanged = false

        val callback = object : XmppChatMessageCallback {
            override fun onMessageReceived(message: XmppMessage) {
                callbackReceived = true
            }

            override fun onTopicChanged(roomJid: String, newTopic: String) {
                topicChanged = true
            }
        }

        val listener = MucMessageListener(
            roomJid = roomJid,
            myNicknameProvider = { myNick },
            callback = callback
        )

        val msg = Message(JidCreate.from(roomJid), Message.Type.groupchat).apply {
            from = JidCreate.from("$roomJid/Alice")
            body = "Testing callback"
        }
        listener.processMessage(msg)
        assertTrue("Callback onMessageReceived should be called", callbackReceived)

        val topicMsg = Message(JidCreate.from(roomJid), Message.Type.groupchat).apply {
            from = JidCreate.from("$roomJid/Alice")
            subject = "New Topic"
        }
        listener.processMessage(topicMsg)
        assertTrue("Callback onTopicChanged should be called", topicChanged)
    }

    @Test
    fun testMultiUserChatManagerHookMethods() {
        val connectionManager = XmppConnectionManager()
        val mucManager = MultiUserChatManager(connectionManager)

        // Attempting to hook into an unjoined room gracefully returns null / false
        val hookedListener = mucManager.hookMessageListener("unjoined@conference.xmpp.today") { }
        assertNull("Hooking into unjoined room should return null safely", hookedListener)

        val added = mucManager.addMessageListener("unjoined@conference.xmpp.today", MucMessageListener("unjoined@conference.xmpp.today", "tester") {})
        assertFalse("Adding listener to unjoined room returns false safely", added)
    }
}
