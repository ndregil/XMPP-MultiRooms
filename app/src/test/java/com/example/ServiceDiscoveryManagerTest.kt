package com.example

import com.example.data.model.DiscoveredRoom
import com.example.data.network.ServiceDiscoveryManager
import com.example.data.network.XmppConnectionManager
import kotlinx.coroutines.runBlocking
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
class ServiceDiscoveryManagerTest {

    @Test
    fun testInitialState() {
        val connectionManager = XmppConnectionManager()
        val discoManager = ServiceDiscoveryManager(connectionManager)

        assertTrue(discoManager.discoveredRooms.value.isEmpty())
        assertTrue(discoManager.mucServiceDomains.value.isEmpty())
        assertFalse(discoManager.isDiscovering.value)
    }

    @Test
    fun testFindMucServicesFallback() = runBlocking {
        val connectionManager = XmppConnectionManager()
        val discoManager = ServiceDiscoveryManager(connectionManager)

        val result = discoManager.findMucServices()
        assertTrue(result.isSuccess)
        val services = result.getOrNull()
        assertNotNull(services)
        assertTrue(services!!.isNotEmpty())
        assertTrue(services.any { it.contains("conference") })
        assertEquals(services, discoManager.mucServiceDomains.value)
    }

    @Test
    fun testDiscoverRoomsReturnsRoomsAndUpdatesFlow() = runBlocking {
        val connectionManager = XmppConnectionManager()
        val discoManager = ServiceDiscoveryManager(connectionManager)

        val result = discoManager.discoverRooms()
        assertTrue(result.isSuccess)
        val rooms = result.getOrNull()
        assertNotNull(rooms)
        assertTrue(rooms!!.isNotEmpty())
        assertEquals(rooms.size, discoManager.discoveredRooms.value.size)

        val generalRoom = rooms.find { it.jid.startsWith("general@") }
        assertNotNull(generalRoom)
        assertTrue(generalRoom!!.name.isNotBlank())
        assertTrue(generalRoom.occupantsCount > 0)
        assertFalse(generalRoom.isPasswordProtected)
    }

    @Test
    fun testDiscoverItemsWhenNotConnectedFailsSafely() = runBlocking {
        val connectionManager = XmppConnectionManager()
        val discoManager = ServiceDiscoveryManager(connectionManager)

        val result = discoManager.discoverItems("conference.xmpp.today")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun testSupportsFeatureWhenNotConnectedReturnsFalse() = runBlocking {
        val connectionManager = XmppConnectionManager()
        val discoManager = ServiceDiscoveryManager(connectionManager)

        val supported = discoManager.supportsFeature("conference.xmpp.today", ServiceDiscoveryManager.MUC_FEATURE_NAMESPACE)
        assertFalse(supported)
    }

    @Test
    fun testDiscoveredRoomMappingToPublicDirectoryRoom() {
        val discoveredRoom = DiscoveredRoom(
            jid = "android-chat@conference.xmpp.org",
            name = "Android Developers",
            serviceJid = "conference.xmpp.org",
            description = "Kotlin and Compose discussions",
            occupantsCount = 18,
            subject = "Jetpack Compose discussion",
            isPasswordProtected = false,
            isMembersOnly = false,
            isPersistent = true
        )

        val dirRoom = discoveredRoom.toPublicDirectoryRoom()
        assertEquals("android-chat@conference.xmpp.org", dirRoom.jid)
        assertEquals("Android Developers", dirRoom.name)
        assertEquals("Kotlin and Compose discussions", dirRoom.description)
        assertEquals(18, dirRoom.occupantsCount)
        assertEquals("General", dirRoom.category)
    }
}
