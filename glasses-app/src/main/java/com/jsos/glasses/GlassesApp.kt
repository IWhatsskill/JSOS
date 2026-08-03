package com.jsos.glasses

import android.app.Application
import android.util.Log
import com.jsos.glasses.input.R08AccessibilityRecovery

class GlassesApp : Application() {

    companion object {
        const val TAG = "GlassesHUD"
        lateinit var instance: GlassesApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "JSOS HUD initialized")
        val recoveryResult = R08AccessibilityRecovery.ensureEnabled(this)
        Log.i(TAG, "R08 accessibility startup recovery=$recoveryResult")
    }
}
