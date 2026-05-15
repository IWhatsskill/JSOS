package com.jsos.phone

import android.app.Application
import android.util.Log
import com.jsos.phone.glasses.RokidSdkManager

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

        // Initialize Rokid SDK
        if (RokidSdkManager.initialize(this)) {
            Log.d(TAG, "Rokid SDK initialized successfully")
        } else {
            Log.w(TAG, "Rokid SDK initialization failed - check rokid.accessKey in local.properties")
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        RokidSdkManager.cleanup()
    }
}
