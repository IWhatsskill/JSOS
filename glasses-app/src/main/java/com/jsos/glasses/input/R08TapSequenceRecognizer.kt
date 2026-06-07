package com.jsos.glasses.input

import android.os.Handler

internal class R08TapSequenceRecognizer(
    private val handler: Handler,
    private val duplicateIgnoreMs: Long,
    private val interTapTimeoutMs: Long,
    private val maxTapCount: Int,
    private val onResolved: (Int) -> Unit
) {
    private var pendingResolution: Runnable? = null
    private var lastTapAt = 0L
    private var tapCount = 0

    fun onTap(now: Long) {
        val pending = pendingResolution
        if (pending != null) {
            val delta = now - lastTapAt
            if (delta < duplicateIgnoreMs) {
                return
            }
            if (delta <= interTapTimeoutMs) {
                handler.removeCallbacks(pending)
                tapCount++
                lastTapAt = now
                if (tapCount >= maxTapCount) {
                    resolve()
                } else {
                    schedule()
                }
                return
            }
            cancel()
        }

        tapCount = 1
        lastTapAt = now
        schedule()
    }

    fun cancel() {
        pendingResolution?.let(handler::removeCallbacks)
        pendingResolution = null
        lastTapAt = 0L
        tapCount = 0
    }

    private fun schedule() {
        val runnable = Runnable { resolve() }
        pendingResolution = runnable
        handler.postDelayed(runnable, interTapTimeoutMs)
    }

    private fun resolve() {
        val count = tapCount.coerceIn(1, maxTapCount)
        pendingResolution = null
        lastTapAt = 0L
        tapCount = 0
        onResolved(count)
    }
}
