package com.example.data.network.fcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Notification action received: $action")

        if (action == FcmPushManager.ACTION_MARK_AS_READ) {
            val roomJid = intent.getStringExtra(FcmPushManager.EXTRA_ROOM_JID)
            val notificationId = intent.getIntExtra(FcmPushManager.EXTRA_NOTIFICATION_ID, -1)

            // Cancel notification
            if (notificationId != -1) {
                try {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error dismissing notification $notificationId", e)
                }
            }

            // Reset unread count in Room database
            if (!roomJid.isNullOrBlank()) {
                receiverScope.launch {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        db.roomDao().resetUnreadCount(roomJid)
                        Log.d(TAG, "Unread count reset for room: $roomJid")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to reset unread count in Room", e)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "NotificationAction"
    }
}
