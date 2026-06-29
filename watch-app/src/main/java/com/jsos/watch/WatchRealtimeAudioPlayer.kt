package com.jsos.watch

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import com.jsos.shared.WatchRealtimeAudioChunk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class WatchRealtimeAudioPlayer(
    private val onStatus: (String) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val playbackLock = Any()
    private var audioTrack: AudioTrack? = null
    private var audioQueue: Channel<ByteArray>? = null
    private var playbackJob: Job? = null
    private var sampleRate: Int = SAMPLE_RATE
    private var lastSequence: Long = -1L

    fun onChunk(chunk: WatchRealtimeAudioChunk) {
        if (chunk.encoding != "pcm16" || chunk.channelCount != 1) {
            onStatus("LIVE AUDIO ERR")
            return
        }
        if (chunk.sequence <= lastSequence) return
        lastSequence = chunk.sequence

        val audio = try {
            Base64.decode(chunk.base64, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode realtime audio")
            onStatus("LIVE AUDIO ERR")
            return
        }
        if (audio.isEmpty()) return

        val targetSampleRate = chunk.sampleRate.takeIf { it > 0 } ?: SAMPLE_RATE
        ensureStarted(targetSampleRate)
        val result = audioQueue?.trySend(audio)
        if (result?.isFailure == true) {
            onStatus("LIVE AUDIO DROP")
        }
    }

    fun stop() {
        synchronized(playbackLock) {
            audioQueue?.close()
            audioQueue = null
            playbackJob?.cancel()
            playbackJob = null
            releaseTrackLocked()
            lastSequence = -1L
        }
        onStatus("AUDIO READY")
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private fun ensureStarted(targetSampleRate: Int) {
        synchronized(playbackLock) {
            if (audioTrack != null && audioQueue != null && sampleRate == targetSampleRate) return

            audioQueue?.close()
            audioQueue = null
            playbackJob?.cancel()
            playbackJob = null
            releaseTrackLocked()

            val minBuffer = AudioTrack.getMinBufferSize(
                targetSampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) {
                onStatus("LIVE AUDIO ERR")
                return
            }

            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(targetSampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(minBuffer * 4)
                    .build()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create realtime AudioTrack")
                onStatus("LIVE AUDIO ERR")
                return
            }

            sampleRate = targetSampleRate
            audioTrack = track
            track.play()

            val queue = Channel<ByteArray>(capacity = Channel.UNLIMITED)
            audioQueue = queue
            playbackJob = scope.launch {
                for (audio in queue) {
                    writeAudio(audio)
                }
            }
            onStatus("LIVE AUDIO")
        }
    }

    private fun writeAudio(audio: ByteArray) {
        synchronized(playbackLock) {
            val track = audioTrack ?: return
            if (track.state != AudioTrack.STATE_INITIALIZED) return
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                runCatching { track.play() }
            }
            val written = track.write(audio, 0, audio.size, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                Log.w(TAG, "Realtime AudioTrack write failed: $written")
            }
        }
    }

    private fun releaseTrackLocked() {
        audioTrack?.let { track ->
            runCatching { track.stop() }
            runCatching { track.flush() }
            runCatching { track.release() }
        }
        audioTrack = null
    }

    private companion object {
        private const val TAG = "WatchRealtimeAudio"
        private const val SAMPLE_RATE = 24_000
    }
}
