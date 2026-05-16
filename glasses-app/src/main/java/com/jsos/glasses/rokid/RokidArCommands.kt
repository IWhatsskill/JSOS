package com.jsos.glasses.rokid

import android.content.Context
import android.content.Intent
import android.util.Log
import com.jsos.glasses.GlassesApp

object RokidArCommands {
    private const val ACTION_CMD = "com.rokid.os.master.assist.server.cmd"
    private const val SCENE_AR_PICTURE = "ar_picture"
    private const val SCENE_MIX_RECORD = "mix_record"

    fun startArScreenshot(context: Context) {
        sendScene(context, SCENE_AR_PICTURE, open = true)
    }

    fun startArRecord(context: Context) {
        sendScene(context, SCENE_MIX_RECORD, open = true)
    }

    fun stopArRecord(context: Context) {
        sendScene(context, SCENE_MIX_RECORD, open = false)
    }

    private fun sendScene(context: Context, scene: String, open: Boolean) {
        val intent = Intent(ACTION_CMD).apply {
            putExtra("cmd_type", "control_scene")
            putExtra("scene", scene)
            putExtra("open", if (open) "true" else "false")
        }
        context.sendBroadcast(intent)
        Log.i(GlassesApp.TAG, "Rokid AR scene command sent: scene=$scene open=$open")
    }
}
