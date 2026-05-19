package com.jsos.phone.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jsos.phone.R
import com.jsos.phone.audio.LiveTalkAudioRouteManager
import com.jsos.phone.codex.CodexCliBridgeClient
import com.jsos.phone.glasses.GlassesConnectionManager
import com.jsos.phone.glasses.RokidSdkManager
import com.jsos.phone.glasses.WakeSignalManager
import com.jsos.phone.openclaw.DeviceIdentity
import com.jsos.phone.openclaw.OpenClawClient
import com.jsos.phone.security.SecurePrefs
import com.jsos.phone.talk.LiveTalkState
import com.jsos.phone.talk.OpenClawTalkManager
import com.jsos.phone.ui.settings.SettingsScreen
import com.jsos.phone.ui.settings.SettingsTarget
import com.jsos.phone.ui.theme.JsosPalette
import com.jsos.phone.tts.ElevenLabsClient
import com.jsos.phone.tts.TtsPlaybackManager
import com.jsos.phone.tts.TtsSettingsManager
import com.jsos.phone.voice.VoiceCommandHandler
import com.jsos.phone.voice.VoiceLanguageManager
import com.jsos.phone.voice.AgentWakeRouter
import com.jsos.phone.voice.VoiceRecognitionManager
import com.jsos.shared.ChatMessage
import com.jsos.shared.ConnectionUpdate
import com.jsos.shared.SessionInfo
import com.jsos.shared.TtsState
import com.jsos.shared.stableSessionDisplayName
import java.util.UUID
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class CoreTab {
    Home,
    Chat,
    Connection,
    Voice,
    Hud,
    Diagnostics,
}

private enum class GlassesVoiceButtonMode(val prefValue: String, val label: String) {
    Command("command", "COMMAND"),
    LiveTalk("live_talk", "LIVE TALK");

    companion object {
        fun fromPref(value: String?): GlassesVoiceButtonMode =
            values().firstOrNull { it.prefValue == value } ?: Command
    }
}

private enum class DashboardIconStyle {
    Gateway,
    Glasses,
    Hud,
    Session,
    Diagnostics,
}

private enum class LiveTalkSource {
    Phone,
    Glasses,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Managers
    val glassesManager = remember { GlassesConnectionManager(context) }
    val deviceIdentity = remember { DeviceIdentity(context) }
    val openClawClient = remember { OpenClawClient(deviceIdentity) }
    val liveTalkManager = remember { OpenClawTalkManager(openClawClient) }
    val voiceHandler = remember { VoiceCommandHandler(context) }
    val voiceLanguageManager = remember { VoiceLanguageManager(context) }
    val voiceRecognitionManager = remember { VoiceRecognitionManager(context) }
    val ttsSettingsManager = remember { TtsSettingsManager(context) }
    val elevenLabsClient = remember { ElevenLabsClient() }
    val ttsPlaybackManager = remember { TtsPlaybackManager(context, elevenLabsClient, ttsSettingsManager) }
    val liveTalkAudioRouteManager = remember { LiveTalkAudioRouteManager(context) }
    val codexCliBridgeClient = remember { CodexCliBridgeClient() }

    // State
    val glassesState by glassesManager.connectionState.collectAsState()
    val openClawState by openClawClient.connectionState.collectAsState()
    val agentActivity by openClawClient.agentActivity.collectAsState()
    val gatewayProtocol by openClawClient.gatewayProtocol.collectAsState()
    val chatMessages by openClawClient.chatMessages.collectAsState()
    val isListening by voiceRecognitionManager.isListening.collectAsState()
    val voiceMode by voiceRecognitionManager.activeMode.collectAsState()
    val selectedVoiceLanguage by voiceLanguageManager.selectedLanguage.collectAsState()
    val sessionList by openClawClient.sessionList.collectAsState()
    val currentSessionKey by openClawClient.currentSessionKey.collectAsState()
    val unreadSessions by openClawClient.unreadSessions.collectAsState()
    val wakeOnStreamEnabled by glassesManager.wakeSignalManager.enabled.collectAsState()
    val ttsEnabled by ttsSettingsManager.isEnabled.collectAsState()
    val ttsVoiceName by ttsSettingsManager.selectedVoiceName.collectAsState()
    val liveTalkState by liveTalkManager.state.collectAsState()
    var gatewayConnectedSinceMs by remember { mutableStateOf<Long?>(null) }
    var gatewayLinkNowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(openClawState) {
        if (openClawState is OpenClawClient.ConnectionState.Connected) {
            val now = System.currentTimeMillis()
            gatewayConnectedSinceMs = gatewayConnectedSinceMs ?: now
            gatewayLinkNowMs = now
        } else {
            gatewayConnectedSinceMs = null
        }
    }

    LaunchedEffect(gatewayConnectedSinceMs) {
        while (gatewayConnectedSinceMs != null) {
            gatewayLinkNowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val gatewayLinkDuration = gatewayConnectedSinceMs?.let { connectedSince ->
        formatLinkDuration((gatewayLinkNowMs - connectedSince).coerceAtLeast(0L))
    } ?: "--"
    val gatewayProtocolLabel = gatewayProtocol?.toString() ?: "4-5"

    // Persist non-sensitive OpenClaw settings in SharedPreferences.
    val prefs = remember { context.getSharedPreferences("jsos", android.content.Context.MODE_PRIVATE) }
    val securePrefs = remember { SecurePrefs(context).also { it.migrateString(prefs, "openclaw_token") } }
    var openClawHost by remember {
        mutableStateOf(prefs.getString("openclaw_host", "10.0.2.2") ?: "10.0.2.2")
    }
    var openClawPort by remember {
        mutableStateOf(prefs.getString("openclaw_port", "18789") ?: "18789")
    }
    var openClawToken by remember {
        mutableStateOf(securePrefs.getString("openclaw_token", "") ?: "")
    }
    val phoneLoadingMore by openClawClient.isLoadingMoreHistory.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var settingsTarget by remember { mutableStateOf(SettingsTarget.SystemLink) }
    var selectedTab by remember { mutableStateOf(CoreTab.Home) }
    var showSessionPicker by remember { mutableStateOf(false) }
    var pendingPhotos by remember { mutableStateOf<List<String>>(emptyList()) }
    var ignoreAiExitUntilMs by remember { mutableLongStateOf(0L) }
    var liveTalkSource by remember { mutableStateOf<LiveTalkSource?>(null) }
    var agentWakeEnabled by remember { mutableStateOf(false) }
    var agentWakeSessionKey by remember { mutableStateOf<String?>(null) }
    var agentWakeStatus by remember { mutableStateOf("OFF") }
    var glassesVoiceButtonMode by remember {
        mutableStateOf(GlassesVoiceButtonMode.fromPref(prefs.getString("glasses_voice_button_mode", "command")))
    }
    val listState = rememberLazyListState()
    val openSettings: (SettingsTarget) -> Unit = { target ->
        settingsTarget = target
        showSettings = true
    }
    val toggleOpenClawGateway: () -> Unit = {
        when (openClawState) {
            is OpenClawClient.ConnectionState.Connected,
            is OpenClawClient.ConnectionState.Connecting,
            is OpenClawClient.ConnectionState.Authenticating,
            is OpenClawClient.ConnectionState.PairingRequired -> {
                openClawClient.disconnect()
            }
            is OpenClawClient.ConnectionState.Disconnected,
            is OpenClawClient.ConnectionState.Error -> {
                val portNum = openClawPort.toIntOrNull() ?: 18789
                openClawClient.connect(openClawHost, portNum, openClawToken)
            }
        }
    }
    val setGlassesVoiceButtonMode: (GlassesVoiceButtonMode) -> Unit = { mode ->
        if (mode == GlassesVoiceButtonMode.Command && liveTalkManager.isActive) {
            liveTalkManager.stop()
            liveTalkAudioRouteManager.clear()
            codexCliBridgeClient.disconnect()
            liveTalkSource = null
        }
        glassesVoiceButtonMode = mode
        prefs.edit().putString("glasses_voice_button_mode", mode.prefValue).apply()
    }

    // How many messages we send to glasses (starts at 20, grows on demand)
    var glassesMessageLimit by remember { mutableIntStateOf(20) }

    // Initialize voice handler and query available languages
    LaunchedEffect(Unit) {
        voiceHandler.initialize()
        voiceLanguageManager.queryAvailableLanguages()
        // Set up partial result forwarding for both voice recognition managers
        voiceHandler.onPartialResult = { partialText ->
            RokidSdkManager.sendAsrContent(partialText)
            val stateMsg = org.json.JSONObject().apply {
                put("type", "voice_state")
                put("state", "recognizing")
                put("text", partialText)
            }
            glassesManager.sendRawMessage(stateMsg.toString())
        }
        voiceRecognitionManager.onPartialResult = { partialText ->
            RokidSdkManager.sendAsrContent(partialText)
            val stateMsg = org.json.JSONObject().apply {
                put("type", "voice_state")
                put("state", "recognizing")
                put("text", partialText)
            }
            glassesManager.sendRawMessage(stateMsg.toString())
        }

        // Try to auto-reconnect to previously paired glasses on startup
        glassesManager.tryAutoReconnectOnStartup()
    }

    // Fetch session list when OpenClaw connects
    LaunchedEffect(openClawState) {
        if (openClawState is OpenClawClient.ConnectionState.Connected) {
            openClawClient.requestSessions()
        }
    }

    // Codex CLI bridge callbacks for the experimental HUD terminal mode.
    LaunchedEffect(openClawHost) {
        codexCliBridgeClient.onStatus = { state, detail ->
            val statusMsg = org.json.JSONObject().apply {
                put("type", "cli_status")
                put("state", state.name.lowercase())
                put("detail", detail ?: "")
                put("url", codexCliBridgeUrl(openClawHost))
            }
            glassesManager.sendRawMessage(statusMsg.toString())
        }
        codexCliBridgeClient.onOutput = { output, append ->
            val outputMsg = org.json.JSONObject().apply {
                put("type", "cli_output")
                put("text", output)
                put("append", append)
            }
            glassesManager.sendRawMessage(outputMsg.toString())
        }
    }
    // Sync TTS state to glasses when settings change
    LaunchedEffect(ttsEnabled, ttsVoiceName) {
        if (glassesState is GlassesConnectionManager.ConnectionState.Connected) {
            val ttsStateMsg = TtsState(
                enabled = ttsEnabled,
                voiceName = ttsVoiceName
            )
            glassesManager.sendRawMessage(ttsStateMsg.toJson())
        }
    }

    // Start/stop foreground service based on glasses connection state,
    // and send current chat history when glasses connect.
    // IMPORTANT: Don't stop the service during Reconnecting - killing the foreground
    // service drops the wake lock and lets Android kill the Bluetooth connection,
    // making reconnection impossible. Only stop on true Disconnected (not reconnecting).
    LaunchedEffect(glassesState) {
        when (glassesState) {
            is GlassesConnectionManager.ConnectionState.Connected -> {
                android.util.Log.i("MainScreen", "Glasses connected - starting foreground service")
                com.jsos.phone.service.GlassesConnectionService.start(context)
                // Send current chat history to glasses if we have any
                val currentMessages = openClawClient.chatMessages.value
                if (currentMessages.isNotEmpty()) {
                    android.util.Log.i("MainScreen", "Sending ${currentMessages.size} history messages to newly connected glasses")
                    glassesManager.sendRawMessage(buildChatHistoryJson(currentMessages))
                }
                // Send TTS state to glasses
                val ttsStateMsg = TtsState(
                    enabled = ttsSettingsManager.isEnabled.value,
                    voiceName = ttsSettingsManager.selectedVoiceName.value
                )
                glassesManager.sendRawMessage(ttsStateMsg.toJson())
            }
            is GlassesConnectionManager.ConnectionState.Disconnected -> {
                // Only stop the service if we're truly disconnected (no saved pairing to reconnect to).
                // If we have a pairing, the service keeps BT alive for auto-reconnect.
                if (!RokidSdkManager.hasSavedConnectionInfo()) {
                    android.util.Log.i("MainScreen", "Glasses disconnected (no pairing) - stopping foreground service")
                    com.jsos.phone.service.GlassesConnectionService.stop(context)
                } else {
                    android.util.Log.i("MainScreen", "Glasses disconnected but paired - keeping foreground service for reconnect")
                }
            }
            is GlassesConnectionManager.ConnectionState.Reconnecting -> {
                // Keep foreground service alive during reconnection attempts
                android.util.Log.i("MainScreen", "Glasses reconnecting - keeping foreground service")
                com.jsos.phone.service.GlassesConnectionService.start(context)
            }
            else -> {}
        }
    }

    // Auto-scroll to bottom when new messages arrive (but not during load-more).
    // Detect prepend by checking if the first message ID changed - this is more
    // reliable than checking phoneLoadingMore which may already be false by the
    // time this effect runs (both StateFlows update in the same coroutine frame).
    var previousFirstMsgId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            val currentFirstId = chatMessages.first().id
            val wasPrepend = previousFirstMsgId != null && currentFirstId != previousFirstMsgId
            if (!wasPrepend) {
                listState.animateScrollToItem(chatMessages.size)
            }
            previousFirstMsgId = currentFirstId
        }
    }

    // Phone UI: detect scroll-to-top and load more history
    val phoneCanScrollBackward by remember { derivedStateOf { listState.canScrollBackward } }
    LaunchedEffect(phoneCanScrollBackward) {
        if (!phoneCanScrollBackward && chatMessages.isNotEmpty() && !phoneLoadingMore) {
            openClawClient.loadMoreHistory()
        }
    }

    // Wire OpenClaw client callbacks to forward to glasses
    LaunchedEffect(Unit) {
        openClawClient.onChatMessage = { msg ->
            // Check if this is a spontaneous message (not preceded by our stream start)
            // This could be a cron job message or a message from another session
            val isNewMessage = msg.role == "assistant" && !glassesManager.wakeSignalManager.wakeState.value.let {
                it is WakeSignalManager.WakeState.Awake || it is WakeSignalManager.WakeState.WakingUp
            }
            glassesManager.sendRawMessage(msg.toJson(), isNewMessage = isNewMessage)
        }
        openClawClient.onChatHistory = { messages ->
            // Full history reload (initial load or session switch) - reset glasses limit
            glassesMessageLimit = 20
            val json = buildChatHistoryJson(messages)
            android.util.Log.i("MainScreen", "Forwarding chat_history to glasses: ${messages.size} messages, ${json.length} chars")
            glassesManager.sendRawMessage(json)
        }
        openClawClient.onAgentThinking = { msg ->
            // Agent is about to start streaming - notify wake manager
            glassesManager.notifyStreamStart(msg.id)
            glassesManager.sendRawMessage(msg.toJson(), isStreamContent = true)
        }
        openClawClient.onChatStream = { msg ->
            // Streaming content - mark as such for wake signal handling
            glassesManager.sendRawMessage(msg.toJson(), isStreamContent = true)
        }
        openClawClient.onChatStreamEnd = { msg ->
            // Streaming complete - notify wake manager
            glassesManager.notifyStreamEnd(msg.id)
            glassesManager.sendRawMessage(msg.toJson())
            // Trigger TTS if enabled
            val fullText = openClawClient.chatMessages.value.lastOrNull { it.id == msg.id }?.content
            if (fullText != null) {
                ttsPlaybackManager.onMessageComplete(fullText)
            }
        }
        openClawClient.onSessionList = { msg ->
            glassesManager.sendRawMessage(msg.toJson())
        }
        openClawClient.onConnectionUpdate = { msg ->
            glassesManager.sendRawMessage(msg.toJson())
        }
        openClawClient.onMoreHistoryLoaded = { prependedCount, hasMore ->
            if (prependedCount > 0) {
                // Increase glasses limit to include the new older messages
                glassesMessageLimit += prependedCount
            }
            // Send the updated full list to glasses with the load-more flag
            val allMessages = openClawClient.chatMessages.value
            val json = buildChatHistoryJson(allMessages, glassesMessageLimit, isLoadMore = true, hasMore = hasMore)
            android.util.Log.i("MainScreen", "Forwarding expanded chat_history to glasses: limit=$glassesMessageLimit of ${allMessages.size}, prepended=$prependedCount, hasMore=$hasMore")
            glassesManager.sendRawMessage(json)
        }
    }

    // Handle AI scene events (glasses long-press triggers voice input)
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    fun dismissRokidAiSceneBurst() {
        RokidSdkManager.sendExitEvent()
        mainHandler.postDelayed({ RokidSdkManager.sendExitEvent() }, 50)
        mainHandler.postDelayed({ RokidSdkManager.sendExitEvent() }, 150)
        mainHandler.postDelayed({ RokidSdkManager.sendExitEvent() }, 300)
    }
    fun sendVoiceState(state: String, mode: String = "live", text: String? = null) {
        val stateMsg = org.json.JSONObject().apply {
            put("type", "voice_state")
            put("state", state)
            put("mode", mode)
            if (!text.isNullOrBlank()) put("text", text)
        }
        glassesManager.sendRawMessage(stateMsg.toString())
    }
    fun sendVoiceError(message: String) {
        val resultMsg = org.json.JSONObject().apply {
            put("type", "voice_result")
            put("result_type", "error")
            put("text", message)
        }
        glassesManager.sendRawMessage(resultMsg.toString())
    }
    fun stopGlassesVoiceRecognition() {
        android.util.Log.d("MainScreen", "Stopping glasses voice recognition")
        voiceRecognitionManager.stopListening()
        RokidSdkManager.clearCommunicationDevice()
        sendVoiceState("idle")
        mainHandler.postDelayed({ RokidSdkManager.sendExitEvent() }, 100)
    }
    fun submitChatMessage(text: String, photosToSend: List<String>, clearInputOnSuccess: Boolean = false) {
        if (text.isBlank()) return
        openClawClient.sendMessage(text, photosToSend.ifEmpty { null }) { success ->
            mainHandler.post {
                if (success) {
                    if (clearInputOnSuccess && inputText == text) {
                        inputText = ""
                    }
                    if (photosToSend.isNotEmpty() && pendingPhotos == photosToSend) {
                        pendingPhotos = emptyList()
                        glassesManager.sendRawMessage("""{"type":"remove_photo","all":true}""")
                    }
                } else {
                    android.widget.Toast.makeText(context, "Send failed", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    fun submitAgentWakeMessage(session: SessionInfo, message: String) {
        if (message.isBlank()) return
        if (openClawClient.connectionState.value !is OpenClawClient.ConnectionState.Connected) {
            agentWakeStatus = "OFFLINE"
            android.widget.Toast.makeText(context, "OpenClaw gateway offline", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (openClawClient.agentActivity.value != OpenClawClient.AgentActivityState.Ready) {
            agentWakeStatus = "BUSY"
            return
        }

        agentWakeSessionKey = session.key
        agentWakeStatus = session.name.uppercase()
        if (openClawClient.currentSessionKey.value != session.key) {
            openClawClient.switchSession(session.key, loadHistory = false)
        }
        submitChatMessage(message, emptyList())
    }

    fun handleAgentWakeTranscript(transcript: String) {
        val sessions = openClawClient.sessionList.value
        if (sessions.isEmpty()) {
            openClawClient.requestSessions()
            agentWakeStatus = "SESSIONS"
            return
        }

        val decision = AgentWakeRouter.route(transcript, sessions, agentWakeSessionKey)
        when (decision.action) {
            AgentWakeRouter.Action.SendToAgent,
            AgentWakeRouter.Action.ContinueActive -> {
                val session = decision.session ?: return
                submitAgentWakeMessage(session, decision.message)
            }
            AgentWakeRouter.Action.SwitchOnly -> {
                val session = decision.session ?: return
                agentWakeSessionKey = session.key
                agentWakeStatus = session.name.uppercase()
                if (openClawClient.currentSessionKey.value != session.key) {
                    openClawClient.switchSession(session.key)
                }
            }
            AgentWakeRouter.Action.ClearActive -> {
                agentWakeSessionKey = null
                agentWakeStatus = "ARMED"
            }
            AgentWakeRouter.Action.Sleep -> {
                agentWakeEnabled = false
                agentWakeSessionKey = null
                agentWakeStatus = "OFF"
                voiceRecognitionManager.stopListening()
            }
            AgentWakeRouter.Action.NoMatch -> {
                agentWakeStatus = "NAME?"
            }
            AgentWakeRouter.Action.Empty -> Unit
        }
    }

    fun startAgentWakeMode() {
        if (openClawClient.connectionState.value !is OpenClawClient.ConnectionState.Connected) {
            android.widget.Toast.makeText(context, "Connect OpenClaw first", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (liveTalkManager.isActive) {
            liveTalkManager.stop()
            liveTalkAudioRouteManager.clear()
            codexCliBridgeClient.disconnect()
            liveTalkSource = null
            sendVoiceState("idle")
        }
        openClawClient.requestSessions()
        agentWakeEnabled = true
        agentWakeSessionKey = null
        agentWakeStatus = "ARMED"
        val started = voiceRecognitionManager.startContinuousListening(
            languageTag = voiceLanguageManager.getActiveLanguageTag(),
            onResult = { transcript -> handleAgentWakeTranscript(transcript) },
            onError = {
                agentWakeEnabled = false
                agentWakeSessionKey = null
                agentWakeStatus = "ERROR"
                android.widget.Toast.makeText(context, "Agent Wake error", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
        if (!started) {
            agentWakeEnabled = false
            agentWakeStatus = "NO KEY"
            android.widget.Toast.makeText(context, "Enable OpenAI Realtime voice first", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun stopAgentWakeMode() {
        agentWakeEnabled = false
        agentWakeSessionKey = null
        agentWakeStatus = "OFF"
        voiceRecognitionManager.stopListening()
    }
    fun stopLiveTalk() {
        liveTalkManager.stop()
        liveTalkAudioRouteManager.clear()
        liveTalkSource = null
        sendVoiceState("idle")
    }

    fun startLiveTalk(source: LiveTalkSource) {
        if (liveTalkManager.isActive) {
            if (liveTalkSource == source) {
                stopLiveTalk()
                return
            }
            stopLiveTalk()
        }
        if (openClawState !is OpenClawClient.ConnectionState.Connected) {
            sendVoiceError("OpenClaw gateway offline")
            sendVoiceState("idle")
            return
        }

        if (agentWakeEnabled) {
            stopAgentWakeMode()
        }

        liveTalkSource = source
        when (source) {
            LiveTalkSource.Glasses -> {
                android.util.Log.d("MainScreen", "Glasses requested OpenClaw Live Talk")
                ignoreAiExitUntilMs = System.currentTimeMillis() + 1200L
                dismissRokidAiSceneBurst()
                liveTalkAudioRouteManager.routeToGlasses()
                RokidSdkManager.sendAsrContent("LIVE TALK")
            }
            LiveTalkSource.Phone -> {
                android.util.Log.d("MainScreen", "Phone requested OpenClaw Live Talk")
                liveTalkAudioRouteManager.routeToPhoneSpeaker()
            }
        }
        sendVoiceState("listening", text = "Live Talk")

        liveTalkManager.start(sessionKey = currentSessionKey ?: "main") { transcript ->
            mainHandler.post {
                if (transcript.isFinal) {
                    val role = if (transcript.role.equals("user", ignoreCase = true)) "user" else "assistant"
                    val msg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = role,
                        content = transcript.text
                    )
                    glassesManager.sendRawMessage(msg.toJson(), isStreamContent = role == "assistant")
                } else {
                    val state = if (transcript.role.equals("user", ignoreCase = true)) "recognizing" else "processing"
                    sendVoiceState(state, text = transcript.text.take(160))
                }
            }
        }
    }

    fun startLiveTalkFromGlasses() = startLiveTalk(LiveTalkSource.Glasses)
    fun toggleLiveTalkFromPhone() = startLiveTalk(LiveTalkSource.Phone)

    LaunchedEffect(liveTalkState) {
        when (val state = liveTalkState) {
            is LiveTalkState.Connecting -> sendVoiceState("processing", text = "Connecting Live Talk")
            is LiveTalkState.Listening -> sendVoiceState("listening", text = "Live Talk")
            is LiveTalkState.Speaking -> sendVoiceState("processing", text = "JSOS")
            is LiveTalkState.Error -> {
                liveTalkAudioRouteManager.clear()
                codexCliBridgeClient.disconnect()
                liveTalkSource = null
                sendVoiceError(state.message)
                sendVoiceState("idle")
            }
            is LiveTalkState.Idle -> Unit
        }
    }
    LaunchedEffect(glassesVoiceButtonMode, openClawState, currentSessionKey) {
        glassesManager.onAiKeyDown = {
            android.util.Log.i("MainScreen", ">>> AI key down from glasses - mode=${glassesVoiceButtonMode.label}")
            mainHandler.post {
                if (glassesVoiceButtonMode == GlassesVoiceButtonMode.LiveTalk) {
                    startLiveTalkFromGlasses()
                } else {
                    if (voiceRecognitionManager.isListening.value) {
                        stopGlassesVoiceRecognition()
                        return@post
                    }
                    dismissRokidAiSceneBurst()
                    RokidSdkManager.setCommunicationDevice()
                    startVoiceRecognitionWithManager(
                        voiceRecognitionManager = voiceRecognitionManager,
                        voiceHandler = voiceHandler,
                        openClawClient = openClawClient,
                        glassesManager = glassesManager,
                        mainHandler = mainHandler,
                        isRetry = false,
                        languageTag = voiceLanguageManager.getActiveLanguageTag(),
                        pendingPhotos = { pendingPhotos },
                        onPhotosConsumed = { pendingPhotos = emptyList() }
                    )
                }
            }
        }
        glassesManager.onAiExit = {
            val now = System.currentTimeMillis()
            if (liveTalkManager.isActive && liveTalkSource == LiveTalkSource.Glasses && now >= ignoreAiExitUntilMs) {
                android.util.Log.d("MainScreen", "AI scene exited on glasses - stopping Live Talk")
                mainHandler.post { stopLiveTalk() }
            } else {
                android.util.Log.d("MainScreen", "AI scene exited on glasses (ignored; guard active or no Live Talk)")
            }
        }
    }

    // Handle messages from glasses and forward to OpenClaw
    LaunchedEffect(glassesVoiceButtonMode, openClawState, currentSessionKey) {
        glassesManager.onMessageFromGlasses = handler@ { message ->
            try {
                val json = org.json.JSONObject(message)
                val type = json.optString("type", "")
                when (type) {
                    "user_input" -> {
                        val text = json.optString("text", "")
                        val photosToSend = pendingPhotos
                        android.util.Log.d("MainScreen", "Received user input from glasses (${text.length} chars, photos=${photosToSend.size})")
                        submitChatMessage(text, photosToSend)
                    }
                    "start_voice" -> {
                        if (glassesVoiceButtonMode == GlassesVoiceButtonMode.LiveTalk) {
                            startLiveTalkFromGlasses()
                            return@handler
                        }
                        android.util.Log.d("MainScreen", "Glasses requested voice recognition start")
                        dismissRokidAiSceneBurst()
                        com.jsos.phone.glasses.RokidSdkManager.setCommunicationDevice()
                        // Keep SDK AI scene alive (it times out without ASR content)
                        com.jsos.phone.glasses.RokidSdkManager.sendAsrContent("...")
                        // Send voice state with mode info
                        val modeIndicator = if (voiceRecognitionManager.isOpenAIAvailable()) "openai" else "device"
                        // Send "processing" state when VAD detects speech end
                        voiceRecognitionManager.onSpeechStopped = {
                            val processingMsg = org.json.JSONObject().apply {
                                put("type", "voice_state")
                                put("state", "processing")
                                put("mode", modeIndicator)
                            }
                            glassesManager.sendRawMessage(processingMsg.toString())
                        }
                        voiceRecognitionManager.startListening(languageTag = voiceLanguageManager.getActiveLanguageTag()) { result ->
                            com.jsos.phone.glasses.RokidSdkManager.clearCommunicationDevice()
                            when (result) {
                                is VoiceCommandHandler.VoiceResult.Text -> {
                                    android.util.Log.d("MainScreen", "Voice result text received (${result.text.length} chars)")
                                    val resultMsg = org.json.JSONObject().apply {
                                        put("type", "voice_result")
                                        put("result_type", "text")
                                        put("text", result.text)
                                    }
                                    glassesManager.sendRawMessage(resultMsg.toString())
                                    // Don't send to OpenClaw here - glasses stages the text
                                    // and sends user_input when user confirms via Send button
                                }
                                is VoiceCommandHandler.VoiceResult.Command -> {
                                    android.util.Log.d("MainScreen", "Voice result command received (commandLength=${result.command.length})")
                                    val resultMsg = org.json.JSONObject().apply {
                                        put("type", "voice_result")
                                        put("result_type", "command")
                                        put("text", result.command)
                                    }
                                    glassesManager.sendRawMessage(resultMsg.toString())
                                }
                                is VoiceCommandHandler.VoiceResult.Error -> {
                                    android.util.Log.e("MainScreen", "Voice result error (redacted)")
                                    val resultMsg = org.json.JSONObject().apply {
                                        put("type", "voice_result")
                                        put("result_type", "error")
                                        put("text", result.message)
                                    }
                                    glassesManager.sendRawMessage(resultMsg.toString())
                                }
                            }
                        }
                        val stateMsg = org.json.JSONObject().apply {
                            put("type", "voice_state")
                            put("state", "listening")
                            put("mode", modeIndicator)
                        }
                        glassesManager.sendRawMessage(stateMsg.toString())
                    }
                    "cancel_voice", "stop_voice", "voice_stop", "end_voice" -> {
                        android.util.Log.d("MainScreen", "Glasses requested voice stop: $type")
                        if (liveTalkManager.isActive) {
                            stopLiveTalk()
                            return@handler
                        }
                        stopGlassesVoiceRecognition()
                    }
                    "list_sessions" -> {
                        android.util.Log.d("MainScreen", "Requesting session list for glasses")
                        openClawClient.requestSessions()
                    }
                    "switch_session" -> {
                        val sessionKey = json.optString("sessionKey", "")
                        android.util.Log.d("MainScreen", "Switching session (keyLength=${sessionKey.length})")
                        if (sessionKey.isNotEmpty()) {
                            openClawClient.switchSession(sessionKey)
                        }
                    }
                    "create_session" -> {
                        android.util.Log.d("MainScreen", "Creating new session from glasses")
                        openClawClient.createSession()
                    }
                    "slash_command" -> {
                        val command = json.optString("command", "")
                        android.util.Log.d("MainScreen", "Slash command from glasses: $command")
                        if (command.isNotEmpty()) {
                            openClawClient.sendSlashCommand(command)
                        }
                    }
                    "cli_connect" -> {
                        val bridgeUrl = codexCliBridgeUrl(openClawHost)
                        android.util.Log.d("MainScreen", "Glasses requested Codex CLI bridge connect")
                        codexCliBridgeClient.connect(bridgeUrl)
                    }
                    "cli_disconnect" -> {
                        android.util.Log.d("MainScreen", "Glasses requested Codex CLI bridge disconnect")
                        codexCliBridgeClient.disconnect()
                    }
                    "cli_input" -> {
                        val text = json.optString("text", "")
                        android.util.Log.d("MainScreen", "Codex CLI input from glasses (${text.length} chars)")
                        codexCliBridgeClient.sendInput(text)
                    }
                    "cli_stop" -> {
                        android.util.Log.d("MainScreen", "Glasses requested Codex CLI stop")
                        codexCliBridgeClient.stop()
                    }
                    "request_state" -> {
                        android.util.Log.d("MainScreen", "Glasses requested current state")
                        // Send OpenClaw connection status
                        val isConnected = openClawState is OpenClawClient.ConnectionState.Connected
                        val currentKey = openClawClient.currentSessionKey.value
                        val currentName = currentKey?.let { key ->
                            openClawClient.sessionList.value.firstOrNull { it.key == key }?.name
                                ?: stableSessionDisplayName(key)
                        }
                        val connUpdate = ConnectionUpdate(
                            connected = isConnected,
                            sessionId = currentKey,
                            sessionName = currentName
                        )
                        glassesManager.sendRawMessage(connUpdate.toJson())
                        // Send current chat history
                        val currentMessages = openClawClient.chatMessages.value
                        glassesManager.sendRawMessage(buildChatHistoryJson(currentMessages))
                        // Send TTS state
                        val ttsStateMsg = TtsState(
                            enabled = ttsSettingsManager.isEnabled.value,
                            voiceName = ttsSettingsManager.selectedVoiceName.value
                        )
                        glassesManager.sendRawMessage(ttsStateMsg.toJson())
                    }
                    "tts_toggle" -> {
                        val enabled = json.optBoolean("enabled", false)
                        android.util.Log.d("MainScreen", "TTS toggle from glasses: $enabled")
                        ttsSettingsManager.setEnabled(enabled)
                        // Send updated state back to glasses
                        val ttsStateMsg = TtsState(
                            enabled = enabled,
                            voiceName = ttsSettingsManager.selectedVoiceName.value
                        )
                        glassesManager.sendRawMessage(ttsStateMsg.toJson())
                    }
                    "take_photo" -> {
                        android.util.Log.d("MainScreen", "Glasses requested photo capture")
                        RokidSdkManager.onPhotoResult = { status, photoBytes ->
                            mainHandler.post {
                                android.util.Log.d("MainScreen", "Photo callback: status=$status, bytes=${photoBytes?.size}")
                                if (photoBytes != null && photoBytes.isNotEmpty()) {
                                    val base64 = android.util.Base64.encodeToString(photoBytes, android.util.Base64.NO_WRAP)
                                    pendingPhotos = pendingPhotos + base64
                                    val thumbnail = createThumbnailBase64(photoBytes, 80, 60)
                                    val resultMsg = org.json.JSONObject().apply {
                                        put("type", "photo_result")
                                        put("status", "captured")
                                        put("thumbnail", thumbnail)
                                    }
                                    glassesManager.sendRawMessage(resultMsg.toString())
                                } else {
                                    android.util.Log.e("MainScreen", "Photo capture failed: status=$status")
                                    val resultMsg = org.json.JSONObject().apply {
                                        put("type", "photo_result")
                                        put("status", "error")
                                        put("message", "Capture failed: $status")
                                    }
                                    glassesManager.sendRawMessage(resultMsg.toString())
                                }
                                RokidSdkManager.onPhotoResult = null
                            }
                        }
                        RokidSdkManager.takeGlassPhotoGlobal(640, 480, 75)
                    }
                    "remove_photo" -> {
                        val all = json.optBoolean("all", false)
                        val index = json.optInt("index", -1)
                        if (all) {
                            pendingPhotos = emptyList()
                        } else if (index in pendingPhotos.indices) {
                            pendingPhotos = pendingPhotos.toMutableList().apply { removeAt(index) }
                        }
                    }
                    "request_more_history" -> {
                        // Glasses scrolled to top and wants older messages
                        val allMessages = openClawClient.chatMessages.value
                        android.util.Log.d("MainScreen", "Glasses requesting more history (glassesLimit=$glassesMessageLimit, phoneCache=${allMessages.size})")

                        if (glassesMessageLimit < allMessages.size) {
                            // Phone has more cached messages - serve from cache
                            glassesMessageLimit = (glassesMessageLimit + 15).coerceAtMost(allMessages.size)
                            val chatJson = buildChatHistoryJson(allMessages, glassesMessageLimit, isLoadMore = true, hasMore = true)
                            glassesManager.sendRawMessage(chatJson)
                        } else {
                            // Phone cache exhausted - fetch more from OpenClaw
                            openClawClient.loadMoreHistory()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainScreen", "Error parsing glasses message", e)
            }
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            liveTalkManager.stop()
            liveTalkAudioRouteManager.clear()
            codexCliBridgeClient.cleanup()
            glassesManager.disconnect()
            openClawClient.cleanup()
            voiceHandler.cleanup()
            voiceRecognitionManager.cleanup()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        MatrixRainBackground(modifier = Modifier.fillMaxSize())
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            MatrixHeroHeader()
        },
        bottomBar = {
            Column {
                // Thumbnail strip for queued photos
                if (selectedTab == CoreTab.Chat && pendingPhotos.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        pendingPhotos.forEachIndexed { index, base64 ->
                            val thumbnail = remember(base64) {
                                try {
                                    val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
                                    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                                        ?.asImageBitmap()
                                } catch (_: Exception) { null }
                            }
                            if (thumbnail != null) {
                                Box {
                                    Image(
                                        bitmap = thumbnail,
                                        contentDescription = "Queued photo ${index + 1}",
                                        modifier = Modifier
                                            .height(56.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                    // Remove button
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove photo",
                                        modifier = Modifier
                                            .size(18.dp)
                                            .align(Alignment.TopEnd)
                                            .background(
                                                Color.Black.copy(alpha = 0.6f),
                                                RoundedCornerShape(9.dp)
                                            )
                                            .clickable {
                                                pendingPhotos = pendingPhotos
                                                    .toMutableList()
                                                    .apply { removeAt(index) }
                                                glassesManager.sendRawMessage(
                                                    """{"type":"remove_photo","index":$index}"""
                                                )
                                            }
                                            .padding(2.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            if (selectedTab == CoreTab.Chat) {
            BottomAppBar(
                containerColor = Color(0xFF12191C).copy(alpha = 0.58f),
                contentColor = Color(0xFFE6F6EA),
                tonalElevation = 0.dp,
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp),
                    placeholder = { Text("Type message...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JsosPalette.Cyan,
                        unfocusedBorderColor = JsosPalette.Cyan.copy(alpha = 0.48f),
                        focusedTextColor = Color(0xFFE6F6EA),
                        unfocusedTextColor = Color(0xFFE6F6EA),
                        focusedContainerColor = JsosPalette.Card,
                        unfocusedContainerColor = JsosPalette.Card,
                        cursorColor = JsosPalette.Cyan,
                        focusedPlaceholderColor = JsosPalette.Muted,
                        unfocusedPlaceholderColor = JsosPalette.Muted,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank()) {
                                submitChatMessage(
                                    text = inputText,
                                    photosToSend = pendingPhotos,
                                    clearInputOnSuccess = true
                                )
                            }
                        }
                    ),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                )

                // Camera button - always takes a new photo, adds to pending list
                IconButton(
                    onClick = {
                        android.util.Log.d("MainScreen", "Taking photo from glasses camera")
                        android.widget.Toast.makeText(context, "Capturing photo...", android.widget.Toast.LENGTH_SHORT).show()
                        RokidSdkManager.onPhotoResult = { status, photoBytes ->
                            mainHandler.post {
                                android.util.Log.d("MainScreen", "Photo callback: status=$status, bytes=${photoBytes?.size}")
                                if (photoBytes != null && photoBytes.isNotEmpty()) {
                                    val base64 = android.util.Base64.encodeToString(photoBytes, android.util.Base64.NO_WRAP)
                                    pendingPhotos = pendingPhotos + base64
                                    android.util.Log.d("MainScreen", "Photo added (total: ${pendingPhotos.size})")
                                    android.widget.Toast.makeText(context, "Photo ${pendingPhotos.size} captured!", android.widget.Toast.LENGTH_SHORT).show()
                                    val thumbnail = createThumbnailBase64(photoBytes, 80, 60)
                                    val resultMsg = org.json.JSONObject().apply {
                                        put("type", "photo_result")
                                        put("status", "captured")
                                        put("thumbnail", thumbnail)
                                    }
                                    glassesManager.sendRawMessage(resultMsg.toString())
                                } else {
                                    android.util.Log.e("MainScreen", "Photo capture failed: status=$status")
                                    android.widget.Toast.makeText(context, "Photo failed: $status", android.widget.Toast.LENGTH_LONG).show()
                                }
                                RokidSdkManager.onPhotoResult = null
                            }
                        }
                        RokidSdkManager.takeGlassPhotoGlobal(640, 480, 75)
                    },
                    enabled = glassesState is GlassesConnectionManager.ConnectionState.Connected
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Take photo",
                        tint = if (pendingPhotos.isNotEmpty()) JsosPalette.Green else JsosPalette.Cyan.copy(alpha = 0.80f)
                    )
                }

                // Voice button with mode indicator
                IconButton(
                    onClick = {
                        if (isListening) {
                            voiceRecognitionManager.stopListening()
                        } else {
                            voiceRecognitionManager.startListening(languageTag = voiceLanguageManager.getActiveLanguageTag()) { result ->
                                when (result) {
                                    is VoiceCommandHandler.VoiceResult.Text -> {
                                        if (result.text.isNotEmpty()) {
                                            submitChatMessage(result.text, emptyList())
                                        }
                                    }
                                    is VoiceCommandHandler.VoiceResult.Command -> {
                                        // Voice commands handled by glasses
                                    }
                                    is VoiceCommandHandler.VoiceResult.Error -> {
                                        // Handle error - could show toast
                                    }
                                }
                            }
                        }
                    }
                ) {
                    // Icon color indicates mode when listening:
                    // Red = listening, with tint for OpenAI (blue) vs device (red)
                    val iconTint = when {
                        !isListening -> JsosPalette.Cyan.copy(alpha = 0.88f)
                        voiceMode == VoiceRecognitionManager.RecognitionMode.OPENAI -> JsosPalette.Cyan
                        else -> JsosPalette.Red
                    }
                    Icon(
                        if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = when {
                            !isListening -> "Voice input"
                            voiceMode == VoiceRecognitionManager.RecognitionMode.OPENAI -> "Listening (OpenAI)"
                            else -> "Listening (Device)"
                        },
                        tint = iconTint
                    )
                }

                // Send button
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            submitChatMessage(
                                text = inputText,
                                photosToSend = pendingPhotos,
                                clearInputOnSuccess = true
                            )
                        }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = JsosPalette.Cyan)
                }
            }
            }

            CoreBottomNavigation(
                selectedTab = selectedTab,
                onSelect = { tab -> selectedTab = tab },
                onOpenSettings = { openSettings(SettingsTarget.SystemLink) },
            )
            } // Column
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                CoreTab.Home -> {
                    HomeDashboard(
                        glassesState = glassesState,
                        openClawState = openClawState,
                        gatewayLinkDuration = gatewayLinkDuration,
                        gatewayProtocolLabel = gatewayProtocolLabel,
                        sessions = sessionList,
                        currentSessionKey = currentSessionKey,
                        unreadSessions = unreadSessions,
                        showSessionPicker = showSessionPicker,
                        onToggleSessionPicker = {
                            if (!showSessionPicker) {
                                openClawClient.requestSessions()
                            }
                            showSessionPicker = !showSessionPicker
                        },
                        onSelectSession = { session ->
                            showSessionPicker = false
                            openClawClient.switchSession(session.key)
                        },
                        onDismissSessionPicker = { showSessionPicker = false },
                        isListening = isListening,
                        voiceMode = voiceMode,
                        ttsEnabled = ttsEnabled,
                        glassesVoiceButtonMode = glassesVoiceButtonMode,
                        liveTalkState = liveTalkState,
                        onGlassesVoiceButtonModeChange = setGlassesVoiceButtonMode,
                        currentSessionName = sessionList.firstOrNull { it.key == currentSessionKey }?.name
                            ?: currentSessionKey
                            ?: "NONE",
                        unreadCount = unreadSessions.size,
                        pendingPhotoCount = pendingPhotos.size,
                        onConnectGlasses = { glassesManager.startScanning() },
                        onConnectOpenClaw = toggleOpenClawGateway,
                        onNavigate = { tab -> selectedTab = tab },
                        onOpenSettings = openSettings,
                    )
                }

                CoreTab.Chat -> {
                    ChatDeck(
                        sessions = sessionList,
                        currentSessionKey = currentSessionKey,
                        unreadSessions = unreadSessions,
                        showSessionPicker = showSessionPicker,
                        onToggleSessionPicker = {
                            if (!showSessionPicker) {
                                openClawClient.requestSessions()
                            }
                            showSessionPicker = !showSessionPicker
                        },
                        onSelectSession = { session ->
                            showSessionPicker = false
                            openClawClient.switchSession(session.key)
                        },
                        onDismissSessionPicker = { showSessionPicker = false },
                        openClawState = openClawState,
                        agentActivity = agentActivity,
                        chatMessages = chatMessages,
                        listState = listState,
                    )
                }

                CoreTab.Connection -> {
                    ConnectionDeck(
                        glassesState = glassesState,
                        openClawState = openClawState,
                        gatewayProtocolLabel = gatewayProtocolLabel,
                        onConnectGlasses = { glassesManager.startScanning() },
                        onConnectOpenClaw = toggleOpenClawGateway,
                        onOpenSettings = openSettings,
                    )
                }

                CoreTab.Voice -> {
                    VoiceDeck(
                        isListening = isListening,
                        voiceMode = voiceMode,
                        ttsEnabled = ttsEnabled,
                        glassesVoiceButtonMode = glassesVoiceButtonMode,
                        liveTalkState = liveTalkState,
                        liveTalkSource = liveTalkSource,
                        agentWakeEnabled = agentWakeEnabled,
                        agentWakeStatus = agentWakeStatus,
                        agentWakeSessionName = agentWakeSessionKey?.let { key ->
                            sessionList.firstOrNull { it.key == key }?.name
                        },
                        onToggleAgentWake = {
                            if (agentWakeEnabled) stopAgentWakeMode() else startAgentWakeMode()
                        },
                        onTogglePhoneLiveTalk = ::toggleLiveTalkFromPhone,
                        onGlassesVoiceButtonModeChange = setGlassesVoiceButtonMode,
                        onOpenSettings = openSettings,
                    )
                }

                CoreTab.Hud -> {
                    HudDeck(
                        glassesState = glassesState,
                        pendingPhotoCount = pendingPhotos.size,
                        onOpenSettings = openSettings,
                    )
                }

                CoreTab.Diagnostics -> {
                    DiagnosticsDeck(
                        openClawState = openClawState,
                        glassesState = glassesState,
                        gatewayProtocolLabel = gatewayProtocolLabel,
                        currentSessionName = sessionList.firstOrNull { it.key == currentSessionKey }?.name
                            ?: currentSessionKey
                            ?: "NONE",
                        unreadCount = unreadSessions.size,
                        isListening = isListening,
                        voiceMode = voiceMode,
                        ttsEnabled = ttsEnabled,
                        pendingPhotoCount = pendingPhotos.size,
                        messageCount = chatMessages.size,
                        onOpenSettings = openSettings,
                    )
                }
            }
        }
    }

    // Glasses state for settings
    val debugModeEnabled by glassesManager.debugModeEnabled.collectAsState()
    val discoveredDevices by glassesManager.discoveredDevices.collectAsState()
    var hasCachedSn by remember { mutableStateOf(RokidSdkManager.hasCachedSn()) }
    var cachedSn by remember { mutableStateOf(RokidSdkManager.getCachedSn()) }
    var cachedDeviceName by remember { mutableStateOf(RokidSdkManager.getCachedDeviceName()) }

    // Settings screen (full-screen overlay with slide-up animation)
    AnimatedVisibility(
        visible = showSettings,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        SettingsScreen(
            // Server
            openClawHost = openClawHost,
            openClawPort = openClawPort,
            openClawToken = openClawToken,
            openClawState = openClawState,
            onApplyServerSettings = { host, port, token ->
                openClawHost = host
                openClawPort = port
                openClawToken = token
                prefs.edit()
                    .putString("openclaw_host", host)
                    .putString("openclaw_port", port)
                    .remove("openclaw_token")
                    .apply()
                securePrefs.putString("openclaw_token", token)
                val portNum = port.toIntOrNull() ?: 18789
                openClawClient.disconnect()
                openClawClient.connect(host, portNum, token)
            },
            // Glasses
            glassesState = glassesState,
            discoveredDevices = discoveredDevices,
            debugModeEnabled = debugModeEnabled,
            onStartScanning = { glassesManager.startScanning() },
            onStopScanning = { glassesManager.stopScanning() },
            onConnectDevice = { device -> glassesManager.connectToDevice(device) },
            onDisconnectGlasses = { glassesManager.disconnect() },
            onClearSn = {
                RokidSdkManager.clearCachedSn()
                hasCachedSn = false
                cachedSn = null
                cachedDeviceName = null
            },
            onCancelReconnect = { glassesManager.cancelReconnect() },
            onRetryReconnect = { glassesManager.retryReconnectNow() },
            hasCachedSn = hasCachedSn,
            cachedSn = cachedSn,
            cachedDeviceName = cachedDeviceName,
            // Wake on stream
            wakeOnStreamEnabled = wakeOnStreamEnabled,
            onWakeOnStreamChange = { enabled ->
                glassesManager.wakeSignalManager.setEnabled(enabled)
            },
            // Voice
            voiceLanguageManager = voiceLanguageManager,
            voiceRecognitionManager = voiceRecognitionManager,
            // TTS
            ttsSettingsManager = ttsSettingsManager,
            elevenLabsClient = elevenLabsClient,
            // Developer
            onDebugModeChange = { enabled ->
                if (enabled) glassesManager.enableDebugMode()
                else glassesManager.disableDebugMode()
            },
            // Navigation
            initialTarget = settingsTarget,
            onBack = {
                showSettings = false
                glassesManager.stopScanning()
            },
        )
    }
    } // Box
}

@Composable
private fun MatrixRainBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "matrix_rain")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "matrix_phase",
    )
    val glyphs = remember {
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ<>[]{}+=-"
    }

    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    JsosPalette.ScreenTop,
                    JsosPalette.ScreenMid,
                    JsosPalette.ScreenBottom,
                ),
                startY = 0f,
                endY = size.height,
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF245257).copy(alpha = 0.42f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.24f, size.height * 0.03f),
                radius = size.width * 0.72f,
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF1E452B).copy(alpha = 0.23f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.58f, size.height * 0.47f),
                radius = size.width * 0.82f,
            )
        )

        val columnStep = 15.dp.toPx()
        val rowStep = 16.dp.toPx()
        val textSize = 13.sp.toPx()
        val columns = (size.width / columnStep).roundToInt() + 3
        val travel = size.height + rowStep * 34f

        drawIntoCanvas { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.MONOSPACE
                this.textSize = textSize
                textAlign = Paint.Align.CENTER
            }
            for (col in 0 until columns) {
                val x = (col - 1) * columnStep + if (col % 2 == 0) 0f else columnStep * 0.34f
                val length = 14 + (col * 7 % 20)
                val speed = 0.78f + (col % 5) * 0.08f
                val headY = ((phase * travel * speed) + col * rowStep * 3.7f) % travel - rowStep * 20f
                for (i in 0 until length) {
                    val y = headY - i * rowStep
                    if (y < -rowStep || y > size.height + rowStep) continue

                    val fade = 1f - (i.toFloat() / length.toFloat())
                    val head = i == 0
                    val alpha = if (head) 0.90f else (0.20f + (col % 4) * 0.035f) * fade
                    val colorAlpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
                    paint.color = if (head) {
                        android.graphics.Color.argb(colorAlpha, 230, 255, 238)
                    } else {
                        android.graphics.Color.argb(colorAlpha, 36, 255, 136)
                    }
                    paint.setShadowLayer(
                        if (head) 18f else 7f,
                        0f,
                        0f,
                        if (head) {
                            android.graphics.Color.argb(190, 225, 255, 238)
                        } else {
                            android.graphics.Color.argb(130, 99, 244, 92)
                        }
                    )
                    val glyphIndex = ((phase * glyphs.length * 4f).roundToInt() + col * 17 + i * 7) % glyphs.length
                    canvas.nativeCanvas.drawText(glyphs[glyphIndex].toString(), x, y, paint)
                }
            }
            paint.clearShadowLayer()
        }

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0A1214).copy(alpha = 0.04f),
                    Color.Transparent,
                    Color(0xFF040708).copy(alpha = 0.08f),
                )
            )
        )
    }
}

@Composable
private fun MatrixHeroHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 10.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            color = JsosPalette.CardAlt,
            border = BorderStroke(1.dp, JsosPalette.Cyan.copy(alpha = 0.50f)),
            shape = RoundedCornerShape(11.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.jsos_core_foreground),
                contentDescription = "JSOS Core",
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(11.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            "JSOS Spatial Operating System",
            color = JsosPalette.Cyan,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CoreBottomNavigation(
    selectedTab: CoreTab,
    onSelect: (CoreTab) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = JsosPalette.Green,
        selectedTextColor = JsosPalette.Green,
        indicatorColor = Color.Transparent,
        unselectedIconColor = JsosPalette.Cyan.copy(alpha = 0.82f),
        unselectedTextColor = JsosPalette.Muted,
    )
    NavigationBar(
        containerColor = Color(0xFF12191C).copy(alpha = 0.58f),
        contentColor = JsosPalette.Text,
        tonalElevation = 0.dp,
    ) {
        NavigationBarItem(
            selected = selectedTab == CoreTab.Home,
            onClick = { onSelect(CoreTab.Home) },
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("Home", fontFamily = FontFamily.Monospace, fontSize = 9.sp) },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = selectedTab == CoreTab.Chat,
            onClick = { onSelect(CoreTab.Chat) },
            icon = { Icon(Icons.Default.Forum, contentDescription = null) },
            label = { Text("Chat", fontFamily = FontFamily.Monospace, fontSize = 9.sp) },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = selectedTab == CoreTab.Voice,
            onClick = { onSelect(CoreTab.Voice) },
            icon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
            label = { Text("Voice", fontFamily = FontFamily.Monospace, fontSize = 9.sp) },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = false,
            onClick = onOpenSettings,
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Settings", fontFamily = FontFamily.Monospace, fontSize = 9.sp) },
            colors = itemColors,
        )
    }
}

@Composable
private fun HomeDashboard(
    glassesState: GlassesConnectionManager.ConnectionState,
    openClawState: OpenClawClient.ConnectionState,
    gatewayLinkDuration: String,
    gatewayProtocolLabel: String,
    sessions: List<SessionInfo>,
    currentSessionKey: String?,
    unreadSessions: Set<String>,
    showSessionPicker: Boolean,
    onToggleSessionPicker: () -> Unit,
    onSelectSession: (SessionInfo) -> Unit,
    onDismissSessionPicker: () -> Unit,
    isListening: Boolean,
    voiceMode: VoiceRecognitionManager.RecognitionMode,
    ttsEnabled: Boolean,
    glassesVoiceButtonMode: GlassesVoiceButtonMode,
    liveTalkState: LiveTalkState,
    onGlassesVoiceButtonModeChange: (GlassesVoiceButtonMode) -> Unit,
    currentSessionName: String,
    unreadCount: Int,
    pendingPhotoCount: Int,
    onConnectGlasses: () -> Unit,
    onConnectOpenClaw: () -> Unit,
    onNavigate: (CoreTab) -> Unit,
    onOpenSettings: (SettingsTarget) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HomeStatusStrip(
            glassesState = glassesState,
            openClawState = openClawState,
            isListening = isListening,
            voiceMode = glassesVoiceButtonMode,
        )

        VoiceModeQuickSwitch(
            mode = glassesVoiceButtonMode,
            liveTalkState = liveTalkState,
            onModeChange = onGlassesVoiceButtonModeChange,
        )

        DashboardModuleStack(
            glassesState = glassesState,
            openClawState = openClawState,
            gatewayLinkDuration = gatewayLinkDuration,
            gatewayProtocolLabel = gatewayProtocolLabel,
            isListening = isListening,
            voiceMode = voiceMode,
            ttsEnabled = ttsEnabled,
            currentSessionName = currentSessionName,
            unreadCount = unreadCount,
            pendingPhotoCount = pendingPhotoCount,
            onConnectOpenClaw = onConnectOpenClaw,
            onNavigate = onNavigate,
            onOpenSettings = onOpenSettings,
        )
    }
}

@Composable
private fun HomeStatusStrip(
    glassesState: GlassesConnectionManager.ConnectionState,
    openClawState: OpenClawClient.ConnectionState,
    isListening: Boolean,
    voiceMode: GlassesVoiceButtonMode,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompactStatusTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Dns,
            label = "Gateway",
            value = if (openClawState is OpenClawClient.ConnectionState.Connected) "ONLINE" else "OFFLINE",
            color = if (openClawState is OpenClawClient.ConnectionState.Connected) JsosPalette.Green else JsosPalette.Disabled,
            showDot = openClawState is OpenClawClient.ConnectionState.Connected,
        )
        CompactStatusTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Visibility,
            label = "Glasses",
            value = if (glassesState is GlassesConnectionManager.ConnectionState.Connected) "PAIRED" else "OFFLINE",
            color = if (glassesState is GlassesConnectionManager.ConnectionState.Connected) JsosPalette.Green else JsosPalette.Orange,
            showDot = glassesState is GlassesConnectionManager.ConnectionState.Connected,
        )
        CompactStatusTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.GraphicEq,
            label = "Voice",
            value = if (voiceMode == GlassesVoiceButtonMode.LiveTalk) "LIVE" else if (isListening) "LISTEN" else "READY",
            color = if (voiceMode == GlassesVoiceButtonMode.LiveTalk || isListening) JsosPalette.Green else JsosPalette.Cyan,
            showDot = voiceMode == GlassesVoiceButtonMode.LiveTalk || isListening,
        )
    }
}

@Composable
private fun CompactStatusTile(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
    showDot: Boolean,
) {
    Surface(
        modifier = modifier.height(64.dp),
        color = JsosPalette.Card,
        border = BorderStroke(1.dp, JsosPalette.Cyan.copy(alpha = 0.32f)),
        shape = RoundedCornerShape(7.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    color = JsosPalette.Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    maxLines = 1,
                )
                Text(
                    value,
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    lineHeight = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showDot) {
                Icon(
                    Icons.Default.Circle,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(7.dp),
                )
            }
        }
    }
}

@Composable
private fun ChatDeck(
    sessions: List<SessionInfo>,
    currentSessionKey: String?,
    unreadSessions: Set<String>,
    showSessionPicker: Boolean,
    onToggleSessionPicker: () -> Unit,
    onSelectSession: (SessionInfo) -> Unit,
    onDismissSessionPicker: () -> Unit,
    openClawState: OpenClawClient.ConnectionState,
    agentActivity: OpenClawClient.AgentActivityState,
    chatMessages: List<ChatMessage>,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    val speakerLabel = sessions.firstOrNull { it.key == currentSessionKey }?.name
        ?: currentSessionKey?.let { stableSessionDisplayName(it) }
        ?: "JSOS"

    Column(modifier = Modifier.fillMaxSize()) {
        if (openClawState is OpenClawClient.ConnectionState.Connected) {
            SessionSelector(
                sessions = sessions,
                currentSessionKey = currentSessionKey,
                unreadSessionKeys = unreadSessions,
                expanded = showSessionPicker,
                onToggle = onToggleSessionPicker,
                onSelect = onSelectSession,
                onDismiss = onDismissSessionPicker,
                agentActivity = agentActivity,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            if (chatMessages.isEmpty()) {
                Text(
                    "Awaiting JSOS stream...",
                    color = JsosPalette.Disabled,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Text(
                            "LIVE FEED",
                            color = JsosPalette.Cyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
                        )
                    }
                    items(chatMessages) { msg ->
                        ChatMessageRow(msg, speakerLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceModeQuickSwitch(
    mode: GlassesVoiceButtonMode,
    liveTalkState: LiveTalkState,
    onModeChange: (GlassesVoiceButtonMode) -> Unit,
) {
    val liveActive = mode == GlassesVoiceButtonMode.LiveTalk
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = JsosPalette.CardAlt,
        border = BorderStroke(1.dp, JsosPalette.Cyan.copy(alpha = if (liveActive) 0.72f else 0.56f)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WaveGlyph(
                tint = if (liveActive) JsosPalette.Green else JsosPalette.Cyan,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "GLASSES BUTTON",
                    color = JsosPalette.Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                )
                Text(
                    if (liveActive) "LIVE TALK" else "COMMAND",
                    color = JsosPalette.Cyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
            Text(
                liveTalkStateLabel(liveTalkState),
                color = JsosPalette.Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier.padding(end = 8.dp),
            )
            TextButton(onClick = { onModeChange(GlassesVoiceButtonMode.Command) }) {
                Text("CMD", fontFamily = FontFamily.Monospace, color = if (!liveActive) JsosPalette.Cyan else JsosPalette.Disabled)
            }
            TextButton(onClick = { onModeChange(GlassesVoiceButtonMode.LiveTalk) }) {
                Text("LIVE", fontFamily = FontFamily.Monospace, color = if (liveActive) JsosPalette.Cyan else JsosPalette.Disabled)
            }
        }
    }
}

@Composable
private fun WaveGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.04f, size.height * 0.58f)
            lineTo(size.width * 0.22f, size.height * 0.58f)
            lineTo(size.width * 0.32f, size.height * 0.25f)
            lineTo(size.width * 0.46f, size.height * 0.80f)
            lineTo(size.width * 0.60f, size.height * 0.17f)
            lineTo(size.width * 0.73f, size.height * 0.58f)
            lineTo(size.width * 0.96f, size.height * 0.58f)
        }
        drawPath(
            path = path,
            color = tint.copy(alpha = 0.25f),
            style = Stroke(width = size.width * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = size.width * 0.065f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun DashboardModuleStack(
    glassesState: GlassesConnectionManager.ConnectionState,
    openClawState: OpenClawClient.ConnectionState,
    gatewayLinkDuration: String,
    gatewayProtocolLabel: String,
    isListening: Boolean,
    voiceMode: VoiceRecognitionManager.RecognitionMode,
    ttsEnabled: Boolean,
    currentSessionName: String,
    unreadCount: Int,
    pendingPhotoCount: Int,
    onConnectOpenClaw: () -> Unit,
    onNavigate: (CoreTab) -> Unit,
    onOpenSettings: (SettingsTarget) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DashboardModuleCard(
            icon = Icons.Default.Cloud,
            iconStyle = DashboardIconStyle.Gateway,
            title = "OpenClaw Gateway",
            accent = when (openClawState) {
                is OpenClawClient.ConnectionState.Connected -> JsosPalette.Green
                is OpenClawClient.ConnectionState.Error -> JsosPalette.Red
                is OpenClawClient.ConnectionState.PairingRequired -> JsosPalette.Orange
                is OpenClawClient.ConnectionState.Connecting,
                is OpenClawClient.ConnectionState.Authenticating -> JsosPalette.Yellow
                else -> JsosPalette.Disabled
            },
            rows = listOf(
                "Status" to when (openClawState) {
                    is OpenClawClient.ConnectionState.Connected -> "ONLINE"
                    is OpenClawClient.ConnectionState.Connecting -> "LINKING"
                    is OpenClawClient.ConnectionState.Authenticating -> "AUTH"
                    is OpenClawClient.ConnectionState.PairingRequired -> "PAIR"
                    is OpenClawClient.ConnectionState.Error -> "ERROR"
                    else -> "OFFLINE"
                },
                "Protocol" to gatewayProtocolLabel,
                "Link" to if (openClawState is OpenClawClient.ConnectionState.Connected) gatewayLinkDuration else "--",
            ),
            onClick = { onOpenSettings(SettingsTarget.SystemLink) },
            actionIcon = Icons.Default.PowerSettingsNew,
            onAction = onConnectOpenClaw,
        )

        DashboardModuleCard(
            icon = Icons.Default.Visibility,
            iconStyle = DashboardIconStyle.Glasses,
            title = "Rokid Glasses",
            accent = when (glassesState) {
                is GlassesConnectionManager.ConnectionState.Connected -> JsosPalette.Green
                is GlassesConnectionManager.ConnectionState.Error -> JsosPalette.Red
                is GlassesConnectionManager.ConnectionState.Reconnecting -> JsosPalette.Orange
                is GlassesConnectionManager.ConnectionState.Scanning,
                is GlassesConnectionManager.ConnectionState.Connecting -> JsosPalette.Yellow
                else -> JsosPalette.Disabled
            },
            rows = listOf(
                "Status" to when (glassesState) {
                    is GlassesConnectionManager.ConnectionState.Connected -> "PAIRED"
                    is GlassesConnectionManager.ConnectionState.Scanning -> "SCAN"
                    is GlassesConnectionManager.ConnectionState.Connecting -> "LINKING"
                    is GlassesConnectionManager.ConnectionState.Reconnecting -> "RETRY"
                    is GlassesConnectionManager.ConnectionState.Error -> "ERROR"
                    else -> "OFFLINE"
                },
                "Device" to if (glassesState is GlassesConnectionManager.ConnectionState.Connected) glassesState.deviceName else "Rokid",
                "Signal" to if (glassesState is GlassesConnectionManager.ConnectionState.Connected) "OK" else "--",
            ),
            onClick = { onOpenSettings(SettingsTarget.HudLink) },
        )

        DashboardModuleCard(
            icon = Icons.Default.GridView,
            iconStyle = DashboardIconStyle.Hud,
            title = "JSOS HUD",
            accent = JsosPalette.Green,
            rows = listOf(
                "Installed" to "1.0.1",
                "Protocol" to "4",
                "Status" to if (glassesState is GlassesConnectionManager.ConnectionState.Connected) "READY" else "STANDBY",
            ),
            onClick = { onOpenSettings(SettingsTarget.Deployment) },
        )

        DashboardModuleCard(
            icon = Icons.Default.Person,
            iconStyle = DashboardIconStyle.Session,
            title = "Session",
            accent = if (unreadCount > 0) JsosPalette.Green else JsosPalette.Cyan,
            rows = listOf(
                "Active" to currentSessionName,
                "Mode" to "LIVE",
                "Unread" to unreadCount.toString(),
            ),
            onClick = { onNavigate(CoreTab.Chat) },
        )

        DashboardModuleCard(
            icon = Icons.Default.GraphicEq,
            iconStyle = DashboardIconStyle.Diagnostics,
            title = "Diagnostics",
            accent = if (isListening || ttsEnabled || pendingPhotoCount > 0) JsosPalette.Green else JsosPalette.Disabled,
            rows = listOf(
                "Voice" to when {
                    isListening && voiceMode == VoiceRecognitionManager.RecognitionMode.OPENAI -> "REALTIME"
                    isListening -> "DEVICE"
                    else -> "READY"
                },
                "TTS" to if (ttsEnabled) "ON" else "OFF",
                "Media" to pendingPhotoCount.toString(),
            ),
            onClick = { onNavigate(CoreTab.Diagnostics) },
        )
    }
}

@Composable
private fun DashboardModuleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconStyle: DashboardIconStyle? = null,
    title: String,
    accent: Color,
    rows: List<Pair<String, String>>,
    onClick: (() -> Unit)? = null,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = JsosPalette.Card,
        border = BorderStroke(1.dp, JsosPalette.Cyan.copy(alpha = 0.46f)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.width(34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                DashboardModuleIcon(
                    style = iconStyle,
                    fallback = icon,
                    tint = JsosPalette.Cyan,
                    modifier = Modifier.size(30.dp),
                )
                Spacer(Modifier.height(6.dp))
                StatusDot(color = accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = JsosPalette.Cyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rows.forEach { (label, value) ->
                        val valueColor = when {
                            label.equals("Status", ignoreCase = true) -> accent
                            title == "Diagnostics" && (label == "Voice" || label == "TTS") -> accent
                            else -> JsosPalette.Cyan
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                label,
                                color = JsosPalette.Muted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                lineHeight = 10.sp,
                                maxLines = 1,
                            )
                            Text(
                                value,
                                color = valueColor,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                lineHeight = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (onClick != null) {
                if (actionIcon != null && onAction != null) {
                    IconButton(
                        onClick = onAction,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            actionIcon,
                            contentDescription = "Connect",
                            tint = JsosPalette.Cyan,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                } else {
                    Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = JsosPalette.Muted,
                    modifier = Modifier.size(20.dp),
                )
            }
            }
        }
    }
}

@Composable
private fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(8.dp)) {
        drawCircle(
            color = color.copy(alpha = 0.28f),
            radius = size.minDimension * 0.75f,
            center = Offset(size.width / 2f, size.height / 2f),
        )
        drawCircle(
            color = color,
            radius = size.minDimension * 0.42f,
            center = Offset(size.width / 2f, size.height / 2f),
        )
    }
}

@Composable
private fun DashboardModuleIcon(
    style: DashboardIconStyle?,
    fallback: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    if (style == null) {
        Icon(fallback, contentDescription = null, tint = tint, modifier = modifier)
        return
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.075f
        val glow = tint.copy(alpha = 0.22f)
        val dim = tint.copy(alpha = 0.68f)

        when (style) {
            DashboardIconStyle.Gateway -> {
                val center = Offset(w / 2f, h / 2f)
                val radius = w * 0.34f
                val points = (0 until 6).map { index ->
                    val angle = -PI / 2.0 + index * PI / 3.0
                    Offset(
                        x = center.x + cos(angle).toFloat() * radius,
                        y = center.y + sin(angle).toFloat() * radius,
                    )
                }
                points.forEachIndexed { index, point ->
                    val next = points[(index + 1) % points.size]
                    drawLine(glow, point, next, strokeWidth = stroke * 1.8f, cap = StrokeCap.Round)
                    drawLine(dim, point, next, strokeWidth = stroke, cap = StrokeCap.Round)
                    drawCircle(tint, radius = stroke * 1.15f, center = point)
                }
                drawCircle(glow, radius = radius * 0.34f, center = center)
                drawCircle(tint, radius = stroke * 1.25f, center = center)
            }

            DashboardIconStyle.Glasses -> {
                val lensSize = Size(w * 0.38f, h * 0.24f)
                val top = h * 0.38f
                val left = Offset(w * 0.05f, top)
                val right = Offset(w * 0.57f, top)
                drawOval(
                    color = glow,
                    topLeft = left,
                    size = lensSize,
                    style = Stroke(width = stroke * 2f),
                )
                drawOval(
                    color = glow,
                    topLeft = right,
                    size = lensSize,
                    style = Stroke(width = stroke * 2f),
                )
                drawOval(
                    color = dim,
                    topLeft = left,
                    size = lensSize,
                    style = Stroke(width = stroke),
                )
                drawOval(
                    color = dim,
                    topLeft = right,
                    size = lensSize,
                    style = Stroke(width = stroke),
                )
                drawLine(dim, Offset(w * 0.43f, h * 0.50f), Offset(w * 0.57f, h * 0.50f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(dim, Offset(w * 0.04f, h * 0.48f), Offset(w * 0.01f, h * 0.42f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(dim, Offset(w * 0.96f, h * 0.48f), Offset(w * 0.99f, h * 0.42f), strokeWidth = stroke, cap = StrokeCap.Round)
            }

            DashboardIconStyle.Hud -> {
                val cell = w * 0.16f
                val gap = w * 0.08f
                val startX = (w - cell * 3f - gap * 2f) / 2f
                val startY = (h - cell * 3f - gap * 2f) / 2f
                for (row in 0 until 3) {
                    for (col in 0 until 3) {
                        val left = startX + col * (cell + gap)
                        val top = startY + row * (cell + gap)
                        drawRoundRect(
                            color = glow,
                            topLeft = Offset(left - 1.dp.toPx(), top - 1.dp.toPx()),
                            size = Size(cell + 2.dp.toPx(), cell + 2.dp.toPx()),
                            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                        )
                        drawRoundRect(
                            color = tint,
                            topLeft = Offset(left, top),
                            size = Size(cell, cell),
                            cornerRadius = CornerRadius(1.2.dp.toPx(), 1.2.dp.toPx()),
                        )
                    }
                }
            }

            DashboardIconStyle.Session -> {
                drawCircle(
                    color = glow,
                    radius = w * 0.16f,
                    center = Offset(w * 0.5f, h * 0.28f),
                    style = Stroke(width = stroke * 1.8f),
                )
                drawCircle(
                    color = tint,
                    radius = w * 0.13f,
                    center = Offset(w * 0.5f, h * 0.28f),
                    style = Stroke(width = stroke),
                )
                drawArc(
                    color = glow,
                    startAngle = 205f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(w * 0.16f, h * 0.42f),
                    size = Size(w * 0.68f, h * 0.5f),
                    style = Stroke(width = stroke * 1.8f, cap = StrokeCap.Round),
                )
                drawArc(
                    color = tint,
                    startAngle = 205f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(w * 0.18f, h * 0.45f),
                    size = Size(w * 0.64f, h * 0.44f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }

            DashboardIconStyle.Diagnostics -> {
                val path = Path().apply {
                    moveTo(w * 0.05f, h * 0.58f)
                    lineTo(w * 0.22f, h * 0.58f)
                    lineTo(w * 0.32f, h * 0.26f)
                    lineTo(w * 0.45f, h * 0.78f)
                    lineTo(w * 0.57f, h * 0.18f)
                    lineTo(w * 0.72f, h * 0.58f)
                    lineTo(w * 0.95f, h * 0.58f)
                }
                drawPath(
                    path = path,
                    color = glow,
                    style = Stroke(width = stroke * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                drawPath(
                    path = path,
                    color = tint,
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

@Composable
private fun ConnectionDeck(
    glassesState: GlassesConnectionManager.ConnectionState,
    openClawState: OpenClawClient.ConnectionState,
    gatewayProtocolLabel: String,
    onConnectGlasses: () -> Unit,
    onConnectOpenClaw: () -> Unit,
    onOpenSettings: (SettingsTarget) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DashboardModuleCard(
            icon = Icons.Default.Cloud,
            title = "OpenClaw Gateway",
            accent = if (openClawState is OpenClawClient.ConnectionState.Connected) Color(0xFF63F45C) else Color(0xFFFFD166),
            rows = listOf(
                "Status" to if (openClawState is OpenClawClient.ConnectionState.Connected) "ONLINE" else "OFFLINE",
                "Tap" to "SYSTEM LINK",
                "Protocol" to gatewayProtocolLabel,
            ),
            onClick = { onOpenSettings(SettingsTarget.SystemLink) },
            actionIcon = Icons.Default.PowerSettingsNew,
            onAction = onConnectOpenClaw,
        )
        DashboardModuleCard(
            icon = Icons.Default.Visibility,
            title = "Rokid Glasses",
            accent = if (glassesState is GlassesConnectionManager.ConnectionState.Connected) Color(0xFF63F45C) else Color(0xFFFFD166),
            rows = listOf(
                "Status" to if (glassesState is GlassesConnectionManager.ConnectionState.Connected) "PAIRED" else "OFFLINE",
                "Tap" to "HUD LINK",
                "Bridge" to "CXR-M",
            ),
            onClick = { onOpenSettings(SettingsTarget.HudLink) },
        )
        TabHint("Gateway opens SYSTEM LINK. Rokid Glasses opens HUD LINK. The small power buttons handle quick connect.")
    }
}

@Composable
private fun VoiceDeck(
    isListening: Boolean,
    voiceMode: VoiceRecognitionManager.RecognitionMode,
    ttsEnabled: Boolean,
    glassesVoiceButtonMode: GlassesVoiceButtonMode,
    liveTalkState: LiveTalkState,
    liveTalkSource: LiveTalkSource?,
    agentWakeEnabled: Boolean,
    agentWakeStatus: String,
    agentWakeSessionName: String?,
    onToggleAgentWake: () -> Unit,
    onTogglePhoneLiveTalk: () -> Unit,
    onGlassesVoiceButtonModeChange: (GlassesVoiceButtonMode) -> Unit,
    onOpenSettings: (SettingsTarget) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DashboardModuleCard(
            icon = Icons.Default.GraphicEq,
            title = "Voice Engine",
            accent = if (isListening) Color(0xFF22D3EE) else Color(0xFF63F45C),
            rows = listOf(
                "Status" to if (isListening) "LISTEN" else "READY",
                "Model" to if (voiceMode == VoiceRecognitionManager.RecognitionMode.OPENAI) "4O-TRANSCRIBE" else "DEVICE",
                "Tap" to "CONFIG",
            ),
            onClick = { onOpenSettings(SettingsTarget.Voice) },
        )
        AgentWakeCard(
            enabled = agentWakeEnabled,
            status = agentWakeStatus,
            activeSessionName = agentWakeSessionName,
            onToggle = onToggleAgentWake,
        )
        CoreLiveTalkCard(
            liveTalkState = liveTalkState,
            liveTalkSource = liveTalkSource,
            onTogglePhoneLiveTalk = onTogglePhoneLiveTalk,
        )
        GlassesVoiceButtonCard(
            mode = glassesVoiceButtonMode,
            liveTalkState = liveTalkState,
            onModeChange = onGlassesVoiceButtonModeChange,
        )
        DashboardModuleCard(
            icon = Icons.Default.RecordVoiceOver,
            title = "TTS Engine",
            accent = if (ttsEnabled) Color(0xFF63F45C) else Color(0xFF587465),
            rows = listOf(
                "Status" to if (ttsEnabled) "ON" else "OFF",
                "Provider" to "ElevenLabs",
                "Tap" to "CONFIG",
            ),
            onClick = { onOpenSettings(SettingsTarget.ResponseVoice) },
        )
        TabHint("Core Live Talk uses the phone speaker. The glasses voice button can stay classic STT or route Live Talk to Rokid.")
    }
}

@Composable
private fun AgentWakeCard(
    enabled: Boolean,
    status: String,
    activeSessionName: String?,
    onToggle: () -> Unit,
) {
    val accent = if (enabled) Color(0xFF63F45C) else Color(0xFF22D3EE)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = JsosPalette.Card,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.70f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Agent Wake",
                    color = JsosPalette.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = activeSessionName?.uppercase() ?: status,
                    color = accent,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clickable(onClick = onToggle),
                shape = RoundedCornerShape(8.dp),
                color = if (enabled) Color(0xFF16321D) else Color(0xFF071010),
                border = BorderStroke(1.dp, accent),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (enabled) "STOP AGENT WAKE" else "START AGENT WAKE",
                        color = accent,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
            }

            Text(
                text = "Say an agent name first. Continue speaking without repeating it.",
                color = JsosPalette.Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }
}
@Composable
private fun CoreLiveTalkCard(
    liveTalkState: LiveTalkState,
    liveTalkSource: LiveTalkSource?,
    onTogglePhoneLiveTalk: () -> Unit,
) {
    val phoneActive = liveTalkSource == LiveTalkSource.Phone && liveTalkState !is LiveTalkState.Idle
    val glassesActive = liveTalkSource == LiveTalkSource.Glasses && liveTalkState !is LiveTalkState.Idle
    val accent = if (phoneActive) Color(0xFF63F45C) else Color(0xFF22D3EE)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = JsosPalette.Card,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.70f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Core Live Talk",
                    color = JsosPalette.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when {
                        phoneActive -> liveTalkStateLabel(liveTalkState)
                        glassesActive -> "ROKID"
                        else -> "IDLE"
                    },
                    color = if (phoneActive || glassesActive) Color(0xFF63F45C) else Color(0xFF8EA99B),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clickable(onClick = onTogglePhoneLiveTalk),
                    shape = RoundedCornerShape(8.dp),
                    color = if (phoneActive) Color(0xFF16321D) else Color(0xFF071010),
                    border = BorderStroke(1.dp, accent),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = when {
                                phoneActive -> "STOP PHONE LIVE"
                                glassesActive -> "SWITCH TO PHONE"
                                else -> "START PHONE LIVE"
                            },
                            color = accent,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            Text(
                text = "Output: phone speaker",
                color = JsosPalette.Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun GlassesVoiceButtonCard(
    mode: GlassesVoiceButtonMode,
    liveTalkState: LiveTalkState,
    onModeChange: (GlassesVoiceButtonMode) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = JsosPalette.Card,
        border = BorderStroke(1.dp, Color(0xFF1D5C2A)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Glasses Voice Button",
                    color = JsosPalette.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = liveTalkStateLabel(liveTalkState),
                    color = if (liveTalkState is LiveTalkState.Idle) Color(0xFF8EA99B) else Color(0xFF63F45C),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassesVoiceModePill(
                    label = "COMMAND",
                    selected = mode == GlassesVoiceButtonMode.Command,
                    modifier = Modifier.weight(1f),
                    onClick = { onModeChange(GlassesVoiceButtonMode.Command) },
                )
                GlassesVoiceModePill(
                    label = "LIVE TALK",
                    selected = mode == GlassesVoiceButtonMode.LiveTalk,
                    modifier = Modifier.weight(1f),
                    onClick = { onModeChange(GlassesVoiceButtonMode.LiveTalk) },
                )
            }
        }
    }
}

@Composable
private fun GlassesVoiceModePill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(38.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = if (selected) JsosPalette.CardAlt else JsosPalette.ScreenTop,
        border = BorderStroke(1.dp, if (selected) JsosPalette.Green else JsosPalette.BorderSubtle),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = if (selected) JsosPalette.Green else JsosPalette.Muted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun liveTalkStateLabel(state: LiveTalkState): String =
    when (state) {
        is LiveTalkState.Idle -> "IDLE"
        is LiveTalkState.Connecting -> "LINKING"
        is LiveTalkState.Listening -> "LIVE"
        is LiveTalkState.Speaking -> "SPEAKING"
        is LiveTalkState.Error -> "ERROR"
    }

@Composable
private fun HudDeck(
    glassesState: GlassesConnectionManager.ConnectionState,
    pendingPhotoCount: Int,
    onOpenSettings: (SettingsTarget) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DashboardModuleCard(
            icon = Icons.Default.GridView,
            title = "JSOS HUD",
            accent = Color(0xFF63F45C),
            rows = listOf(
                "Install" to "Hi Rokid",
                "Protocol" to "4",
                "Tap" to "DEPLOY",
            ),
            onClick = { onOpenSettings(SettingsTarget.Deployment) },
        )
        DashboardModuleCard(
            icon = Icons.Default.CameraAlt,
            title = "Media Bridge",
            accent = if (pendingPhotoCount > 0) Color(0xFF22D3EE) else Color(0xFF587465),
            rows = listOf(
                "Camera" to if (glassesState is GlassesConnectionManager.ConnectionState.Connected) "READY" else "HUD OFF",
                "Queue" to pendingPhotoCount.toString(),
                "Transfer" to "CORE",
            ),
        )
        TabHint("Tap JSOS HUD to open the Hi Rokid / APK Manager deployment panel.")
    }
}

@Composable
private fun DiagnosticsDeck(
    openClawState: OpenClawClient.ConnectionState,
    glassesState: GlassesConnectionManager.ConnectionState,
    gatewayProtocolLabel: String,
    currentSessionName: String,
    unreadCount: Int,
    isListening: Boolean,
    voiceMode: VoiceRecognitionManager.RecognitionMode,
    ttsEnabled: Boolean,
    pendingPhotoCount: Int,
    messageCount: Int,
    onOpenSettings: (SettingsTarget) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DashboardModuleCard(
            icon = Icons.Default.Cloud,
            title = "OpenClaw Link",
            accent = when (openClawState) {
                is OpenClawClient.ConnectionState.Connected -> Color(0xFF63F45C)
                is OpenClawClient.ConnectionState.Error -> Color(0xFFFF5F56)
                is OpenClawClient.ConnectionState.PairingRequired -> Color(0xFFFF9F1C)
                is OpenClawClient.ConnectionState.Connecting,
                is OpenClawClient.ConnectionState.Authenticating -> Color(0xFFFFD166)
                is OpenClawClient.ConnectionState.Disconnected -> Color(0xFF587465)
            },
            rows = listOf(
                "Status" to when (openClawState) {
                    is OpenClawClient.ConnectionState.Connected -> "ONLINE"
                    is OpenClawClient.ConnectionState.Connecting -> "LINKING"
                    is OpenClawClient.ConnectionState.Authenticating -> "AUTH"
                    is OpenClawClient.ConnectionState.PairingRequired -> "PAIR"
                    is OpenClawClient.ConnectionState.Error -> "ERROR"
                    is OpenClawClient.ConnectionState.Disconnected -> "OFFLINE"
                },
                "Protocol" to gatewayProtocolLabel,
                "Session" to currentSessionName,
            ),
            onClick = { onOpenSettings(SettingsTarget.SystemLink) },
        )

        DashboardModuleCard(
            icon = Icons.Default.Visibility,
            title = "HUD Link",
            accent = when (glassesState) {
                is GlassesConnectionManager.ConnectionState.Connected -> Color(0xFF63F45C)
                is GlassesConnectionManager.ConnectionState.Error -> Color(0xFFFF5F56)
                is GlassesConnectionManager.ConnectionState.Reconnecting -> Color(0xFFFF9F1C)
                is GlassesConnectionManager.ConnectionState.Scanning,
                is GlassesConnectionManager.ConnectionState.Connecting -> Color(0xFFFFD166)
                is GlassesConnectionManager.ConnectionState.Disconnected -> Color(0xFF587465)
            },
            rows = listOf(
                "Status" to when (glassesState) {
                    is GlassesConnectionManager.ConnectionState.Connected -> "ONLINE"
                    is GlassesConnectionManager.ConnectionState.Scanning -> "SCAN"
                    is GlassesConnectionManager.ConnectionState.Connecting -> "LINKING"
                    is GlassesConnectionManager.ConnectionState.Reconnecting -> "RETRY"
                    is GlassesConnectionManager.ConnectionState.Error -> "ERROR"
                    is GlassesConnectionManager.ConnectionState.Disconnected -> "OFFLINE"
                },
                "Device" to if (glassesState is GlassesConnectionManager.ConnectionState.Connected) glassesState.deviceName else "Rokid",
                "Bridge" to "CXR-M",
            ),
            onClick = { onOpenSettings(SettingsTarget.HudLink) },
        )

        DashboardModuleCard(
            icon = Icons.Default.GraphicEq,
            title = "Voice Stack",
            accent = if (isListening || ttsEnabled) Color(0xFF63F45C) else Color(0xFF587465),
            rows = listOf(
                "STT" to when {
                    isListening && voiceMode == VoiceRecognitionManager.RecognitionMode.OPENAI -> "REALTIME"
                    isListening -> "DEVICE"
                    else -> "READY"
                },
                "TTS" to if (ttsEnabled) "ON" else "OFF",
                "Mic" to if (isListening) "LIVE" else "IDLE",
            ),
            onClick = { onOpenSettings(SettingsTarget.Voice) },
        )

        DashboardModuleCard(
            icon = Icons.Default.Forum,
            title = "Session",
            accent = if (unreadCount > 0 || pendingPhotoCount > 0) Color(0xFF22D3EE) else Color(0xFF587465),
            rows = listOf(
                "Active" to currentSessionName,
                "Loaded" to messageCount.toString(),
                "Unread" to unreadCount.toString(),
            ),
        )
    }
}

@Composable
private fun TabHint(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF040907),
        border = BorderStroke(1.dp, Color(0xFF24573A)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text,
                                color = JsosPalette.MutedStrong,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
fun ChatMessageRow(msg: ChatMessage, speakerLabel: String = "JSOS") {
    val isUser = msg.role == "user"
    val lines = msg.content.lines()
    val isSystemOnly = lines.any { isSystemFeedLine(it) } && lines.none { it.isNotBlank() && !isSystemFeedLine(it) }
    val accent = when {
        isSystemOnly -> JsosPalette.Muted
        else -> JsosPalette.Cyan
    }
    val label = when {
        isSystemOnly -> "SYSTEM>"
        isUser -> "YOU>"
        else -> "${speakerLabel}>"
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isSystemOnly) 3.dp else 4.dp),
        color = if (isSystemOnly) JsosPalette.CardDark.copy(alpha = 0.68f) else JsosPalette.Card,
        border = BorderStroke(1.dp, JsosPalette.Cyan.copy(alpha = if (isSystemOnly) 0.22f else 0.46f)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (isSystemOnly) 7.dp else 10.dp)
        ) {
            Text(
                text = label,
                color = accent,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(3.dp))
            lines.forEach { line ->
                val systemLine = isSystemFeedLine(line)
                Text(
                    text = line,
                    color = if (systemLine) JsosPalette.Muted else JsosPalette.Text,
                    fontSize = if (systemLine) 10.sp else 13.sp,
                    lineHeight = if (systemLine) 14.sp else 18.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (systemLine) FontWeight.Normal else FontWeight.Medium,
                )
            }
        }
    }
}

private fun formatLinkDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private fun isSystemFeedLine(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.startsWith("System:") ||
        trimmed.contains("gateway disconnected", ignoreCase = true) ||
        trimmed.contains("gateway connected", ignoreCase = true)
}

@Composable
private fun CoreCommandPanel(
    isListening: Boolean,
    voiceMode: VoiceRecognitionManager.RecognitionMode,
    ttsEnabled: Boolean,
    pendingPhotoCount: Int,
    onOpenSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        color = Color(0xFF050C0A),
        border = BorderStroke(1.dp, Color(0xFF24573A)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "CORE COMMAND",
                    color = Color(0xFF63F45C),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (isListening) "LISTEN" else "READY",
                    color = if (isListening) Color(0xFF22D3EE) else Color(0xFF9EB7A8),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CommandTile(
                    modifier = Modifier.weight(1f),
                    icon = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                    label = "Voice",
                    value = when {
                    isListening -> if (voiceMode == VoiceRecognitionManager.RecognitionMode.OPENAI) "Realtime" else "Device"
                        else -> "Standby"
                    },
                    color = if (isListening) Color(0xFF22D3EE) else Color(0xFF587465),
                    actionIcon = null,
                    onAction = {},
                )

                CommandTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.RecordVoiceOver,
                    label = "TTS",
                    value = if (ttsEnabled) "Enabled" else "Off",
                    color = if (ttsEnabled) Color(0xFF63F45C) else Color(0xFF587465),
                    actionIcon = null,
                    onAction = {},
                )

                CommandTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.InstallMobile,
                    label = "HUD APK",
                    value = if (pendingPhotoCount > 0) "Media $pendingPhotoCount" else "Hi Rokid",
                    color = Color(0xFF22D3EE),
                    actionIcon = Icons.Default.Tune,
                    onAction = onOpenSettings,
                )

                CommandTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CameraAlt,
                    label = "Cam",
                    value = if (pendingPhotoCount > 0) "$pendingPhotoCount queued" else "Ready",
                    color = if (pendingPhotoCount > 0) Color(0xFF63F45C) else Color(0xFF587465),
                    actionIcon = null,
                    onAction = {},
                )
            }
        }
    }
}

@Composable
private fun CommandTile(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    onAction: () -> Unit,
) {
    Surface(
        modifier = modifier.height(68.dp),
        color = Color(0xFF07100D),
        border = BorderStroke(1.dp, color.copy(alpha = 0.34f)),
        shape = RoundedCornerShape(7.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    color = Color(0xFF9EB7A8),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    value,
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (actionIcon != null) {
                IconButton(
                    onClick = onAction,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(actionIcon, contentDescription = label, tint = color, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusBar(
    glassesState: GlassesConnectionManager.ConnectionState,
    openClawState: OpenClawClient.ConnectionState,
    onConnectGlasses: () -> Unit,
    onConnectOpenClaw: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoreStatusCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Visibility,
            label = "HUD",
            value = when (glassesState) {
                is GlassesConnectionManager.ConnectionState.Connected -> "ONLINE"
                is GlassesConnectionManager.ConnectionState.Connecting -> "LINKING"
                is GlassesConnectionManager.ConnectionState.Scanning -> "SCAN"
                is GlassesConnectionManager.ConnectionState.Reconnecting -> "RETRY ${glassesState.attempt}"
                is GlassesConnectionManager.ConnectionState.Error -> "ERROR"
                else -> "OFFLINE"
            },
            color = when (glassesState) {
                is GlassesConnectionManager.ConnectionState.Connected -> Color(0xFF63F45C)
                is GlassesConnectionManager.ConnectionState.Connecting,
                is GlassesConnectionManager.ConnectionState.Scanning -> Color(0xFFFFD166)
                is GlassesConnectionManager.ConnectionState.Reconnecting -> Color(0xFFFF9F1C)
                is GlassesConnectionManager.ConnectionState.Error -> Color(0xFFFF5F56)
                else -> Color(0xFF587465)
            },
            actionIcon = if (glassesState is GlassesConnectionManager.ConnectionState.Disconnected) Icons.Default.PowerSettingsNew else null,
            onAction = onConnectGlasses,
        )

        CoreStatusCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Cloud,
            label = "CORE",
            value = when (openClawState) {
                is OpenClawClient.ConnectionState.Connected -> "ONLINE"
                is OpenClawClient.ConnectionState.Connecting -> "LINKING"
                is OpenClawClient.ConnectionState.Authenticating -> "AUTH"
                is OpenClawClient.ConnectionState.PairingRequired -> "PAIR"
                is OpenClawClient.ConnectionState.Error -> "ERROR"
                else -> "OFFLINE"
            },
            color = when (openClawState) {
                is OpenClawClient.ConnectionState.Connected -> Color(0xFF63F45C)
                is OpenClawClient.ConnectionState.Connecting,
                is OpenClawClient.ConnectionState.Authenticating -> Color(0xFFFFD166)
                is OpenClawClient.ConnectionState.PairingRequired -> Color(0xFFFF9F1C)
                is OpenClawClient.ConnectionState.Error -> Color(0xFFFF5F56)
                else -> Color(0xFF587465)
            },
            actionIcon = Icons.Default.PowerSettingsNew,
            onAction = onConnectOpenClaw,
        )
    }
}

@Composable
private fun CoreStatusCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    onAction: () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF07100D),
        border = BorderStroke(1.dp, color.copy(alpha = 0.48f)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    color = Color(0xFF9EB7A8),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
                Text(
                    value,
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            if (actionIcon != null) {
                IconButton(
                    onClick = onAction,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(actionIcon, contentDescription = "Connect", tint = color, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun SessionSelector(
    sessions: List<SessionInfo>,
    currentSessionKey: String?,
    unreadSessionKeys: Set<String> = emptySet(),
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (SessionInfo) -> Unit,
    onDismiss: () -> Unit,
    agentActivity: OpenClawClient.AgentActivityState = OpenClawClient.AgentActivityState.Ready,
) {
    val currentSession = sessions.firstOrNull { it.key == currentSessionKey }
    val displayName = currentSession?.name
        ?: currentSessionKey?.let { stableSessionDisplayName(it) }
        ?: "No session"
    val hasAnyUnread = unreadSessionKeys.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            color = JsosPalette.Card,
            border = BorderStroke(1.dp, JsosPalette.Cyan.copy(alpha = 0.46f)),
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Forum,
                    contentDescription = "Session",
                    modifier = Modifier.size(18.dp),
                    tint = if (hasAnyUnread) JsosPalette.Green else JsosPalette.Cyan
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "SESSION",
                    color = JsosPalette.Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = displayName,
                    color = JsosPalette.Text,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                AgentActivityBadge(agentActivity)
                if (hasAnyUnread) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Circle,
                        contentDescription = "Unread messages in other sessions",
                        modifier = Modifier.size(8.dp),
                        tint = JsosPalette.Green
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = JsosPalette.Muted,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            if (sessions.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Loading sessions...") },
                    onClick = {},
                    enabled = false
                )
            } else {
                sessions.forEach { session ->
                    val isCurrent = session.key == currentSessionKey
                    val hasUnread = session.key in unreadSessionKeys
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isCurrent) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Current",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                } else if (hasUnread) {
                                    Icon(
                                        Icons.Default.Circle,
                                        contentDescription = "New messages",
                                        modifier = Modifier.size(10.dp),
                                        tint = Color(0xFF4CAF50)
                                    )
                                    Spacer(Modifier.width(11.dp))
                                }
                                Text(
                                    text = session.name,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                                            else if (hasUnread) Color(0xFF4CAF50)
                                            else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                        },
                        onClick = { onSelect(session) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentActivityBadge(activity: OpenClawClient.AgentActivityState) {
    val active = activity != OpenClawClient.AgentActivityState.Ready
    val transition = rememberInfiniteTransition(label = "agent_activity")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "agent_activity_dots",
    )
    val dots = if (active) {
        ".".repeat(phase.toInt().coerceIn(0, 2) + 1).padEnd(3, ' ')
    } else {
        ""
    }
    val label = when (activity) {
        OpenClawClient.AgentActivityState.Ready -> "READY"
        OpenClawClient.AgentActivityState.Thinking -> "THINKING"
        OpenClawClient.AgentActivityState.Writing -> "WRITING"
    }
    val color = when (activity) {
        OpenClawClient.AgentActivityState.Ready -> JsosPalette.Green
        OpenClawClient.AgentActivityState.Thinking -> JsosPalette.Yellow
        OpenClawClient.AgentActivityState.Writing -> JsosPalette.Cyan
    }

    Surface(
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.38f)),
        shape = RoundedCornerShape(5.dp),
    ) {
        Text(
            text = "$label$dots",
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            maxLines = 1,
            modifier = Modifier
                .widthIn(min = 54.dp)
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

/**
 * Start voice recognition using VoiceRecognitionManager (OpenAI with fallback).
 */
private fun startVoiceRecognitionWithManager(
    voiceRecognitionManager: VoiceRecognitionManager,
    voiceHandler: VoiceCommandHandler,
    openClawClient: OpenClawClient,
    glassesManager: GlassesConnectionManager,
    mainHandler: android.os.Handler,
    isRetry: Boolean,
    languageTag: String? = null,
    pendingPhotos: () -> List<String> = { emptyList() },
    onPhotosConsumed: () -> Unit = {}
) {
    // Send initial voice state with mode indicator
    val modeIndicator = if (voiceRecognitionManager.isOpenAIAvailable()) "openai" else "device"
    val stateMsg = org.json.JSONObject().apply {
        put("type", "voice_state")
        put("state", "listening")
        put("mode", modeIndicator)
    }
    glassesManager.sendRawMessage(stateMsg.toString())

    // Keep the SDK AI scene alive - it times out if no ASR content is sent.
    // With OpenAI Realtime, there are no partials during active speech (only after VAD pause),
    // so the AI scene would close before any transcription arrives. Sending initial content
    // resets the timeout. Real partial results replace this via onPartialResult.
    RokidSdkManager.sendAsrContent("...")
    RokidSdkManager.sendExitEvent()

    // Send "processing" state to glasses when VAD detects speech end
    voiceRecognitionManager.onSpeechStopped = {
        val processingMsg = org.json.JSONObject().apply {
            put("type", "voice_state")
            put("state", "processing")
            put("mode", modeIndicator)
        }
        glassesManager.sendRawMessage(processingMsg.toString())
    }

    voiceRecognitionManager.startListening(languageTag = languageTag) { result ->
        val actualMode = voiceRecognitionManager.getModeDescription()
        android.util.Log.i("MainScreen", ">>> Voice result received (mode=$actualMode, retry=$isRetry, type=${result.javaClass.simpleName})")

        when (result) {
            is VoiceCommandHandler.VoiceResult.Text -> {
                RokidSdkManager.clearCommunicationDevice()
                if (result.text.isNotEmpty()) {
                    android.util.Log.i("MainScreen", "Voice text received ($actualMode, ${result.text.length} chars)")
                    RokidSdkManager.sendAsrContent(result.text)
                    RokidSdkManager.notifyAsrEnd()
                    // Don't send to OpenClaw here - glasses stages the text
                    // and sends user_input when user confirms via Send button
                    val resultMsg = org.json.JSONObject().apply {
                        put("type", "voice_result")
                        put("result_type", "text")
                        put("text", result.text)
                    }
                    glassesManager.sendRawMessage(resultMsg.toString())
                    mainHandler.postDelayed({ RokidSdkManager.sendExitEvent() }, 1500)
                } else {
                    android.util.Log.i("MainScreen", "Voice: no speech detected, dismissing")
                    RokidSdkManager.notifyAsrNone()
                    // Send voice_state idle to glasses so the voice overlay closes
                    val idleMsg = org.json.JSONObject().apply {
                        put("type", "voice_state")
                        put("state", "idle")
                    }
                    glassesManager.sendRawMessage(idleMsg.toString())
                    mainHandler.postDelayed({ RokidSdkManager.sendExitEvent() }, 500)
                }
            }
            is VoiceCommandHandler.VoiceResult.Command -> {
                RokidSdkManager.clearCommunicationDevice()
                android.util.Log.i("MainScreen", "Voice command received ($actualMode, commandLength=${result.command.length})")
                RokidSdkManager.sendAsrContent(result.command)
                RokidSdkManager.notifyAsrEnd()
                val resultMsg = org.json.JSONObject().apply {
                    put("type", "voice_result")
                    put("result_type", "command")
                    put("text", result.command)
                }
                glassesManager.sendRawMessage(resultMsg.toString())
                mainHandler.postDelayed({ RokidSdkManager.sendExitEvent() }, 1000)
            }
            is VoiceCommandHandler.VoiceResult.Error -> {
                // VoiceRecognitionManager handles fallback internally, but if we still get an error
                // after fallback attempt, we can retry with phone mic as last resort
                if (!isRetry) {
                    android.util.Log.w("MainScreen", "Voice error (redacted), retrying with phone mic...")
                    RokidSdkManager.clearCommunicationDevice()
                    mainHandler.postDelayed({
                        startVoiceRecognition(voiceHandler, openClawClient, glassesManager, mainHandler, isRetry = true, languageTag = languageTag, pendingPhotos = pendingPhotos, onPhotosConsumed = onPhotosConsumed)
                    }, 200)
                } else {
                    android.util.Log.e("MainScreen", "Voice error after retry (redacted)")
                    RokidSdkManager.clearCommunicationDevice()
                    RokidSdkManager.notifyAsrError()
                    val resultMsg = org.json.JSONObject().apply {
                        put("type", "voice_result")
                        put("result_type", "error")
                        put("text", result.message)
                    }
                    glassesManager.sendRawMessage(resultMsg.toString())
                    mainHandler.postDelayed({ RokidSdkManager.sendExitEvent() }, 2000)
                }
            }
        }
    }
}

/**
 * Start voice recognition with automatic retry on error (fallback handler only).
 */
private fun startVoiceRecognition(
    voiceHandler: VoiceCommandHandler,
    openClawClient: OpenClawClient,
    glassesManager: GlassesConnectionManager,
    mainHandler: android.os.Handler,
    isRetry: Boolean,
    languageTag: String? = null,
    pendingPhotos: () -> List<String> = { emptyList() },
    onPhotosConsumed: () -> Unit = {}
) {
    voiceHandler.startListening(languageTag = languageTag) { result ->
        android.util.Log.i("MainScreen", ">>> Voice result received (retry=$isRetry, type=${result.javaClass.simpleName})")
        when (result) {
            is VoiceCommandHandler.VoiceResult.Text -> {
                RokidSdkManager.clearCommunicationDevice()
                if (result.text.isNotEmpty()) {
                    android.util.Log.i("MainScreen", "AI voice text received (${result.text.length} chars)")
                    RokidSdkManager.sendAsrContent(result.text)
                    RokidSdkManager.notifyAsrEnd()
                    // Don't send to OpenClaw here - glasses stages the text
                    // and sends user_input when user confirms via Send button
                    val resultMsg = org.json.JSONObject().apply {
                        put("type", "voice_result")
                        put("result_type", "text")
                        put("text", result.text)
                    }
                    glassesManager.sendRawMessage(resultMsg.toString())
                    mainHandler.postDelayed({ RokidSdkManager.sendExitEvent() }, 1500)
                } else {
                    android.util.Log.i("MainScreen", "AI voice: no speech detected, dismissing")
                    RokidSdkManager.notifyAsrNone()
                    val idleMsg = org.json.JSONObject().apply {
                        put("type", "voice_state")
                        put("state", "idle")
                    }
                    glassesManager.sendRawMessage(idleMsg.toString())
                    mainHandler.postDelayed({ RokidSdkManager.sendExitEvent() }, 500)
                }
            }
            is VoiceCommandHandler.VoiceResult.Command -> {
                RokidSdkManager.clearCommunicationDevice()
                android.util.Log.i("MainScreen", "AI voice command received (commandLength=${result.command.length})")
                RokidSdkManager.sendAsrContent(result.command)
                RokidSdkManager.notifyAsrEnd()
                val resultMsg = org.json.JSONObject().apply {
                    put("type", "voice_result")
                    put("result_type", "command")
                    put("text", result.command)
                }
                glassesManager.sendRawMessage(resultMsg.toString())
                mainHandler.postDelayed({ RokidSdkManager.sendExitEvent() }, 1000)
            }
            is VoiceCommandHandler.VoiceResult.Error -> {
                if (!isRetry) {
                    android.util.Log.w("MainScreen", "Voice error (redacted), retrying with phone mic...")
                    RokidSdkManager.clearCommunicationDevice()
                    mainHandler.postDelayed({
                        startVoiceRecognition(voiceHandler, openClawClient, glassesManager, mainHandler, isRetry = true, languageTag = languageTag, pendingPhotos = pendingPhotos, onPhotosConsumed = onPhotosConsumed)
                    }, 200)
                } else {
                    android.util.Log.e("MainScreen", "AI voice error after retry (redacted)")
                    RokidSdkManager.clearCommunicationDevice()
                    RokidSdkManager.notifyAsrError()
                    val resultMsg = org.json.JSONObject().apply {
                        put("type", "voice_result")
                        put("result_type", "error")
                        put("text", result.message)
                    }
                    glassesManager.sendRawMessage(resultMsg.toString())
                    mainHandler.postDelayed({ RokidSdkManager.sendExitEvent() }, 2000)
                }
            }
        }
    }
}

/**
 * Build a chat_history JSON message for sending to glasses.
 * Sends full message content; the glasses transport layer chunks large JSON safely.
 *
 * @param maxMessages How many most-recent messages to include (default 20)
 * @param isLoadMore  If true, glasses will adjust scroll position instead of jumping to bottom
 * @param hasMore     Whether even older messages exist beyond what's being sent
 */
private fun buildChatHistoryJson(
    messages: List<ChatMessage>,
    maxMessages: Int = 20,
    isLoadMore: Boolean = false,
    hasMore: Boolean = true
): String {
    val recentMessages = if (messages.size > maxMessages) messages.takeLast(maxMessages) else messages

    return org.json.JSONObject().apply {
        put("type", "chat_history")
        if (isLoadMore) {
            put("isLoadMore", true)
            put("hasMore", hasMore)
        }
        val arr = org.json.JSONArray()
        for (msg in recentMessages) {
            arr.put(org.json.JSONObject().apply {
                put("id", msg.id)
                put("role", msg.role)
                put("content", msg.content)
                put("timestamp", msg.timestamp)
            })
        }
        put("messages", arr)
    }.toString()
}

private fun createThumbnailBase64(imageBytes: ByteArray, maxWidth: Int, maxHeight: Int): String {
    val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        ?: return ""
    val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, maxWidth, maxHeight, true)
    // Convert to high-contrast grayscale for the monochrome green glasses display.
    // Store luminance in alpha channel so glasses can tint it green.
    val grayscale = android.graphics.Bitmap.createBitmap(scaled.width, scaled.height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(grayscale)
    val paint = android.graphics.Paint()
    // Grayscale color matrix
    val cm = android.graphics.ColorMatrix()
    cm.setSaturation(0f)
    // Boost contrast: scale by 1.8, offset by -100
    val contrast = android.graphics.ColorMatrix(floatArrayOf(
        1.8f, 0f, 0f, 0f, -100f,
        0f, 1.8f, 0f, 0f, -100f,
        0f, 0f, 1.8f, 0f, -100f,
        0f, 0f, 0f, 1f, 0f
    ))
    cm.postConcat(contrast)
    paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
    canvas.drawBitmap(scaled, 0f, 0f, paint)
    if (scaled !== bitmap) bitmap.recycle()
    scaled.recycle()
    val stream = java.io.ByteArrayOutputStream()
    grayscale.compress(android.graphics.Bitmap.CompressFormat.WEBP, 60, stream)
    grayscale.recycle()
    return android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
}

private fun codexCliBridgeUrl(openClawHost: String): String {
    val trimmed = openClawHost.trim().ifEmpty { "10.0.2.2" }
    val hasSecureScheme = trimmed.startsWith("wss://", ignoreCase = true)
    val withoutScheme = trimmed
        .removePrefix("ws://")
        .removePrefix("wss://")
        .removePrefix("http://")
        .removePrefix("https://")
    val hostOnly = withoutScheme.substringBefore('/').substringBefore(':').ifEmpty { "10.0.2.2" }
    val scheme = if (hasSecureScheme) "wss" else "ws"
    return "$scheme://$hostOnly:18890/codex-cli"
}
