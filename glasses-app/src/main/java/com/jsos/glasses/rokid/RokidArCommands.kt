package com.jsos.glasses.rokid

import android.content.Context
import android.content.Intent
import android.util.Log
import com.jsos.glasses.GlassesApp

object RokidArCommands {
    private const val ACTION_CMD = "com.rokid.os.master.assist.server.cmd"
    private const val ASSIST_SERVER_PACKAGE = "com.rokid.os.sprite.assistserver"
    private const val SCENE_AI_ASSIST = "ai_assist"
    private const val SCENE_TAKE_PICTURE = "take_picture"
    private const val SCENE_AR_PICTURE = "ar_picture"
    private const val SCENE_MIX_RECORD = "mix_record"

    fun openAiAssist(context: Context): Boolean {
        return sendScene(context, SCENE_AI_ASSIST, open = true)
    }

    fun takePhoto(context: Context): Boolean {
        return sendScene(context, SCENE_TAKE_PICTURE, open = true)
    }

    fun startArScreenshot(context: Context) {
        sendScene(context, SCENE_AR_PICTURE, open = true)
    }

    fun startArRecord(context: Context) {
        sendScene(context, SCENE_MIX_RECORD, open = true)
    }

    fun stopArRecord(context: Context) {
        sendScene(context, SCENE_MIX_RECORD, open = false)
    }

    private fun sendScene(context: Context, scene: String, open: Boolean): Boolean {
        val intent = Intent(ACTION_CMD).apply {
            setPackage(ASSIST_SERVER_PACKAGE)
            putExtra("cmd_type", "control_scene")
            putExtra("scene", scene)
            putExtra("open", if (open) "true" else "false")
        }
        return try {
            context.sendBroadcast(intent)
            Log.i(GlassesApp.TAG, "Rokid scene command sent: scene=$scene open=$open")
            true
        } catch (error: RuntimeException) {
            Log.w(GlassesApp.TAG, "Rokid scene command failed: scene=$scene open=$open")
            false
        }
    }
}
