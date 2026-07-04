package com.jsos.phone.codex

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class CodexCliSession(
    val id: String,
    val label: String,
    val subtitle: String? = null,
    val updatedAt: Long? = null,
    val isCurrent: Boolean = false
)

/**
 * Phone-side client for a user-configured Codex CLI bridge.
 *
 * The bridge is intentionally simple: JSOS Core owns the WebSocket, JSOS HUD
 * remains a terminal-style controller, and the bridge decides how it connects
 * to Codex on the user's machine.
 */
class CodexCliBridgeClient {
    enum class State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var webSocket: WebSocket? = null

    private val connectionGeneration = AtomicLong(0)

    @Volatile
    var currentState: State = State.DISCONNECTED
        private set

    var onStatus: ((State, String?) -> Unit)? = null
    var onOutput: ((String, Boolean) -> Unit)? = null
    var onSessions: ((List<CodexCliSession>, String?) -> Unit)? = null
    var onSessionResumed: ((CodexCliSession) -> Unit)? = null

    fun connect(url: String) {
        if (currentState == State.CONNECTED || currentState == State.CONNECTING) {
            emitStatus(null)
            return
        }

        currentState = State.CONNECTING
        emitStatus(null)
        val generation = connectionGeneration.incrementAndGet()

        val request = Request.Builder().url(url).build()
        val socket = client.newWebSocket(request, createListener(generation))
        webSocket = socket
        Log.d(TAG, "Connecting Codex CLI bridge")
    }

    fun sendInput(text: String, photos: List<String> = emptyList()): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        Log.d(TAG, "sendInput requested: textChars=${trimmed.length}, photos=${photos.size}")

        val socket = webSocket
        if (socket == null || currentState != State.CONNECTED) {
            currentState = State.ERROR
            emitStatus("Bridge offline")
            Log.w(TAG, "sendInput rejected: bridge offline")
            return false
        }

        val limitedPhotos = photos.take(MAX_BRIDGE_IMAGES)
        if (photos.size > MAX_BRIDGE_IMAGES) {
            Log.w(TAG, "sendInput limited images: ${photos.size} -> $MAX_BRIDGE_IMAGES")
        }

        val bridgeImages = limitedPhotos.mapNotNull { prepareBridgeImage(it) }
        Log.d(TAG, "sendInput prepared images: ${bridgeImages.size}/${photos.size}")
        if (photos.isNotEmpty() && bridgeImages.isEmpty()) {
            currentState = State.ERROR
            emitStatus("Image encode failed")
            onOutput?.invoke("[error] Codex image encode failed", true)
            Log.w(TAG, "sendInput rejected: no valid bridge images")
            return false
        }
        val payload = JSONObject().apply {
            put("type", "input")
            put("text", trimmed)
            if (bridgeImages.isNotEmpty()) {
                put("images", JSONArray().apply {
                    bridgeImages.forEach { image ->
                        put(JSONObject().apply {
                            put("imageBase64", image.base64)
                            put("mimeType", image.mimeType)
                        })
                    }
                })
            }
        }
        val sent = socket.send(payload.toString())
        Log.d(TAG, "sendInput queued: sent=$sent, payloadChars=${payload.toString().length}, images=${bridgeImages.size}")
        if (!sent) {
            currentState = State.ERROR
            emitStatus("Send failed")
        }
        return sent
    }

    fun stop() {
        webSocket?.send(JSONObject().put("type", "stop").toString())
    }

    fun requestSessions(): Boolean =
        sendControl(JSONObject().put("type", "codex_sessions.list"))

    fun resumeSession(sessionId: String): Boolean {
        val trimmed = sessionId.trim()
        if (trimmed.isEmpty()) return false
        return sendControl(
            JSONObject()
                .put("type", "codex_session.resume")
                .put("sessionId", trimmed)
        )
    }

    fun deleteSession(sessionId: String): Boolean {
        val trimmed = sessionId.trim()
        if (trimmed.isEmpty()) return false
        return sendControl(
            JSONObject()
                .put("type", "codex_session.delete")
                .put("sessionId", trimmed)
        )
    }

    fun disconnect() {
        connectionGeneration.incrementAndGet()
        webSocket?.send(JSONObject().put("type", "stop").toString())
        webSocket?.close(1000, "JSOS CLI disconnect")
        webSocket = null
        currentState = State.DISCONNECTED
        emitStatus(null)
    }

    fun cleanup() {
        disconnect()
        client.dispatcher.executorService.shutdown()
    }

    private fun createListener(generation: Long) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!isCurrentSocket(generation, webSocket, allowUnassigned = true)) return
            this@CodexCliBridgeClient.webSocket = webSocket
            currentState = State.CONNECTED
            emitStatus(null)
            Log.d(TAG, "Codex CLI bridge connected")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!isCurrentSocket(generation, webSocket)) return
            handleBridgeMessage(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (isCurrentSocket(generation, webSocket)) {
                this@CodexCliBridgeClient.webSocket = null
                currentState = State.ERROR
                emitStatus("Codex bridge error")
            }
            Log.w(TAG, "Codex CLI bridge failed (${t.javaClass.simpleName})")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (isCurrentSocket(generation, webSocket)) {
                this@CodexCliBridgeClient.webSocket = null
                currentState = State.DISCONNECTED
                emitStatus(if (reason.isBlank()) null else "Codex bridge disconnected")
            }
            Log.d(TAG, "Codex CLI bridge closed: code=$code")
        }
    }

    private fun isCurrentSocket(
        generation: Long,
        callbackSocket: WebSocket,
        allowUnassigned: Boolean = false
    ): Boolean {
        if (generation != connectionGeneration.get()) return false
        val currentSocket = webSocket
        return currentSocket == callbackSocket || (allowUnassigned && currentSocket == null)
    }

    private fun handleBridgeMessage(raw: String) {
        val parsed = runCatching { JSONObject(raw) }.getOrNull()
        if (parsed == null) {
            onOutput?.invoke(raw, true)
            return
        }

        when (parsed.optString("type", "output")) {
            "status" -> {
                val stateText = parsed.optString("state", "")
                currentState = when (stateText.lowercase()) {
                    "connected", "online", "ready", "running" -> State.CONNECTED
                    "connecting" -> State.CONNECTING
                    "error" -> State.ERROR
                    "disconnected", "offline", "closed" -> State.DISCONNECTED
                    else -> currentState
                }
                val message = parsed.optString("message", "").ifBlank { null }
                emitStatus(message)
            }
            "error" -> {
                currentState = State.ERROR
                val message = parsed.optString("message", "Codex bridge error")
                emitStatus(message)
                onOutput?.invoke("[error] $message", true)
            }
            "codex_sessions" -> {
                val currentSessionId = parsed.optString("currentSessionId", "").ifBlank { null }
                val sessions = parseSessions(parsed.optJSONArray("sessions"), currentSessionId)
                onSessions?.invoke(sessions, currentSessionId)
            }
            "codex_resumed", "codex_session_resumed" -> {
                val currentSessionId = parsed.optString("sessionId", "").ifBlank { null }
                val session = parsed.optJSONObject("session")?.let { parseSession(it, currentSessionId) }
                    ?: currentSessionId?.let { id ->
                        CodexCliSession(
                            id = id,
                            label = parsed.optString("label", id.take(8)),
                            subtitle = parsed.optString("subtitle", "").ifBlank { null },
                            updatedAt = parsed.optionalLong("updatedAt"),
                            isCurrent = true
                        )
                    }
                if (session != null) {
                    onSessionResumed?.invoke(session)
                }
            }
            else -> {
                val output = parsed.optString("text", parsed.optString("message", ""))
                if (output.isNotBlank()) {
                    onOutput?.invoke(output, parsed.optBoolean("append", true))
                }
            }
        }
    }

    private fun emitStatus(detail: String?) {
        onStatus?.invoke(currentState, detail)
    }

    private fun sendControl(payload: JSONObject): Boolean {
        val socket = webSocket
        if (socket == null || currentState != State.CONNECTED) {
            currentState = State.ERROR
            emitStatus("Bridge offline")
            return false
        }
        val sent = socket.send(payload.toString())
        if (!sent) {
            currentState = State.ERROR
            emitStatus("Send failed")
        }
        return sent
    }

    private fun parseSessions(array: JSONArray?, currentSessionId: String?): List<CodexCliSession> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val session = array.optJSONObject(i)?.let { parseSession(it, currentSessionId) }
                if (session != null) add(session)
            }
        }
    }

    private fun parseSession(obj: JSONObject, currentSessionId: String?): CodexCliSession? {
        val id = obj.optString("id", obj.optString("sessionId", "")).trim()
        if (id.isEmpty()) return null
        return CodexCliSession(
            id = id,
            label = obj.optString("label", id.take(8)).ifBlank { id.take(8) },
            subtitle = obj.optString("subtitle", "").ifBlank { null },
            updatedAt = obj.optionalLong("updatedAt"),
            isCurrent = obj.optBoolean("isCurrent", currentSessionId == id)
        )
    }

    private fun JSONObject.optionalLong(name: String): Long? =
        if (has(name)) optLong(name, 0L).takeIf { it > 0L } else null

    private fun prepareBridgeImage(base64: String): BridgeImage? {
        val sourceBytes = runCatching {
            Base64.decode(base64, Base64.DEFAULT)
        }.getOrNull() ?: return null
        if (sourceBytes.size > MAX_BRIDGE_IMAGE_BYTES) {
            Log.w(TAG, "Codex image rejected: decoded bytes=${sourceBytes.size}")
            return null
        }

        return when {
            sourceBytes.isJpeg() -> BridgeImage(
                base64 = Base64.encodeToString(sourceBytes, Base64.NO_WRAP),
                mimeType = "image/jpeg"
            )
            sourceBytes.isPng() -> BridgeImage(
                base64 = Base64.encodeToString(sourceBytes, Base64.NO_WRAP),
                mimeType = "image/png"
            )
            else -> transcodeToJpeg(sourceBytes)
        }
    }

    private fun transcodeToJpeg(sourceBytes: ByteArray): BridgeImage? {
        val bitmap = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size) ?: return null
        return try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            val jpegBytes = stream.toByteArray()
            if (jpegBytes.size > MAX_BRIDGE_IMAGE_BYTES) {
                Log.w(TAG, "Codex transcoded image rejected: bytes=${jpegBytes.size}")
                return null
            }
            BridgeImage(
                base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP),
                mimeType = "image/jpeg"
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun ByteArray.isJpeg(): Boolean =
        size >= 3 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte() && this[2] == 0xFF.toByte()

    private fun ByteArray.isPng(): Boolean =
        size >= 8 &&
                this[0] == 0x89.toByte() &&
                this[1] == 'P'.code.toByte() &&
                this[2] == 'N'.code.toByte() &&
                this[3] == 'G'.code.toByte()

    private data class BridgeImage(
        val base64: String,
        val mimeType: String
    )

    private companion object {
        const val TAG = "CodexCliBridge"
        const val MAX_BRIDGE_IMAGES = 1
        const val MAX_BRIDGE_IMAGE_BYTES = 4 * 1024 * 1024
    }
}
