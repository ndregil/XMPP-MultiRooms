package com.example

import com.example.data.model.ClientCaps
import com.example.data.model.ClientPlatform
import com.example.data.model.RoomOccupant
import com.example.data.model.RosterContact
import com.example.data.model.XmppMessage
import com.example.ui.components.getClientPlatformColor
import com.example.ui.components.getClientPlatformIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientCapsTest {

    @Test
    fun testBotCapsResolution() {
        val botCaps = ClientCaps.resolveFor("BudiBot", "budibot@xmpp.today")
        assertEquals(ClientPlatform.BOT, botCaps.platform)
        assertEquals("Prosody Bot Service", botCaps.clientName)
        assertTrue(botCaps.features.any { it.contains("caps") })
    }

    @Test
    fun testDesktopDinoCapsResolution() {
        val dinoCaps = ClientCaps.resolveFor("ArchGuru", "guru@xmpp.today")
        assertEquals(ClientPlatform.DESKTOP, dinoCaps.platform)
        assertEquals("Dino", dinoCaps.clientName)
        assertTrue(dinoCaps.readableFeatures.any { it.first.contains("OMEMO") })
    }

    @Test
    fun testDesktopGajimCapsResolution() {
        val gajimCaps = ClientCaps.resolveFor("SecuritySec", "sec@xmpp.today")
        assertEquals(ClientPlatform.DESKTOP, gajimCaps.platform)
        assertEquals("Gajim", gajimCaps.clientName)
    }

    @Test
    fun testIosSiskinCapsResolution() {
        val iosCaps = ClientCaps.resolveFor("Siti_Dev", "siti@xmpp.today")
        assertEquals(ClientPlatform.IOS, iosCaps.platform)
        assertEquals("Siskin IM", iosCaps.clientName)
    }

    @Test
    fun testWebConverseCapsResolution() {
        val webCaps = ClientCaps.resolveFor("KopiLover", "kopi@xmpp.today")
        assertEquals(ClientPlatform.WEB, webCaps.platform)
        assertEquals("Converse.js", webCaps.clientName)
    }

    @Test
    fun testAndroidMultiRoomCapsResolution() {
        val androidCaps = ClientCaps.resolveFor("KotlinMaster", "km@xmpp.today")
        assertEquals(ClientPlatform.ANDROID, androidCaps.platform)
        assertEquals("XMPP MultiRoom", androidCaps.clientName)
    }

    @Test
    fun testRoomOccupantAutoAssignsCaps() {
        val occupant = RoomOccupant(
            nickname = "DinoLinuxUser",
            jid = "dino@xmpp.today/desktop"
        )
        assertEquals(ClientPlatform.DESKTOP, occupant.clientCaps.platform)
        assertEquals("Dino", occupant.clientCaps.clientName)
    }

    @Test
    fun testRosterContactAutoAssignsCaps() {
        val contact = RosterContact(
            jid = "support_bot@xmpp.today",
            name = "Support Bot"
        )
        assertEquals(ClientPlatform.BOT, contact.clientCaps.platform)
        assertEquals("Prosody Bot Service", contact.clientCaps.clientName)
    }

    @Test
    fun testXmppMessageAutoAssignsSenderCaps() {
        val msg = XmppMessage(
            roomJid = "general@conference.xmpp.today",
            senderJid = "general@conference.xmpp.today/BudiBot",
            senderNickname = "BudiBot",
            body = "Halo dari bot!"
        )
        assertEquals(ClientPlatform.BOT, msg.senderClientCaps.platform)
    }

    @Test
    fun testReadableFeaturesDescriptions() {
        val caps = ClientCaps(
            features = listOf(
                "http://jabber.org/protocol/caps",
                "http://jabber.org/protocol/muc",
                "urn:xmpp:mam:2",
                "urn:xmpp:receipts",
                "urn:xmpp:ping"
            )
        )
        val readable = caps.readableFeatures
        assertEquals(5, readable.size)
        assertTrue(readable.any { it.first.contains("XEP-0115") })
        assertTrue(readable.any { it.first.contains("XEP-0045") })
        assertTrue(readable.any { it.first.contains("XEP-0313") })
        assertTrue(readable.any { it.first.contains("XEP-0184") })
        assertTrue(readable.any { it.first.contains("XEP-0199") })
    }

    @Test
    fun testClientPlatformIconsAndColors() {
        for (platform in ClientPlatform.values()) {
            val icon = getClientPlatformIcon(platform)
            assertNotNull(icon)
            val color = getClientPlatformColor(platform)
            assertNotNull(color)
        }
    }
}
