package com.jsos.shared

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

private val watchGson = Gson()

object WatchCoreIds {
    const val CORE = "core"
    const val DEFAULT = CORE

    fun label(coreId: String): String =
        when (coreId) {
            CORE -> "JSOS Core"
            else -> coreId.uppercase().ifBlank { "JSOS Core" }
        }
}

object WatchBridgeActions {
    const val BROKER_PACKAGE = "com.jsos.watch"
}

object WatchVoiceOutputRoutes {
    const val GLASSES = "GLASSES"
    const val PHONE = "PHONE"
    const val WATCH = "WATCH"
    const val OFF = "OFF"
    const val DEFAULT = GLASSES

    fun next(route: String): String =
        when (route) {
            GLASSES -> PHONE
            PHONE -> WATCH
            WATCH -> OFF
            else -> GLASSES
        }
}

object WatchPaths {
    const val PREFIX = "/jsos/watch"
    const val PING = "$PREFIX/ping"
    const val PONG = "$PREFIX/pong"
    const val STATE_REQUEST = "$PREFIX/state_request"
    const val CORE_STATUS = "$PREFIX/core_status"
    const val COMMAND = "$PREFIX/command"
    const val COMMAND_ACK = "$PREFIX/command_ack"
    const val CODEX_SESSIONS = "$PREFIX/codex_sessions"
    const val CODEX_SNAPSHOT = "$PREFIX/codex_snapshot"
    const val CHAT_SNAPSHOT = "$PREFIX/chat_snapshot"
    const val TTS_AUDIO_CHUNK = "$PREFIX/tts_audio_chunk"
    const val TTS_AUDIO_STOP = "$PREFIX/tts_audio_stop"
}

object WatchCommandActions {
    const val STOP_SPEAKING = "stop_speaking"
    const val HUD_CLEAR = "hud_clear"
    const val HUD_CLOSE = "hud_close"
    const val LIVE_TALK_TOGGLE = "live_talk_toggle"
    const val REQUEST_STATE = "request_state"
    const val CHAT_MORE = "chat_more"
    const val SESSION_PREVIOUS = "session_previous"
    const val SESSION_NEXT = "session_next"
    const val MODEL_PREVIOUS = "model_previous"
    const val MODEL_NEXT = "model_next"
    const val CODEX_SESSIONS_REQUEST = "codex_sessions_request"
    const val CODEX_RESUME = "codex_resume"
    const val CODEX_INPUT = "codex_input"
    const val CODEX_STOP = "codex_stop"
    const val CODEX_CLEAR = "codex_clear"
    const val ASSISTANT_COMMAND = "assistant_command"
    const val TTS_TOGGLE = "tts_toggle"
    const val STT_TOGGLE = "stt_toggle"
    const val VOICE_OUTPUT_NEXT = "voice_output_next"
}

data class WatchPing(
    @SerializedName("type") val type: String = "ping",
    @SerializedName("id") val id: String,
    @SerializedName("coreId") val coreId: String = WatchCoreIds.DEFAULT,
    @SerializedName("createdAt") val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): String = watchGson.toJson(this)

    companion object {
        fun fromJson(json: String): WatchPing = watchGson.fromJson(json, WatchPing::class.java)
    }
}

data class WatchPong(
    @SerializedName("type") val type: String = "pong",
    @SerializedName("id") val id: String,
    @SerializedName("coreId") val coreId: String = WatchCoreIds.DEFAULT,
    @SerializedName("coreLabel") val coreLabel: String = "JSOS Core",
    @SerializedName("receivedAt") val receivedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): String = watchGson.toJson(this)

    companion object {
        fun fromJson(json: String): WatchPong = watchGson.fromJson(json, WatchPong::class.java)
    }
}

data class WatchCoreStatus(
    @SerializedName("type") val type: String = "core_status",
    @SerializedName("coreId") val coreId: String = WatchCoreIds.DEFAULT,
    @SerializedName("coreLabel") val coreLabel: String = "JSOS Core",
    @SerializedName("coreOnline") val coreOnline: Boolean = true,
    @SerializedName("hudOnline") val hudOnline: Boolean = false,
    @SerializedName("gatewayOnline") val gatewayOnline: Boolean = false,
    @SerializedName("liveTalkState") val liveTalkState: String = "IDLE",
    @SerializedName("ttsEnabled") val ttsEnabled: Boolean = false,
    @SerializedName("sttEnabled") val sttEnabled: Boolean = false,
    @SerializedName("voiceOutputRoute") val voiceOutputRoute: String = WatchVoiceOutputRoutes.DEFAULT,
    @SerializedName("currentSession") val currentSession: String = "",
    @SerializedName("currentModel") val currentModel: String = "",
    @SerializedName("lastAnswer") val lastAnswer: String = "",
    @SerializedName("lastAction") val lastAction: String = "",
    @SerializedName("lastResult") val lastResult: String = "",
    @SerializedName("lastError") val lastError: String = "",
    @SerializedName("lastEventAt") val lastEventAt: Long = 0L,
    @SerializedName("updatedAt") val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): String = watchGson.toJson(this)

    companion object {
        fun fromJson(json: String): WatchCoreStatus = watchGson.fromJson(json, WatchCoreStatus::class.java)
    }
}

data class WatchCommand(
    @SerializedName("type") val type: String = "command",
    @SerializedName("id") val id: String,
    @SerializedName("coreId") val coreId: String = WatchCoreIds.DEFAULT,
    @SerializedName("action") val action: String,
    @SerializedName("targetId") val targetId: String = "",
    @SerializedName("createdAt") val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): String = watchGson.toJson(this)

    companion object {
        fun fromJson(json: String): WatchCommand = watchGson.fromJson(json, WatchCommand::class.java)
    }
}

data class WatchCommandAck(
    @SerializedName("type") val type: String = "command_ack",
    @SerializedName("id") val id: String,
    @SerializedName("coreId") val coreId: String = WatchCoreIds.DEFAULT,
    @SerializedName("action") val action: String,
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("message") val message: String = "",
    @SerializedName("handledAt") val handledAt: Long = System.currentTimeMillis()
) {
    fun toJson(): String = watchGson.toJson(this)

    companion object {
        fun fromJson(json: String): WatchCommandAck = watchGson.fromJson(json, WatchCommandAck::class.java)
    }
}

data class WatchCodexSession(
    @SerializedName("id") val id: String,
    @SerializedName("label") val label: String,
    @SerializedName("subtitle") val subtitle: String = "",
    @SerializedName("isCurrent") val isCurrent: Boolean = false,
    @SerializedName("updatedAt") val updatedAt: Long? = null
)

data class WatchCodexSessions(
    @SerializedName("type") val type: String = "codex_sessions",
    @SerializedName("coreId") val coreId: String = WatchCoreIds.DEFAULT,
    @SerializedName("sessions") val sessions: List<WatchCodexSession> = emptyList(),
    @SerializedName("currentSessionId") val currentSessionId: String = "",
    @SerializedName("updatedAt") val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): String = watchGson.toJson(this)

    companion object {
        fun fromJson(json: String): WatchCodexSessions = watchGson.fromJson(json, WatchCodexSessions::class.java)
    }
}

data class WatchChatMessage(
    @SerializedName("id") val id: String,
    @SerializedName("role") val role: String,
    @SerializedName("text") val text: String,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
)

data class WatchChatSnapshot(
    @SerializedName("type") val type: String = "chat_snapshot",
    @SerializedName("coreId") val coreId: String = WatchCoreIds.DEFAULT,
    @SerializedName("currentSession") val currentSession: String = "",
    @SerializedName("currentModel") val currentModel: String = "",
    @SerializedName("messages") val messages: List<WatchChatMessage> = emptyList(),
    @SerializedName("hasMore") val hasMore: Boolean = true,
    @SerializedName("isLoadMore") val isLoadMore: Boolean = false,
    @SerializedName("prependedCount") val prependedCount: Int = 0,
    @SerializedName("updatedAt") val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): String = watchGson.toJson(this)

    companion object {
        fun fromJson(json: String): WatchChatSnapshot = watchGson.fromJson(json, WatchChatSnapshot::class.java)
    }
}

data class WatchCodexSnapshot(
    @SerializedName("type") val type: String = "codex_snapshot",
    @SerializedName("coreId") val coreId: String = WatchCoreIds.DEFAULT,
    @SerializedName("status") val status: String = "DISCONNECTED",
    @SerializedName("detail") val detail: String = "",
    @SerializedName("currentSessionId") val currentSessionId: String = "",
    @SerializedName("currentSessionLabel") val currentSessionLabel: String = "",
    @SerializedName("messages") val messages: List<WatchChatMessage> = emptyList(),
    @SerializedName("updatedAt") val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): String = watchGson.toJson(this)

    companion object {
        fun fromJson(json: String): WatchCodexSnapshot = watchGson.fromJson(json, WatchCodexSnapshot::class.java)
    }
}

data class WatchTtsAudioChunk(
    @SerializedName("type") val type: String = "tts_audio_chunk",
    @SerializedName("coreId") val coreId: String = WatchCoreIds.DEFAULT,
    @SerializedName("audioId") val audioId: String,
    @SerializedName("sequence") val sequence: Int,
    @SerializedName("total") val total: Int,
    @SerializedName("mimeType") val mimeType: String = "audio/mpeg",
    @SerializedName("base64") val base64: String,
    @SerializedName("createdAt") val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): String = watchGson.toJson(this)

    companion object {
        fun fromJson(json: String): WatchTtsAudioChunk = watchGson.fromJson(json, WatchTtsAudioChunk::class.java)
    }
}

data class WatchTtsAudioStop(
    @SerializedName("type") val type: String = "tts_audio_stop",
    @SerializedName("coreId") val coreId: String = WatchCoreIds.DEFAULT,
    @SerializedName("createdAt") val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): String = watchGson.toJson(this)

    companion object {
        fun fromJson(json: String): WatchTtsAudioStop = watchGson.fromJson(json, WatchTtsAudioStop::class.java)
    }
}
