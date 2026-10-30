package com.example

import com.example.data.network.MultiUserChatManager
import com.example.data.network.XmppConnectionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MultiUserChatManagerTest {

    @Test
    fun testInitialState() {
        val connectionManager = XmppConnectionManager()
        val mucManager = MultiUserChatManager(connectionManager)

        assertTrue(mucManager.joinedRooms.value.isEmpty())
        assertTrue(mucManager.roomOccupants.value.isEmpty())
        assertFalse(mucManager.isRoomJoined("test@conference.xmpp.today"))
        assertNull(mucManager.getMultiUserChat("test@conference.xmpp.today"))
        assertTrue(mucManager.getOccupants("test@conference.xmpp.today").isEmpty())
    }

    @Test
    fun testOperationsWhenNotConnectedFailGracefully() = runBlocking {
        val connectionManager = XmppConnectionManager()
        val mucManager = MultiUserChatManager(connectionManager)

        // Attempting to join without active connection fails with descriptive exception
        val joinResult = mucManager.joinRoom("general@conference.xmpp.today", "tester")
        assertTrue(joinResult.isFailure)
        assertTrue(joinResult.exceptionOrNull() is IllegalStateException)

        // Attempting to create without active connection fails
        val createResult = mucManager.createAndConfigureRoom("newroom@conference.xmpp.today", "tester")
        assertTrue(createResult.isFailure)

        // Attempting to send message to unjoined room fails
        val sendResult = mucManager.sendMessage("general@conference.xmpp.today", "Hello")
        assertTrue(sendResult.isFailure)

        // Leaving an unjoined room succeeds safely as no-op
        val leaveResult = mucManager.leaveRoom("general@conference.xmpp.today")
        assertTrue(leaveResult.isSuccess)

        // Leaving all rooms when none joined is safe
        mucManager.leaveAllRooms()
        assertEquals(0, mucManager.joinedRooms.value.size)
    }
}
