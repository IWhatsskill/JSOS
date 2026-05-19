package com.jsos.phone.codex

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Phone-side client for a private VPS Codex CLI bridge.
 *
 * The bridge is intentionally simple: JSOS Core owns the WebSocket, JSOS HUD
 * remains a terminal-style controller, and the VPS service can decide how it
 * starts /usr/local/bin/codexcli.
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

    @Volatile
    var currentState: State = State.DISCONNECTED
        private set

    var onStatus: ((State, String?) -> Unit)? = null
    var onOutput: ((String, Boolean) -> Unit)? = null

    fun connect(url: String) {
        if (currentState == State.CONNECTED || currentState == State.CONNECTING) {
            emitStatus(null)
            return
        }

        currentState = State.CONNECTING
        emitStatus(null)

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, listener)
        Log.d(TAG, "Connecting Codex CLI bridge")
    }

    fun sendInput(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val socket = webSocket
        if (socket == null || currentState != State.CONNECTED) {
            currentState = State.ERROR
            emitStatus("Bridge offline")
            return
        }

        val payload = JSONObject().apply {
            put("type", "input")
            put("text", trimmed)
        }
        if (!socket.send(payload.toString())) {
            currentState = State.ERROR
            emitStatus("Send failed")
        }
    }

    fun stop() {
        webSocket?.send(JSONObject().put("type", "stop").toString())
    }

    fun disconnect() {
        webSocket?.close(1000, "JSOS CLI disconnect")
        webSocket = null
        currentState = State.DISCONNECTED
        emitStatus(null)
    }

    fun cleanup() {
        disconnect()
        client.dispatcher.executorService.shutdown()
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            this@CodexCliBridgeClient.webSocket = webSocket
            currentState = State.CONNECTED
            emitStatus(null)
            Log.d(TAG, "Codex CLI bridge connected")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleBridgeMessage(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (this@CodexCliBridgeClient.webSocket == webSocket) {
                this@CodexCliBridgeClient.webSocket = null
                currentState = State.ERROR
                emitStatus(t.message ?: "Bridge failed")
            }
            Log.w(TAG, "Codex CLI bridge failed (${t.javaClass.simpleName})")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (this@CodexCliBridgeClient.webSocket == webSocket) {
                this@CodexCliBridgeClient.webSocket = null
                currentState = State.DISCONNECTED
                emitStatus(reason.ifBlank { null })
            }
            Log.d(TAG, "Codex CLI bridge closed: code=$code")
        }
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
                emitStatus(parsed.optString("message", "").ifBlank { null })
            }
            "error" -> {
                currentState = State.ERROR
                val message = parsed.optString("message", "Codex bridge error")
                emitStatus(message)
                onOutput?.invoke("[error] $message", true)
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

    private companion object {
        const val TAG = "CodexCliBridge"
    }
}
