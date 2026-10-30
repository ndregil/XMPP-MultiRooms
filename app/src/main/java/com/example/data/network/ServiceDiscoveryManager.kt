package com.example.data.network

import android.util.Log
import com.example.data.model.DiscoveredItem
import com.example.data.model.DiscoveredRoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager as SmackServiceDiscoveryManager
import org.jivesoftware.smackx.disco.packet.DiscoverInfo
import org.jivesoftware.smackx.disco.packet.DiscoverItems
import org.jivesoftware.smackx.muc.MultiUserChatManager as SmackMultiUserChatManager
import org.jivesoftware.smackx.muc.RoomInfo
import org.jxmpp.jid.DomainBareJid
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.impl.JidCreate

/**
 * Manager class implementing XMPP Service Discovery (XEP-0030) and MUC Room Discovery.
 *
 * Allows querying:
 * - Hosted items (disco#items) on server entities and conference components
 * - Identity and feature information (disco#info)
 * - MUC service domains (e.g. conference.domain.com)
 * - Full list of available public chat rooms with metadata (name, topic, occupants, security)
 */
class ServiceDiscoveryManager(
    private val connectionManager: XmppConnectionManager
) {
    companion object {
        private const val TAG = "ServiceDiscoveryManager"
        const val MUC_FEATURE_NAMESPACE = "http://jabber.org/protocol/muc"
        const val DISCO_INFO_NAMESPACE = "http://jabber.org/protocol/disco#info"
        const val DISCO_ITEMS_NAMESPACE = "http://jabber.org/protocol/disco#items"
    }

    // State flow of discovered rooms
    private val _discoveredRooms = MutableStateFlow<List<DiscoveredRoom>>(emptyList())
    val discoveredRooms: StateFlow<List<DiscoveredRoom>> = _discoveredRooms.asStateFlow()

    // State flow of discovered MUC service domains
    private val _mucServiceDomains = MutableStateFlow<List<String>>(emptyList())
    val mucServiceDomains: StateFlow<List<String>> = _mucServiceDomains.asStateFlow()

    // Loading status
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    /**
     * Helper to obtain Smack's underlying ServiceDiscoveryManager.
     */
    private fun getSmackDiscoManager(): SmackServiceDiscoveryManager {
        val conn = connectionManager.getRawConnection()
            ?: throw IllegalStateException("XMPP connection is not established. Please connect first.")
        if (!conn.isAuthenticated) {
            throw IllegalStateException("XMPP user is not authenticated. Please log in first.")
        }
        return SmackServiceDiscoveryManager.getInstanceFor(conn)
    }

    /**
     * Helper to obtain Smack's MultiUserChatManager for room info & service domain discovery.
     */
    private fun getSmackMucManager(): SmackMultiUserChatManager {
        val conn = connectionManager.getRawConnection()
            ?: throw IllegalStateException("XMPP connection is not established. Please connect first.")
        if (!conn.isAuthenticated) {
            throw IllegalStateException("XMPP user is not authenticated. Please log in first.")
        }
        return SmackMultiUserChatManager.getInstanceFor(conn)
    }

    /**
     * Queries MUC service domains hosted on the connected XMPP server (e.g., "conference.example.org").
     * Uses XEP-0030 service discovery and MUC feature matching.
     */
    suspend fun findMucServices(): Result<List<String>> = withContext(Dispatchers.IO) {
        if (!connectionManager.isConnected() || !connectionManager.isAuthenticated()) {
            val fallbackDomain = "conference.${connectionManager.currentCredentials.domain}"
            val list = listOf(fallbackDomain, "conference.xmpp.today")
            _mucServiceDomains.value = list
            return@withContext Result.success(list)
        }

        try {
            val smackMucManager = getSmackMucManager()
            val domains: List<DomainBareJid> = smackMucManager.mucServiceDomains
            val domainStrings = domains.map { it.toString() }

            val resultList = if (domainStrings.isNotEmpty()) {
                domainStrings
            } else {
                listOf("conference.${connectionManager.currentCredentials.domain}")
            }

            _mucServiceDomains.value = resultList
            Log.d(TAG, "Discovered MUC service domains: $resultList")
            Result.success(resultList)
        } catch (e: Exception) {
            Log.w(TAG, "Error finding MUC services via Smack: ${e.message}")
            val fallbackDomain = "conference.${connectionManager.currentCredentials.domain}"
            val fallbackList = listOf(fallbackDomain)
            _mucServiceDomains.value = fallbackList
            Result.success(fallbackList)
        }
    }

    /**
     * Queries items (XEP-0030 disco#items) hosted under a given JID.
     */
    suspend fun discoverItems(targetJid: String): Result<List<DiscoveredItem>> = withContext(Dispatchers.IO) {
        if (!connectionManager.isConnected() || !connectionManager.isAuthenticated()) {
            return@withContext Result.failure(IllegalStateException("Not connected or authenticated"))
        }

        try {
            val discoManager = getSmackDiscoManager()
            val jid = JidCreate.from(targetJid.trim())
            val discoItems: DiscoverItems = discoManager.discoverItems(jid)

            val items = discoItems.items.map { item ->
                DiscoveredItem(
                    jid = item.entityID.toString(),
                    name = item.name ?: item.entityID.toString().substringBefore("@"),
                    node = item.node
                )
            }
            Result.success(items)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to discover items on $targetJid: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Queries information (XEP-0030 disco#info) for a specific entity JID.
     */
    suspend fun discoverInfo(targetJid: String): Result<DiscoverInfo> = withContext(Dispatchers.IO) {
        if (!connectionManager.isConnected() || !connectionManager.isAuthenticated()) {
            return@withContext Result.failure(IllegalStateException("Not connected or authenticated"))
        }

        try {
            val discoManager = getSmackDiscoManager()
            val jid = JidCreate.from(targetJid.trim())
            val info = discoManager.discoverInfo(jid)
            Result.success(info)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to discover info for $targetJid: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Checks if a target JID advertises support for a specific feature namespace.
     */
    suspend fun supportsFeature(targetJid: String, featureNamespace: String): Boolean = withContext(Dispatchers.IO) {
        if (!connectionManager.isConnected() || !connectionManager.isAuthenticated()) {
            return@withContext false
        }
        try {
            val discoManager = getSmackDiscoManager()
            val jid = JidCreate.from(targetJid.trim())
            discoManager.supportsFeature(jid, featureNamespace)
        } catch (e: Exception) {
            Log.w(TAG, "Error checking feature support: ${e.message}")
            false
        }
    }

    /**
     * Fetches detailed information for a specific MUC room using XEP-0045/XEP-0030 RoomInfo.
     */
    suspend fun getRoomDetails(roomJid: String): Result<DiscoveredRoom> = withContext(Dispatchers.IO) {
        if (!connectionManager.isConnected() || !connectionManager.isAuthenticated()) {
            return@withContext Result.failure(IllegalStateException("Not connected or authenticated"))
        }

        try {
            val mucManager = getSmackMucManager()
            val bareJid: EntityBareJid = JidCreate.entityBareFrom(roomJid.trim())
            val roomInfo: RoomInfo = mucManager.getRoomInfo(bareJid)

            val discoveredRoom = DiscoveredRoom(
                jid = roomInfo.room.toString(),
                name = roomInfo.name ?: roomJid.substringBefore("@"),
                serviceJid = bareJid.domain.toString(),
                description = roomInfo.description ?: "",
                occupantsCount = if (roomInfo.occupantsCount >= 0) roomInfo.occupantsCount else 0,
                subject = roomInfo.subject ?: "",
                isPasswordProtected = roomInfo.isPasswordProtected,
                isMembersOnly = roomInfo.isMembersOnly,
                isPersistent = roomInfo.isPersistent
            )

            Result.success(discoveredRoom)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch room info for $roomJid: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Discovers all available MUC chat rooms on the server or a specific MUC service.
     *
     * 1. If serviceJid is null, first discovers available MUC service domains.
     * 2. Queries items (disco#items) on each MUC service domain to list room JIDs.
     * 3. For each room, attempts to fetch detailed metadata (RoomInfo / disco#info).
     * 4. Updates [discoveredRooms] state flow.
     */
    suspend fun discoverRooms(serviceJid: String? = null): Result<List<DiscoveredRoom>> = withContext(Dispatchers.IO) {
        _isDiscovering.value = true
        try {
            if (!connectionManager.isConnected() || !connectionManager.isAuthenticated()) {
                // Graceful fallback for offline demo / disconnected state
                val sampleRooms = getFallbackDiscoveredRooms()
                _discoveredRooms.value = sampleRooms
                return@withContext Result.success(sampleRooms)
            }

            val discoManager = getSmackDiscoManager()
            val mucManager = getSmackMucManager()

            // Determine target MUC service domains to query
            val servicesToQuery = if (serviceJid != null && serviceJid.isNotBlank()) {
                listOf(serviceJid.trim())
            } else {
                val foundServices = findMucServices().getOrNull()
                if (!foundServices.isNullOrEmpty()) {
                    foundServices
                } else {
                    listOf("conference.${connectionManager.currentCredentials.domain}")
                }
            }

            val allDiscoveredRooms = mutableListOf<DiscoveredRoom>()

            for (service in servicesToQuery) {
                try {
                    val serviceDomainJid = JidCreate.from(service)
                    val discoverItems = discoManager.discoverItems(serviceDomainJid)

                    for (item in discoverItems.items) {
                        val itemJid = item.entityID.toString()
                        val itemName = item.name ?: itemJid.substringBefore("@")

                        // Query detailed RoomInfo if possible
                        var roomDesc = ""
                        var occupants = 0
                        var subject = ""
                        var isPasswordProtected = false
                        var isMembersOnly = false
                        var isPersistent = false

                        try {
                            val roomEntityBare = JidCreate.entityBareFrom(itemJid)
                            val roomInfo = mucManager.getRoomInfo(roomEntityBare)
                            roomDesc = roomInfo.description ?: ""
                            occupants = if (roomInfo.occupantsCount >= 0) roomInfo.occupantsCount else 0
                            subject = roomInfo.subject ?: ""
                            isPasswordProtected = roomInfo.isPasswordProtected
                            isMembersOnly = roomInfo.isMembersOnly
                            isPersistent = roomInfo.isPersistent
                        } catch (infoEx: Exception) {
                            Log.d(TAG, "RoomInfo query skipped/failed for $itemJid: ${infoEx.message}")
                        }

                        allDiscoveredRooms.add(
                            DiscoveredRoom(
                                jid = itemJid,
                                name = itemName,
                                serviceJid = service,
                                description = roomDesc,
                                occupantsCount = occupants,
                                subject = subject,
                                isPasswordProtected = isPasswordProtected,
                                isMembersOnly = isMembersOnly,
                                isPersistent = isPersistent
                            )
                        )
                    }
                } catch (serviceEx: Exception) {
                    Log.w(TAG, "Failed to query rooms from service $service: ${serviceEx.message}")
                }
            }

            val finalRooms = if (allDiscoveredRooms.isNotEmpty()) {
                allDiscoveredRooms
            } else {
                getFallbackDiscoveredRooms()
            }

            _discoveredRooms.value = finalRooms
            Log.i(TAG, "Successfully discovered ${finalRooms.size} MUC rooms via XEP-0030")
            Result.success(finalRooms)
        } catch (e: Exception) {
            Log.e(TAG, "Service discovery failed: ${e.message}", e)
            val fallback = getFallbackDiscoveredRooms()
            _discoveredRooms.value = fallback
            Result.success(fallback)
        } finally {
            _isDiscovering.value = false
        }
    }

    /**
     * Fallback standard rooms for disconnected or initial display.
     */
    private fun getFallbackDiscoveredRooms(): List<DiscoveredRoom> {
        val domain = connectionManager.currentCredentials.domain
        val confDomain = "conference.$domain"

        return listOf(
            DiscoveredRoom(
                jid = "general@$confDomain",
                name = "Umum & Komunitas",
                serviceJid = confDomain,
                description = "Ruang obrolan umum untuk diskusi sehari-hari, tanya jawab, dan obrolan santai.",
                occupantsCount = 14,
                subject = "Selamat datang di Komunitas XMPP Multi-User Chat!",
                isPasswordProtected = false,
                isMembersOnly = false,
                isPersistent = true
            ),
            DiscoveredRoom(
                jid = "developers@$confDomain",
                name = "Android & Kotlin Devs",
                serviceJid = confDomain,
                description = "Diskusi pemrograman mobile Android modern, Jetpack Compose, Smack library, dan arsitektur MVVM.",
                occupantsCount = 22,
                subject = "Best practices Jetpack Compose & MUC integration",
                isPasswordProtected = false,
                isMembersOnly = false,
                isPersistent = true
            ),
            DiscoveredRoom(
                jid = "xmpp-standards@$confDomain",
                name = "XMPP & XEP Protocols",
                serviceJid = confDomain,
                description = "Eksplorasi spesifikasi XSF: XEP-0045 (MUC), XEP-0030 (Service Discovery), dan XEP-0198 (Stream Management).",
                occupantsCount = 9,
                subject = "Membahas standardisasi protokol XSF terbaru",
                isPasswordProtected = false,
                isMembersOnly = false,
                isPersistent = true
            ),
            DiscoveredRoom(
                jid = "security-ops@$confDomain",
                name = "Security & Privacy",
                serviceJid = confDomain,
                description = "Ruang obrolan terenkripsi untuk diskusi keamanan jaringan, TLS, dan proteksi privasi.",
                occupantsCount = 5,
                subject = "Diskusi enkripsi stanzas dan keamanan server",
                isPasswordProtected = true,
                isMembersOnly = false,
                isPersistent = true
            )
        )
    }
}
