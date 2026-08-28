package com.jsos.glasses.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.jsos.glasses.input.RingMediaKeyHandler.RingGesture
import java.util.Locale

class JsosRingAccessibilityService : AccessibilityService() {
    private var r08BleController: R08BleController? = null
    private lateinit var keyHandler: RingMediaKeyHandler
    private lateinit var navigator: R08AccessibilityNavigator
    private var commandReceiverRegistered = false
    private var tripleTapAction = R08RingTapAction.ROKID_AI
    private var quadrupleTapAction = R08RingTapAction.PHOTO
    private var suppressNativeAiUntilMs = 0L

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getStringExtra(EXTRA_COMMAND)) {
                COMMAND_RECONNECT -> {
                    val controller = r08BleController ?: R08BleController(this@JsosRingAccessibilityService)
                        .also {
                            r08BleController = it
                            it.start()
                        }
                    controller.restart()
                    Log.i(TAG, "command reconnect")
                }
                COMMAND_FORGET -> {
                    val submitted = r08BleController?.forgetBondedR08()
                        ?: R08BleController(this@JsosRingAccessibilityService).forgetBondedR08()
                    Log.i(TAG, "command forget submitted=$submitted")
                }
                COMMAND_SUPPRESS_NATIVE_AI_ONCE -> {
                    suppressNativeAiUntilMs =
                        SystemClock.uptimeMillis() + NATIVE_AI_SUPPRESSION_WINDOW_MS
                    if (!closeNativeAiOpenedByHardwareVoice()) {
                        Log.i(TAG, "armed one-shot native AI suppression for JSOS hardware voice")
                    }
                }
                COMMAND_SYNC_ACTIONS -> {
                    tripleTapAction = R08RingTapAction.fromId(
                        intent.getStringExtra(EXTRA_TRIPLE_TAP_ACTION),
                        tripleTapAction
                    )
                    quadrupleTapAction = R08RingTapAction.fromId(
                        intent.getStringExtra(EXTRA_QUADRUPLE_TAP_ACTION),
                        quadrupleTapAction
                    )
                    Log.i(
                        TAG,
                        "command actions synced triple=${tripleTapAction.id} " +
                            "quadruple=${quadrupleTapAction.id}"
                    )
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        navigator = R08AccessibilityNavigator(this)
        tripleTapAction = R08RingActionSettings.tripleTap(this)
        quadrupleTapAction = R08RingActionSettings.quadrupleTap(this)
        keyHandler = RingMediaKeyHandler(TAG) { ringGesture ->
            handleGlobalRingGesture(ringGesture)
        }

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 40L
        info.flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        serviceInfo = info

        r08BleController = R08BleController(this).also { it.start() }
        registerCommandReceiver()
        Log.i(TAG, "connected r08Ble=true keyFilter=true navigator=true")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::navigator.isInitialized || event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            navigator.setCurrentWindow(
                packageName = event.packageName?.toString().orEmpty(),
                className = event.className?.toString().orEmpty()
            )
            if (SystemClock.uptimeMillis() <= suppressNativeAiUntilMs &&
                closeNativeAiOpenedByHardwareVoice()
            ) {
                return
            }
        }
    }

    override fun onInterrupt() {
        Log.i(TAG, "interrupted")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (::navigator.isInitialized && navigator.isPackageActive(packageName)) {
            return false
        }
        if (!isRingDevice(event.device)) {
            return false
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && isRingMediaKey(event.keyCode)) {
            Log.d(
                TAG,
                "global key device=${event.device?.name.orEmpty()} code=${event.keyCode} " +
                    "name=${KeyEvent.keyCodeToString(event.keyCode)} scan=${event.scanCode}"
            )
        }
        if (event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0 &&
            isMusicPagePlayPauseKey(event.keyCode) &&
            navigator.isRokidLauncherMusicPageActive()
        ) {
            navigator.noteMusicPagePlayPauseKeyDown()
        }
        if (isMusicPageSkipKey(event.keyCode) && navigator.isRokidLauncherMusicPageActive()) {
            keyHandler.cleanup()
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                Log.d(TAG, "music page media passthrough key=${KeyEvent.keyCodeToString(event.keyCode)}")
            }
            return false
        }
        return keyHandler.handle(event)
    }

    override fun onDestroy() {
        if (::keyHandler.isInitialized) {
            keyHandler.cleanup()
        }
        if (::navigator.isInitialized) {
            navigator.release()
        }
        unregisterCommandReceiver()
        r08BleController?.stop()
        r08BleController = null
        super.onDestroy()
    }

    private fun registerCommandReceiver() {
        if (commandReceiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            commandReceiver,
            IntentFilter(ACTION_COMMAND),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        commandReceiverRegistered = true
    }

    private fun unregisterCommandReceiver() {
        if (!commandReceiverRegistered) return
        try {
            unregisterReceiver(commandReceiver)
        } catch (_: IllegalArgumentException) {
        }
        commandReceiverRegistered = false
    }

    private fun handleGlobalRingGesture(gesture: RingGesture) {
        if (!::navigator.isInitialized) return
        Log.d(TAG, "global gesture=$gesture launcher=${navigator.isRokidLauncherActive()}")
        when (gesture) {
            RingGesture.SWIPE_FORWARD -> navigator.moveForward()
            RingGesture.SWIPE_BACKWARD -> navigator.moveBackward()
            RingGesture.TAP -> navigator.activate()
            RingGesture.DOUBLE_TAP -> navigator.back()
            RingGesture.TRIPLE_TAP -> executeMappedTapAction(3)
            RingGesture.QUADRUPLE_TAP -> executeMappedTapAction(4)
        }
    }

    private fun closeNativeAiOpenedByHardwareVoice(): Boolean {
        if (!::navigator.isInitialized ||
            !navigator.isNativeRokidAssistantDialogActive()
        ) {
            return false
        }
        suppressNativeAiUntilMs = 0L
        navigator.back()
        Log.i(TAG, "closed native AI opened by JSOS hardware voice hold")
        return true
    }

    private fun executeMappedTapAction(tapCount: Int) {
        val action = if (tapCount >= 4) quadrupleTapAction else tripleTapAction
        val sent = R08RingActionSettings.executeGlobal(this, action)
        if (!sent) {
            Log.w(TAG, "mapped tap action failed tapCount=$tapCount action=${action.id}")
        }
    }

    private fun isRingMediaKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
            keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            keyCode == KeyEvent.KEYCODE_HEADSETHOOK
    }

    private fun isMusicPageSkipKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
            keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
    }

    private fun isMusicPagePlayPauseKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            keyCode == KeyEvent.KEYCODE_HEADSETHOOK
    }

    private fun isRingDevice(device: InputDevice?): Boolean {
        val name = device?.name ?: return false
        return name.uppercase(Locale.US).contains("R08")
    }

    companion object {
        private const val TAG = "JSOSRingSvc"
        private const val NATIVE_AI_SUPPRESSION_WINDOW_MS = 2_000L
        const val ACTION_COMMAND = "com.jsos.glasses.R08_COMMAND"
        const val EXTRA_COMMAND = "command"
        const val COMMAND_RECONNECT = "reconnect"
        const val COMMAND_FORGET = "forget"
        const val COMMAND_SUPPRESS_NATIVE_AI_ONCE = "suppress_native_ai_once"
        const val COMMAND_SYNC_ACTIONS = "sync_actions"
        const val EXTRA_TRIPLE_TAP_ACTION = "triple_tap_action"
        const val EXTRA_QUADRUPLE_TAP_ACTION = "quadruple_tap_action"
    }
}
