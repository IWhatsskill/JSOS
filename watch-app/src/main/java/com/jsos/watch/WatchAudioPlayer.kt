package com.jsos.watch

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import com.jsos.shared.WatchTtsAudioChunk
import java.io.File
import java.io.FileOutputStream

class WatchAudioPlayer(
    context: Context,
    private val onStatus: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private var pendingAudioId: String? = null
    private var pendingTotal: Int = 0
    private val pendingChunks = mutableMapOf<Int, String>()
    private var mediaPlayer: MediaPlayer? = null
    private var currentFile: File? = null

    fun onChunk(chunk: WatchTtsAudioChunk) {
        if (chunk.audioId != pendingAudioId) {
            pendingAudioId = chunk.audioId
            pendingTotal = chunk.total.coerceAtLeast(1)
            pendingChunks.clear()
            onStatus("AUDIO ${pendingChunks.size}/$pendingTotal")
        }

        if (chunk.sequence !in 0 until pendingTotal) return
        pendingChunks[chunk.sequence] = chunk.base64
        onStatus("AUDIO ${pendingChunks.size}/$pendingTotal")

        if (pendingChunks.size >= pendingTotal) {
            writeAndPlay()
        }
    }

    fun stop() {
        pendingAudioId = null
        pendingTotal = 0
        pendingChunks.clear()
        stopPlayback()
        onStatus("AUDIO READY")
    }

    fun release() {
        stop()
    }

    private fun writeAndPlay() {
        val audioId = pendingAudioId ?: return
        val total = pendingTotal
        val file = File.createTempFile("watch_tts_", ".mp3", appContext.cacheDir)

        try {
            FileOutputStream(file).use { output ->
                for (sequence in 0 until total) {
                    val base64 = pendingChunks[sequence] ?: throw IllegalStateException("Missing chunk $sequence")
                    output.write(Base64.decode(base64, Base64.NO_WRAP))
                }
            }
            pendingAudioId = null
            pendingTotal = 0
            pendingChunks.clear()
            play(file, audioId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to assemble watch audio", e)
            file.delete()
            stop()
            onStatus("AUDIO ERROR")
        }
    }

    private fun play(file: File, audioId: String) {
        stopPlayback()
        currentFile = file
        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    Log.d(TAG, "Watch audio completed: $audioId")
                    stopPlayback()
                    onStatus("AUDIO DONE")
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Watch audio error: what=$what extra=$extra")
                    stopPlayback()
                    onStatus("AUDIO ERROR")
                    true
                }
                prepare()
            }
            mediaPlayer = player
            player.start()
            onStatus("SPEAKING")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play watch audio", e)
            stopPlayback()
            onStatus("AUDIO ERROR")
        }
    }

    private fun stopPlayback() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop watch audio", e)
        }
        mediaPlayer = null
        currentFile?.delete()
        currentFile = null
    }

    private companion object {
        private const val TAG = "WatchAudioPlayer"
    }
}
