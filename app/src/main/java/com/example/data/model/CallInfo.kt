package com.example.data.model

enum class CallState {
    IDLE,
    OUTGOING_RINGING,
    INCOMING_RINGING,
    CONNECTED,
    ENDED
}

data class CallInfo(
    val callId: String,
    val remoteJid: String,
    val remoteNickname: String,
    val callType: String, // "Audio" or "Video"
    val state: CallState,
    val startTime: Long = 0,
    val durationSeconds: Int = 0,
    val isVideoEnabled: Boolean = false,
    val isAudioMuted: Boolean = false
)
