package com.jsos.glasses.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.SystemClock
import android.util.Log

internal class R08GestureDispatcher(
    private val service: AccessibilityService
) {
    fun tap(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 70))
            .build()
        service.dispatchGesture(gesture, null, null)
        Log.d(TAG, "tap x=$x y=$y")
    }

    fun longPress(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, LONG_PRESS_DURATION_MS))
            .build()
        val submitted = service.dispatchGesture(gesture, null, null)
        Log.d(TAG, "longPress x=$x y=$y submitted=$submitted")
    }

    fun horizontalSwipe(forward: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val metrics = service.resources.displayMetrics
        val y = metrics.heightPixels * 0.52f
        val startX = if (forward) metrics.widthPixels * 0.78f else metrics.widthPixels * 0.22f
        val endX = if (forward) metrics.widthPixels * 0.22f else metrics.widthPixels * 0.78f
        val path = Path().apply {
            moveTo(startX, y)
            lineTo(endX, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 130))
            .build()
        val submitted = service.dispatchGesture(gesture, null, null)
        Log.d(TAG, "horizontalSwipe forward=$forward submitted=$submitted t=${SystemClock.uptimeMillis()}")
    }

    fun verticalSwipe(forward: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val metrics = service.resources.displayMetrics
        val x = metrics.widthPixels * 0.50f
        val startY = if (forward) metrics.heightPixels * 0.74f else metrics.heightPixels * 0.28f
        val endY = if (forward) metrics.heightPixels * 0.28f else metrics.heightPixels * 0.74f
        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 160))
            .build()
        val submitted = service.dispatchGesture(gesture, null, null)
        Log.d(TAG, "verticalSwipe forward=$forward submitted=$submitted")
    }

    companion object {
        private const val TAG = "JSOSR08Gestures"
        private const val LONG_PRESS_DURATION_MS = 850L
    }
}
