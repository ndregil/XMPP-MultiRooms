package com.example.data.network.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.entity.MessageEntity
import com.example.data.model.DeliveryStatus
import com.example.data.model.MessageType
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class PushMessagePayload(
    val messageId: String = UUID.randomUUID().toString(),
    val roomJid: String,
    val senderNickname: String,
    val body: String,
    val isWhisper: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

object FcmPushManager {

    const val CHANNEL_MUC = "xmpp_muc_channel"
    const val CHANNEL_WHISPER = "xmpp_whisper_channel"
    const val EXTRA_ROOM_JID = "target_room_jid"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
    const val ACTION_MARK_AS_READ = "com.example.ACTION_MARK_AS_READ"

    private const val PREFS_NAME = "xmpp_fcm_prefs"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val KEY_PUSH_ENABLED = "push_enabled"
    private const val KEY_PUSH_NODE = "push_node"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _fcmToken = MutableStateFlow<String?>("fcm_token_xmpp_" + UUID.randomUUID().toString().take(12))
    val fcmToken: StateFlow<String?> = _fcmToken.asStateFlow()

    private val _isPushEnabled = MutableStateFlow(true)
    val isPushEnabled: StateFlow<Boolean> = _isPushEnabled.asStateFlow()

    private val _pushServerNode = MutableStateFlow("push.xmpp.today")
    val pushServerNode: StateFlow<String> = _pushServerNode.asStateFlow()

    private val _lastReceivedPayload = MutableStateFlow<PushMessagePayload?>(null)
    val lastReceivedPayload: StateFlow<PushMessagePayload?> = _lastReceivedPayload.asStateFlow()

    // Tracks currently active/foreground room JID to avoid spamming alerts if user is in that room
    var activeRoomJid: String? = null

    fun init(context: Context) {
        createNotificationChannels(context)
        loadPreferences(context)

        // Attempt to fetch live FCM Token from FirebaseMessaging
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) {
                        updateToken(context, token)
                    }
                }
                .addOnFailureListener {
                    // Handled gracefully in environments without full Play Services
                }
        } catch (e: Exception) {
            // Handled gracefully
        }
    }

    private fun loadPreferences(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedToken = prefs.getString(KEY_FCM_TOKEN, null)
        if (!savedToken.isNullOrBlank()) {
            _fcmToken.value = savedToken
        }
        _isPushEnabled.value = prefs.getBoolean(KEY_PUSH_ENABLED, true)
        _pushServerNode.value = prefs.getString(KEY_PUSH_NODE, "push.xmpp.today") ?: "push.xmpp.today"
    }

    fun updateToken(context: Context, token: String) {
        _fcmToken.value = token
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun setPushEnabled(context: Context, enabled: Boolean) {
        _isPushEnabled.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PUSH_ENABLED, enabled).apply()
    }

    fun setPushServerNode(context: Context, node: String) {
        _pushServerNode.value = node
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PUSH_NODE, node).apply()
    }

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // MUC Group Chat Channel
            val mucChannel = NotificationChannel(
                CHANNEL_MUC,
                "XMPP Room & Group Chat",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi pesan baru di room obrolan multi-user XMPP"
                enableVibration(true)
                setShowBadge(true)
            }

            // Private Whisper Channel
            val whisperChannel = NotificationChannel(
                CHANNEL_WHISPER,
                "Bisikan Pribadi XMPP",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi pesan bisikan pribadi antar pengguna di room"
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(mucChannel)
            notificationManager.createNotificationChannel(whisperChannel)
        }
    }

    /**
     * Shows an Android system notification for an incoming push message.
     */
    fun showSystemNotification(context: Context, payload: PushMessagePayload) {
        if (!_isPushEnabled.value) return

        // If the user is currently viewing this room, suppress notification
        if (activeRoomJid != null && activeRoomJid.equals(payload.roomJid, ignoreCase = true)) {
            return
        }

        _lastReceivedPayload.value = payload

        val notificationId = (payload.roomJid.hashCode() and 0x7FFFFFFF)
        val channelId = if (payload.isWhisper) CHANNEL_WHISPER else CHANNEL_MUC

        // Intent to open MainActivity on notification click
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ROOM_JID, payload.roomJid)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Mark as Read
        val markReadIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_MARK_AS_READ
            putExtra(EXTRA_ROOM_JID, payload.roomJid)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1000,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (payload.isWhisper) {
            "Bisikan dari ${payload.senderNickname}"
        } else {
            "${payload.senderNickname} (${payload.roomJid.substringBefore("@")})"
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(payload.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "Buka Room", contentPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Tandai Dibaca", markReadPendingIntent)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // Missing POST_NOTIFICATIONS permission
        } catch (e: Exception) {
            // Handled safely
        }
    }

    /**
     * Simulates receiving an FCM background push notification.
     * Persists message into Room database and posts Android notification.
     */
    fun simulateBackgroundPushNotification(
        context: Context,
        roomJid: String,
        senderNick: String,
        body: String,
        isWhisper: Boolean = false
    ) {
        val payload = PushMessagePayload(
            roomJid = roomJid,
            senderNickname = senderNick,
            body = body,
            isWhisper = isWhisper,
            timestamp = System.currentTimeMillis()
        )

        // 1. Persist directly to Room database so messages survive backgrounding
        scope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val msgEntity = MessageEntity(
                    id = payload.messageId,
                    roomJid = payload.roomJid,
                    senderJid = "${payload.roomJid}/${payload.senderNickname}",
                    senderNickname = payload.senderNickname,
                    body = payload.body,
                    timestamp = payload.timestamp,
                    isMine = false,
                    messageType = if (payload.isWhisper) MessageType.WHISPER else MessageType.GROUPCHAT,
                    status = DeliveryStatus.DELIVERED
                )
                db.messageDao().insertMessage(msgEntity)

                val room = db.roomDao().getRoomByJid(payload.roomJid)
                if (room != null) {
                    db.roomDao().insertOrUpdateRoom(
                        room.copy(
                            lastMessageText = if (payload.isWhisper) "[Bisikan] ${payload.body}" else payload.body,
                            lastMessageTime = payload.timestamp,
                            unreadCount = room.unreadCount + 1
                        )
                    )
                }
            } catch (e: Exception) {
                // Handled safely
            }
        }

        // 2. Dispatch system push notification
        showSystemNotification(context, payload)
    }
}
