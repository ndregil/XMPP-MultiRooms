package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.data.network.fcm.FcmPushManager
import com.example.ui.XmppViewModel
import com.example.ui.screens.MultiRoomMainScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: XmppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize FCM push channels and load preferences
        FcmPushManager.init(this)
        handleNotificationIntent(intent)

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    MultiRoomMainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val targetRoomJid = intent?.getStringExtra(FcmPushManager.EXTRA_ROOM_JID)
        if (!targetRoomJid.isNullOrBlank()) {
            viewModel.selectRoom(targetRoomJid)
        }
    }
}


