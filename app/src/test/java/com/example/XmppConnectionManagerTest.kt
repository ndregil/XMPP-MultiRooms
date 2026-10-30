package com.example

import com.example.data.model.ConnectionStatus
import com.example.data.model.XmppAccount
import com.example.data.network.XmppConnectionManager
import com.example.data.network.XmppCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class XmppConnectionManagerTest {

    @Test
    fun testCredentialsInitializationAndUpdates() {
        val manager = XmppConnectionManager()

        // Default initial credentials
        assertEquals("guest_user", manager.credentials.value.username)
        assertEquals("xmpp.chat.org", manager.credentials.value.domain)
        assertEquals(ConnectionStatus.DISCONNECTED, manager.connectionStatus.value)
        assertFalse(manager.isConnected())
        assertFalse(manager.isAuthenticated())

        // Update credentials
        val newCreds = XmppCredentials(
            username = "alice",
            password = "secretPassword123",
            domain = "example.com",
            host = "chat.example.com",
            port = 5222,
            nickname = "Alice"
        )
        manager.setCredentials(newCreds)

        assertEquals("alice", manager.credentials.value.username)
        assertEquals("secretPassword123", manager.credentials.value.password)
        assertEquals("example.com", manager.credentials.value.domain)
        assertEquals("chat.example.com", manager.credentials.value.host)

        // Convert to XmppAccount
        val account = manager.credentials.value.toAccount()
        assertEquals("alice", account.username)
        assertEquals("example.com", account.domain)

        // Update from account
        val customAccount = XmppAccount(
            username = "bob",
            domain = "xmpp.org",
            host = "xmpp.org",
            nickname = "BobTheBuilder"
        )
        manager.updateCredentialsFromAccount(customAccount, password = "bobPassword")
        assertEquals("bob", manager.credentials.value.username)
        assertEquals("bobPassword", manager.credentials.value.password)
        assertEquals("xmpp.org", manager.credentials.value.domain)

        // Clear credentials
        manager.clearCredentials()
        assertEquals("guest_user", manager.credentials.value.username)
        assertEquals("", manager.credentials.value.password)
    }

    @Test
    fun testConnectionInitialState() {
        val manager = XmppConnectionManager()
        assertNull(manager.getRawConnection())
        assertNull(manager.getMultiUserChatManager())
        assertNull(manager.lastError.value)
    }
}
