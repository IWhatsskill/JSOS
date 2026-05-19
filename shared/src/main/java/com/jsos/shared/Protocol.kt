package com.jsos.shared

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName

/**
 * Shared protocol definitions for communication between:
 * - Phone <-> OpenClaw Gateway (WebSocket)
 * - Phone <-> Glasses (BLE/CXR)
 */

private val gson = Gson()

// ============================================
// OpenClaw Gateway Protocol
// ============================================

/**
 * Request sent from client to OpenClaw Gateway.
 */
data class OpenClawRequest(
    @SerializedName("type") val type: String = "req",
    @SerializedName("id") val id: String,
    @SerializedName("method") val method: String,
    @SerializedName("params") val params: JsonObject? = null
) {
    fun toJson(): String = gson.toJson(this)
}

/**
 * Response from OpenClaw Gateway to client.
 */
data class OpenClawResponse(
    @SerializedName("type") val type: String = "res",
    @SerializedName("id") val id: String,
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("payload") val payload: JsonObject? = null,
    @SerializedName("error") val error: JsonObject? = null
) {
    companion object {
        fun fromJson(json: String): OpenClawResponse = gson.fromJson(json, OpenClawResponse::class.java)
    }
}

/**
 * Server-pushed event from OpenClaw Gateway.
 */
data class OpenClawEvent(
    @SerializedName("type") val type: String = "event",
    @SerializedName("event") val event: String,
    @SerializedName("payload") val payload: JsonObject? = null,
    @SerializedName("seq") val seq: Long? = null,
    @SerializedName("stateVersion") val stateVersion: Long? = null
) {
    companion object {
        fun fromJson(json: String): OpenClawEvent = gson.fromJson(json, OpenClawEvent::class.java)
    }
}

/** OpenClaw Gateway methods. */
object OpenClawMethods {
    const val CONNECT = "connect"
    const val CHAT_SEND = "chat.send"
    const val CHANNEL_SEND = "channel.send"
    const val CHANNEL_LIST = "channel.list"
    const val TALK_SESSION_CREATE = "talk.session.create"
    const val TALK_SESSION_APPEND_AUDIO = "talk.session.appendAudio"
    const val TALK_SESSION_CANCEL_OUTPUT = "talk.session.cancelOutput"
    const val TALK_SESSION_CLOSE = "talk.session.close"
    const val TALK_SESSION_SUBMIT_TOOL_RESULT = "talk.session.submitToolResult"
    const val TALK_CLIENT_TOOL_CALL = "talk.client.toolCall"
    const val SESSION_CREATE = "session.create"
    const val SESSION_RESET = "sessions.reset"
    const val SESSION_LIST = "sessions.list"
    const val SESSION_RUN = "session.run"
    const val CHAT_HISTORY = "chat.history"
    const val CONFIG_GET = "config.get"
    const val SYSTEM_PRESENCE = "system-presence"
}

/** OpenClaw Gateway event names. */
object OpenClawEvents {
    const val CONNECT_CHALLENGE = "connect.challenge"
    const val AGENT = "agent"
    const val CHAT = "chat"
    const val TALK = "talk.event"
    const val PRESENCE = "presence"
    const val HEARTBEAT = "heartbeat"
}

/**
 * Parse a raw WebSocket frame into the appropriate OpenClaw message type.
 * Returns null if the frame is not valid JSON or has no recognized type.
 */
fun parseOpenClawFrame(json: String): Any? {
    return try {
        val obj = JsonParser.parseString(json).asJsonObject
        when (obj.get("type")?.asString) {
            "res" -> gson.fromJson(obj, OpenClawResponse::class.java)
            "event" -> gson.fromJson(obj, OpenClawEvent::class.java)
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

// ============================================
// Phone -> Glasses Messages
// ============================================

/**
 * A chat message to display on the glasses HUD.
 * Sent when a message is complete (user echo or finished assistant message).
 */
data class ChatMessage(
    @SerializedName("type") val type: String = "chat_message",
    @SerializedName("id") val id: String,
    @SerializedName("role") val role: String,  // "user" or "assistant"
    @SerializedName("content") val content: String,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        fun fromJson(json: String): ChatMessage = gson.fromJson(json, ChatMessage::class.java)
    }
}

/**
 * Agent has acknowledged the request but no content yet.
 * Glasses should show a thinking/processing indicator.
 */
data class AgentThinking(
    @SerializedName("type") val type: String = "agent_thinking",
    @SerializedName("id") val id: String
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        fun fromJson(json: String): AgentThinking = gson.fromJson(json, AgentThinking::class.java)
    }
}

/**
 * A streaming text chunk from the agent.
 * Glasses should append this to the message with the given id.
 */
data class ChatStream(
    @SerializedName("type") val type: String = "chat_stream",
    @SerializedName("id") val id: String,
    @SerializedName("role") val role: String = "assistant",
    @SerializedName("chunk") val chunk: String
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        fun fromJson(json: String): ChatStream = gson.fromJson(json, ChatStream::class.java)
    }
}

/**
 * Streaming is complete for the given message.
 * Glasses should remove the streaming cursor and mark the message as final.
 */
data class ChatStreamEnd(
    @SerializedName("type") val type: String = "chat_stream_end",
    @SerializedName("id") val id: String
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        fun fromJson(json: String): ChatStreamEnd = gson.fromJson(json, ChatStreamEnd::class.java)
    }
}

/**
 * OpenClaw connection state update.
 */
data class ConnectionUpdate(
    @SerializedName("type") val type: String = "connection_update",
    @SerializedName("connected") val connected: Boolean,
    @SerializedName("sessionId") val sessionId: String? = null,
    @SerializedName("sessionName") val sessionName: String? = null
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        fun fromJson(json: String): ConnectionUpdate = gson.fromJson(json, ConnectionUpdate::class.java)
    }
}

/**
 * List of available sessions from OpenClaw.
 */
data class SessionListUpdate(
    @SerializedName("type") val type: String = "session_list",
    @SerializedName("sessions") val sessions: List<SessionInfo>,
    @SerializedName("currentSessionKey") val currentSessionKey: String? = null,
    @SerializedName("unreadSessionKeys") val unreadSessionKeys: List<String> = emptyList()
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        fun fromJson(json: String): SessionListUpdate = gson.fromJson(json, SessionListUpdate::class.java)
    }
}

data class SessionInfo(
    @SerializedName("key") val key: String,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("label") val label: String? = null,
    @SerializedName("derivedTitle") val derivedTitle: String? = null,
    @SerializedName("updatedAt") val updatedAt: Long? = null,
    @SerializedName("kind") val kind: String? = null,
    @SerializedName("agentId") val agentId: String? = null,
    @SerializedName("origin") val origin: String? = null,
    @SerializedName("deliveryContext") val deliveryContext: JsonObject? = null
) {
    /** Best available display name for this session */
    val name: String get() = stableSessionDisplayName(
        key = key,
        label = label,
        displayName = displayName,
        derivedTitle = derivedTitle,
        agentId = agentId,
        origin = origin,
        deliveryContext = deliveryContext
    )
}

fun stableSessionDisplayName(
    key: String,
    label: String? = null,
    displayName: String? = null,
    derivedTitle: String? = null,
    agentId: String? = null,
    origin: String? = null,
    deliveryContext: JsonObject? = null
): String {
    val keyRoute = parseAgentSessionKey(key)
    val explicitDisplayName = displayName.cleanExplicitSessionLabel()
    val explicitLabel = label.cleanExplicitSessionLabel()
    val explicitDerivedTitle = derivedTitle.cleanExplicitSessionLabel()
    val rawDiscordLabel = displayName.rawDiscordChannelLabel()
    val keyAgentLabel = keyRoute?.agentId?.toJsosAgentLabel()

    if (keyRoute != null) {
        if (keyRoute.origin == "whatsapp") return "WhatsApp"
        if (keyRoute.agentId == "main" && keyRoute.origin == "main") return "Main"

        val routedLabel = keyAgentLabel ?: explicitDisplayName ?: explicitLabel ?: rawDiscordLabel ?: explicitDerivedTitle
        return when (keyRoute.origin) {
            "discord" -> routedLabel ?: key
            "main" -> routedLabel ?: key
            else -> routedLabel ?: key
        }
    }

    val effectiveAgentId = agentId?.takeIf { it.isNotBlank() } ?: keyRoute?.agentId
    val agentLabel = effectiveAgentId?.toJsosAgentLabel()
    if (agentLabel != null) {
        return when {
            isWhatsappSession(key, origin, displayName, deliveryContext) -> "WhatsApp"
            isDiscordSession(key, origin, displayName, deliveryContext) -> agentLabel
            isWebSession(key, origin, displayName, deliveryContext) -> agentLabel
            else -> agentLabel
        }
    }

    return when {
        key.startsWith("agent:main:whatsapp:direct:") -> "WhatsApp"
        explicitLabel != null -> explicitLabel
        explicitDisplayName != null -> explicitDisplayName
        rawDiscordLabel != null -> rawDiscordLabel
        explicitDerivedTitle != null -> explicitDerivedTitle
        else -> key
    }
}

fun shouldShowInJsosSessionPicker(session: SessionInfo): Boolean = shouldShowInJsosSessionPicker(
    key = session.key,
    displayName = session.displayName,
    label = session.label,
    derivedTitle = session.derivedTitle,
    agentId = session.agentId,
    origin = session.origin,
    deliveryContext = session.deliveryContext
)

fun shouldShowInJsosSessionPicker(
    key: String,
    displayName: String? = null,
    label: String? = null,
    derivedTitle: String? = null,
    agentId: String? = null,
    origin: String? = null,
    deliveryContext: JsonObject? = null
): Boolean {
    val keyRoute = parseAgentSessionKey(key)
    if (keyRoute?.agentId == "main" && keyRoute.origin == "main") return true
    if (keyRoute?.agentId == "main" && keyRoute.origin == "whatsapp") return true
    if (isWhatsappSession(key, origin, displayName, deliveryContext)) return true

    if (!isDiscordSession(key, origin, displayName, deliveryContext)) return false

    val name = stableSessionDisplayName(
        key = key,
        label = label,
        displayName = displayName,
        derivedTitle = derivedTitle,
        agentId = agentId,
        origin = origin,
        deliveryContext = deliveryContext
    )
    return visibleDiscordSessionNames.any { it.equals(name, ignoreCase = true) }
}

fun sessionDisplaySortKey(name: String): String {
    val trimmed = name.trim()
    val rank = when (trimmed.lowercase()) {
        "main" -> "00"
        "whatsapp" -> "01"
        "gpt-5" -> "02"
        "codex lab" -> "03"
        "coding lab" -> "04"
        "qwen" -> "05"
        "cli lab" -> "06"
        "general" -> "07"
        else -> "90-${trimmed.lowercase()}"
    }

    return "$rank|${trimmed.lowercase()}"
}

private data class AgentSessionKey(
    val agentId: String,
    val origin: String
)

private fun parseAgentSessionKey(key: String): AgentSessionKey? {
    val parts = key.split(":")
    if (parts.size < 3 || parts[0] != "agent") return null
    return AgentSessionKey(
        agentId = parts[1],
        origin = parts[2]
    )
}

private fun String.toJsosAgentLabel(): String = when (this) {
    "main" -> "Main"
    "coding-lab" -> "Coding Lab"
    "codex-lab" -> "Codex Lab"
    "codex-cli-lab" -> "CLI Lab"
    "discord-general" -> "General"
    "discord-gpt-5", "discord-gpt-5.5" -> "GPT-5"
    "discord-qwen-397b" -> "Qwen"
    else -> toReadableAgentLabel()
}

private val visibleDiscordSessionNames = setOf(
    "GPT-5",
    "Codex Lab",
    "Coding Lab",
    "Qwen",
    "CLI Lab",
    "General"
)

private fun String?.cleanExplicitSessionLabel(): String? {
    val trimmed = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (trimmed.startsWith("discord:", ignoreCase = true)) return null
    if (trimmed.startsWith("agent:", ignoreCase = true)) return null
    val withoutTransportPrefix = trimmed
        .removePrefixIgnoreCase("DC-")
        .removePrefixIgnoreCase("Web-")
    val withoutAgentSuffix = withoutTransportPrefix
        .replace(Regex("\\s*\\([^)]*\\)\\s*$"), "")
        .trim()
    return withoutAgentSuffix.takeIf { it.isNotBlank() }?.toJsosAgentLabel()
}

private fun String?.rawDiscordChannelLabel(): String? {
    val trimmed = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (!trimmed.startsWith("discord:", ignoreCase = true)) return null
    val channel = trimmed.substringAfterLast("#", missingDelimiterValue = "").trim()
    return channel.takeIf { it.isNotBlank() }?.toJsosAgentLabel()
}

private fun String.removePrefixIgnoreCase(prefix: String): String {
    return if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this
}

private fun String.toReadableAgentLabel(): String {
    val normalized = removePrefix("discord-").takeIf { it.isNotBlank() } ?: this
    return normalized
        .split('-', '_')
        .filter { it.isNotBlank() }
        .joinToString("-") { it.toReadableAgentToken() }
        .ifBlank { this }
}

private fun String.toReadableAgentToken(): String {
    return when (lowercase()) {
        "ai" -> "AI"
        "api" -> "API"
        "cli" -> "CLI"
        "codex" -> "Codex"
        "coding" -> "Coding"
        "dc" -> "DC"
        "general" -> "General"
        "gpt" -> "GPT"
        "hud" -> "HUD"
        "jarvis" -> "Main"
        "jsos" -> "JSOS"
        "lab" -> "Lab"
        "qwen" -> "Qwen"
        "stt" -> "STT"
        "tts" -> "TTS"
        "ui" -> "UI"
        "ux" -> "UX"
        else -> replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

private fun isDiscordSession(
    key: String,
    origin: String?,
    displayName: String?,
    deliveryContext: JsonObject?
): Boolean {
    return key.contains(":discord:") ||
        origin.equals("discord", ignoreCase = true) ||
        displayName?.startsWith("discord:", ignoreCase = true) == true ||
        deliveryContext.hasAny("channel", "channelId", "threadId", "accountId", "guildId")
}

private fun isWhatsappSession(
    key: String,
    origin: String?,
    displayName: String?,
    deliveryContext: JsonObject?
): Boolean {
    return key.contains(":whatsapp:") ||
        origin.equals("whatsapp", ignoreCase = true) ||
        displayName?.startsWith("whatsapp", ignoreCase = true) == true ||
        deliveryContext.hasAny("phoneNumber", "contactId")
}

private fun isWebSession(
    key: String,
    origin: String?,
    displayName: String?,
    deliveryContext: JsonObject?
): Boolean {
    return key.endsWith(":main") ||
        origin.isNullOrBlank() ||
        origin.equals("main", ignoreCase = true) ||
        origin.equals("web", ignoreCase = true) ||
        displayName.isNullOrBlank() ||
        deliveryContext == null
}

private fun JsonObject?.hasAny(vararg names: String): Boolean {
    if (this == null) return false
    return names.any { name -> has(name) && !get(name).isJsonNull }
}

// ============================================
// Glasses -> Phone Messages
// ============================================

/**
 * User input from glasses (text and optional photo).
 */
data class UserInput(
    @SerializedName("type") val type: String = "user_input",
    @SerializedName("text") val text: String,
    @SerializedName("imageBase64") val imageBase64: String? = null
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        fun fromJson(json: String): UserInput = gson.fromJson(json, UserInput::class.java)
    }
}

/**
 * Session management action from glasses.
 */
data class SessionAction(
    @SerializedName("type") val type: String,  // "list_sessions" or "switch_session"
    @SerializedName("sessionKey") val sessionKey: String? = null
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        fun fromJson(json: String): SessionAction = gson.fromJson(json, SessionAction::class.java)
    }
}

/**
 * Slash command from glasses (e.g. "/model", "/clear").
 */
data class SlashCommand(
    @SerializedName("type") val type: String = "slash_command",
    @SerializedName("command") val command: String
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        fun fromJson(json: String): SlashCommand = gson.fromJson(json, SlashCommand::class.java)
    }
}

/**
 * Request for more chat history from glasses.
 * Phone should load more history and send back a history_prepend message.
 * @param beforeMessageId The ID of the oldest currently-displayed message.
 *                        Phone uses this to know what messages glasses already have.
 */
data class RequestMoreHistory(
    @SerializedName("type") val type: String = "request_more_history",
    @SerializedName("beforeMessageId") val beforeMessageId: String? = null
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        fun fromJson(json: String): RequestMoreHistory = gson.fromJson(json, RequestMoreHistory::class.java)
    }
}

// ============================================
// Wake Signal Protocol (Phone <-> Glasses)
// ============================================

/**
 * Wake signal sent from phone to glasses to wake the display.
 * Phone sends this before sending content when glasses may be in standby.
 *
 * The wake mechanism works as follows:
 * 1. Phone detects new streaming content or spontaneous messages
 * 2. Phone sends wake_signal with reason and buffered message count
 * 3. Glasses receives via CXR bridge (which stays active even in standby)
 * 4. Glasses wakes display and sends wake_ack confirming readiness
 * 5. Phone delivers buffered messages after receiving ack
 *
 * @param reason The reason for the wake signal (stream_content, new_message, cron_message)
 * @param bufferedCount Number of messages buffered and waiting to be delivered
 * @param messageId Optional ID of the message that triggered the wake (for correlation)
 */
data class WakeSignal(
    @SerializedName("type") val type: String = "wake_signal",
    @SerializedName("reason") val reason: String,
    @SerializedName("bufferedCount") val bufferedCount: Int = 0,
    @SerializedName("messageId") val messageId: String? = null,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        const val REASON_STREAM_CONTENT = "stream_content"
        const val REASON_NEW_MESSAGE = "new_message"
        const val REASON_CRON_MESSAGE = "cron_message"

        fun fromJson(json: String): WakeSignal = gson.fromJson(json, WakeSignal::class.java)
    }
}

/**
 * Acknowledgment from glasses that it has woken and is ready to receive messages.
 * Phone should deliver buffered messages after receiving this.
 *
 * @param ready True if glasses is awake and ready, false if wake failed
 * @param timestamp When the glasses acknowledged the wake signal
 */
data class WakeAck(
    @SerializedName("type") val type: String = "wake_ack",
    @SerializedName("ready") val ready: Boolean = true,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        fun fromJson(json: String): WakeAck = gson.fromJson(json, WakeAck::class.java)
    }
}

// ============================================
// TTS State Protocol (Phone <-> Glasses)
// ============================================

/**
 * TTS toggle request from glasses to phone.
 * Glasses sends this when user toggles voice responses in the More menu.
 */
data class TtsToggle(
    @SerializedName("type") val type: String = "tts_toggle",
    @SerializedName("enabled") val enabled: Boolean
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        fun fromJson(json: String): TtsToggle = gson.fromJson(json, TtsToggle::class.java)
    }
}

/**
 * TTS state update from phone to glasses.
 * Phone sends this when TTS settings change or on connection.
 */
data class TtsState(
    @SerializedName("type") val type: String = "tts_state",
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("voiceName") val voiceName: String? = null
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        fun fromJson(json: String): TtsState = gson.fromJson(json, TtsState::class.java)
    }
}

// ============================================
// Utility
// ============================================

/**
 * Extract the "type" field from a JSON message string.
 */
fun extractMessageType(json: String): String? {
    return try {
        JsonParser.parseString(json).asJsonObject.get("type")?.asString
    } catch (e: Exception) {
        null
    }
}
