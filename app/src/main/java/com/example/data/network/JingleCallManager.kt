package com.example.data.network

import android.util.Log
import com.example.data.model.CallInfo
import com.example.data.model.CallState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.Stanza
import org.jxmpp.jid.impl.JidCreate
import java.util.UUID

/**
 * Simplified Jingle Call Manager for signalling Voice and Video calls.
 * Note: Media streaming (WebRTC) is not included in this implementation
 * as it requires heavy native libraries. This handles the XMPP signalling layer.
 */
class JingleCallManager(
    private val connectionManager: XmppConnectionManager,
    private val scope: CoroutineScope
) {
    private val TAG = "JingleCallManager"

    private val _currentCall = MutableStateFlow<CallInfo?>(null)
    val currentCall: StateFlow<CallInfo?> = _currentCall.asStateFlow()

    private var durationJob: Job? = null

    init {
        scope.launch {
            connectionManager.connectionStatus.collect { status ->
                if (status == com.example.data.model.ConnectionStatus.CONNECTED) {
                    setupStanzaListener()
                }
            }
        }
    }

    private fun setupStanzaListener() {
        val connection = connectionManager.getRawConnection() ?: return
        
        connection.addAsyncStanzaListener({ stanza ->
            if (stanza is IQ && stanza.childElementNamespace == "urn:xmpp:jingle:1") {
                handleJingleStanza(stanza)
            }
        }, { stanza ->
            stanza is IQ && stanza.childElementNamespace == "urn:xmpp:jingle:1"
        })
    }

    private fun handleJingleStanza(iq: IQ) {
        val jingleElement = iq.childElementXML.toString()
        val action = Regex("action=['\"]([^'\"]+)['\"]").find(jingleElement)?.groupValues?.get(1)
        val sid = Regex("sid=['\"]([^'\"]+)['\"]").find(jingleElement)?.groupValues?.get(1) ?: return
        val initiator = iq.from?.toString() ?: return

        Log.d(TAG, "Received Jingle IQ: action=$action, sid=$sid, from=$initiator")

        when (action) {
            "session-initiate" -> {
                if (_currentCall.value == null) {
                    val callType = if (jingleElement.contains("video")) "Video" else "Voice"
                    _currentCall.value = CallInfo(
                        callId = sid,
                        remoteJid = initiator,
                        remoteNickname = initiator.substringBefore("@"),
                        callType = callType,
                        state = CallState.INCOMING_RINGING
                    )
                }
            }
            "session-accept" -> {
                val call = _currentCall.value
                if (call != null && call.callId == sid) {
                    _currentCall.value = call.copy(
                        state = CallState.CONNECTED,
                        startTime = System.currentTimeMillis()
                    )
                    startDurationCounter()
                }
            }
            "session-terminate" -> {
                val call = _currentCall.value
                if (call != null && call.callId == sid) {
                    _currentCall.value = call.copy(state = CallState.ENDED)
                    scope.launch {
                        delay(2000)
                        if (_currentCall.value?.callId == sid) {
                            _currentCall.value = null
                        }
                    }
                }
            }
        }
    }

    fun initiateCall(remoteJid: String, remoteNickname: String, callType: String) {
        if (_currentCall.value != null) return

        val connection = connectionManager.getRawConnection() ?: return
        val callId = UUID.randomUUID().toString()
        val newCall = CallInfo(
            callId = callId,
            remoteJid = remoteJid,
            remoteNickname = remoteNickname,
            callType = callType,
            state = CallState.OUTGOING_RINGING
        )
        _currentCall.value = newCall

        val jingleXml = """
            <jingle xmlns='urn:xmpp:jingle:1' action='session-initiate' initiator='${connection.user}' sid='$callId'>
                <content creator='initiator' name='${callType.lowercase()}'>
                    <description xmlns='urn:xmpp:jingle:apps:rtp:1' media='${callType.lowercase()}'>
                        <payload-type id='96' name='${if (callType == "Video") "VP8" else "OPUS"}' clockrate='${if (callType == "Video") "90000" else "48000"}'/>
                    </description>
                </content>
            </jingle>
        """.trimIndent()

        sendJingleIq(remoteJid, jingleXml)
    }

    fun acceptCall() {
        val call = _currentCall.value ?: return
        val connection = connectionManager.getRawConnection() ?: return
        if (call.state != CallState.INCOMING_RINGING) return

        val jingleXml = """
            <jingle xmlns='urn:xmpp:jingle:1' action='session-accept' responder='${connection.user}' sid='${call.callId}'>
                <content creator='initiator' name='${call.callType.lowercase()}'>
                    <description xmlns='urn:xmpp:jingle:apps:rtp:1' media='${call.callType.lowercase()}'/>
                </content>
            </jingle>
        """.trimIndent()

        sendJingleIq(call.remoteJid, jingleXml)

        _currentCall.value = call.copy(
            state = CallState.CONNECTED,
            startTime = System.currentTimeMillis()
        )
        startDurationCounter()
    }

    fun rejectCall() {
        val call = _currentCall.value ?: return
        sendTerminate(call, "decline")
        _currentCall.value = null
    }

    fun endCall() {
        val call = _currentCall.value ?: return
        sendTerminate(call, "success")
        _currentCall.value = call.copy(state = CallState.ENDED)
        durationJob?.cancel()
        scope.launch {
            delay(2000)
            if (_currentCall.value?.callId == call.callId) {
                _currentCall.value = null
            }
        }
    }

    private fun sendTerminate(call: CallInfo, reason: String) {
        val jingleXml = """
            <jingle xmlns='urn:xmpp:jingle:1' action='session-terminate' sid='${call.callId}'>
                <reason><$reason/></reason>
            </jingle>
        """.trimIndent()
        sendJingleIq(call.remoteJid, jingleXml)
    }

    private fun sendJingleIq(to: String, childXml: String) {
        val connection = connectionManager.getRawConnection() ?: return
        val iq = object : IQ("jingle", "urn:xmpp:jingle:1") {
            override fun getIQChildElementBuilder(xml: org.jivesoftware.smack.packet.IQ.IQChildElementXmlStringBuilder): org.jivesoftware.smack.packet.IQ.IQChildElementXmlStringBuilder {
                xml.rightAngleBracket()
                xml.append(childXml)
                return xml
            }
        }
        iq.to = JidCreate.from(to)
        iq.type = IQ.Type.set
        
        scope.launch {
            try {
                connection.sendStanza(iq)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending Jingle IQ", e)
            }
        }
    }

    private fun startDurationCounter() {
        durationJob?.cancel()
        durationJob = scope.launch(Dispatchers.Default) {
            while (true) {
                delay(1000)
                val call = _currentCall.value ?: break
                if (call.state == CallState.CONNECTED) {
                    val duration = ((System.currentTimeMillis() - call.startTime) / 1000).toInt()
                    _currentCall.value = call.copy(durationSeconds = duration)
                } else {
                    break
                }
            }
        }
    }

    fun toggleMute() {
        val call = _currentCall.value ?: return
        _currentCall.value = call.copy(isAudioMuted = !call.isAudioMuted)
    }

    fun toggleVideo() {
        val call = _currentCall.value ?: return
        if (call.callType == "Video") {
            _currentCall.value = call.copy(isVideoEnabled = !call.isVideoEnabled)
        }
    }
}

