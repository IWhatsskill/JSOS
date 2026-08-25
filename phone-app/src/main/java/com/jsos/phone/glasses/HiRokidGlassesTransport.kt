package com.jsos.phone.glasses

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.jsos.shared.HiRokidTransportProtocol
import com.rokid.cxr.Caps
import com.rokid.cxr.link.CXRLink
import com.rokid.cxr.link.callbacks.ICustomCmdCbk
import com.rokid.cxr.link.callbacks.ICXRLinkCbk
import com.rokid.cxr.link.utils.CxrDefs
import com.rokid.sprite.aiapp.externalapp.auth.AuthResult
import com.rokid.sprite.aiapp.externalapp.auth.AuthorizationHelper

/** Phone-to-glasses JSON transport that coexists with Hi Rokid via CXR-L. */
object HiRokidGlassesTransport {
    private const val TAG = "HiRokidTransport"
    private const val GLOBAL_AI_APP_PACKAGE = "com.rokid.sprite.global.aiapp"
    private const val AUTH_ACTIVITY_CLASS =
        "com.rokid.sprite.aiapp.externalapp.auth.AuthorizationActivity"
    private const val MEDIA_SERVICE_ACTION =
        "com.rokid.sprite.aiapp.externalapp.MEDIA_STREAM_SERVICE"
    private const val AUTH_TOKEN_EXTRA = "auth_token"
    private const val AUTH_PACKAGE_EXTRA = "auth_package"
    private const val GLASSES_PACKAGE = "com.jsos.glasses"
    private const val CONNECT_TIMEOUT_MS = 25_000L

    private lateinit var appContext: Context
    private val mainHandler = Handler(Looper.getMainLooper())
    private var token: String? = null
    private var link: CXRLink? = null
    private var generation = 0
    private var cxrlConnected = false
    private var glassBtConnected = false
    private var readyNotified = false

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onFailure: ((String) -> Unit)? = null
    var onMessageFromGlasses: ((String) -> Unit)? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun isHiRokidInstalled(): Boolean {
        if (!::appContext.isInitialized) return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(
                    GLOBAL_AI_APP_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(GLOBAL_AI_APP_PACKAGE, 0)
            }
        }.isSuccess
    }

    fun createAuthorizationIntent(): Intent? {
        if (!isHiRokidInstalled()) return null
        return Intent().setComponent(ComponentName(GLOBAL_AI_APP_PACKAGE, AUTH_ACTIVITY_CLASS))
    }

    fun handleAuthorizationResult(resultCode: Int, data: Intent?) {
        when (val result = AuthorizationHelper.INSTANCE.parseAuthorizationResult(resultCode, data)) {
            is AuthResult.AuthSuccess -> {
                token = result.token
                connect()
            }
            is AuthResult.AuthCancel -> fail("Hi Rokid authorization cancelled")
            is AuthResult.AuthFail -> fail("Hi Rokid authorization failed")
            else -> fail("Hi Rokid authorization returned an unknown result")
        }
    }

    fun isConnected(): Boolean = readyNotified && cxrlConnected && glassBtConnected

    fun disconnect() {
        val notify = readyNotified
        generation += 1
        disconnectActiveLink()
        link = null
        cxrlConnected = false
        glassBtConnected = false
        readyNotified = false
        if (notify) mainHandler.post { onDisconnected?.invoke() }
    }

    fun send(json: String): Boolean {
        val activeLink = link ?: return false
        if (!isConnected()) return false
        return runCatching {
            val payload = Caps().apply {
                write(HiRokidTransportProtocol.PHONE_TO_GLASSES_ROUTE)
                write(json)
            }.serialize()
            val result = activeLink.sendCustomCmd(
                HiRokidTransportProtocol.PHONE_TO_GLASSES_CHANNEL,
                payload,
            )
            result != null && result >= 0
        }.getOrElse {
            Log.e(TAG, "CXR-L custom command failed (redacted)")
            false
        }
    }

    private fun connect() {
        val authToken = token
        if (authToken.isNullOrBlank()) {
            fail("Authorize Hi Rokid first")
            return
        }

        generation += 1
        disconnectActiveLink()
        link = null
        cxrlConnected = false
        glassBtConnected = false
        readyNotified = false
        val currentGeneration = ++generation
        cxrlConnected = false
        glassBtConnected = false
        readyNotified = false

        val newLink = CXRLink(appContext).also { candidate ->
            candidate.setCXRLinkCbk(object : ICXRLinkCbk {
                override fun onCXRLConnected(connected: Boolean) {
                    mainHandler.post {
                        if (currentGeneration != generation) return@post
                        if (connected && !CxrLinkAiEventGuard.install(candidate)) {
                            fail("Failed to protect native Hi Rokid assistant ownership")
                            return@post
                        }
                        cxrlConnected = connected
                        updateReadyState()
                    }
                }

                override fun onGlassBtConnected(connected: Boolean) {
                    mainHandler.post {
                        if (currentGeneration != generation) return@post
                        glassBtConnected = connected
                        updateReadyState()
                    }
                }

                override fun onGlassAiAssistStart() = Unit
                override fun onGlassAiAssistStop() = Unit
            })
            candidate.setCXRCustomCmdCbk(object : ICustomCmdCbk {
                override fun onCustomCmdResult(channel: String?, data: ByteArray?) {
                    val message = decodeIncoming(channel, data) ?: return
                    mainHandler.post {
                        if (currentGeneration == generation) {
                            onMessageFromGlasses?.invoke(message)
                        }
                    }
                }
            })
        }
        link = newLink

        val configured = newLink.configCXRSession(
            CxrDefs.CXRSession(CxrDefs.CXRSessionType.CUSTOMAPP, GLASSES_PACKAGE),
        )
        if (!configured) {
            fail("Failed to configure Hi Rokid CUSTOMAPP session")
            return
        }
        if (!bindGlobalHiRokidService(newLink, authToken)) {
            fail("Hi Rokid service bind failed. Open Hi Rokid and reconnect the glasses")
            return
        }
        mainHandler.postDelayed({
            if (currentGeneration == generation && !isConnected()) {
                fail("Timed out waiting for the Hi Rokid/glasses link")
            }
        }, CONNECT_TIMEOUT_MS)
    }

    private fun updateReadyState() {
        val ready = cxrlConnected && glassBtConnected
        if (ready && !readyNotified) {
            readyNotified = true
            onConnected?.invoke()
        } else if (!ready && readyNotified) {
            readyNotified = false
            onDisconnected?.invoke()
        }
    }

    private fun decodeIncoming(channel: String?, data: ByteArray?): String? {
        if (data == null || data.isEmpty()) return null
        return runCatching {
            val caps = Caps.fromBytes(data)
            val values = (0 until caps.size()).mapNotNull { index ->
                runCatching { caps.at(index).getString() }.getOrNull()
            }
            HiRokidTransportProtocol.decodeGlassesToPhone(channel, values)
        }.getOrNull()
    }

    private fun bindGlobalHiRokidService(cxrLink: CXRLink, authToken: String): Boolean {
        return runCatching {
            val connection = findServiceConnection(cxrLink)
            val intent = Intent(MEDIA_SERVICE_ACTION)
                .setPackage(GLOBAL_AI_APP_PACKAGE)
                .putExtra(AUTH_TOKEN_EXTRA, authToken)
                .putExtra(AUTH_PACKAGE_EXTRA, appContext.packageName)
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
    }

    private fun findServiceConnection(cxrLink: CXRLink): ServiceConnection {
        var type: Class<*>? = cxrLink.javaClass
        while (type != null) {
            val field = type.declaredFields.firstOrNull { candidate ->
                ServiceConnection::class.java.isAssignableFrom(candidate.type)
            }
            if (field != null) {
                field.isAccessible = true
                return field.get(cxrLink) as ServiceConnection
            }
            type = type.superclass
        }
        error("CXR-L ServiceConnection field not found")
    }

    private fun fail(message: String) {
        generation += 1
        disconnectActiveLink()
        link = null
        cxrlConnected = false
        glassBtConnected = false
        readyNotified = false
        Log.e(TAG, message)
        mainHandler.post { onFailure?.invoke(message) }
    }

    private fun disconnectActiveLink() {
        val activeLink = link ?: return
        CxrLinkAiEventGuard.uninstall(activeLink)
        runCatching { activeLink.disconnect() }
    }
}
