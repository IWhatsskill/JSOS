package com.jsos.phone.talk

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.google.gson.JsonObject
import com.jsos.phone.openclaw.OpenClawClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

sealed class LiveTalkState {
    object Idle : LiveTalkState()
    object Connecting : LiveTalkState()
    object Listening : LiveTalkState()
    object Speaking : LiveTalkState()
    data class Error(val message: String) : LiveTalkState()
}

data class LiveTalkTranscript(
    val role: String,
    val text: String,
    val isFinal: Boolean,
)

class OpenClawTalkManager(
    private val openClawClient: OpenClawClient,
) {
    companion object {
        private const val TAG = "OpenClawTalkManager"
        private const val SAMPLE_RATE = 24_000
        private const val SEND_FRAME_BYTES = 4096
        private const val TOOL_RESULT_TIMEOUT_MS = 120_000L
        private const val MIC_GATE_TAIL_MS = 250L
        private const val BARGE_IN_RMS_THRESHOLD = 2400.0
        private const val BARGE_IN_MIC_OPEN_MS = 1600L
        private const val SUPPRESS_ASSISTANT_AUDIO_AFTER_BARGE_IN_MS = 2000L
    }

    private val _state = kotlinx.coroutines.flow.MutableStateFlow<LiveTalkState>(LiveTalkState.Idle)
    val state: kotlinx.coroutines.flow.StateFlow<LiveTalkState> = _state

    private data class ToolRunWaiter(
        val deferred: CompletableDeferred<String>,
        val content: StringBuilder = StringBuilder(),
    )

    private var scope: CoroutineScope? = null
    private var recordingJob: Job? = null
    private var audioPlaybackJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var audioOutputQueue: Channel<ByteArray>? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private val micGateUntilMs = AtomicLong(0L)
    private val forceMicOpenUntilMs = AtomicLong(0L)
    private val suppressAssistantAudioUntilMs = AtomicLong(0L)
    private val playbackLock = Any()
    private var activeSessionId: String? = null
    private var activeRelaySessionId: String? = null
    private var activeSessionKey: String? = null
    private var onTranscript: (LiveTalkTranscript) -> Unit = {}
    private val pendingToolRuns = ConcurrentHashMap<String, ToolRunWaiter>()
    private val sessionGeneration = AtomicLong(0L)

    val isActive: Boolean
        get() = activeSessionId != null || _state.value is LiveTalkState.Connecting

    fun start(
        sessionKey: String?,
        onTranscript: (LiveTalkTranscript) -> Unit,
    ) {
        stopInternal(closeRemote = true, advanceGeneration = true)
        val generation = sessionGeneration.incrementAndGet()
        this.onTranscript = onTranscript

        val talkScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = talkScope
        _state.value = LiveTalkState.Connecting

        openClawClient.onTalkEvent = { payload -> handleTalkEvent(payload, generation) }
        openClawClient.onRawChatEvent = { payload -> handleRawChatEvent(payload, generation) }

        talkScope.launch {
            try {
                val effectiveSessionKey = sessionKey?.takeIf { it.isNotBlank() } ?: "main"
                val response = openClawClient.createTalkSession(effectiveSessionKey)
                if (!isCurrentGeneration(generation)) return@launch
                if (!response.ok) {
                    throw IllegalStateException(response.error?.get("message")?.asString ?: "talk.session.create failed")
                }

                val payload = response.payload ?: throw IllegalStateException("talk.session.create returned no payload")
                val sessionId = payload.stringValue("sessionId")
                    ?: payload.objectValue("session")?.stringValue("sessionId")
                    ?: payload.objectValue("session")?.stringValue("id")
                    ?: throw IllegalStateException("No talk session id returned")

                activeSessionId = sessionId
                activeRelaySessionId = payload.stringValue("relaySessionId")
                    ?: payload.objectValue("session")?.stringValue("relaySessionId")
                activeSessionKey = effectiveSessionKey

                startPlayback(generation)
                startAudioCapture(sessionId, generation)
                if (isCurrentGeneration(generation)) {
                    _state.value = LiveTalkState.Listening
                }
            } catch (e: Exception) {
                if (!isCurrentGeneration(generation)) return@launch
                Log.e(TAG, "Failed to start Live Talk", e)
                val errorMessage = e.message ?: "Live Talk failed"
                stopInternal(closeRemote = false)
                if (isCurrentGeneration(generation)) {
                    _state.value = LiveTalkState.Error(errorMessage)
                }
            }
        }
    }

    fun stop() {
        stopInternal(closeRemote = true, advanceGeneration = true)
    }

    private fun stopInternal(closeRemote: Boolean, advanceGeneration: Boolean = false) {
        if (advanceGeneration) {
            sessionGeneration.incrementAndGet()
        }
        val sessionId = activeSessionId
        activeSessionId = null
        activeRelaySessionId = null
        activeSessionKey = null
        micGateUntilMs.set(0L)
        forceMicOpenUntilMs.set(0L)
        suppressAssistantAudioUntilMs.set(0L)

        stopAudioCapture()
        stopPlayback()

        pendingToolRuns.values.forEach { it.deferred.completeExceptionally(Exception("Live Talk stopped")) }
        pendingToolRuns.clear()

        openClawClient.onTalkEvent = null
        openClawClient.onRawChatEvent = null

        if (closeRemote && sessionId != null) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { openClawClient.closeTalkSession(sessionId) }
            }
        }

        scope?.cancel()
        scope = null
        _state.value = LiveTalkState.Idle
    }

    private fun handleTalkEvent(payload: JsonObject, generation: Long) {
        if (!isCurrentGeneration(generation)) return
        val type = payload.stringValue("type") ?: return
        val payloadSessionId = payload.stringValue("sessionId")
        val sessionId = activeSessionId ?: return
        if (payloadSessionId != null && payloadSessionId != sessionId) return

        when (type) {
            "ready" -> _state.value = LiveTalkState.Listening
            "audio" -> {
                val audio = payload.stringValue("audioBase64") ?: payload.stringValue("audio")
                if (audio != null) {
                    if (isAssistantAudioSuppressed()) return
                    _state.value = LiveTalkState.Speaking
                    queueAudio(audio)
                }
            }
            "clear" -> clearPlayback()
            "transcript" -> {
                val role = payload.stringValue("role") ?: "assistant"
                val text = payload.stringValue("text").orEmpty()
                val isFinal = payload.boolValue("final") ?: false
                val isUserTranscript = role.equals("user", ignoreCase = true)
                if (isUserTranscript && text.isNotBlank() && shouldCancelOutputForUserTranscript()) {
                    handleBargeIn(sessionId, generation, trigger = "transcript")
                }
                if (text.isNotBlank()) {
                    onTranscript(LiveTalkTranscript(role = role, text = text, isFinal = isFinal))
                }
                if (isFinal) _state.value = LiveTalkState.Listening
            }
            "toolCall" -> handleToolCall(payload, generation)
            "error" -> {
                val message = payload.stringValue("message") ?: "Live Talk error"
                Log.e(TAG, "Talk error (redacted)")
                _state.value = LiveTalkState.Error(message)
            }
            "close" -> stopInternal(closeRemote = false)
        }
    }

    private fun handleToolCall(payload: JsonObject, generation: Long) {
        if (!isCurrentGeneration(generation)) return
        val sessionId = activeSessionId ?: return
        val callId = payload.stringValue("callId") ?: return
        val name = payload.stringValue("name") ?: return
        val args = payload.objectValue("args")
        val sessionKey = activeSessionKey
        val relaySessionId = activeRelaySessionId

        scope?.launch {
            try {
                if (!isCurrentGeneration(generation)) return@launch
                val response = openClawClient.runTalkToolCall(
                    sessionKey = sessionKey,
                    relaySessionId = relaySessionId,
                    callId = callId,
                    name = name,
                    args = args,
                )
                if (!response.ok) {
                    throw IllegalStateException(response.error?.get("message")?.asString ?: "talk.client.toolCall failed")
                }

                val runId = extractRunId(response.payload)
                val resultText = if (runId != null) {
                    waitForToolRun(runId)
                } else {
                    response.payload?.stringValue("text")
                        ?: response.payload?.stringValue("content")
                        ?: "OK"
                }
                if (!isCurrentGeneration(generation)) return@launch

                val result = JsonObject().apply {
                    addProperty("text", resultText)
                    addProperty("content", resultText)
                }
                openClawClient.submitTalkToolResult(sessionId, callId, result)
            } catch (e: Exception) {
                if (!isCurrentGeneration(generation)) return@launch
                Log.e(TAG, "Talk tool call failed", e)
                val result = JsonObject().apply {
                    addProperty("error", e.message ?: "Tool call failed")
                }
                runCatching { openClawClient.submitTalkToolResult(sessionId, callId, result) }
            }
        }
    }

    private suspend fun waitForToolRun(runId: String): String {
        val waiter = ToolRunWaiter(CompletableDeferred())
        pendingToolRuns[runId] = waiter
        return try {
            withTimeout(TOOL_RESULT_TIMEOUT_MS) {
                waiter.deferred.await()
            }
        } finally {
            pendingToolRuns.remove(runId)
        }
    }

    private fun handleRawChatEvent(payload: JsonObject, generation: Long) {
        if (!isCurrentGeneration(generation)) return
        val runId = payload.stringValue("runId") ?: return
        val waiter = pendingToolRuns[runId] ?: return
        val state = payload.stringValue("state") ?: return

        when (state) {
            "delta" -> {
                val fullText = extractTextFromChatPayload(payload)
                if (fullText.isNotBlank()) {
                    waiter.content.clear()
                    waiter.content.append(fullText)
                }
            }
            "final" -> {
                val fullText = extractTextFromChatPayload(payload).ifBlank { waiter.content.toString() }
                waiter.deferred.complete(fullText)
            }
            "aborted", "error" -> {
                val message = payload.stringValue("errorMessage") ?: "Tool run $state"
                waiter.deferred.completeExceptionally(Exception(message))
            }
        }
    }

    private fun extractRunId(payload: JsonObject?): String? {
        if (payload == null) return null
        return payload.stringValue("runId")
            ?: payload.stringValue("id")
            ?: payload.objectValue("run")?.stringValue("runId")
            ?: payload.objectValue("run")?.stringValue("id")
    }

    private fun extractTextFromChatPayload(payload: JsonObject): String {
        val message = payload.objectValue("message") ?: return ""
        val contentArray = message.getAsJsonArray("content") ?: return ""
        val sb = StringBuilder()
        for (element in contentArray) {
            val block = element.asJsonObject
            if (block.stringValue("type") == "text") {
                sb.append(block.stringValue("text").orEmpty())
            }
        }
        return sb.toString()
    }

    @Suppress("MissingPermission")
    private fun startAudioCapture(sessionId: String, generation: Long) {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            throw IllegalStateException("AudioRecord buffer unavailable")
        }

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer * 2, SEND_FRAME_BYTES * 4),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("AudioRecord init failed")
        }

        audioRecord = record
        setupAudioEffects(record.audioSessionId)
        record.startRecording()

        recordingJob = scope?.launch {
            val readBuffer = ByteArray(SEND_FRAME_BYTES)
            while (isActive && isCurrentGeneration(generation) && activeSessionId == sessionId) {
                val bytesRead = record.read(readBuffer, 0, readBuffer.size)
                if (bytesRead > 0) {
                    if (isMicGateActive() && !isForceMicOpenActive()) {
                        if (looksLikeBargeIn(readBuffer, bytesRead)) {
                            handleBargeIn(sessionId, generation)
                        } else {
                            continue
                        }
                    }
                    val audioBase64 = Base64.encodeToString(readBuffer.copyOf(bytesRead), Base64.NO_WRAP)
                    openClawClient.appendTalkAudio(sessionId, audioBase64)
                } else {
                    delay(10)
                }
            }
        }
    }

    private fun stopAudioCapture() {
        recordingJob?.cancel()
        recordingJob = null
        releaseAudioEffects()
        audioRecord?.let { record ->
            runCatching { record.stop() }
            runCatching { record.release() }
        }
        audioRecord = null
    }

    private fun startPlayback(generation: Long) {
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            throw IllegalStateException("AudioTrack buffer unavailable")
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuffer * 4)
            .build()

        synchronized(playbackLock) {
            audioTrack = track
            track.play()
        }

        val queue = Channel<ByteArray>(capacity = Channel.UNLIMITED)
        audioOutputQueue = queue
        audioPlaybackJob = scope?.launch {
            for (audio in queue) {
                if (!isCurrentGeneration(generation)) break
                writeAudio(audio)
            }
        }
    }

    private fun queueAudio(audioBase64: String) {
        val audio = try {
            Base64.decode(audioBase64, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode output audio: ${e.message}")
            return
        }
        if (audio.isNotEmpty()) {
            if (isAssistantAudioSuppressed()) return
            val result = audioOutputQueue?.trySend(audio)
            if (result?.isFailure == true) {
                Log.w(TAG, "Output audio queue rejected chunk (${audio.size} bytes)")
            }
        }
    }

    private fun writeAudio(audio: ByteArray) {
        synchronized(playbackLock) {
            val track = audioTrack ?: return
            if (track.state != AudioTrack.STATE_INITIALIZED) return
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                runCatching { track.play() }
            }
            holdMicGateFor(audio.size)
            val written = track.write(audio, 0, audio.size, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                Log.w(TAG, "AudioTrack write failed: $written")
            }
        }
    }

    private fun clearPlayback() {
        var dropped = 0
        val queue = audioOutputQueue
        while (queue?.tryReceive()?.getOrNull() != null) {
            dropped++
        }
        if (dropped > 0) {
            Log.d(TAG, "Dropped $dropped queued output chunks on clear")
        }
        synchronized(playbackLock) {
            audioTrack?.let { track ->
                runCatching {
                    track.pause()
                    track.flush()
                    track.play()
                }
            }
        }
        _state.value = LiveTalkState.Listening
    }

    private fun setupAudioEffects(audioSessionId: Int) {
        releaseAudioEffects()
        if (AcousticEchoCanceler.isAvailable()) {
            runCatching {
                AcousticEchoCanceler.create(audioSessionId)?.also { effect ->
                    effect.enabled = true
                    echoCanceler = effect
                    Log.d(TAG, "Acoustic echo cancellation enabled=${effect.enabled}")
                }
            }.onFailure {
                Log.w(TAG, "Acoustic echo cancellation unavailable: ${it.message}")
            }
        }
        if (NoiseSuppressor.isAvailable()) {
            runCatching {
                NoiseSuppressor.create(audioSessionId)?.also { effect ->
                    effect.enabled = true
                    noiseSuppressor = effect
                    Log.d(TAG, "Noise suppression enabled=${effect.enabled}")
                }
            }.onFailure {
                Log.w(TAG, "Noise suppression unavailable: ${it.message}")
            }
        }
    }

    private fun releaseAudioEffects() {
        echoCanceler?.release()
        echoCanceler = null
        noiseSuppressor?.release()
        noiseSuppressor = null
    }

    private fun holdMicGateFor(audioBytes: Int) {
        val durationMs = ((audioBytes / 2L) * 1000L / SAMPLE_RATE).coerceAtLeast(40L)
        val untilMs = SystemClock.elapsedRealtime() + durationMs + MIC_GATE_TAIL_MS
        while (true) {
            val current = micGateUntilMs.get()
            if (untilMs <= current || micGateUntilMs.compareAndSet(current, untilMs)) return
        }
    }

    private fun isMicGateActive(): Boolean =
        SystemClock.elapsedRealtime() < micGateUntilMs.get()

    private fun isForceMicOpenActive(): Boolean =
        SystemClock.elapsedRealtime() < forceMicOpenUntilMs.get()

    private fun isAssistantAudioSuppressed(): Boolean =
        SystemClock.elapsedRealtime() < suppressAssistantAudioUntilMs.get()

    private fun shouldCancelOutputForUserTranscript(): Boolean {
        if (isAssistantAudioSuppressed()) return false
        return _state.value is LiveTalkState.Speaking || isMicGateActive()
    }

    private fun handleBargeIn(sessionId: String, generation: Long, trigger: String = "audio") {
        val now = SystemClock.elapsedRealtime()
        if (now < suppressAssistantAudioUntilMs.get()) return
        Log.d(TAG, "Live Talk barge-in detected via $trigger")
        micGateUntilMs.set(0L)
        forceMicOpenUntilMs.set(now + BARGE_IN_MIC_OPEN_MS)
        suppressAssistantAudioUntilMs.set(now + SUPPRESS_ASSISTANT_AUDIO_AFTER_BARGE_IN_MS)
        clearPlayback()
        scope?.launch {
            if (!isCurrentGeneration(generation)) return@launch
            runCatching { openClawClient.cancelTalkOutput(sessionId) }
                .onFailure { Log.w(TAG, "Failed to cancel Live Talk output: ${it.message}") }
        }
    }

    private fun looksLikeBargeIn(buffer: ByteArray, bytesRead: Int): Boolean =
        calculatePcm16Rms(buffer, bytesRead) >= BARGE_IN_RMS_THRESHOLD

    private fun calculatePcm16Rms(buffer: ByteArray, bytesRead: Int): Double {
        var sumSquares = 0.0
        var samples = 0
        var i = 0
        while (i + 1 < bytesRead) {
            val sample = ((buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)).toShort().toInt()
            sumSquares += sample.toDouble() * sample.toDouble()
            samples++
            i += 2
        }
        return if (samples > 0) sqrt(sumSquares / samples) else 0.0
    }

    private fun stopPlayback() {
        audioOutputQueue?.close()
        audioOutputQueue = null
        audioPlaybackJob?.cancel()
        audioPlaybackJob = null

        synchronized(playbackLock) {
            val track = audioTrack
            audioTrack = null
            track?.let {
                runCatching { it.stop() }
                runCatching { it.flush() }
                runCatching { it.release() }
            }
        }
    }

    private fun isCurrentGeneration(generation: Long): Boolean =
        generation == sessionGeneration.get()
}

private fun JsonObject.stringValue(name: String): String? =
    if (has(name) && !get(name).isJsonNull) get(name).asString else null

private fun JsonObject.boolValue(name: String): Boolean? =
    if (has(name) && !get(name).isJsonNull) get(name).asBoolean else null

private fun JsonObject.objectValue(name: String): JsonObject? =
    if (has(name) && get(name).isJsonObject) getAsJsonObject(name) else null
