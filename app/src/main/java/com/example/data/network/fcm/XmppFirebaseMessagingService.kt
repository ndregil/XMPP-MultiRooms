package com.example.data.network.fcm

import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.entity.MessageEntity
import com.example.data.model.DeliveryStatus
import com.example.data.model.MessageType
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

class XmppFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token received: $token")
        FcmPushManager.updateToken(applicationContext, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

        val data = remoteMessage.data

        // Parse payload from FCM data message or notification payload
        val roomJid = data["room_jid"]
            ?: data["roomJid"]
            ?: data["jid"]
            ?: "general@conference.xmpp.today"

        val senderNick = data["sender_nick"]
            ?: data["sender"]
            ?: data["nickname"]
            ?: remoteMessage.notification?.title
            ?: "Anonim"

        val body = data["body"]
            ?: data["message"]
            ?: remoteMessage.notification?.body
            ?: "Pesan baru di XMPP"

        val isWhisper = data["is_whisper"]?.toBooleanStrictOrNull()
            ?: data["whisper"]?.toBooleanStrictOrNull()
            ?: false

        val messageId = data["message_id"] ?: remoteMessage.messageId ?: UUID.randomUUID().toString()
        val timestamp = remoteMessage.sentTime.takeIf { it > 0 } ?: System.currentTimeMillis()

        val payload = PushMessagePayload(
            messageId = messageId,
            roomJid = roomJid,
            senderNickname = senderNick,
            body = body,
            isWhisper = isWhisper,
            timestamp = timestamp
        )

        // 1. Save background message into SQLite Room database
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val entity = MessageEntity(
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
                db.messageDao().insertMessage(entity)

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
                Log.e(TAG, "Error persisting FCM background message", e)
            }
        }

        // 2. Display Android system notification
        FcmPushManager.showSystemNotification(applicationContext, payload)
    }

    companion object {
        private const val TAG = "XmppFcmService"
    }
}
