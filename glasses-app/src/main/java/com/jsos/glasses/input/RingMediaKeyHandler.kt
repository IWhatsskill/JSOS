package com.jsos.glasses.input

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent

class RingMediaKeyHandler(
    private val tag: String = TAG,
    private val onGesture: (RingGesture) -> Unit
) {
    enum class RingGesture {
        SWIPE_FORWARD,
        SWIPE_BACKWARD,
        TAP,
        DOUBLE_TAP,
        TRIPLE_TAP,
        QUADRUPLE_TAP
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastTapKeyCode = KeyEvent.KEYCODE_UNKNOWN
    private var lastTapScanCode = 0
    private val tapRecognizer = R08TapSequenceRecognizer(
        handler = handler,
        duplicateIgnoreMs = TAP_BOUNCE_IGNORE_MS,
        interTapTimeoutMs = TAP_SEQUENCE_TIMEOUT_MS,
        maxTapCount = MAX_TAP_SEQUENCE
    ) { tapCount ->
        emitTapCount(tapCount)
    }

    fun handle(event: KeyEvent?): Boolean {
        event ?: return false
        if (!isRingMediaKey(event.keyCode)) return false
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if (event.repeatCount > 0) return true

        return when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                emit(RingGesture.SWIPE_FORWARD, event)
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                emit(RingGesture.SWIPE_BACKWARD, event)
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
        tapRecognizer.cancel()
        lastTapKeyCode = KeyEvent.KEYCODE_UNKNOWN
        lastTapScanCode = 0
    }

    private fun handleTap(event: KeyEvent) {
        lastTapKeyCode = event.keyCode
        lastTapScanCode = event.scanCode
        tapRecognizer.onTap(SystemClock.uptimeMillis())
    }

    private fun emit(gesture: RingGesture, event: KeyEvent) {
        Log.d(
            tag,
            "Ring key=${KeyEvent.keyCodeToString(event.keyCode)} code=${event.keyCode} scan=${event.scanCode} -> $gesture"
        )
        onGesture(gesture)
    }

    private fun emitTapCount(tapCount: Int) {
        val gesture = when (tapCount) {
            1 -> RingGesture.TAP
            2 -> RingGesture.DOUBLE_TAP
            3 -> RingGesture.TRIPLE_TAP
            else -> RingGesture.QUADRUPLE_TAP
        }
        Log.d(
            tag,
            "Ring tapCount=$tapCount key=${KeyEvent.keyCodeToString(lastTapKeyCode)} " +
                "code=$lastTapKeyCode scan=$lastTapScanCode -> $gesture"
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
        private const val MAX_TAP_SEQUENCE = 4
    }
}
