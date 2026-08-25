package com.jsos.phone

import android.app.Application
import android.util.Log
import com.jsos.phone.glasses.GlassesConnectionManager
import com.jsos.phone.glasses.RokidSdkManager
import com.jsos.phone.glasses.shouldEnableRokidAiEvents

class JsosApp : Application() {

    companion object {
        const val TAG = "JSOS"
        lateinit var instance: JsosApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "JSOS app initialized")

        // Apply the persisted transport before SDK initialization so a cold-started
        // Hi Rokid process never briefly claims the native AI key through CXR-M.
        RokidSdkManager.setAiEventHandlingEnabled(
            shouldEnableRokidAiEvents(GlassesConnectionManager.loadPreferredTransport(this))
        )

        // Initialize Rokid SDK
        if (RokidSdkManager.initialize(this)) {
            Log.d(TAG, "Rokid SDK initialized successfully")
        } else {
            Log.w(TAG, "Rokid SDK initialization failed - configure Rokid credentials in JSOS Core")
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        RokidSdkManager.cleanup()
    }
}
