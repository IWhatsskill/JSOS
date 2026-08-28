package com.jsos.glasses.input

import android.view.KeyEvent

/** Routes the Rokid hardware hold without changing normal HUD navigation keys. */
object RokidHardwareVoiceKeyRouter {
    const val HOLD_DELAY_MS = 1_300L

    fun startsCandidate(keyCode: Int, action: Int, repeatCount: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_NOTIFICATION &&
            action == KeyEvent.ACTION_DOWN &&
            repeatCount == 0
    }

    fun cancelsCandidate(keyCode: Int, action: Int, repeatCount: Int): Boolean {
        return action == KeyEvent.ACTION_DOWN &&
            repeatCount == 0 &&
            keyCode in setOf(
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_BACK,
            )
    }

}
