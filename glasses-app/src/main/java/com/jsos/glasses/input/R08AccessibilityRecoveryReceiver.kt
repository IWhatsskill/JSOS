package com.jsos.glasses.input

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Provides isolated recovery attempts after boot, unlock, package updates,
 * and Bluetooth startup. GlassesApp remains the fallback on firmware that
 * suppresses a third-party background broadcast.
 */
class R08AccessibilityRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in SUPPORTED_ACTIONS) return
        if (action == BluetoothAdapter.ACTION_STATE_CHANGED &&
            intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) !=
            BluetoothAdapter.STATE_ON
        ) {
            return
        }

        val result = R08AccessibilityRecovery.ensureEnabled(context)
        Log.i(TAG, "action=$action result=$result")
    }

    companion object {
        private const val TAG = "R08AccessReceiver"
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            BluetoothAdapter.ACTION_STATE_CHANGED
        )
    }
}
