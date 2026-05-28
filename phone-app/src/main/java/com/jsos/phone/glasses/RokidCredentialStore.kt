package com.jsos.phone.glasses

import android.content.Context
import com.jsos.phone.security.SecurePrefs

class RokidCredentialStore(context: Context) {
    private val securePrefs = SecurePrefs(context.applicationContext)

    fun getAccessKey(): String =
        securePrefs.getString(KEY_ACCESS_KEY, "").orEmpty()

    fun getClientSecret(): String =
        securePrefs.getString(KEY_CLIENT_SECRET, "").orEmpty()

    fun isConfigured(): Boolean =
        getAccessKey().isNotBlank() && getClientSecret().isNotBlank()

    fun save(accessKey: String, clientSecret: String) {
        securePrefs.putString(KEY_ACCESS_KEY, accessKey.trim())
        securePrefs.putString(KEY_CLIENT_SECRET, clientSecret.trim())
    }

    fun clear() {
        securePrefs.removeString(KEY_ACCESS_KEY)
        securePrefs.removeString(KEY_CLIENT_SECRET)
    }

    private companion object {
        private const val KEY_ACCESS_KEY = "rokid_runtime_access_key"
        private const val KEY_CLIENT_SECRET = "rokid_runtime_client_secret"
    }
}
