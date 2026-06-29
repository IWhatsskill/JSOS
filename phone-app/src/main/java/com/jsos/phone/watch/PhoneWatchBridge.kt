package com.jsos.phone.watch

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.jsos.shared.WatchCommand
import com.jsos.shared.WatchCommandAck
import com.jsos.shared.WatchCommandActions
import com.jsos.shared.WatchChatSnapshot
import com.jsos.shared.WatchCodexSnapshot
import com.jsos.shared.WatchCodexSessions
import com.jsos.shared.WatchCoreIds
import com.jsos.shared.WatchCoreStatus
import com.jsos.shared.WatchPaths
import com.jsos.shared.WatchPing
import com.jsos.shared.WatchPong
import com.jsos.shared.WatchRealtimeAudioChunk
import com.jsos.shared.WatchRealtimeAudioStop
import com.jsos.shared.WatchTtsAudioChunk
import com.jsos.shared.WatchTtsAudioStop
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class PhoneWatchBridge : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WatchPaths.PING -> handlePing(event)
            WatchPaths.STATE_REQUEST -> sendCoreStatus(event.sourceNodeId)
            WatchPaths.COMMAND -> handleCommand(event)
            else -> super.onMessageReceived(event)
        }
    }

    private fun handlePing(event: MessageEvent) {
        val ping = runCatching {
            WatchPing.fromJson(event.data.toString(Charsets.UTF_8))
        }.getOrNull()

        val pong = WatchPong(
            id = ping?.id ?: System.currentTimeMillis().toString(),
            coreId = WatchCoreIds.CORE,
            coreLabel = WatchCoreIds.label(WatchCoreIds.CORE)
        )
        sendMessage(event.sourceNodeId, WatchPaths.PONG, pong.toJson())
        sendCoreStatus(event.sourceNodeId)
    }

    private fun sendCoreStatus(nodeId: String) {
        sendMessage(nodeId, WatchPaths.CORE_STATUS, latestStatus.toJson())
        sendMessage(nodeId, WatchPaths.CHAT_SNAPSHOT, latestChatSnapshot.toJson())
        sendMessage(nodeId, WatchPaths.CODEX_SNAPSHOT, latestCodexSnapshot.toJson())
    }

    private fun handleCommand(event: MessageEvent) {
        val command = runCatching {
            WatchCommand.fromJson(event.data.toString(Charsets.UTF_8))
        }.getOrNull()

        if (command == null) {
            sendMessage(
                event.sourceNodeId,
                WatchPaths.COMMAND_ACK,
                WatchCommandAck(
                    id = System.currentTimeMillis().toString(),
                    coreId = WatchCoreIds.CORE,
                    action = "unknown",
                    ok = false,
                    message = "Bad command"
                ).toJson()
            )
            return
        }

        if (command.action == WatchCommandActions.REQUEST_STATE) {
            sendCoreStatus(event.sourceNodeId)
            sendMessage(
                event.sourceNodeId,
                WatchPaths.COMMAND_ACK,
                WatchCommandAck(
                    id = command.id,
                    coreId = WatchCoreIds.CORE,
                    action = command.action,
                    ok = true,
                    message = "State sent"
                ).toJson()
            )
            return
        }

        sendBroadcast(
            Intent(ACTION_WATCH_COMMAND)
                .setPackage(packageName)
                .putExtra(EXTRA_COMMAND_ID, command.id)
                .putExtra(EXTRA_COMMAND_ACTION, command.action)
                .putExtra(EXTRA_COMMAND_TARGET_ID, command.targetId)
        )
    }

    private fun sendMessage(nodeId: String, path: String, json: String) {
        Wearable.getMessageClient(this)
            .sendMessage(nodeId, path, json.toByteArray(Charsets.UTF_8))
            .addOnFailureListener {
                Log.w(TAG, "Watch message send failed")
            }
    }

    companion object {
        private const val TAG = "PhoneWatchBridge"
        const val ACTION_WATCH_COMMAND = "com.jsos.phone.watch.WATCH_COMMAND"
        const val EXTRA_COMMAND_ID = "command_id"
        const val EXTRA_COMMAND_ACTION = "command_action"
        const val EXTRA_COMMAND_TARGET_ID = "command_target_id"

        @Volatile
        private var latestStatus = WatchCoreStatus(
            coreId = WatchCoreIds.CORE,
            coreLabel = WatchCoreIds.label(WatchCoreIds.CORE)
        )

        @Volatile
        private var latestChatSnapshot = WatchChatSnapshot(coreId = WatchCoreIds.CORE)

        @Volatile
        private var latestCodexSnapshot = WatchCodexSnapshot(coreId = WatchCoreIds.CORE)

        private val realtimeAudioSequence = AtomicLong(0L)

        fun publishStatus(context: Context, status: WatchCoreStatus) {
            latestStatus = status.copy(
                coreId = WatchCoreIds.CORE,
                coreLabel = WatchCoreIds.label(WatchCoreIds.CORE)
            )
            publishToWatch(context, WatchPaths.CORE_STATUS, latestStatus.toJson())
        }

        fun publishCommandAck(context: Context, ack: WatchCommandAck) {
            val enriched = ack.copy(coreId = WatchCoreIds.CORE)
            publishToWatch(context, WatchPaths.COMMAND_ACK, enriched.toJson())
        }

        fun publishCodexSessions(context: Context, sessions: WatchCodexSessions) {
            val enriched = sessions.copy(coreId = WatchCoreIds.CORE)
            publishToWatch(context, WatchPaths.CODEX_SESSIONS, enriched.toJson())
        }

        fun publishCodexSnapshot(context: Context, snapshot: WatchCodexSnapshot) {
            latestCodexSnapshot = snapshot.copy(coreId = WatchCoreIds.CORE)
            publishToWatch(context, WatchPaths.CODEX_SNAPSHOT, latestCodexSnapshot.toJson())
        }

        fun publishChatSnapshot(context: Context, snapshot: WatchChatSnapshot) {
            latestChatSnapshot = snapshot.copy(coreId = WatchCoreIds.CORE)
            publishToWatch(context, WatchPaths.CHAT_SNAPSHOT, latestChatSnapshot.toJson())
        }

        fun publishTtsAudio(context: Context, file: File) {
            val audioId = UUID.randomUUID().toString()
            val chunkSize = 32 * 1024
            val total = ((file.length() + chunkSize - 1) / chunkSize).toInt().coerceAtLeast(1)
            val buffer = ByteArray(chunkSize)
            var sequence = 0

            FileInputStream(file).use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    val bytes = if (read == buffer.size) buffer else buffer.copyOf(read)
                    val chunk = WatchTtsAudioChunk(
                        coreId = WatchCoreIds.CORE,
                        audioId = audioId,
                        sequence = sequence,
                        total = total,
                        base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    )
                    publishToWatch(context, WatchPaths.TTS_AUDIO_CHUNK, chunk.toJson())
                    sequence += 1
                }
            }
        }

        fun publishTtsAudioStop(context: Context) {
            publishToWatch(
                context,
                WatchPaths.TTS_AUDIO_STOP,
                WatchTtsAudioStop(coreId = WatchCoreIds.CORE).toJson()
            )
        }

        fun publishRealtimeAudio(context: Context, audio: ByteArray) {
            if (audio.isEmpty()) return

            val chunkSize = 8 * 1024
            var offset = 0
            while (offset < audio.size) {
                val end = minOf(offset + chunkSize, audio.size)
                val bytes = audio.copyOfRange(offset, end)
                val chunk = WatchRealtimeAudioChunk(
                    coreId = WatchCoreIds.CORE,
                    sequence = realtimeAudioSequence.incrementAndGet(),
                    base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                )
                publishToWatch(context, WatchPaths.REALTIME_AUDIO_CHUNK, chunk.toJson())
                offset = end
            }
        }

        fun publishRealtimeAudioStop(context: Context) {
            publishToWatch(
                context,
                WatchPaths.REALTIME_AUDIO_STOP,
                WatchRealtimeAudioStop(coreId = WatchCoreIds.CORE).toJson()
            )
        }

        private fun publishToWatch(context: Context, path: String, json: String) {
            val appContext = context.applicationContext
            Wearable.getNodeClient(appContext).connectedNodes
                .addOnSuccessListener { nodes ->
                    nodes.forEach { node ->
                        Wearable.getMessageClient(appContext)
                            .sendMessage(node.id, path, json.toByteArray(Charsets.UTF_8))
                            .addOnFailureListener {
                                Log.w(TAG, "Watch send failed for $path")
                            }
                    }
                }
                .addOnFailureListener {
                    Log.w(TAG, "Watch node lookup failed")
                }
        }
    }
}
