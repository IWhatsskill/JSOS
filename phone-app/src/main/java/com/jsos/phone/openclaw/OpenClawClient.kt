package com.jsos.phone.openclaw

import android.util.Log
import com.jsos.shared.*
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okio.ByteString
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * WebSocket client for connecting to an OpenClaw Gateway.
 * Handles the connect handshake (with Ed25519 device identity),
 * request/response correlation, event streaming, and auto-reconnect.
 */
class OpenClawClient(
    private val deviceIdentity: DeviceIdentity
) {

    companion object {
        private const val TAG = "OpenClawClient"
        private const val RECONNECT_DELAY_MS = 3000L
        private const val MIN_PROTOCOL_VERSION = 4
        private const val MAX_PROTOCOL_VERSION = 5
    }

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Authenticating : ConnectionState()
        object Connected : ConnectionState()
        data class PairingRequired(val message: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    enum class AgentActivityState {
        Ready,
        Thinking,
        Writing,
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _agentActivity = MutableStateFlow(AgentActivityState.Ready)
    val agentActivity: StateFlow<AgentActivityState> = _agentActivity.asStateFlow()

    private val _gatewayProtocol = MutableStateFlow<Int?>(null)
    val gatewayProtocol: StateFlow<Int?> = _gatewayProtocol.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _events = MutableSharedFlow<OpenClawEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<OpenClawEvent> = _events.asSharedFlow()

    // Callbacks for forwarding to glasses
    var onChatMessage: ((ChatMessage) -> Unit)? = null
    var onChatHistory: ((List<ChatMessage>) -> Unit)? = null
    var onAgentThinking: ((AgentThinking) -> Unit)? = null
    var onChatStream: ((ChatStream) -> Unit)? = null
    var onChatStreamEnd: ((ChatStreamEnd) -> Unit)? = null
    var onSessionList: ((SessionListUpdate) -> Unit)? = null
    var onModelOptions: ((ModelOptionsUpdate) -> Unit)? = null
    var onConnectionUpdate: ((ConnectionUpdate) -> Unit)? = null
    /** Fired after loadMoreHistory completes. Args: (prependedCount, hasMore) */
    var onMoreHistoryLoaded: ((Int, Boolean) -> Unit)? = null
    /** Raw chat events are used by the realtime talk bridge for tool-call replies. */
    var onRawChatEvent: ((JsonObject) -> Unit)? = null
    /** OpenClaw realtime voice relay events. */
    var onTalkEvent: ((JsonObject) -> Unit)? = null

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val requestSeq = AtomicLong(1)
    private val connectionGeneration = AtomicLong(0)
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<OpenClawResponse>>()

    // Connection params (saved for reconnect)
    private var host: String = ""
    private var port: Int = 18789
    private var token: String = ""
    private var shouldReconnect = false

    // Active agent run tracking
    private var activeRunId: String? = null
    private var activeMessageId: String? = null
    private var activeSessionKey: String? = null // session that initiated the current run
    private var streamingContent = StringBuilder()

    // Current session tracking (exposed as StateFlow for phone UI)
    private val _currentSessionKey = MutableStateFlow<String?>(null)
    val currentSessionKey: StateFlow<String?> = _currentSessionKey.asStateFlow()

    // History pagination: tracks how many messages were last requested from OpenClaw
    private var currentHistoryLimit = 50
    private val _isLoadingMoreHistory = MutableStateFlow(false)
    val isLoadingMoreHistory: StateFlow<Boolean> = _isLoadingMoreHistory.asStateFlow()

    // Sessions with unread messages (received while that session was not active)
    private val _unreadSessions = MutableStateFlow<Set<String>>(emptySet())
    val unreadSessions: StateFlow<Set<String>> = _unreadSessions.asStateFlow()

    // Available sessions (exposed as StateFlow for phone UI)
    private val _sessionList = MutableStateFlow<List<SessionInfo>>(emptyList())
    val sessionList: StateFlow<List<SessionInfo>> = _sessionList.asStateFlow()

    // Available LLM models from the gateway. Falls back to the local public list.
    private val _modelOptions = MutableStateFlow(LLM_MODEL_OPTIONS)
    val modelOptions: StateFlow<List<LlmModelOption>> = _modelOptions.asStateFlow()

    // Challenge nonce for auth handshake
    private var challengeNonce: String? = null

    fun connect(host: String, port: Int, token: String) {
        val trimmedHost = host.trim().trimEnd('/')
        this.host = trimmedHost
        this.port = port
        this.token = token
        this.shouldReconnect = true
        _gatewayProtocol.value = null
        publishModelOptions(LLM_MODEL_OPTIONS)
        val generation = connectionGeneration.incrementAndGet()

        // Bare hosts stay ws:// for local/private gateway compatibility.
        // Enter a full wss:// URL to use TLS when the gateway supports it.
        val url = GatewayUrl.webSocketUrl(trimmedHost, port)
        val originUrl = GatewayUrl.originUrl(url)

        Log.i(TAG, "Connecting to OpenClaw Gateway: $url")
        _connectionState.value = ConnectionState.Connecting

        val request = Request.Builder()
            .url(url)
            .header("Origin", originUrl)
            .build()

        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!isCurrentSocket(generation, webSocket)) return
                Log.i(TAG, "WebSocket connected to $url (HTTP ${response.code})")
                _connectionState.value = ConnectionState.Authenticating
                // Wait for connect.challenge event from server
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrentSocket(generation, webSocket)) return
                handleFrame(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (!isCurrentSocket(generation, webSocket)) return
                handleFrame(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (!isCurrentSocket(generation, webSocket)) {
                    webSocket.close(1000, null)
                    return
                }
                Log.d(TAG, "WebSocket closing: $code - $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!isCurrentSocket(generation, webSocket)) return
                Log.d(TAG, "WebSocket closed: $code - $reason")
                this@OpenClawClient.webSocket = null
                _connectionState.value = ConnectionState.Disconnected
                _agentActivity.value = AgentActivityState.Ready
                _gatewayProtocol.value = null
                notifyConnectionUpdate(false)
                if (shouldReconnect) scheduleReconnect(generation)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!isCurrentSocket(generation, webSocket)) return
                Log.e(TAG, "WebSocket failed (${t.javaClass.simpleName}, redacted)")
                this@OpenClawClient.webSocket = null
                _connectionState.value = ConnectionState.Error("Gateway connection failed")
                _agentActivity.value = AgentActivityState.Ready
                _gatewayProtocol.value = null
                notifyConnectionUpdate(false)
                failAllPending("Connection lost")
                if (shouldReconnect) scheduleReconnect(generation)
            }
        })
        webSocket = socket
    }

    fun disconnect() {
        connectionGeneration.incrementAndGet()
        shouldReconnect = false
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
        _agentActivity.value = AgentActivityState.Ready
        _gatewayProtocol.value = null
        publishModelOptions(LLM_MODEL_OPTIONS)
        notifyConnectionUpdate(false)
        failAllPending("Disconnected")
    }

    /**
     * Send a user message to OpenClaw and trigger an agent run.
     */
    fun sendMessage(text: String, images: List<String>? = null, onResult: ((Boolean) -> Unit)? = null) {
        scope.launch {
            try {
                if (activeRunId != null || activeMessageId != null) {
                    Log.w(TAG, "Ignoring send while agent run is still active")
                    onResult?.invoke(false)
                    return@launch
                }

                // Send to OpenClaw as chat.send
                val idempotencyKey = UUID.randomUUID().toString()
                val params = JsonObject().apply {
                    addProperty("sessionKey", _currentSessionKey.value ?: "main")
                    addProperty("idempotencyKey", idempotencyKey)
                    addProperty("message", text)
                    if (!images.isNullOrEmpty()) {
                        val attachments = JsonArray()
                        images.forEachIndexed { i, base64 ->
                            val mimeType = detectImageMimeType(base64)
                            val ext = if (mimeType == "image/webp") "webp" else "jpg"
                            attachments.add(JsonObject().apply {
                                addProperty("type", "image")
                                addProperty("mimeType", mimeType)
                                addProperty("fileName", "glasses-photo-${i + 1}.$ext")
                                addProperty("content", base64)
                            })
                        }
                        add("attachments", attachments)
                    }
                }

                val userMsgId = UUID.randomUUID().toString()
                val assistantMsgId = UUID.randomUUID().toString()
                activeMessageId = assistantMsgId
                activeSessionKey = _currentSessionKey.value
                streamingContent.clear()

                val response = sendRequest(OpenClawMethods.CHAT_SEND, params)
                if (response.ok) {
                    // Add the local user message only after the gateway accepts the send.
                    val userMsg = ChatMessage(
                        id = userMsgId,
                        role = "user",
                        content = text
                    )
                    addChatMessage(userMsg)
                    onChatMessage?.invoke(userMsg)

                    // Extract runId from response
                    activeRunId = response.payload?.get("runId")?.asString
                    Log.d(TAG, "Agent run started: runId=$activeRunId")
                    // Notify glasses that agent is thinking
                    _agentActivity.value = AgentActivityState.Thinking
                    onAgentThinking?.invoke(AgentThinking(id = assistantMsgId))
                    onResult?.invoke(true)
                } else {
                    Log.e(TAG, "Agent run failed (redacted)")
                    activeRunId = null
                    activeMessageId = null
                    activeSessionKey = null
                    _agentActivity.value = AgentActivityState.Ready
                    onResult?.invoke(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message (redacted)")
                activeRunId = null
                activeMessageId = null
                activeSessionKey = null
                _agentActivity.value = AgentActivityState.Ready
                onResult?.invoke(false)
            }
        }
    }

    /**
     * Request the list of available sessions from the OpenClaw gateway.
     * The server returns GatewaySessionRow objects with key/sessionKey, agentId,
     * displayName, origin, deliveryContext, derivedTitle, updatedAt, kind, etc.
     */
    fun requestSessions() {
        scope.launch {
            try {
                val params = JsonObject().apply {
                    addProperty("includeDerivedTitles", true)
                }
                val response = sendRequest(OpenClawMethods.SESSION_LIST, params)
                if (response.ok) {
                    val sessionsPayload = response.payload
                    val sessions = mutableListOf<SessionInfo>()
                    val sessionsArray = sessionsPayload?.getAsJsonArray("sessions")
                    sessionsArray?.forEach { element ->
                        val obj = element.asJsonObject
                        val key = obj.stringOrNull("key") ?: obj.stringOrNull("sessionKey") ?: ""
                        val displayName = obj.stringOrNull("displayName")
                        val label = obj.stringOrNull("label")
                        val derivedTitle = obj.stringOrNull("derivedTitle")
                        val agentId = obj.stringOrNull("agentId")
                        val origin = obj.stringOrNull("origin")
                        val deliveryContext = obj.get("deliveryContext")
                            ?.takeIf { it.isJsonObject }
                            ?.asJsonObject
                        val stableName = stableSessionDisplayName(
                            key = key,
                            label = label,
                            displayName = displayName,
                            derivedTitle = derivedTitle,
                            agentId = agentId,
                            origin = origin,
                            deliveryContext = deliveryContext
                        )
                        sessions.add(SessionInfo(
                            key = key,
                            displayName = displayName,
                            label = stableName,
                            derivedTitle = derivedTitle,
                            updatedAt = obj.longOrNull("updatedAt"),
                            kind = obj.stringOrNull("kind"),
                            agentId = agentId,
                            origin = origin,
                            deliveryContext = deliveryContext
                        ))
                    }
                    val dedupedSessions = dedupeSessions(
                        sessions = sessions,
                        currentSessionKey = _currentSessionKey.value,
                        unreadSessionKeys = _unreadSessions.value
                    )
                    _sessionList.value = dedupedSessions
                    onSessionList?.invoke(SessionListUpdate(
                        sessions = dedupedSessions,
                        currentSessionKey = _currentSessionKey.value,
                        unreadSessionKeys = _unreadSessions.value.toList()
                    ))
                } else {
                    Log.e(TAG, "Session list request failed (redacted)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting sessions (redacted)")
            }
        }
    }

    private fun dedupeSessions(
        sessions: List<SessionInfo>,
        currentSessionKey: String?,
        unreadSessionKeys: Set<String>
    ): List<SessionInfo> {
        val byName = linkedMapOf<String, SessionInfo>()
        sessions
            .filter { it.key.isNotBlank() && shouldShowInJsosSessionPicker(it) }
            .forEach { session ->
                val existing = byName[session.name]
                if (existing == null || sessionRank(session, currentSessionKey, unreadSessionKeys) > sessionRank(existing, currentSessionKey, unreadSessionKeys)) {
                    byName[session.name] = session
                }
            }
        return byName.values.sortedWith(
            compareBy<SessionInfo> { sessionDisplaySortKey(it.name) }
                .thenBy { it.name.lowercase() }
                .thenBy { it.key }
        )
    }

    private fun sessionRank(
        session: SessionInfo,
        currentSessionKey: String?,
        unreadSessionKeys: Set<String>
    ): Long {
        var rank = session.updatedAt ?: 0L
        if (session.key in unreadSessionKeys) rank += 1_000_000_000_000L
        if (session.key == currentSessionKey) rank += 2_000_000_000_000L
        return rank
    }

    /**
     * Create a new session by resetting the current session key via sessions.reset.
     * The server generates a fresh session ID while preserving settings.
     * After reset, reloads history (which will be empty) and notifies glasses.
     */
    fun createSession() {
        scope.launch {
            try {
                val key = _currentSessionKey.value ?: "main"
                Log.d(TAG, "Creating new session (resetting keyLength=${key.length})")
                val params = JsonObject().apply {
                    addProperty("key", key)
                }
                val response = sendRequest(OpenClawMethods.SESSION_RESET, params)
                if (response.ok) {
                    val newKey = response.payload?.get("key")?.asString ?: key
                    Log.i(TAG, "Session reset ok (keyLength=${newKey.length})")
                    _currentSessionKey.value = newKey
                    _chatMessages.value = emptyList()
                    notifyConnectionUpdate(true, newKey)
                    onChatHistory?.invoke(emptyList())
                } else {
                    Log.e(TAG, "Session reset failed (redacted)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating session (redacted)")
            }
        }
    }

    /**
     * Switch to a different session by key.
     */
    fun switchSession(sessionKey: String, loadHistory: Boolean = true) {
        Log.d(TAG, "Switching session (keyLength=${sessionKey.length})")
        _currentSessionKey.value = sessionKey
        _chatMessages.value = emptyList()
        currentHistoryLimit = 50
        // Clear unread flag for the session we're switching to
        _unreadSessions.value = _unreadSessions.value - sessionKey
        notifyConnectionUpdate(true, sessionKey)
        if (loadHistory) {
            loadSessionHistory(sessionKey)
        }
    }

    /**
     * Load chat history for the current (or given) session from the gateway.
     * Fetches messages via chat.history, populates local chat, and forwards to glasses.
     * Always notifies glasses (even for empty history) so they can clear stale messages.
     */
    fun loadSessionHistory(sessionKey: String? = null) {
        scope.launch {
            val key = sessionKey ?: _currentSessionKey.value ?: "main"
            try {
                val params = JsonObject().apply {
                    addProperty("sessionKey", key)
                    addProperty("limit", 50)
                }
                Log.d(TAG, "Requesting chat history (sessionKeyLength=${key.length})")
                val response = sendRequest(OpenClawMethods.CHAT_HISTORY, params)
                if (response.ok) {
                    val chatMessages = mutableListOf<ChatMessage>()
                    val messagesArray = response.payload?.getAsJsonArray("messages")
                    Log.d(TAG, "Chat history response: payload keys=${response.payload?.keySet()}, messages count=${messagesArray?.size() ?: "null"}")

                    if (messagesArray != null && messagesArray.size() > 0) {
                        for (element in messagesArray) {
                            try {
                                val msgObj = element.asJsonObject
                                val role = msgObj.get("role")?.asString ?: continue
                                // Only show user and assistant messages
                                if (role != "user" && role != "assistant") continue

                                // content can be either a string or an array of {type,text} blocks
                                val contentElement = msgObj.get("content")
                                val content: String = when {
                                    contentElement == null -> continue
                                    contentElement.isJsonPrimitive -> contentElement.asString
                                    contentElement.isJsonArray -> {
                                        val textBuilder = StringBuilder()
                                        for (block in contentElement.asJsonArray) {
                                            val blockObj = block.asJsonObject
                                            if (blockObj.get("type")?.asString == "text") {
                                                val text = blockObj.get("text")?.asString
                                                if (text != null) textBuilder.append(text)
                                            }
                                        }
                                        textBuilder.toString()
                                    }
                                    else -> continue
                                }
                                if (content.isEmpty()) continue

                                val id = UUID.randomUUID().toString()
                                val timestamp = msgObj.get("timestamp")?.asLong ?: System.currentTimeMillis()
                                chatMessages.add(ChatMessage(
                                    id = id,
                                    role = role,
                                    content = content,
                                    timestamp = timestamp
                                ))
                            } catch (e: Exception) {
                                Log.w(TAG, "Skipping unparseable history message (redacted)")
                            }
                        }
                    }

                    Log.d(TAG, "Loaded ${chatMessages.size} history messages (sessionKeyLength=${key.length})")
                    _chatMessages.value = chatMessages
                    onChatHistory?.invoke(chatMessages)
                } else {
                    Log.e(TAG, "Chat history request failed (redacted)")
                    // Still notify with empty list so glasses clear stale messages
                    _chatMessages.value = emptyList()
                    onChatHistory?.invoke(emptyList())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading session history (sessionKeyLength=${key.length}, redacted)")
                // Still notify with empty list so glasses clear stale messages
                _chatMessages.value = emptyList()
                onChatHistory?.invoke(emptyList())
            }
        }
    }

    /**
     * Load more chat history beyond what's currently cached.
     *
     * OpenClaw's chat.history doesn't support cursor pagination — it returns
     * the most recent N messages. So we increase N and re-fetch, then prepend
     * only the newly-discovered older messages to the existing list (keeping
     * existing message IDs stable).
     */
    fun loadMoreHistory() {
        if (_isLoadingMoreHistory.value) return
        _isLoadingMoreHistory.value = true

        scope.launch {
            val key = _currentSessionKey.value ?: "main"
            val existingMessages = _chatMessages.value
            val oldCount = existingMessages.size

            try {
                // Bump the limit by 50, cap at 500 (OpenClaw hard max is 1000)
                currentHistoryLimit = (currentHistoryLimit + 50).coerceAtMost(500)
                val params = JsonObject().apply {
                    addProperty("sessionKey", key)
                    addProperty("limit", currentHistoryLimit)
                }
                Log.d(TAG, "Requesting more history (sessionKeyLength=${key.length}, limit=$currentHistoryLimit, existing=$oldCount)")
                val response = sendRequest(OpenClawMethods.CHAT_HISTORY, params)

                if (response.ok) {
                    val rawMessages = mutableListOf<ChatMessage>()
                    val messagesArray = response.payload?.getAsJsonArray("messages")
                    // Track total raw count (including system messages) for hasMore check
                    val totalReturnedByGateway = messagesArray?.size() ?: 0

                    if (messagesArray != null && messagesArray.size() > 0) {
                        for (element in messagesArray) {
                            try {
                                val msgObj = element.asJsonObject
                                val role = msgObj.get("role")?.asString ?: continue
                                if (role != "user" && role != "assistant") continue

                                val contentElement = msgObj.get("content")
                                val content: String = when {
                                    contentElement == null -> continue
                                    contentElement.isJsonPrimitive -> contentElement.asString
                                    contentElement.isJsonArray -> {
                                        val textBuilder = StringBuilder()
                                        for (block in contentElement.asJsonArray) {
                                            val blockObj = block.asJsonObject
                                            if (blockObj.get("type")?.asString == "text") {
                                                val text = blockObj.get("text")?.asString
                                                if (text != null) textBuilder.append(text)
                                            }
                                        }
                                        textBuilder.toString()
                                    }
                                    else -> continue
                                }
                                if (content.isEmpty()) continue

                                val timestamp = msgObj.get("timestamp")?.asLong ?: System.currentTimeMillis()
                                rawMessages.add(ChatMessage(
                                    id = "",  // placeholder, assigned below
                                    role = role,
                                    content = content,
                                    timestamp = timestamp
                                ))
                            } catch (e: Exception) {
                                Log.w(TAG, "Skipping unparseable history message (redacted)")
                            }
                        }
                    }

                    // The tail of rawMessages corresponds to our existing messages.
                    // Reuse their IDs; only assign new IDs for the older prefix.
                    val newOlderCount = (rawMessages.size - oldCount).coerceAtLeast(0)
                    val olderMessages = rawMessages.take(newOlderCount).map {
                        it.copy(id = UUID.randomUUID().toString())
                    }

                    // Combined: new older messages + existing (with stable IDs)
                    val combined = olderMessages + existingMessages
                    _chatMessages.value = combined

                    // Did we get everything the gateway has?
                    // Use the raw count (including system messages) vs the limit we sent,
                    // NOT the filtered user/assistant count which is always smaller.
                    val hasMore = totalReturnedByGateway >= currentHistoryLimit

                    Log.d(TAG, "Prepended $newOlderCount older messages (total=${combined.size}, hasMore=$hasMore)")
                    _isLoadingMoreHistory.value = false
                    onMoreHistoryLoaded?.invoke(newOlderCount, hasMore)
                } else {
                    Log.e(TAG, "More history request failed (redacted)")
                    _isLoadingMoreHistory.value = false
                    onMoreHistoryLoaded?.invoke(0, false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading more history (sessionKeyLength=${key.length}, redacted)")
                _isLoadingMoreHistory.value = false
                onMoreHistoryLoaded?.invoke(0, false)
            }
        }
    }

    /**
     * Send a slash command (e.g., "/model", "/clear").
     */
    fun sendSlashCommand(command: String) {
        // Slash commands are just user messages starting with /
        sendMessage(command)
    }

    fun requestModels(view: String = "default") {
        scope.launch {
            try {
                val response = sendRequest(
                    OpenClawMethods.MODELS_LIST,
                    JsonObject().apply { addProperty("view", view) }
                )
                if (!response.ok) {
                    Log.w(TAG, "Model list request failed")
                    return@launch
                }

                val options = parseGatewayModelOptions(response.payload)
                if (options.isEmpty()) {
                    Log.w(TAG, "Gateway returned no usable models; keeping fallback list")
                    publishModelOptions(LLM_MODEL_OPTIONS)
                } else {
                    publishModelOptions(options)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to load gateway model list")
            }
        }
    }

    suspend fun createTalkSession(sessionKey: String?): OpenClawResponse {
        val params = JsonObject().apply {
            if (!sessionKey.isNullOrBlank()) addProperty("sessionKey", sessionKey)
            addProperty("provider", "openai")
            addProperty("mode", "realtime")
            addProperty("transport", "gateway-relay")
            addProperty("brain", "agent-consult")
        }
        return sendRequest(OpenClawMethods.TALK_SESSION_CREATE, params)
    }

    fun appendTalkAudio(sessionId: String, audioBase64: String, timestamp: Long = System.currentTimeMillis()) {
        val params = JsonObject().apply {
            addProperty("sessionId", sessionId)
            addProperty("audioBase64", audioBase64)
            addProperty("timestamp", timestamp)
        }
        sendRequestWithoutWaiting(OpenClawMethods.TALK_SESSION_APPEND_AUDIO, params)
    }

    suspend fun cancelTalkOutput(sessionId: String, reason: String = "barge-in"): OpenClawResponse {
        val params = JsonObject().apply {
            addProperty("sessionId", sessionId)
            addProperty("reason", reason)
        }
        return sendRequest(OpenClawMethods.TALK_SESSION_CANCEL_OUTPUT, params)
    }

    suspend fun closeTalkSession(sessionId: String): OpenClawResponse {
        val params = JsonObject().apply {
            addProperty("sessionId", sessionId)
        }
        return sendRequest(OpenClawMethods.TALK_SESSION_CLOSE, params)
    }

    suspend fun submitTalkToolResult(sessionId: String, callId: String, result: JsonObject): OpenClawResponse {
        val params = JsonObject().apply {
            addProperty("sessionId", sessionId)
            addProperty("callId", callId)
            add("result", result)
        }
        return sendRequest(OpenClawMethods.TALK_SESSION_SUBMIT_TOOL_RESULT, params)
    }

    suspend fun runTalkToolCall(
        sessionKey: String?,
        relaySessionId: String?,
        callId: String,
        name: String,
        args: JsonObject?
    ): OpenClawResponse {
        val params = JsonObject().apply {
            if (!sessionKey.isNullOrBlank()) addProperty("sessionKey", sessionKey)
            if (!relaySessionId.isNullOrBlank()) addProperty("relaySessionId", relaySessionId)
            addProperty("callId", callId)
            addProperty("name", name)
            add("args", args ?: JsonObject())
        }
        return sendRequest(OpenClawMethods.TALK_CLIENT_TOOL_CALL, params)
    }

    fun cleanup() {
        shouldReconnect = false
        scope.cancel()
        disconnect()
    }

    // ============== Internal methods ==============

    private suspend fun sendRequest(
        method: String,
        params: JsonObject? = null
    ): OpenClawResponse {
        val id = "${method}-${requestSeq.getAndIncrement()}"
        val request = OpenClawRequest(id = id, method = method, params = params)
        val deferred = CompletableDeferred<OpenClawResponse>()
        pendingRequests[id] = deferred

        val json = request.toJson()
        Log.d(TAG, "Sending request: method=$method id=$id params=${params != null}")

        try {
            val socket = webSocket ?: throw IllegalStateException("Not connected")
            if (!socket.send(json)) {
                throw IllegalStateException("Failed to send request")
            }

            return withTimeout(30_000) {
                deferred.await()
            }
        } finally {
            pendingRequests.remove(id)
        }
    }

    private fun sendRequestWithoutWaiting(method: String, params: JsonObject? = null) {
        val id = "${method}-${requestSeq.getAndIncrement()}"
        val request = OpenClawRequest(id = id, method = method, params = params)
        val json = request.toJson()
        Log.v(TAG, "Sending async request: method=$method id=$id")
        webSocket?.send(json) ?: Log.w(TAG, "Cannot send $method: not connected")
    }

    private fun handleFrame(json: String) {
        try {
            val obj = JsonParser.parseString(json).asJsonObject
            when (obj.get("type")?.asString) {
                "res" -> handleResponse(obj)
                "event" -> handleEvent(obj)
                else -> Log.w(TAG, "Unknown frame type")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing frame (redacted)")
        }
    }

    private fun handleResponse(obj: JsonObject) {
        val id = obj.get("id")?.asString ?: return
        val ok = obj.get("ok")?.asBoolean ?: false
        val payload = obj.getAsJsonObject("payload")
        val error = obj.getAsJsonObject("error")

        val response = OpenClawResponse(id = id, ok = ok, payload = payload, error = error)

        // Complete the pending request
        val deferred = pendingRequests.remove(id)
        if (deferred != null) {
            deferred.complete(response)
        } else {
            if (!id.startsWith("${OpenClawMethods.TALK_SESSION_APPEND_AUDIO}-")) {
                Log.d(TAG, "No pending request for id=$id (may be agent completion)")
            }
        }
    }

    private fun handleEvent(obj: JsonObject) {
        val eventName = obj.get("event")?.asString ?: return
        val payload = obj.getAsJsonObject("payload")
        val event = OpenClawEvent(event = eventName, payload = payload)

        Log.d(TAG, "Received event: $eventName")

        when (eventName) {
            OpenClawEvents.CONNECT_CHALLENGE -> {
                challengeNonce = payload?.get("nonce")?.asString
                Log.d(TAG, "Received connect challenge")
                performAuth()
            }
            OpenClawEvents.CHAT -> {
                onRawChatEvent?.invoke(payload)
                handleChatEvent(payload)
            }
            OpenClawEvents.TALK -> {
                onTalkEvent?.invoke(payload)
            }
            OpenClawEvents.AGENT -> {
                // Lower-level agent events (tool use, lifecycle)
                Log.d(TAG, "Agent event received")
            }
            "tick", OpenClawEvents.HEARTBEAT -> {
                // Keep-alive, no action needed
            }
            else -> {
                Log.d(TAG, "Unhandled event: $eventName")
            }
        }

        // Emit to shared flow for external observers
        scope.launch { _events.emit(event) }
    }

    private fun performAuth() {
        val nonce = challengeNonce
        if (nonce == null) {
            Log.e(TAG, "No challenge nonce available for auth")
            _connectionState.value = ConnectionState.Error("No challenge nonce")
            return
        }

        scope.launch {
            try {
                val params = JsonObject().apply {
                    addProperty("minProtocol", MIN_PROTOCOL_VERSION)
                    addProperty("maxProtocol", MAX_PROTOCOL_VERSION)

                    add("client", JsonObject().apply {
                        addProperty("id", "openclaw-control-ui")
                        addProperty("version", "1.0.0")
                        addProperty("platform", "android")
                        addProperty("mode", "ui")
                    })

                    addProperty("role", "operator")
                    add("scopes", JsonArray().apply {
                        add("operator.admin")
                        add("operator.read")
                        add("operator.write")
                    })

                    add("auth", JsonObject().apply {
                        addProperty("token", token)
                    })

                    // Device identity for pairing
                    val signedAtMs = System.currentTimeMillis()
                    val scopesList = listOf("operator.admin", "operator.read", "operator.write")
                    add("device", JsonObject().apply {
                        addProperty("id", deviceIdentity.deviceId)
                        addProperty("publicKey", deviceIdentity.publicKeyBase64Url)
                        addProperty("signature", deviceIdentity.signAuthPayload(
                            clientId = "openclaw-control-ui",
                            clientMode = "ui",
                            role = "operator",
                            scopes = scopesList,
                            signedAtMs = signedAtMs,
                            token = token,
                            nonce = nonce
                        ))
                        addProperty("signedAt", signedAtMs)
                        addProperty("nonce", nonce)
                        val savedToken = deviceIdentity.deviceToken
                        if (savedToken != null) {
                            addProperty("deviceToken", savedToken)
                        }
                    })

                    addProperty("locale", "de-DE")
                    addProperty("userAgent", "jsos-android/1.0.0")
                }

                Log.d(TAG, "Sending connect...")
                val response = sendRequest(OpenClawMethods.CONNECT, params)
                if (response.ok) {
                    Log.i(TAG, "Authentication successful!")

                    val negotiatedProtocol = response.payload
                        ?.get("protocol")
                        ?.let { runCatching { it.asInt }.getOrNull() }
                    _gatewayProtocol.value = negotiatedProtocol
                    Log.i(TAG, "OpenClaw protocol negotiated: ${negotiatedProtocol ?: "unknown"}")

                    // Persist deviceToken if returned (from pairing approval)
                    val dt = response.payload?.get("deviceToken")?.asString
                    if (dt != null) {
                        deviceIdentity.deviceToken = dt
                        Log.d(TAG, "Persisted deviceToken")
                    }

                    // Extract the default session key from the hello-ok snapshot
                    val snapshot = response.payload?.getAsJsonObject("snapshot")
                    val sessionDefaults = snapshot?.getAsJsonObject("sessionDefaults")
                    val mainSessionKey = sessionDefaults?.get("mainSessionKey")?.asString
                    if (mainSessionKey != null) {
                        _currentSessionKey.value = mainSessionKey
                        Log.d(TAG, "Default session key from gateway (redacted, length=${mainSessionKey.length})")
                    } else {
                        Log.w(TAG, "No mainSessionKey in connect response, snapshot keys=${snapshot?.keySet()}")
                    }

                    _connectionState.value = ConnectionState.Connected
                    notifyConnectionUpdate(true, _currentSessionKey.value)

                    // Load history for the current session on connect
                    loadSessionHistory()
                    requestModels()
                } else {
                    val errorMsg = response.error?.get("message")?.asString ?: "Authentication failed"
                    val errorCode = response.error?.get("code")?.asString ?: ""
                    Log.e(TAG, "Authentication failed (code=$errorCode, message redacted)")

                    if (errorCode == "pairing_required" || errorMsg.contains("pair", ignoreCase = true)) {
                        _connectionState.value = ConnectionState.PairingRequired("Pairing required")
                        // Keep reconnecting — user needs to approve on gateway
                    } else {
                        _connectionState.value = ConnectionState.Error("Authentication failed")
                        shouldReconnect = false
                    }
                    webSocket?.close(1000, "Auth failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auth error (redacted)")
                _connectionState.value = ConnectionState.Error("Authentication failed")
            }
        }
    }

    /**
     * Handle a "chat" event from the gateway.
     * Payload structure:
     *   { runId, sessionKey, seq, state: "delta"|"final"|"aborted"|"error",
     *     message: { role, content: [{type:"text", text:"..."}] } }
     */
    private fun handleChatEvent(payload: JsonObject?) {
        payload ?: return
        val state = payload.get("state")?.asString ?: return
        val runId = payload.get("runId")?.asString
        val eventSessionKey = payload.get("sessionKey")?.asString

        // Check if this event belongs to a different session than the currently active one.
        // If so, mark that session as having unread messages and don't render into the current view.
        val currentKey = _currentSessionKey.value
        if (eventSessionKey != null && currentKey != null && eventSessionKey != currentKey) {
            Log.d(TAG, "Chat event for inactive session (eventKeyLength=${eventSessionKey.length}, activeKeyLength=${currentKey.length}, state=$state) - marking unread")
            _unreadSessions.value = _unreadSessions.value + eventSessionKey
            // Still need to clean up our streaming state if this was our active run
            // (user switched sessions mid-stream)
            if (runId != null && runId == activeRunId) {
                if (state == "final" || state == "aborted" || state == "error") {
                    Log.d(TAG, "Clearing stale active run $activeRunId for inactive session")
                    activeRunId = null
                    activeMessageId = null
                    activeSessionKey = null
                    _agentActivity.value = AgentActivityState.Ready
                    streamingContent.clear()
                }
            }
            return
        }

        // Only process events for our active run
        if (runId != null && activeRunId != null && runId != activeRunId) return

        val msgId = activeMessageId ?: return

        when (state) {
            "delta" -> {
                // Each delta contains the full accumulated text, not just the new chunk.
                // Diff against what we already have to extract only the new portion.
                val fullText = extractTextFromMessage(payload)
                val previous = streamingContent.toString()
                if (fullText.length > previous.length) {
                    val newChunk = fullText.substring(previous.length)
                    streamingContent.clear()
                    streamingContent.append(fullText)
                    _agentActivity.value = AgentActivityState.Writing
                    onChatStream?.invoke(ChatStream(id = msgId, chunk = newChunk))
                    // Update phone UI with streaming text
                    updateStreamingMessage(msgId, fullText)
                }
            }
            "final" -> {
                val fullText = extractTextFromMessage(payload)
                val previous = streamingContent.toString()
                if (fullText.isNotEmpty() && fullText.length > previous.length) {
                    val newChunk = fullText.substring(previous.length)
                    _agentActivity.value = AgentActivityState.Writing
                    onChatStream?.invoke(ChatStream(id = msgId, chunk = newChunk))
                }
                // Use the final full text if available, otherwise keep what we accumulated
                if (fullText.isNotEmpty()) {
                    streamingContent.clear()
                    streamingContent.append(fullText)
                }
                finalizeStreaming()
            }
            "aborted", "error" -> {
                Log.e(TAG, "Chat run $state (redacted)")
                finalizeStreaming()
            }
        }
    }

    /**
     * Extract text from a chat event message payload.
     * message.content is an array of {type:"text", text:"..."} blocks.
     */
    private fun extractTextFromMessage(payload: JsonObject): String {
        val message = payload.getAsJsonObject("message") ?: return ""
        val contentArray = message.getAsJsonArray("content") ?: return ""
        val sb = StringBuilder()
        for (element in contentArray) {
            val block = element.asJsonObject
            if (block.get("type")?.asString == "text") {
                val text = block.get("text")?.asString
                if (text != null) sb.append(text)
            }
        }
        return sb.toString()
    }

    private fun finalizeStreaming() {
        val msgId = activeMessageId ?: run {
            _agentActivity.value = AgentActivityState.Ready
            return
        }
        val content = streamingContent.toString()

        if (content.isNotEmpty()) {
            val assistantMsg = ChatMessage(
                id = msgId,
                role = "assistant",
                content = content
            )
            // Update in place if already in the list (from streaming), otherwise add
            updateOrAddChatMessage(assistantMsg)
            // Send the complete finalized message to glasses so they have the full
            // content even if any streaming chunks were missed
            onChatMessage?.invoke(assistantMsg)
        }

        onChatStreamEnd?.invoke(ChatStreamEnd(id = msgId))

        activeRunId = null
        activeMessageId = null
        activeSessionKey = null
        _agentActivity.value = AgentActivityState.Ready
        streamingContent.clear()
    }

    private fun addChatMessage(message: ChatMessage) {
        val current = _chatMessages.value.toMutableList()
        current.add(message)
        _chatMessages.value = current
    }

    /** Update existing message by id or add if not found */
    private fun updateOrAddChatMessage(message: ChatMessage) {
        val current = _chatMessages.value.toMutableList()
        val index = current.indexOfFirst { it.id == message.id }
        if (index >= 0) {
            current[index] = message
        } else {
            current.add(message)
        }
        _chatMessages.value = current
    }

    /** Update or insert a streaming assistant message in the chat list */
    private fun updateStreamingMessage(msgId: String, fullText: String) {
        val current = _chatMessages.value.toMutableList()
        val index = current.indexOfFirst { it.id == msgId }
        val msg = ChatMessage(id = msgId, role = "assistant", content = fullText)
        if (index >= 0) {
            current[index] = msg
        } else {
            current.add(msg)
        }
        _chatMessages.value = current
    }

    private fun notifyConnectionUpdate(connected: Boolean, sessionId: String? = null) {
        val sessionName = sessionId?.let { id ->
            _sessionList.value.firstOrNull { it.key == id }?.name
                ?: stableSessionDisplayName(id)
        }
        onConnectionUpdate?.invoke(ConnectionUpdate(
            connected = connected,
            sessionId = sessionId,
            sessionName = sessionName
        ))
    }

    private fun publishModelOptions(options: List<LlmModelOption>) {
        val next = options.ifEmpty { LLM_MODEL_OPTIONS }
        _modelOptions.value = next
        onModelOptions?.invoke(ModelOptionsUpdate(options = next))
    }

    private fun isCurrentSocket(generation: Long, callbackSocket: WebSocket): Boolean {
        return generation == connectionGeneration.get() && webSocket == callbackSocket
    }

    private fun scheduleReconnect(generation: Long) {
        scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (!shouldReconnect || generation != connectionGeneration.get()) return@launch
            val state = _connectionState.value
            if (state is ConnectionState.Disconnected || state is ConnectionState.Error || state is ConnectionState.PairingRequired) {
                connect(host, port, token)
            }
        }
    }

    private fun failAllPending(reason: String) {
        pendingRequests.forEach { (id, deferred) ->
            deferred.completeExceptionally(Exception(reason))
        }
        pendingRequests.clear()
    }

    /** Detect image MIME type from base64 magic bytes. */
    private fun detectImageMimeType(base64: String): String {
        // Decode just enough bytes to check the magic header
        val prefix = base64.take(16)
        return try {
            val bytes = android.util.Base64.decode(prefix, android.util.Base64.DEFAULT)
            when {
                bytes.size >= 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
                bytes.size >= 4 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() -> "image/webp"
                bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "image/png"
                else -> "image/webp" // Default for CXR SDK photos
            }
        } catch (e: Exception) {
            "image/webp"
        }
    }
}

private fun parseGatewayModelOptions(payload: JsonObject?): List<LlmModelOption> {
    val models = payload?.getAsJsonArray("models") ?: return emptyList()
    val seen = linkedSetOf<String>()
    val options = mutableListOf<LlmModelOption>()

    for (element in models) {
        val model = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
        val provider = model.stringOrNull("provider") ?: continue
        val id = model.stringOrNull("id") ?: continue
        val available = model.get("available")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
            ?.asBoolean
            ?: true
        if (!available) continue

        val ref = "$provider/$id"
        if (!seen.add(ref)) continue

        val name = model.stringOrNull("name")
        options += LlmModelOption(
            label = name ?: id,
            command = "/model $ref",
            description = modelSourceLabel(provider, id)
        )
    }

    return options
}

private fun modelSourceLabel(provider: String, id: String): String {
    val source = when {
        id.endsWith(":cloud", ignoreCase = true) -> "cloud"
        id.endsWith("-cloud", ignoreCase = true) -> "cloud"
        else -> ""
    }
    return listOf(provider, source)
        .filter { it.isNotBlank() }
        .joinToString(" / ")
        .ifBlank { provider }
}

private fun JsonObject.stringOrNull(name: String): String? {
    val value = get(name) ?: return null
    if (value.isJsonNull) return null
    return runCatching { value.asString }.getOrNull()?.takeIf { it.isNotBlank() }
}

private fun JsonObject.longOrNull(name: String): Long? {
    val value = get(name) ?: return null
    if (value.isJsonNull) return null
    return runCatching { value.asLong }.getOrNull()
}
