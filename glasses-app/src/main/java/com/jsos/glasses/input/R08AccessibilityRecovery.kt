package com.jsos.glasses.input

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Restores only the JSOS Ring accessibility component after Android or the
 * Rokid firmware removed it. WRITE_SECURE_SETTINGS must be granted once
 * through ADB; without that grant this helper is strictly read-only.
 */
object R08AccessibilityRecovery {
    private const val TAG = "R08AccessRecovery"

    enum class Result {
        ALREADY_ENABLED,
        ENABLED,
        MISSING_SECURE_SETTINGS_PERMISSION,
        WRITE_FAILED
    }

    fun ensureEnabled(context: Context): Result {
        val appContext = context.applicationContext
        if (!hasSecureSettingsPermission(appContext)) {
            Log.w(TAG, "skip missing WRITE_SECURE_SETTINGS grant")
            return Result.MISSING_SECURE_SETTINGS_PERMISSION
        }

        return try {
            val resolver = appContext.contentResolver
            val target = ComponentName(appContext, JsosRingAccessibilityService::class.java)
            val current = Settings.Secure.getString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            val alreadyListed = containsComponent(current, target)
            val globallyEnabled = Settings.Secure.getInt(
                resolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            ) == 1

            if (alreadyListed && globallyEnabled) {
                Log.i(TAG, "already enabled")
                return Result.ALREADY_ENABLED
            }

            val servicesWritten = if (alreadyListed) {
                true
            } else {
                Settings.Secure.putString(
                    resolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    appendComponent(current, target)
                )
            }
            val masterWritten = Settings.Secure.putInt(
                resolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                1
            )

            val readback = Settings.Secure.getString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            val readbackEnabled = Settings.Secure.getInt(
                resolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            ) == 1
            val verified = servicesWritten && masterWritten &&
                containsComponent(readback, target) && readbackEnabled

            if (verified) {
                Log.i(TAG, "enabled and verified")
                Result.ENABLED
            } else {
                Log.e(
                    TAG,
                    "write verification failed servicesWritten=$servicesWritten " +
                        "masterWritten=$masterWritten"
                )
                Result.WRITE_FAILED
            }
        } catch (error: SecurityException) {
            Log.e(TAG, "secure settings write rejected", error)
            Result.WRITE_FAILED
        } catch (error: RuntimeException) {
            Log.e(TAG, "secure settings recovery failed", error)
            Result.WRITE_FAILED
        }
    }

    private fun hasSecureSettingsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun appendComponent(current: String?, target: ComponentName): String {
        val existing = current
            .orEmpty()
            .split(':')
            .map { entry -> entry.trim() }
            .filter { entry -> entry.isNotEmpty() }
        return (existing + target.flattenToString()).joinToString(":")
    }

    private fun containsComponent(value: String?, target: ComponentName): Boolean {
        return value
            .orEmpty()
            .split(':')
            .asSequence()
            .map { entry -> entry.trim() }
            .filter { entry -> entry.isNotEmpty() }
            .mapNotNull { entry -> ComponentName.unflattenFromString(entry) }
            .any { component ->
                component.packageName.equals(target.packageName, ignoreCase = true) &&
                    component.className.equals(target.className, ignoreCase = true)
            }
    }
}
