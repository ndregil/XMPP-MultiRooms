package com.example.data.network

import android.util.Log
import com.example.data.model.ConnectionStatus
import com.example.data.model.UserPresence
import com.example.data.model.XmppAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.ConnectionListener
import org.jivesoftware.smack.ReconnectionManager
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.packet.Presence
import org.jivesoftware.smack.packet.PresenceBuilder
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jivesoftware.smackx.muc.MultiUserChatManager
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.parts.Resourcepart
import java.net.InetAddress

/**
 * Credentials model for XMPP authentication and server connection.
 */
data class XmppCredentials(
    val jid: String = "guest_user@xmpp.chat.org",
    val username: String = "guest_user",
    val domain: String = "xmpp.chat.org",
    val password: String = "",
    val host: String = "xmpp.chat.org",
    val port: Int = 5222,
    val resource: String = "Android-MUC",
    val nickname: String = "AndroidExplorer",
    val useTls: Boolean = true
) {
    fun toAccount(isDemoMode: Boolean = false): XmppAccount {
        return XmppAccount(
            jid = if (jid.contains("@")) jid else "$username@$domain",
            username = username,
            domain = domain,
            nickname = nickname,
            host = host,
            port = port,
            useTls = useTls,
            isDemoMode = isDemoMode
        )
    }

    companion object {
        fun fromAccount(account: XmppAccount, password: String = ""): XmppCredentials {
            return XmppCredentials(
                jid = account.jid,
                username = account.username,
                domain = account.domain,
                password = password,
                host = account.host,
                port = account.port,
                resource = "Android-MUC",
                nickname = account.nickname,
                useTls = account.useTls
            )
        }
    }
}

/**
 * Manager class responsible for initializing, establishing, and managing TCP socket connections
 * and authentication with an XMPP server using the Smack library.
 */
class XmppConnectionManager(
    initialCredentials: XmppCredentials = XmppCredentials()
) {
    companion object {
        private const val TAG = "XmppConnectionManager"
        private const val DEFAULT_CONNECT_TIMEOUT_MS = 15000
    }

    // State flows for connection status and current credentials
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _credentials = MutableStateFlow(initialCredentials)
    val credentials: StateFlow<XmppCredentials> = _credentials.asStateFlow()
    val currentCredentials: XmppCredentials get() = _credentials.value

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // Underlying Smack XMPP TCP connection
    @Volatile
    private var tcpConnection: XMPPTCPConnection? = null

    // Multi-User Chat manager associated with current connection
    @Volatile
    private var mucManager: MultiUserChatManager? = null

    // Connection listener for Smack callbacks
    private val smackConnectionListener = object : ConnectionListener {
        override fun connected(connection: XMPPConnection) {
            Log.d(TAG, "Smack: Connected to ${connection.host}:${connection.port}")
            _connectionStatus.value = ConnectionStatus.AUTHENTICATING
        }

        override fun authenticated(connection: XMPPConnection, resumed: Boolean) {
            Log.d(TAG, "Smack: Authenticated as ${connection.user} (resumed=$resumed)")
            _connectionStatus.value = ConnectionStatus.CONNECTED
            _lastError.value = null
        }

        override fun connectionClosed() {
            Log.d(TAG, "Smack: Connection closed normally")
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }

        override fun connectionClosedOnError(e: Exception) {
            Log.e(TAG, "Smack: Connection closed on error: ${e.message}", e)
            _connectionStatus.value = ConnectionStatus.ERROR
            _lastError.value = e.localizedMessage ?: "Connection closed with error"
        }
    }

    /**
     * Updates and saves login credentials in memory.
     */
    fun setCredentials(newCredentials: XmppCredentials) {
        _credentials.value = newCredentials
    }

    /**
     * Updates credentials from an [XmppAccount] object and optional password.
     */
    fun updateCredentialsFromAccount(account: XmppAccount, password: String = "") {
        _credentials.value = XmppCredentials.fromAccount(account, password)
    }

    /**
     * Clears credentials back to default guest values.
     */
    fun clearCredentials() {
        _credentials.value = XmppCredentials()
    }

    /**
     * Returns true if the TCP connection is active and connected.
     */
    fun isConnected(): Boolean = tcpConnection?.isConnected == true

    /**
     * Returns true if connection is active and user is successfully authenticated.
     */
    fun isAuthenticated(): Boolean = tcpConnection?.isAuthenticated == true

    /**
     * Access the active [XMPPTCPConnection] instance, or null if uninitialized.
     */
    fun getRawConnection(): XMPPTCPConnection? = tcpConnection

    /**
     * Access the [MultiUserChatManager] instance for MUC operations.
     */
    fun getMultiUserChatManager(): MultiUserChatManager? {
        val conn = tcpConnection
        if (conn != null && conn.isAuthenticated) {
            if (mucManager == null) {
                mucManager = MultiUserChatManager.getInstanceFor(conn)
            }
            return mucManager
        }
        return null
    }

    /**
     * Initializes Smack configuration and establishes TCP connection + login.
     * Runs asynchronously on [Dispatchers.IO].
     */
    suspend fun connectAndLogin(
        overrideCredentials: XmppCredentials? = null
    ): Result<XMPPTCPConnection> = withContext(Dispatchers.IO) {
        if (overrideCredentials != null) {
            _credentials.value = overrideCredentials
        }
        val creds = _credentials.value

        try {
            _connectionStatus.value = ConnectionStatus.CONNECTING
            _lastError.value = null

            // Clean up any stale existing connection
            safeDisconnectInternal()

            Log.i(TAG, "Initializing Smack TCP connection to ${creds.host}:${creds.port} (domain=${creds.domain})")

            val domainJid = JidCreate.domainBareFrom(creds.domain.trim())
            val username = creds.username.trim()
            val resource = creds.resource.ifBlank { "Android-MUC" }

            val configBuilder = XMPPTCPConnectionConfiguration.builder()
                .setXmppDomain(domainJid)
                .setHost(creds.host.trim())
                .setPort(creds.port)
                .setResource(Resourcepart.from(resource))
                .setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS)
                .setSecurityMode(
                    if (creds.useTls) ConnectionConfiguration.SecurityMode.ifpossible
                    else ConnectionConfiguration.SecurityMode.disabled
                )

            // Resolve host IP address if hostname is provided
            try {
                val address = InetAddress.getByName(creds.host.trim())
                configBuilder.setHostAddress(address)
            } catch (e: Exception) {
                Log.w(TAG, "DNS resolution warning for ${creds.host}: ${e.message}")
            }

            // Set username and password if provided
            if (username.isNotBlank()) {
                configBuilder.setUsernameAndPassword(username, creds.password)
            }

            val configuration = configBuilder.build()
            val connection = XMPPTCPConnection(configuration)

            // Attach connection listener
            connection.addConnectionListener(smackConnectionListener)

            // Configure automatic reconnection
            val reconnectionManager = ReconnectionManager.getInstanceFor(connection)
            reconnectionManager.enableAutomaticReconnection()

            // Establish TCP connection
            connection.connect()
            Log.i(TAG, "TCP connection established successfully.")

            // Authenticate if credentials are provided
            if (username.isNotBlank() && creds.password.isNotBlank()) {
                _connectionStatus.value = ConnectionStatus.AUTHENTICATING
                connection.login(username, creds.password, Resourcepart.from(resource))
                Log.i(TAG, "User '$username' authenticated successfully.")
            } else if (username.isNotBlank()) {
                // Anonymous or passwordless attempt
                _connectionStatus.value = ConnectionStatus.AUTHENTICATING
                connection.login()
                Log.i(TAG, "Logged in anonymously or with preconfigured credentials.")
            }

            tcpConnection = connection
            mucManager = MultiUserChatManager.getInstanceFor(connection)
            _connectionStatus.value = ConnectionStatus.CONNECTED

            Result.success(connection)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect and login with Smack: ${e.message}", e)
            _connectionStatus.value = ConnectionStatus.ERROR
            _lastError.value = e.localizedMessage ?: "XMPP connection failure"
            safeDisconnectInternal()
            Result.failure(e)
        }
    }

    /**
     * Connects socket without immediate authentication.
     */
    suspend fun connect(): Result<XMPPTCPConnection> = withContext(Dispatchers.IO) {
        val creds = _credentials.value
        try {
            _connectionStatus.value = ConnectionStatus.CONNECTING
            _lastError.value = null

            safeDisconnectInternal()

            val domainJid = JidCreate.domainBareFrom(creds.domain.trim())
            val resource = creds.resource.ifBlank { "Android-MUC" }

            val configBuilder = XMPPTCPConnectionConfiguration.builder()
                .setXmppDomain(domainJid)
                .setHost(creds.host.trim())
                .setPort(creds.port)
                .setResource(Resourcepart.from(resource))
                .setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS)
                .setSecurityMode(
                    if (creds.useTls) ConnectionConfiguration.SecurityMode.ifpossible
                    else ConnectionConfiguration.SecurityMode.disabled
                )

            val connection = XMPPTCPConnection(configBuilder.build())
            connection.addConnectionListener(smackConnectionListener)

            connection.connect()
            tcpConnection = connection
            _connectionStatus.value = ConnectionStatus.CONNECTING

            Result.success(connection)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect: ${e.message}", e)
            _connectionStatus.value = ConnectionStatus.ERROR
            _lastError.value = e.localizedMessage ?: "Connection error"
            safeDisconnectInternal()
            Result.failure(e)
        }
    }

    /**
     * Authenticates an already-connected Smack session.
     */
    suspend fun login(passwordOverride: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        val conn = tcpConnection ?: return@withContext Result.failure(IllegalStateException("Connection is not established"))
        val creds = _credentials.value
        val pass = passwordOverride ?: creds.password
        val user = creds.username.trim()
        val resource = Resourcepart.from(creds.resource.ifBlank { "Android-MUC" })

        try {
            _connectionStatus.value = ConnectionStatus.AUTHENTICATING
            if (user.isNotBlank() && pass.isNotBlank()) {
                conn.login(user, pass, resource)
            } else {
                conn.login()
            }
            mucManager = MultiUserChatManager.getInstanceFor(conn)
            _connectionStatus.value = ConnectionStatus.CONNECTED
            _lastError.value = null
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Login failed: ${e.message}", e)
            _connectionStatus.value = ConnectionStatus.ERROR
            _lastError.value = e.localizedMessage ?: "Authentication failed"
            Result.failure(e)
        }
    }

    /**
     * Broadcasts updated user presence to the server.
     */
    suspend fun updatePresence(
        userPresence: UserPresence,
        statusMessage: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val conn = tcpConnection
        if (conn == null || !conn.isAuthenticated) {
            return@withContext Result.failure(IllegalStateException("Not connected or authenticated"))
        }

        try {
            val presenceMode = when (userPresence) {
                UserPresence.ONLINE -> Presence.Mode.available
                UserPresence.CHAT -> Presence.Mode.chat
                UserPresence.AWAY -> Presence.Mode.away
                UserPresence.DND -> Presence.Mode.dnd
                UserPresence.OFFLINE -> null
            }

            val presence = if (userPresence == UserPresence.OFFLINE) {
                PresenceBuilder.buildPresence()
                    .ofType(Presence.Type.unavailable)
                    .setStatus(statusMessage ?: "Offline")
                    .build()
            } else {
                val builder = PresenceBuilder.buildPresence()
                    .ofType(Presence.Type.available)
                    .setStatus(statusMessage ?: "Active on Android MUC")
                if (presenceMode != null) {
                    builder.setMode(presenceMode)
                }
                builder.build()
            }

            conn.sendStanza(presence)
            Log.d(TAG, "Sent presence update: $userPresence, status: $statusMessage")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update presence: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Disconnects the active connection gracefully.
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        safeDisconnectInternal()
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    /**
     * Internal safe cleanup of TCP socket and listeners without throwing exceptions.
     */
    private fun safeDisconnectInternal() {
        try {
            tcpConnection?.let { conn ->
                conn.removeConnectionListener(smackConnectionListener)
                if (conn.isConnected) {
                    conn.disconnect()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception during Smack disconnect: ${e.message}")
        } finally {
            tcpConnection = null
            mucManager = null
        }
    }
}
