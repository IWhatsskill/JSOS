package com.jsos.phone.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.jsos.phone.glasses.RokidSdkManager

class LiveTalkAudioRouteManager(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    fun routeToGlasses() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = false
        RokidSdkManager.setCommunicationDevice()
    }

    fun routeToPhoneSpeaker() {
        RokidSdkManager.clearCommunicationDevice()
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val speaker = audioManager.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (speaker != null) {
                val routed = audioManager.setCommunicationDevice(speaker)
                if (!routed) {
                    Log.w(TAG, "Failed to route Live Talk to phone speaker")
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        }
    }

    fun clear() {
        RokidSdkManager.clearCommunicationDevice()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    private companion object {
        private const val TAG = "LiveTalkAudioRoute"
    }
}
