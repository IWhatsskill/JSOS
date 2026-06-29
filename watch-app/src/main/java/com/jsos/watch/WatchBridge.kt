package com.jsos.watch

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.jsos.shared.WatchCommand
import com.jsos.shared.WatchCommandAck
import com.jsos.shared.WatchCommandActions
import com.jsos.shared.WatchChatMessage
import com.jsos.shared.WatchChatSnapshot
import com.jsos.shared.WatchCodexSession
import com.jsos.shared.WatchCodexSessions
import com.jsos.shared.WatchCodexSnapshot
import com.jsos.shared.WatchCoreIds
import com.jsos.shared.WatchCoreStatus
import com.jsos.shared.WatchPaths
import com.jsos.shared.WatchPing
import com.jsos.shared.WatchPong
import com.jsos.shared.WatchTtsAudioChunk
import com.jsos.shared.WatchTtsAudioStop
import com.jsos.shared.WatchVoiceOutputRoutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class WatchUiState(
    val selectedCoreId: String = WatchCoreIds.DEFAULT,
    val coreOnline: Boolean = false,
    val hudOnline: Boolean = false,
    val gatewayOnline: Boolean = false,
    val statusLabel: String = "CORE ?",
    val detail: String = "WAIT",
    val liveTalkState: String = "IDLE",
    val ttsEnabled: Boolean = false,
    val sttEnabled: Boolean = false,
    val voiceOutputRoute: String = WatchVoiceOutputRoutes.DEFAULT,
    val watchAudioStatus: String = "AUDIO READY",
    val currentSession: String = "",
    val currentModel: String = "",
    val lastAnswer: String = "",
    val chatMessages: List<WatchChatMessage> = emptyList(),
    val chatHasMore: Boolean = true,
    val chatLoadingMore: Boolean = false,
    val chatPrependedCount: Int = 0,
    val codexSessions: List<WatchCodexSession> = emptyList(),
    val showCodexSessions: Boolean = false,
    val codexStatus: String = "DISCONNECTED",
    val codexDetail: String = "",
    val codexCurrentSessionId: String = "",
    val codexCurrentSessionLabel: String = "",
    val codexMessages: List<WatchChatMessage> = emptyList(),
    val commandPending: Boolean = false,
    val lastCommandLabel: String = "",
    val lastCommandOk: Boolean? = null,
    val lastPongAt: Long? = null,
    val lastAction: String = "",
    val lastResult: String = "",
    val lastEventAt: Long = 0L,
    val lastError: String? = null
)

class WatchBridge(
    context: Context
) : MessageClient.OnMessageReceivedListener {

    private val appContext = context.applicationContext
    private val messageClient = Wearable.getMessageClient(appContext)
    private val nodeClient = Wearable.getNodeClient(appContext)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val _state = MutableStateFlow(WatchUiState())
    val state: StateFlow<WatchUiState> = _state.asStateFlow()
    private val audioPlayer = WatchAudioPlayer(appContext) { status ->
        _state.value = _state.value.copy(watchAudioStatus = status)
    }

    fun start() {
        messageClient.addListener(this)
        pingCore()
    }

    fun stop() {
        messageClient.removeListener(this)
        audioPlayer.release()
    }

    fun selectCore(coreId: String) {
        if (coreId == _state.value.selectedCoreId) {
            pingCore()
            return
        }
        _state.value = WatchUiState(
            selectedCoreId = coreId,
            statusLabel = "CORE ?",
            detail = "SWITCH ${WatchCoreIds.label(coreId)}"
        )
        pingCore()
    }

    fun pingCore() {
        val coreId = _state.value.selectedCoreId
        _state.value = _state.value.copy(
            statusLabel = "CORE ?",
            detail = "PING ${WatchCoreIds.label(coreId)}",
            lastError = null
        )
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    _state.value = WatchUiState(
                        selectedCoreId = coreId,
                        statusLabel = "CORE OFFLINE",
                        detail = "NO PHONE",
                        lastError = "No connected phone"
                    )
                    return@addOnSuccessListener
                }
                val ping = WatchPing(id = UUID.randomUUID().toString(), coreId = coreId)
                nodes.forEach { node ->
                    messageClient.sendMessage(
                        node.id,
                        WatchPaths.PING,
                        ping.toJson().toByteArray(Charsets.UTF_8)
                    ).addOnFailureListener {
                        _state.value = WatchUiState(
                            selectedCoreId = coreId,
                            statusLabel = "CORE ERROR",
                            detail = "SEND FAILED",
                            lastError = "Ping failed"
                        )
                    }
                }
            }
            .addOnFailureListener {
                _state.value = WatchUiState(
                    selectedCoreId = coreId,
                    statusLabel = "CORE ERROR",
                    detail = "NODES FAILED",
                    lastError = "Node lookup failed"
                )
            }
    }

    fun stopSpeaking() {
        sendCommand(WatchCommandActions.STOP_SPEAKING)
    }

    fun closeHud() {
        sendCommand(WatchCommandActions.HUD_CLOSE)
    }

    fun toggleLiveTalk() {
        sendCommand(WatchCommandActions.LIVE_TALK_TOGGLE)
    }

    fun toggleTts() {
        sendCommand(WatchCommandActions.TTS_TOGGLE)
    }

    fun toggleStt() {
        sendCommand(WatchCommandActions.STT_TOGGLE)
    }

    fun nextVoiceOutput() {
        sendCommand(WatchCommandActions.VOICE_OUTPUT_NEXT)
    }

    fun requestState() {
        sendCommand(WatchCommandActions.REQUEST_STATE)
    }

    fun requestMoreChat() {
        val current = _state.value
        if (current.chatLoadingMore || !current.chatHasMore || current.chatMessages.isEmpty()) return
        _state.value = current.copy(chatLoadingMore = true)
        sendCommand(WatchCommandActions.CHAT_MORE)
    }

    fun previousSession() {
        sendCommand(WatchCommandActions.SESSION_PREVIOUS)
    }

    fun nextSession() {
        sendCommand(WatchCommandActions.SESSION_NEXT)
    }

    fun previousModel() {
        sendCommand(WatchCommandActions.MODEL_PREVIOUS)
    }

    fun nextModel() {
        sendCommand(WatchCommandActions.MODEL_NEXT)
    }

    fun requestCodexSessions() {
        _state.value = _state.value.copy(showCodexSessions = true)
        sendCommand(WatchCommandActions.CODEX_SESSIONS_REQUEST)
    }

    fun resumeCodexSession(sessionId: String) {
        sendCommand(WatchCommandActions.CODEX_RESUME, targetId = sessionId)
    }

    fun sendAssistantCommand(text: String) {
        sendCommand(WatchCommandActions.ASSISTANT_COMMAND, targetId = text.take(600))
    }

    fun sendCodexInput(text: String) {
        sendCommand(WatchCommandActions.CODEX_INPUT, targetId = text.take(1200))
    }

    fun stopCodex() {
        sendCommand(WatchCommandActions.CODEX_STOP)
    }

    fun clearCodex() {
        sendCommand(WatchCommandActions.CODEX_CLEAR)
    }

    fun closeCodexSessions() {
        _state.value = _state.value.copy(showCodexSessions = false)
    }

    private fun sendCommand(action: String, targetId: String = "") {
        val coreId = _state.value.selectedCoreId
        val command = WatchCommand(
            id = UUID.randomUUID().toString(),
            coreId = coreId,
            action = action,
            targetId = targetId
        )
        val label = actionLabel(action)
        val isChatMore = action == WatchCommandActions.CHAT_MORE
        _state.value = _state.value.copy(
            commandPending = true,
            lastCommandLabel = "$label ...",
            lastCommandOk = null,
            lastError = null
        )
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    _state.value = _state.value.copy(
                        commandPending = false,
                        lastCommandLabel = "$label ERR",
                        lastCommandOk = false,
                        statusLabel = "CORE OFFLINE",
                        detail = "NO PHONE",
                        lastError = "No connected phone",
                        chatLoadingMore = if (isChatMore) false else _state.value.chatLoadingMore
                    )
                    vibrate(ok = false)
                    return@addOnSuccessListener
                }
                nodes.forEach { node ->
                    messageClient.sendMessage(
                        node.id,
                        WatchPaths.COMMAND,
                        command.toJson().toByteArray(Charsets.UTF_8)
                    ).addOnFailureListener {
                        _state.value = _state.value.copy(
                            commandPending = false,
                            lastCommandLabel = "$label ERR",
                            lastCommandOk = false,
                            detail = "SEND FAILED",
                            lastError = "Command failed",
                            chatLoadingMore = if (isChatMore) false else _state.value.chatLoadingMore
                        )
                        vibrate(ok = false)
                    }
                }
            }
            .addOnFailureListener {
                _state.value = _state.value.copy(
                    commandPending = false,
                    lastCommandLabel = "$label ERR",
                lastCommandOk = false,
                detail = "NODES FAILED",
                lastError = "Node lookup failed",
                chatLoadingMore = if (isChatMore) false else _state.value.chatLoadingMore
            )
            vibrate(ok = false)
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WatchPaths.PONG -> {
                val pong = runCatching {
                    WatchPong.fromJson(event.data.toString(Charsets.UTF_8))
                }.getOrNull()
                if (!isSelectedCore(pong?.coreId)) return
                _state.value = _state.value.copy(
                    coreOnline = true,
                    statusLabel = "CORE ONLINE",
                    detail = pong?.coreLabel ?: WatchCoreIds.label(_state.value.selectedCoreId),
                    lastPongAt = System.currentTimeMillis(),
                    lastError = null
                )
            }
            WatchPaths.COMMAND_ACK -> {
                val ack = runCatching {
                    WatchCommandAck.fromJson(event.data.toString(Charsets.UTF_8))
                }.getOrNull()
                if (!isSelectedCore(ack?.coreId)) return
                val ok = ack?.ok == true
                val isChatMoreAck = ack?.action == WatchCommandActions.CHAT_MORE
                val ackMessage = ack?.message.orEmpty().trim()
                val chatLoadingAfterAck = isChatMoreAck && ok && ackMessage.equals("Loading chat", ignoreCase = true)
                val commandLabel = if (!ok && ackMessage.isNotBlank()) {
                    ackMessage
                } else {
                    "${actionLabel(ack?.action.orEmpty())} ${if (ok) "OK" else "ERR"}"
                }
                _state.value = _state.value.copy(
                    commandPending = false,
                    lastCommandLabel = commandLabel,
                    lastCommandOk = ok,
                    detail = ackMessage.ifBlank { "ACK" },
                    lastError = if (ok) null else ackMessage.ifBlank { "Command failed" },
                    chatLoadingMore = if (isChatMoreAck) chatLoadingAfterAck else _state.value.chatLoadingMore
                )
                vibrate(ok)
            }
            WatchPaths.CODEX_SESSIONS -> {
                val sessions = runCatching {
                    WatchCodexSessions.fromJson(event.data.toString(Charsets.UTF_8))
                }.getOrNull()
                if (!isSelectedCore(sessions?.coreId)) return
                _state.value = _state.value.copy(
                    codexSessions = sessions?.sessions.orEmpty(),
                    showCodexSessions = true,
                    lastError = null
                )
            }
            WatchPaths.CODEX_SNAPSHOT -> {
                val snapshot = runCatching {
                    WatchCodexSnapshot.fromJson(event.data.toString(Charsets.UTF_8))
                }.getOrNull()
                if (!isSelectedCore(snapshot?.coreId)) return
                _state.value = _state.value.copy(
                    codexStatus = snapshot?.status.orEmpty().ifBlank { "DISCONNECTED" },
                    codexDetail = snapshot?.detail.orEmpty(),
                    codexCurrentSessionId = snapshot?.currentSessionId.orEmpty(),
                    codexCurrentSessionLabel = snapshot?.currentSessionLabel.orEmpty(),
                    codexMessages = snapshot?.messages.orEmpty(),
                    lastError = null
                )
            }
            WatchPaths.CHAT_SNAPSHOT -> {
                val snapshot = runCatching {
                    WatchChatSnapshot.fromJson(event.data.toString(Charsets.UTF_8))
                }.getOrNull()
                if (!isSelectedCore(snapshot?.coreId)) return
                _state.value = _state.value.copy(
                    currentSession = snapshot?.currentSession?.ifBlank { _state.value.currentSession } ?: _state.value.currentSession,
                    currentModel = snapshot?.currentModel?.ifBlank { _state.value.currentModel } ?: _state.value.currentModel,
                    chatMessages = snapshot?.messages.orEmpty(),
                    chatHasMore = snapshot?.hasMore ?: _state.value.chatHasMore,
                    chatLoadingMore = false,
                    chatPrependedCount = snapshot?.prependedCount ?: 0,
                    lastAnswer = snapshot?.messages
                        ?.lastOrNull { it.role.equals("assistant", ignoreCase = true) }
                        ?.text
                        ?: _state.value.lastAnswer,
                    lastError = null
                )
            }
            WatchPaths.CORE_STATUS -> {
                val status = runCatching {
                    WatchCoreStatus.fromJson(event.data.toString(Charsets.UTF_8))
                }.getOrNull()
                if (!isSelectedCore(status?.coreId)) return
                val current = _state.value
                val statusAction = status?.lastAction.orEmpty().trim()
                val statusResult = status?.lastResult.orEmpty().trim()
                val statusError = status?.lastError.orEmpty().trim()
                val statusEventAt = status?.lastEventAt ?: 0L
                val hasNewEvent = statusEventAt > current.lastEventAt
                _state.value = current.copy(
                    coreOnline = status?.coreOnline == true,
                    hudOnline = status?.hudOnline == true,
                    gatewayOnline = status?.gatewayOnline == true,
                    statusLabel = if (status?.coreOnline == true) "CORE ONLINE" else "CORE OFFLINE",
                    detail = statusError.ifBlank { status?.liveTalkState ?: status?.coreLabel ?: "STATUS" },
                    liveTalkState = status?.liveTalkState ?: "IDLE",
                    ttsEnabled = status?.ttsEnabled == true,
                    sttEnabled = status?.sttEnabled == true,
                    voiceOutputRoute = status?.voiceOutputRoute ?: WatchVoiceOutputRoutes.DEFAULT,
                    currentSession = status?.currentSession.orEmpty(),
                    currentModel = status?.currentModel.orEmpty(),
                    lastAnswer = status?.lastAnswer.orEmpty(),
                    lastAction = statusAction.ifBlank { current.lastAction },
                    lastResult = statusResult.ifBlank { current.lastResult },
                    lastEventAt = maxOf(current.lastEventAt, statusEventAt),
                    lastCommandLabel = if (statusAction.isNotBlank() && statusResult.isNotBlank()) {
                        "$statusAction: $statusResult".take(80)
                    } else {
                        current.lastCommandLabel
                    },
                    lastCommandOk = when {
                        statusError.isNotBlank() -> false
                        hasNewEvent && statusAction.isNotBlank() -> true
                        else -> current.lastCommandOk
                    },
                    lastError = when {
                        statusError.isNotBlank() -> statusError
                        hasNewEvent -> null
                        else -> current.lastError
                    }
                )
            }
            WatchPaths.TTS_AUDIO_CHUNK -> {
                val chunk = runCatching {
                    WatchTtsAudioChunk.fromJson(event.data.toString(Charsets.UTF_8))
                }.getOrNull()
                if (!isSelectedCore(chunk?.coreId)) return
                if (chunk != null) {
                    audioPlayer.onChunk(chunk)
                }
            }
            WatchPaths.TTS_AUDIO_STOP -> {
                val stop = runCatching {
                    WatchTtsAudioStop.fromJson(event.data.toString(Charsets.UTF_8))
                }.getOrNull()
                if (!isSelectedCore(stop?.coreId)) return
                audioPlayer.stop()
            }
        }
    }

    private fun isSelectedCore(coreId: String?): Boolean =
        coreId.isNullOrBlank() || coreId == _state.value.selectedCoreId

    private fun vibrate(ok: Boolean) {
        val duration = if (ok) 35L else 120L
        vibrator?.vibrate(
            VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    private fun actionLabel(action: String): String =
        when (action) {
            WatchCommandActions.STOP_SPEAKING -> "STOP"
            WatchCommandActions.HUD_CLEAR -> "CLOSE"
            WatchCommandActions.HUD_CLOSE -> "CLOSE"
            WatchCommandActions.LIVE_TALK_TOGGLE -> "LIVE"
            WatchCommandActions.TTS_TOGGLE -> "TTS"
            WatchCommandActions.STT_TOGGLE -> "STT"
            WatchCommandActions.VOICE_OUTPUT_NEXT -> "OUT"
            WatchCommandActions.REQUEST_STATE -> "STATE"
            WatchCommandActions.CHAT_MORE -> "MORE"
            WatchCommandActions.SESSION_PREVIOUS -> "SESS-"
            WatchCommandActions.SESSION_NEXT -> "SESS+"
            WatchCommandActions.MODEL_PREVIOUS -> "MOD-"
            WatchCommandActions.MODEL_NEXT -> "MOD+"
            WatchCommandActions.CODEX_SESSIONS_REQUEST -> "CODEX"
            WatchCommandActions.CODEX_RESUME -> "RESUME"
            WatchCommandActions.CODEX_INPUT -> "CODEX"
            WatchCommandActions.CODEX_STOP -> "CSTOP"
            WatchCommandActions.CODEX_CLEAR -> "CCLR"
            WatchCommandActions.ASSISTANT_COMMAND -> "ASK"
            else -> "CMD"
        }
}
