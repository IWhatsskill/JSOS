package com.jsos.phone.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores sensitive runtime strings in SharedPreferences after encrypting them
 * with an Android Keystore-backed AES-GCM key.
 */
class SecurePrefs(context: Context) {

    companion object {
        private const val TAG = "SecurePrefs"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "jsos.runtime.secrets.v1"
        private const val PREFS_NAME = "jsos_secure"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    @Synchronized
    fun getString(key: String, defaultValue: String? = null): String? {
        val encrypted = prefs.getString(key, null) ?: return defaultValue
        return try {
            decrypt(encrypted)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decrypt secure value for $key", e)
            defaultValue
        }
    }

    @Synchronized
    fun putString(key: String, value: String?) {
        if (value.isNullOrEmpty()) {
            removeString(key)
            return
        }
        prefs.edit()
            .putString(key, encrypt(value))
            .apply()
    }

    @Synchronized
    fun removeString(key: String) {
        prefs.edit().remove(key).apply()
    }

    @Synchronized
    fun migrateString(plainPrefs: SharedPreferences, key: String) {
        val legacyValue = plainPrefs.getString(key, null)
        if (!legacyValue.isNullOrEmpty() && !prefs.contains(key)) {
            putString(key, legacyValue)
        }
        if (plainPrefs.contains(key)) {
            plainPrefs.edit().remove(key).apply()
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
            ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val parts = value.split(":", limit = 2)
        require(parts.size == 2) { "Invalid secure value format" }

        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()

        generator.init(spec)
        return generator.generateKey()
    }
}
