package com.jsos.phone.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.jsos.phone.glasses.RokidSdkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Manages TTS audio playback using MediaPlayer.
 * New messages interrupt current playback.
 */
class TtsPlaybackManager(
    private val context: Context,
    private val client: ElevenLabsClient,
    private val settings: TtsSettingsManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val playbackLock = Any()
    private var playbackGeneration = 0
    private var synthesisJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentTempFile: File? = null

    /**
     * Speak the given text using ElevenLabs TTS.
     * Stops any current playback first.
     */
    fun speak(text: String) {
        if (!settings.isEnabled.value) {
            Log.d(TAG, "TTS disabled, skipping")
            return
        }

        val apiKey = settings.apiKey.value
        val voiceId = settings.selectedVoiceId.value

        if (apiKey.isBlank()) {
            Log.w(TAG, "No API key configured")
            return
        }

        if (voiceId == null) {
            Log.w(TAG, "No voice selected")
            return
        }

        val generation = beginNewPlayback()

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                Log.d(TAG, "Synthesizing TTS (${text.length} chars)")

                val speed = settings.speed.value.toDouble()
                val result = client.synthesize(apiKey, voiceId, text, speed)

                result.onSuccess { inputStream ->
                    if (!isCurrentGeneration(generation)) {
                        inputStream.close()
                        return@onSuccess
                    }

                    // Write to temp file for MediaPlayer
                    val tempFile = File.createTempFile("tts_", ".mp3", context.cacheDir)

                    try {
                        FileOutputStream(tempFile).use { output ->
                            inputStream.copyTo(output)
                        }
                    } finally {
                        inputStream.close()
                    }

                    if (!isCurrentGeneration(generation)) {
                        tempFile.delete()
                        return@onSuccess
                    }

                    synchronized(playbackLock) {
                        if (generation == playbackGeneration) {
                            currentTempFile = tempFile
                        }
                    }

                    Log.d(TAG, "Audio saved to temp file: ${tempFile.absolutePath}")

                    // Play on main thread
                    withContext(Dispatchers.Main) {
                        if (isCurrentGeneration(generation)) {
                            playAudioFile(tempFile, generation)
                        } else {
                            tempFile.delete()
                        }
                    }
                }.onFailure { error ->
                    if (isCurrentGeneration(generation)) {
                        Log.e(TAG, "TTS synthesis failed", error)
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "TTS synthesis cancelled")
                throw e
            } catch (e: Exception) {
                if (isCurrentGeneration(generation)) {
                    Log.e(TAG, "Error during TTS", e)
                }
            }
        }

        synchronized(playbackLock) {
            if (generation == playbackGeneration) {
                synthesisJob = job
            } else {
                job.cancel()
            }
        }
        job.start()
    }

    private fun beginNewPlayback(): Int {
        val generation = synchronized(playbackLock) {
            playbackGeneration += 1
            synthesisJob?.cancel()
            synthesisJob = null
            playbackGeneration
        }
        stopPlaybackResources()
        return generation
    }

    private fun isCurrentGeneration(generation: Int): Boolean =
        synchronized(playbackLock) { generation == playbackGeneration }

    private fun playAudioFile(file: File, generation: Int) {
        if (!isCurrentGeneration(generation)) {
            file.delete()
            return
        }

        try {
            mediaPlayer?.release()
            mediaPlayer = null

            RokidSdkManager.setCommunicationDevice()

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    Log.d(TAG, "Playback completed")
                    cleanup(generation)
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    cleanup(generation)
                    true
                }
                prepare()
            }

            if (isCurrentGeneration(generation)) {
                mediaPlayer = player
                player.start()
                Log.d(TAG, "Playback started")
            } else {
                player.release()
                file.delete()
            }
        } catch (e: Exception) {
            if (isCurrentGeneration(generation)) {
                Log.e(TAG, "Error playing audio file", e)
                cleanup(generation)
            } else {
                file.delete()
            }
        }
    }

    /**
     * Stop current playback and cleanup.
     */
    fun stop() {
        synchronized(playbackLock) {
            playbackGeneration += 1
            synthesisJob?.cancel()
            synthesisJob = null
        }
        stopPlaybackResources()
    }

    private fun stopPlaybackResources() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaPlayer", e)
        }
        mediaPlayer = null
        RokidSdkManager.clearCommunicationDevice()
        deleteTempFile()
    }

    private fun cleanup(generation: Int? = null) {
        if (generation != null && !isCurrentGeneration(generation)) return

        mediaPlayer?.release()
        mediaPlayer = null
        RokidSdkManager.clearCommunicationDevice()
        deleteTempFile()
    }

    private fun deleteTempFile() {
        currentTempFile?.let { file ->
            try {
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting temp file", e)
            }
        }
        currentTempFile = null
    }

    /**
     * Called when a chat message stream ends.
     * Speaks the message if TTS is enabled.
     */
    fun onMessageComplete(text: String) {
        if (settings.isEnabled.value && text.isNotBlank()) {
            speak(text)
        }
    }

    companion object {
        private const val TAG = "TtsPlaybackManager"
    }
}
