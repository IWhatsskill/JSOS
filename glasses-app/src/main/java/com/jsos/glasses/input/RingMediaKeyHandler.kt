package com.jsos.glasses.input

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.jsos.glasses.input.GestureHandler.Gesture

class RingMediaKeyHandler(
    private val tag: String = TAG,
    private val onGesture: (Gesture) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingTap: Runnable? = null
    private var lastTapAt = 0L

    fun handle(event: KeyEvent?): Boolean {
        event ?: return false
        if (!isRingMediaKey(event.keyCode)) return false
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if (event.repeatCount > 0) return true

        return when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                emit(Gesture.SWIPE_FORWARD, event)
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                emit(Gesture.SWIPE_BACKWARD, event)
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> {
                handleTap(event)
                true
            }
            else -> false
        }
    }

    fun cleanup() {
        pendingTap?.let(handler::removeCallbacks)
        pendingTap = null
        lastTapAt = 0L
    }

    private fun handleTap(event: KeyEvent) {
        val now = SystemClock.uptimeMillis()
        val pending = pendingTap
        if (pending != null) {
            val delta = now - lastTapAt
            if (delta < TAP_BOUNCE_IGNORE_MS) return
            if (delta <= TAP_SEQUENCE_TIMEOUT_MS) {
                handler.removeCallbacks(pending)
                pendingTap = null
                lastTapAt = 0L
                emit(Gesture.DOUBLE_TAP, event)
                return
            }
            cleanup()
        }

        lastTapAt = now
        val singleTap = Runnable {
            if (lastTapAt == now) {
                pendingTap = null
                lastTapAt = 0L
                emit(Gesture.TAP, event)
            }
        }
        pendingTap = singleTap
        handler.postDelayed(singleTap, TAP_SEQUENCE_TIMEOUT_MS)
    }

    private fun emit(gesture: Gesture, event: KeyEvent) {
        Log.d(
            tag,
            "Ring key=${KeyEvent.keyCodeToString(event.keyCode)} code=${event.keyCode} scan=${event.scanCode} -> $gesture"
        )
        onGesture(gesture)
    }

    private fun isRingMediaKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
            keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            keyCode == KeyEvent.KEYCODE_HEADSETHOOK
    }

    companion object {
        private const val TAG = "JSOSRingKeys"
        private const val TAP_BOUNCE_IGNORE_MS = 75L
        private const val TAP_SEQUENCE_TIMEOUT_MS = 650L
    }
}
