package com.jsos.phone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.jsos.phone.ring.RingInputAction
import com.jsos.phone.ring.RingInputBus
import com.jsos.phone.ui.theme.JsosTheme
import com.jsos.phone.ui.theme.JsosPalette
import com.jsos.phone.ui.screens.MainScreen

class MainActivity : ComponentActivity() {

    private val ringInputHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var ringMediaSession: MediaSession? = null
    private var pendingRingTap: Runnable? = null
    private var lastRingTapAtMs: Long = 0L

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            initializeApp()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeRingMediaSession()

        if (hasAllPermissions()) {
            initializeApp()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    override fun onResume() {
        super.onResume()
        ringMediaSession?.setActive(true)
    }

    override fun onDestroy() {
        pendingRingTap?.let { ringInputHandler.removeCallbacks(it) }
        pendingRingTap = null
        ringMediaSession?.release()
        ringMediaSession = null
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (handleRingKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    private fun initializeRingMediaSession() {
        if (ringMediaSession != null) return
        ringMediaSession = MediaSession(this, "JSOS Ring Input").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }
                    return event?.let { handleRingKeyEvent(it) } ?: false
                }
            })
            val actions = PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(actions)
                    .setState(PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0f)
                    .build()
            )
            setActive(true)
        }
    }

    private fun handleRingKeyEvent(event: KeyEvent): Boolean {
        val action = when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> RingInputAction.Forward
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> RingInputAction.Backward
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> RingInputAction.Tap
            else -> return false
        }

        if (event.action == KeyEvent.ACTION_DOWN) return true
        if (event.action != KeyEvent.ACTION_UP) return true

        if (action == RingInputAction.Tap) {
            handleRingTap()
        } else {
            RingInputBus.emit(action)
        }
        return true
    }

    private fun handleRingTap() {
        val now = SystemClock.uptimeMillis()
        if (lastRingTapAtMs > 0L && now - lastRingTapAtMs <= RING_DOUBLE_TAP_WINDOW_MS) {
            pendingRingTap?.let { ringInputHandler.removeCallbacks(it) }
            pendingRingTap = null
            lastRingTapAtMs = 0L
            RingInputBus.emit(RingInputAction.DoubleTap)
            return
        }

        lastRingTapAtMs = now
        val tap = Runnable {
            if (lastRingTapAtMs == now) {
                RingInputBus.emit(RingInputAction.Tap)
                lastRingTapAtMs = 0L
            }
            pendingRingTap = null
        }
        pendingRingTap = tap
        ringInputHandler.postDelayed(tap, RING_DOUBLE_TAP_WINDOW_MS)
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun initializeApp() {
        window.statusBarColor = JsosPalette.ScreenTopArgb.toInt()
        window.navigationBarColor = JsosPalette.ScreenTopArgb.toInt()

        setContent {
            JsosTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = JsosPalette.ScreenTop
                ) {
                    MainScreen()
                }
            }
        }
    }

    companion object {
        private const val RING_DOUBLE_TAP_WINDOW_MS = 350L
    }
}
