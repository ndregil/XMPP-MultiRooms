package com.example

import com.example.data.model.UserPresence
import com.example.data.network.RosterManager
import com.example.data.network.XmppConnectionManager
import com.example.data.network.toUserPresence
import kotlinx.coroutines.runBlocking
import org.jivesoftware.smack.packet.Presence
import org.jivesoftware.smack.packet.PresenceBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RosterManagerTest {

    @Test
    fun testInitialContactsAndPresence() {
        val connectionManager = XmppConnectionManager()
        val rosterManager = RosterManager(connectionManager)

        val contacts = rosterManager.contacts.value
        assertFalse(contacts.isEmpty())
        assertTrue(contacts.any { it.jid == "alice@xmpp.chat.org" })

        val presenceMap = rosterManager.presenceMap.value
        assertEquals(UserPresence.ONLINE, presenceMap["alice@xmpp.chat.org"])
        assertEquals(UserPresence.OFFLINE, presenceMap["eva@xmpp.chat.org"])
    }

    @Test
    fun testLoadContactsOfflineFallback() = runBlocking {
        val connectionManager = XmppConnectionManager()
        val rosterManager = RosterManager(connectionManager)

        val result = rosterManager.loadContacts()
        assertTrue(result.isSuccess)
        val loaded = result.getOrNull()
        assertNotNull(loaded)
        assertTrue(loaded!!.isNotEmpty())
        assertTrue(rosterManager.isLoaded.value)
    }

    @Test
    fun testAddContactOffline() = runBlocking {
        val connectionManager = XmppConnectionManager()
        val rosterManager = RosterManager(connectionManager)

        val newJid = "zack@xmpp.chat.org"
        val newName = "Zack Fair"
        val group = "Gaming"

        val result = rosterManager.addContact(newJid, newName, group)
        assertTrue(result.isSuccess)

        val found = rosterManager.contacts.value.find { it.jid == newJid }
        assertNotNull(found)
        assertEquals(newName, found!!.name)
        assertTrue(found.groups.contains(group))
        assertEquals(UserPresence.ONLINE, rosterManager.presenceMap.value[newJid])
    }

    @Test
    fun testAddContactInvalidJidFails() = runBlocking {
        val connectionManager = XmppConnectionManager()
        val rosterManager = RosterManager(connectionManager)

        val resultBlank = rosterManager.addContact("   ", "Blank JID")
        assertTrue(resultBlank.isFailure)

        val resultInvalid = rosterManager.addContact("not-a-valid-jid", "Invalid")
        assertTrue(resultInvalid.isFailure)
    }

    @Test
    fun testRemoveContactOffline() = runBlocking {
        val connectionManager = XmppConnectionManager()
        val rosterManager = RosterManager(connectionManager)

        val targetJid = "eva@xmpp.chat.org"
        assertTrue(rosterManager.contacts.value.any { it.jid == targetJid })

        val result = rosterManager.removeContact(targetJid)
        assertTrue(result.isSuccess)

        assertFalse(rosterManager.contacts.value.any { it.jid == targetJid })
        assertFalse(rosterManager.presenceMap.value.containsKey(targetJid))
    }

    @Test
    fun testRenameContactOffline() = runBlocking {
        val connectionManager = XmppConnectionManager()
        val rosterManager = RosterManager(connectionManager)

        val targetJid = "bob@xmpp.chat.org"
        val newName = "Robert 'Bob' Martin"

        val result = rosterManager.renameContact(targetJid, newName)
        assertTrue(result.isSuccess)

        val updated = rosterManager.contacts.value.find { it.jid == targetJid }
        assertNotNull(updated)
        assertEquals(newName, updated!!.name)
    }

    @Test
    fun testAddContactToGroupOffline() = runBlocking {
        val connectionManager = XmppConnectionManager()
        val rosterManager = RosterManager(connectionManager)

        val targetJid = "alice@xmpp.chat.org"
        val newGroup = "VIP Members"

        val result = rosterManager.addContactToGroup(targetJid, newGroup)
        assertTrue(result.isSuccess)

        val contact = rosterManager.contacts.value.find { it.jid == targetJid }
        assertNotNull(contact)
        assertTrue(contact!!.groups.contains(newGroup))
    }

    @Test
    fun testGetContactPresence() {
        val connectionManager = XmppConnectionManager()
        val rosterManager = RosterManager(connectionManager)

        val alicePresence = rosterManager.getContactPresence("alice@xmpp.chat.org")
        assertEquals(UserPresence.ONLINE, alicePresence)

        val unknownPresence = rosterManager.getContactPresence("nonexistent_user@nowhere.org")
        assertEquals(UserPresence.OFFLINE, unknownPresence)
    }

    @Test
    fun testPresenceMappingExtension() {
        // Unavailable / offline
        val offlinePresence = PresenceBuilder.buildPresence()
            .ofType(Presence.Type.unavailable)
            .build()
        assertEquals(UserPresence.OFFLINE, offlinePresence.toUserPresence())

        // Available Online
        val onlinePresence = PresenceBuilder.buildPresence()
            .ofType(Presence.Type.available)
            .setMode(Presence.Mode.available)
            .build()
        assertEquals(UserPresence.ONLINE, onlinePresence.toUserPresence())

        // Chat
        val chatPresence = PresenceBuilder.buildPresence()
            .ofType(Presence.Type.available)
            .setMode(Presence.Mode.chat)
            .build()
        assertEquals(UserPresence.CHAT, chatPresence.toUserPresence())

        // Away
        val awayPresence = PresenceBuilder.buildPresence()
            .ofType(Presence.Type.available)
            .setMode(Presence.Mode.away)
            .build()
        assertEquals(UserPresence.AWAY, awayPresence.toUserPresence())

        // DND
        val dndPresence = PresenceBuilder.buildPresence()
            .ofType(Presence.Type.available)
            .setMode(Presence.Mode.dnd)
            .build()
        assertEquals(UserPresence.DND, dndPresence.toUserPresence())
    }
}
