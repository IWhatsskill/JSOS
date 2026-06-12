package com.jsos.glasses.input

import android.content.Context
import com.jsos.glasses.rokid.RokidArCommands

internal enum class R08RingTapAction(
    val id: String,
    val label: String,
    val description: String
) {
    ROKID_AI("rokid_ai", "AI", "Open Rokid AI"),
    PHOTO("photo", "PHOTO", "Take normal photo"),
    AR_PIC("ar_pic", "AR PIC", "Take AR picture"),
    AR_REC_TOGGLE("ar_rec", "AR REC", "Toggle AR recording"),
    NONE("none", "NONE", "No action");

    companion object {
        fun fromId(id: String?, fallback: R08RingTapAction): R08RingTapAction {
            return values().firstOrNull { it.id == id } ?: fallback
        }
    }
}

internal object R08RingActionSettings {
    private const val PREFS = "jsos_r08_ring_actions"
    private const val KEY_TRIPLE_TAP = "triple_tap_action"
    private const val KEY_QUADRUPLE_TAP = "quadruple_tap_action"
    private const val KEY_AR_RECORDING_REQUESTED = "ar_recording_requested"
    private const val KEY_AR_RECORDING_REQUESTED_AT = "ar_recording_requested_at"
    private const val RECORD_REQUEST_TTL_MS = 2L * 60L * 60L * 1000L

    private val cycleActions = listOf(
        R08RingTapAction.ROKID_AI,
        R08RingTapAction.PHOTO,
        R08RingTapAction.AR_PIC,
        R08RingTapAction.AR_REC_TOGGLE,
        R08RingTapAction.NONE
    )

    fun tripleTap(context: Context): R08RingTapAction {
        return get(context, KEY_TRIPLE_TAP, R08RingTapAction.ROKID_AI)
    }

    fun quadrupleTap(context: Context): R08RingTapAction {
        return get(context, KEY_QUADRUPLE_TAP, R08RingTapAction.PHOTO)
    }

    fun actionForTapCount(context: Context, tapCount: Int): R08RingTapAction {
        return if (tapCount >= 4) quadrupleTap(context) else tripleTap(context)
    }

    fun cycleTripleTap(context: Context): R08RingTapAction {
        return cycle(context, KEY_TRIPLE_TAP, tripleTap(context))
    }

    fun cycleQuadrupleTap(context: Context): R08RingTapAction {
        return cycle(context, KEY_QUADRUPLE_TAP, quadrupleTap(context))
    }

    fun execute(context: Context, action: R08RingTapAction): Boolean {
        return when (action) {
            R08RingTapAction.ROKID_AI -> RokidArCommands.openAiAssist(context)
            R08RingTapAction.PHOTO -> RokidArCommands.takePhoto(context)
            R08RingTapAction.AR_PIC -> RokidArCommands.startArScreenshot(context)
            R08RingTapAction.AR_REC_TOGGLE -> toggleArRecording(context)
            R08RingTapAction.NONE -> true
        }
    }

    fun isArRecordingRequested(context: Context): Boolean {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_AR_RECORDING_REQUESTED, false)) {
            return false
        }
        val requestedAt = prefs.getLong(KEY_AR_RECORDING_REQUESTED_AT, 0L)
        val ageMs = System.currentTimeMillis() - requestedAt
        if (requestedAt <= 0L || ageMs < 0L || ageMs > RECORD_REQUEST_TTL_MS) {
            prefs.edit()
                .putBoolean(KEY_AR_RECORDING_REQUESTED, false)
                .remove(KEY_AR_RECORDING_REQUESTED_AT)
                .apply()
            return false
        }
        return true
    }

    private fun toggleArRecording(context: Context): Boolean {
        val start = !isArRecordingRequested(context)
        val sent = if (start) {
            RokidArCommands.startArRecord(context)
        } else {
            RokidArCommands.stopArRecord(context)
        }
        if (sent) {
            val editor = prefs(context).edit()
                .putBoolean(KEY_AR_RECORDING_REQUESTED, start)
            if (start) {
                editor.putLong(KEY_AR_RECORDING_REQUESTED_AT, System.currentTimeMillis())
            } else {
                editor.remove(KEY_AR_RECORDING_REQUESTED_AT)
            }
            editor.apply()
        }
        return sent
    }

    private fun cycle(
        context: Context,
        key: String,
        current: R08RingTapAction
    ): R08RingTapAction {
        val index = cycleActions.indexOf(current).takeIf { it >= 0 } ?: 0
        val next = cycleActions[(index + 1) % cycleActions.size]
        prefs(context).edit().putString(key, next.id).apply()
        return next
    }

    private fun get(
        context: Context,
        key: String,
        fallback: R08RingTapAction
    ): R08RingTapAction {
        return R08RingTapAction.fromId(prefs(context).getString(key, fallback.id), fallback)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
