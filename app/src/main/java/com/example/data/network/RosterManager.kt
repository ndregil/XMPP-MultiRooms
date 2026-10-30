package com.example.data.network

import android.util.Log
import com.example.data.model.RosterContact
import com.example.data.model.UserPresence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jivesoftware.smack.packet.Presence
import org.jivesoftware.smack.packet.PresenceBuilder
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smack.roster.RosterEntry
import org.jivesoftware.smack.roster.RosterGroup
import org.jivesoftware.smack.roster.RosterListener
import org.jivesoftware.smack.roster.RosterLoadedListener
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate

/**
 * Manager class using Smack's Roster API to fetch, add, remove, rename, and
 * monitor online/offline presence status of XMPP contacts.
 *
 * Implements [RosterListener] to observe real-time roster updates and presence broadcasts.
 * Includes demo fallback mode for offline testing and rapid prototyping.
 */
class RosterManager(
    private val connectionManager: XmppConnectionManager,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    companion object {
        private const val TAG = "RosterManager"

        val DEMO_CONTACTS = listOf(
            RosterContact(
                jid = "alice@xmpp.chat.org",
                name = "Alice Vance",
                presence = UserPresence.ONLINE,
                statusMessage = "Working on XMPP MUC Client 🚀",
                groups = listOf("Colleagues"),
                subscriptionType = "both"
            ),
            RosterContact(
                jid = "bob@xmpp.chat.org",
                name = "Bob Martin",
                presence = UserPresence.CHAT,
                statusMessage = "Available for chat & code review",
                groups = listOf("Friends"),
                subscriptionType = "both"
            ),
            RosterContact(
                jid = "charlie@xmpp.chat.org",
                name = "Charlie Brown",
                presence = UserPresence.AWAY,
                statusMessage = "Stepped out for lunch 🥪",
                groups = listOf("Colleagues"),
                subscriptionType = "both"
            ),
            RosterContact(
                jid = "david@xmpp.chat.org",
                name = "David Hassel",
                presence = UserPresence.DND,
                statusMessage = "Focus mode: Do Not Disturb ⛔",
                groups = listOf("Developers"),
                subscriptionType = "to"
            ),
            RosterContact(
                jid = "eva@xmpp.chat.org",
                name = "Eva Green",
                presence = UserPresence.OFFLINE,
                statusMessage = "Last seen today at 10:15",
                groups = listOf("Friends"),
                subscriptionType = "both"
            )
        )
    }

    // Reactive StateFlows for UI observation
    private val _contacts = MutableStateFlow<List<RosterContact>>(DEMO_CONTACTS)
    val contacts: StateFlow<List<RosterContact>> = _contacts.asStateFlow()

    private val _presenceMap = MutableStateFlow<Map<String, UserPresence>>(
        DEMO_CONTACTS.associate { it.jid to it.presence }
    )
    val presenceMap: StateFlow<Map<String, UserPresence>> = _presenceMap.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // Active Smack Roster instance
    @Volatile
    private var activeRoster: Roster? = null

    // Listener for Roster events
    private val rosterListener = object : RosterListener {
        override fun entriesAdded(addresses: Collection<Jid>) {
            Log.d(TAG, "Roster entries added: ${addresses.map { it.toString() }}")
            coroutineScope.launch {
                refreshContactsFromRoster()
            }
        }

        override fun entriesUpdated(addresses: Collection<Jid>) {
            Log.d(TAG, "Roster entries updated: ${addresses.map { it.toString() }}")
            coroutineScope.launch {
                refreshContactsFromRoster()
            }
        }

        override fun entriesDeleted(addresses: Collection<Jid>) {
            Log.d(TAG, "Roster entries deleted: ${addresses.map { it.toString() }}")
            val deletedBareJids = addresses.map { it.asBareJid().toString() }.toSet()
            _contacts.value = _contacts.value.filterNot { it.jid in deletedBareJids }
            _presenceMap.value = _presenceMap.value.filterKeys { it !in deletedBareJids }
        }

        override fun presenceChanged(presence: Presence) {
            val fromJid = presence.from?.asBareJid()?.toString() ?: return
            val userPresence = presence.toUserPresence()
            val statusMsg = presence.status ?: ""

            Log.d(TAG, "Presence changed for $fromJid: $userPresence ($statusMsg)")

            // Update presence map
            _presenceMap.value = _presenceMap.value + (fromJid to userPresence)

            // Update contact in contacts list
            _contacts.value = _contacts.value.map { contact ->
                if (contact.jid.equals(fromJid, ignoreCase = true)) {
                    contact.copy(
                        presence = userPresence,
                        statusMessage = if (statusMsg.isNotBlank()) statusMsg else contact.statusMessage
                    )
                } else {
                    contact
                }
            }
        }
    }

    private val rosterLoadedListener = object : RosterLoadedListener {
        override fun onRosterLoaded(roster: Roster) {
            Log.i(TAG, "Smack Roster loaded callback received. Entry count: ${roster.entries.size}")
            _isLoaded.value = true
            coroutineScope.launch {
                refreshContactsFromRoster()
            }
        }

        override fun onRosterLoadingFailed(exception: Exception) {
            Log.e(TAG, "Smack Roster loading failed: ${exception.message}", exception)
            _isLoading.value = false
        }
    }

    /**
     * Initializes the Smack Roster for the active connection, sets subscription mode,
     * and attaches listeners.
     */
    suspend fun initializeRoster(): Result<Unit> = withContext(Dispatchers.IO) {
        val connection = connectionManager.getRawConnection()
        if (connection == null || !connection.isAuthenticated) {
            Log.d(TAG, "Not connected or authenticated. Using demo contact set.")
            _isLoaded.value = true
            return@withContext Result.success(Unit)
        }

        try {
            _isLoading.value = true
            _lastError.value = null

            val roster = Roster.getInstanceFor(connection)
            activeRoster = roster

            // Automatically accept incoming presence subscription requests
            roster.subscriptionMode = Roster.SubscriptionMode.accept_all

            // Attach listeners (ensure not duplicated)
            roster.removeRosterListener(rosterListener)
            roster.addRosterListener(rosterListener)

            roster.removeRosterLoadedListener(rosterLoadedListener)
            roster.addRosterLoadedListener(rosterLoadedListener)

            if (roster.isLoaded) {
                _isLoaded.value = true
                refreshContactsFromRoster()
            } else {
                try {
                    roster.reload()
                } catch (e: Exception) {
                    Log.w(TAG, "Initial roster reload warning: ${e.message}")
                }
            }

            _isLoading.value = false
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Smack Roster: ${e.message}", e)
            _lastError.value = e.localizedMessage ?: "Roster initialization failure"
            _isLoading.value = false
            Result.failure(e)
        }
    }

    /**
     * Fetches or refreshes contacts list from Smack Roster, with offline fallback.
     */
    suspend fun loadContacts(): Result<List<RosterContact>> = withContext(Dispatchers.IO) {
        val connection = connectionManager.getRawConnection()
        if (connection == null || !connection.isAuthenticated) {
            _isLoaded.value = true
            return@withContext Result.success(_contacts.value)
        }

        try {
            _isLoading.value = true
            _lastError.value = null

            val roster = activeRoster ?: Roster.getInstanceFor(connection).also {
                activeRoster = it
                it.addRosterListener(rosterListener)
            }

            if (!roster.isLoaded) {
                try {
                    roster.reload()
                } catch (e: Exception) {
                    Log.w(TAG, "Roster reload caught non-fatal exception: ${e.message}")
                }
            }

            val contactsList = refreshContactsFromRoster()
            _isLoading.value = false
            _isLoaded.value = true
            Result.success(contactsList)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching contacts from Roster: ${e.message}", e)
            _lastError.value = e.localizedMessage ?: "Failed to load contacts"
            _isLoading.value = false
            Result.failure(e)
        }
    }

    /**
     * Adds a new contact (XEP-0160 / RFC 6121) to the roster, assigning optional group,
     * and sends a subscription request stanza.
     */
    suspend fun addContact(
        jidString: String,
        name: String,
        groupName: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanJid = jidString.trim()
        if (cleanJid.isBlank() || !cleanJid.contains("@")) {
            return@withContext Result.failure(IllegalArgumentException("Invalid XMPP JID format: '$cleanJid'. Contact JID must be in user@domain format."))
        }

        val bareJid: BareJid = try {
            JidCreate.bareFrom(cleanJid)
        } catch (e: Exception) {
            return@withContext Result.failure(IllegalArgumentException("Invalid XMPP JID format: '$cleanJid'"))
        }

        val cleanName = name.trim().ifBlank { bareJid.localpartOrNull?.asUnescapedString() ?: cleanJid }
        val connection = connectionManager.getRawConnection()

        if (connection != null && connection.isAuthenticated) {
            try {
                val roster = activeRoster ?: Roster.getInstanceFor(connection).also { activeRoster = it }
                val groups = if (!groupName.isNullOrBlank()) arrayOf(groupName.trim()) else null

                // Create item in Smack roster
                roster.createItem(bareJid, cleanName, groups)

                // Send subscription request presence
                val subscribePresence = PresenceBuilder.buildPresence()
                    .ofType(Presence.Type.subscribe)
                    .to(bareJid)
                    .build()
                connection.sendStanza(subscribePresence)

                Log.i(TAG, "Contact added to roster and subscription sent: $bareJid")
                refreshContactsFromRoster()
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add contact '$cleanJid' to Smack roster: ${e.message}", e)
                _lastError.value = e.localizedMessage ?: "Failed to add contact"
                Result.failure(e)
            }
        } else {
            // Local offline demo mode addition
            val newContact = RosterContact(
                jid = cleanJid,
                name = cleanName,
                presence = UserPresence.ONLINE,
                statusMessage = "Added in local session",
                groups = if (!groupName.isNullOrBlank()) listOf(groupName.trim()) else listOf("Contacts"),
                subscriptionType = "both"
            )
            _contacts.value = _contacts.value.filterNot { it.jid.equals(cleanJid, ignoreCase = true) } + newContact
            _presenceMap.value = _presenceMap.value + (cleanJid to UserPresence.ONLINE)
            Result.success(Unit)
        }
    }

    /**
     * Removes an entry from the XMPP roster and sends an unsubscribe stanza.
     */
    suspend fun removeContact(jidString: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanJid = jidString.trim()
        val bareJid = try {
            JidCreate.bareFrom(cleanJid)
        } catch (e: Exception) {
            return@withContext Result.failure(IllegalArgumentException("Invalid JID: $cleanJid"))
        }

        val connection = connectionManager.getRawConnection()
        if (connection != null && connection.isAuthenticated) {
            try {
                val roster = activeRoster ?: Roster.getInstanceFor(connection).also { activeRoster = it }
                val entry = roster.getEntry(bareJid)
                if (entry != null) {
                    roster.removeEntry(entry)
                }

                // Send unsubscribe stanza
                val unsubscribePresence = PresenceBuilder.buildPresence()
                    .ofType(Presence.Type.unsubscribe)
                    .to(bareJid)
                    .build()
                connection.sendStanza(unsubscribePresence)

                Log.i(TAG, "Contact removed from roster: $bareJid")
                refreshContactsFromRoster()
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove contact $cleanJid: ${e.message}", e)
                _lastError.value = e.localizedMessage ?: "Failed to remove contact"
                Result.failure(e)
            }
        } else {
            // Offline demo removal
            _contacts.value = _contacts.value.filterNot { it.jid.equals(cleanJid, ignoreCase = true) }
            _presenceMap.value = _presenceMap.value.filterKeys { !it.equals(cleanJid, ignoreCase = true) }
            Result.success(Unit)
        }
    }

    /**
     * Renames a contact's display name in the roster.
     */
    suspend fun renameContact(jidString: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Name cannot be blank"))
        }

        val cleanJid = jidString.trim()
        val connection = connectionManager.getRawConnection()

        if (connection != null && connection.isAuthenticated) {
            try {
                val bareJid = JidCreate.bareFrom(cleanJid)
                val roster = activeRoster ?: Roster.getInstanceFor(connection).also { activeRoster = it }
                val entry = roster.getEntry(bareJid)
                if (entry != null) {
                    entry.name = cleanName
                    Log.i(TAG, "Contact renamed in Smack roster: $bareJid -> $cleanName")
                }
                refreshContactsFromRoster()
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to rename contact $cleanJid: ${e.message}", e)
                Result.failure(e)
            }
        } else {
            // Offline demo update
            _contacts.value = _contacts.value.map { contact ->
                if (contact.jid.equals(cleanJid, ignoreCase = true)) {
                    contact.copy(name = cleanName)
                } else {
                    contact
                }
            }
            Result.success(Unit)
        }
    }

    /**
     * Adds a contact to a specified group in the roster.
     */
    suspend fun addContactToGroup(jidString: String, groupName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanGroup = groupName.trim()
        if (cleanGroup.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Group name cannot be blank"))
        }

        val cleanJid = jidString.trim()
        val connection = connectionManager.getRawConnection()

        if (connection != null && connection.isAuthenticated) {
            try {
                val bareJid = JidCreate.bareFrom(cleanJid)
                val roster = activeRoster ?: Roster.getInstanceFor(connection).also { activeRoster = it }
                val entry = roster.getEntry(bareJid)
                    ?: return@withContext Result.failure(IllegalStateException("Contact not found in roster"))

                var group = roster.getGroup(cleanGroup)
                if (group == null) {
                    group = roster.createGroup(cleanGroup)
                }
                group.addEntry(entry)

                Log.i(TAG, "Added contact $cleanJid to group $cleanGroup")
                refreshContactsFromRoster()
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to assign group: ${e.message}", e)
                Result.failure(e)
            }
        } else {
            _contacts.value = _contacts.value.map { contact ->
                if (contact.jid.equals(cleanJid, ignoreCase = true)) {
                    val updatedGroups = (contact.groups + cleanGroup).distinct()
                    contact.copy(groups = updatedGroups)
                } else {
                    contact
                }
            }
            Result.success(Unit)
        }
    }

    /**
     * Queries presence for a given JID from cache or Smack Roster.
     */
    fun getContactPresence(jidString: String): UserPresence {
        _presenceMap.value[jidString]?.let { return it }

        val connection = connectionManager.getRawConnection()
        if (connection != null && connection.isAuthenticated) {
            try {
                val bareJid = JidCreate.bareFrom(jidString)
                val roster = activeRoster ?: Roster.getInstanceFor(connection)
                val presence = roster.getPresence(bareJid)
                val userPresence = presence.toUserPresence()
                _presenceMap.value = _presenceMap.value + (jidString to userPresence)
                return userPresence
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
        return UserPresence.OFFLINE
    }

    /**
     * Sends a manual subscription request to a peer.
     */
    suspend fun sendSubscriptionRequest(jidString: String): Result<Unit> = withContext(Dispatchers.IO) {
        val connection = connectionManager.getRawConnection()
            ?: return@withContext Result.failure(IllegalStateException("Connection not available"))

        try {
            val bareJid = JidCreate.bareFrom(jidString.trim())
            val presence = PresenceBuilder.buildPresence()
                .ofType(Presence.Type.subscribe)
                .to(bareJid)
                .build()
            connection.sendStanza(presence)
            Log.i(TAG, "Sent subscription request to $bareJid")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed sending subscription request: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Internal helper to convert Smack Roster entries to [RosterContact] list
     * and update [_contacts] and [_presenceMap].
     */
    private fun refreshContactsFromRoster(): List<RosterContact> {
        val roster = activeRoster ?: return _contacts.value

        val entries: Set<RosterEntry> = try {
            roster.entries
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch entries from roster: ${e.message}")
            emptySet()
        }

        if (entries.isEmpty() && !connectionManager.isAuthenticated()) {
            return _contacts.value
        }

        val mappedContacts = entries.map { entry ->
            val bareJid = entry.jid
            val jidString = bareJid.toString()
            val presence = roster.getPresence(bareJid)
            val userPresence = presence.toUserPresence()
            val statusMsg = presence.status ?: ""
            val groupNames = entry.groups.map { it.name }

            RosterContact(
                jid = jidString,
                name = entry.name ?: "",
                presence = userPresence,
                statusMessage = statusMsg,
                groups = groupNames,
                subscriptionType = entry.type.name.lowercase(),
                isPendingApproval = entry.isApproved.not()
            )
        }

        _contacts.value = mappedContacts
        _presenceMap.value = mappedContacts.associate { it.jid to it.presence }
        return mappedContacts
    }

    /**
     * Cleans up listeners when disconnecting.
     */
    fun release() {
        try {
            activeRoster?.removeRosterListener(rosterListener)
            activeRoster?.removeRosterLoadedListener(rosterLoadedListener)
        } catch (e: Exception) {
            Log.w(TAG, "Exception during RosterManager release: ${e.message}")
        } finally {
            activeRoster = null
        }
    }
}

/**
 * Maps Smack's [Presence] stanza mode/type to application domain [UserPresence].
 */
fun Presence.toUserPresence(): UserPresence {
    if (!isAvailable || type == Presence.Type.unavailable) {
        return UserPresence.OFFLINE
    }
    return when (mode) {
        Presence.Mode.chat -> UserPresence.CHAT
        Presence.Mode.available -> UserPresence.ONLINE
        Presence.Mode.away, Presence.Mode.xa -> UserPresence.AWAY
        Presence.Mode.dnd -> UserPresence.DND
        null -> UserPresence.ONLINE
    }
}
