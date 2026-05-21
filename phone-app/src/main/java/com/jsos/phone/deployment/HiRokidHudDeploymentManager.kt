package com.jsos.phone.deployment

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.rokid.cxr.link.CXRLink
import com.rokid.cxr.link.callbacks.ICXRLinkCbk
import com.rokid.cxr.link.callbacks.IGlassAppCbk
import com.rokid.cxr.link.utils.CxrDefs
import com.rokid.sprite.aiapp.externalapp.auth.AuthResult
import com.rokid.sprite.aiapp.externalapp.auth.AuthorizationHelper
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HiRokidHudDeploymentManager(
    context: Context,
) {
    companion object {
        private const val GLOBAL_AI_APP_PACKAGE = "com.rokid.sprite.global.aiapp"
        private const val AUTH_ACTIVITY_CLASS = "com.rokid.sprite.aiapp.externalapp.auth.AuthorizationActivity"
        private const val AUTH_ACTION = "com.rokid.sprite.aiapp.externalapp.AUTHORIZATION"
        private const val MEDIA_SERVICE_ACTION = "com.rokid.sprite.aiapp.externalapp.MEDIA_STREAM_SERVICE"
        private const val AUTH_TOKEN_EXTRA = "auth_token"
        private const val AUTH_PACKAGE_EXTRA = "auth_package"
        private const val REBIND_DELAY_MS = 900L
        private const val LINK_READY_TIMEOUT_MS = 25_000L
        private const val INSTALL_TIMEOUT_MS = 90_000L
        private const val STABLE_LINK_DELAY_MS = 1_200L
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var token: String? = null
    private var cxrLink: CXRLink? = null
    private var pendingUpload: File? = null
    private var uploadStarted = false
    private var cxrlConnected = false
    private var glassBtConnected = false
    private var cxrlEverConnected = false
    private var glassBtEverConnected = false
    private var attemptId = 0
    private var linkWaitJob: Job? = null
    private var uploadReadyJob: Job? = null
    private var timeoutJob: Job? = null

    val hasAuthorization: Boolean
        get() = !token.isNullOrBlank()

    fun isHiRokidInstalled(): Boolean {
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

    fun isWifiEnabled(): Boolean {
        val wifiManager = appContext.getSystemService(WifiManager::class.java)
        return wifiManager?.isWifiEnabled == true
    }

    fun createAuthorizationIntent(): Intent? {
        if (!isHiRokidInstalled()) return null
        return Intent().setComponent(ComponentName(GLOBAL_AI_APP_PACKAGE, AUTH_ACTIVITY_CLASS))
    }

    fun handleAuthorizationResult(resultCode: Int, data: Intent?): String {
        return when (val result = AuthorizationHelper.INSTANCE.parseAuthorizationResult(resultCode, data)) {
            is AuthResult.AuthSuccess -> {
                token = result.token
                "Hi Rokid authorization ready."
            }
            is AuthResult.AuthCancel -> {
                "Hi Rokid authorization cancelled."
            }
            is AuthResult.AuthFail -> {
                "Hi Rokid authorization failed."
            }
            else -> {
                "Hi Rokid authorization returned an unknown result."
            }
        }
    }

    fun installHudApk(
        apkUri: Uri,
        scope: CoroutineScope,
        onStatus: (String) -> Unit,
        onBusyChanged: (Boolean) -> Unit,
    ) {
        if (!isHiRokidInstalled()) {
            onStatus("Install Hi Rokid on this phone first.")
            return
        }

        if (!isWifiEnabled()) {
            onStatus("Turn on phone Wi-Fi first. Hi Rokid needs it for the glasses link.")
            return
        }

        val authToken = token
        if (authToken.isNullOrBlank()) {
            onStatus("Authorize Hi Rokid first, then retry install.")
            return
        }

        scope.launch {
            onBusyChanged(true)
            runCatching {
                val stagedApk = withContext(Dispatchers.IO) { stageApk(apkUri) }
                val packageName = readPackageName(stagedApk)
                onStatus("HUD APK staged: $packageName")
                connectAndUpload(
                    scope = scope,
                    authToken = authToken,
                    packageName = packageName,
                    apkFile = stagedApk,
                    onStatus = onStatus,
                    onBusyChanged = onBusyChanged,
                )
            }.onFailure { error ->
                pendingUpload = null
                onStatus("HUD deployment failed: ${error.javaClass.simpleName}")
                onBusyChanged(false)
            }
        }
    }

    fun cleanup() {
        attemptId += 1
        resetActiveDeployment()
    }

    private fun resetActiveDeployment() {
        linkWaitJob?.cancel()
        linkWaitJob = null
        uploadReadyJob?.cancel()
        uploadReadyJob = null
        timeoutJob?.cancel()
        timeoutJob = null
        runCatching { cxrLink?.disconnect() }
        pendingUpload?.delete()
        pendingUpload = null
        cxrLink = null
        uploadStarted = false
        cxrlConnected = false
        glassBtConnected = false
        cxrlEverConnected = false
        glassBtEverConnected = false
    }

    private suspend fun connectAndUpload(
        scope: CoroutineScope,
        authToken: String,
        packageName: String,
        apkFile: File,
        onStatus: (String) -> Unit,
        onBusyChanged: (Boolean) -> Unit,
    ) {
        cleanup()
        postStatus(onStatus, "Resetting Hi Rokid link...")
        delay(REBIND_DELAY_MS)

        val currentAttempt = ++attemptId

        val link = CXRLink(appContext).also { newLink ->
            newLink.setCXRLinkCbk(object : ICXRLinkCbk {
                override fun onCXRLConnected(connected: Boolean) {
                    postStatus(onStatus, "CXR-L service connected: $connected")
                    mainHandler.post {
                        if (!isCurrentAttempt(currentAttempt)) return@post
                        cxrlConnected = connected
                        if (connected) {
                            cxrlEverConnected = true
                        } else if (pendingUpload != null && (uploadStarted || cxrlEverConnected)) {
                            failDeployment(
                                attempt = currentAttempt,
                                message = "Hi Rokid CXR-L link dropped. Retry install after Hi Rokid reconnects.",
                                onStatus = onStatus,
                                onBusyChanged = onBusyChanged,
                            )
                            return@post
                        }
                        maybeUploadPending(scope, currentAttempt, onStatus, onBusyChanged)
                    }
                }

                override fun onGlassBtConnected(connected: Boolean) {
                    postStatus(onStatus, "Hi Rokid glasses Bluetooth connected: $connected")
                    mainHandler.post {
                        if (!isCurrentAttempt(currentAttempt)) return@post
                        glassBtConnected = connected
                        if (connected) {
                            glassBtEverConnected = true
                        } else if (pendingUpload != null && (uploadStarted || glassBtEverConnected)) {
                            failDeployment(
                                attempt = currentAttempt,
                                message = "Glasses Bluetooth link dropped. Reopen Hi Rokid or reconnect glasses, then retry.",
                                onStatus = onStatus,
                                onBusyChanged = onBusyChanged,
                            )
                            return@post
                        }
                        maybeUploadPending(scope, currentAttempt, onStatus, onBusyChanged)
                    }
                }

                override fun onGlassAiAssistStart() {
                    postStatus(onStatus, "Hi Rokid assistant started.")
                }

                override fun onGlassAiAssistStop() {
                    postStatus(onStatus, "Hi Rokid assistant stopped.")
                }
            })
            cxrLink = newLink
            newLink
        }

        pendingUpload = apkFile
        uploadStarted = false
        cxrlConnected = false
        glassBtConnected = false
        cxrlEverConnected = false
        glassBtEverConnected = false

        linkWaitJob = scope.launch {
            delay(LINK_READY_TIMEOUT_MS)
            mainHandler.post {
                if (isCurrentAttempt(currentAttempt) && pendingUpload != null && !uploadStarted) {
                    failDeployment(
                        attempt = currentAttempt,
                        message = "Timed out waiting for Hi Rokid/glasses link. Open Hi Rokid, confirm glasses connection, then retry.",
                        onStatus = onStatus,
                        onBusyChanged = onBusyChanged,
                    )
                }
            }
        }

        timeoutJob = scope.launch {
            delay(INSTALL_TIMEOUT_MS)
            mainHandler.post {
                if (isCurrentAttempt(currentAttempt) && pendingUpload != null) {
                    failDeployment(
                        attempt = currentAttempt,
                        message = "Timed out waiting for Hi Rokid/glasses install result. Retry after Hi Rokid reconnects.",
                        onStatus = onStatus,
                        onBusyChanged = onBusyChanged,
                    )
                }
            }
        }

        val configured = link.configCXRSession(
            CxrDefs.CXRSession(CxrDefs.CXRSessionType.CUSTOMAPP, packageName),
        )
        if (!configured) {
            failDeployment(
                attempt = currentAttempt,
                message = "Failed to configure Hi Rokid CUSTOMAPP session.",
                onStatus = onStatus,
                onBusyChanged = onBusyChanged,
            )
            return
        }

        onStatus("Binding to Hi Rokid service...")
        val bindStarted = bindGlobalHiRokidService(link, authToken)
        if (!bindStarted) {
            failDeployment(
                attempt = currentAttempt,
                message = "Hi Rokid service bind failed. Open Hi Rokid, confirm glasses connection, then retry.",
                onStatus = onStatus,
                onBusyChanged = onBusyChanged,
            )
        }
    }

    private fun maybeUploadPending(
        scope: CoroutineScope,
        currentAttempt: Int,
        onStatus: (String) -> Unit,
        onBusyChanged: (Boolean) -> Unit,
    ) {
        pendingUpload ?: return
        if (!isCurrentAttempt(currentAttempt)) return
        if (uploadStarted || !cxrlConnected || !glassBtConnected) return
        if (uploadReadyJob?.isActive == true) return

        onStatus("Link ready. Stabilizing Hi Rokid connection...")
        uploadReadyJob = scope.launch {
            delay(STABLE_LINK_DELAY_MS)
            mainHandler.post {
                if (!isCurrentAttempt(currentAttempt)) return@post
                val apkFile = pendingUpload ?: return@post
                if (uploadStarted) return@post
                if (!cxrlConnected || !glassBtConnected) {
                    postStatus(onStatus, "Waiting for stable Hi Rokid/glasses link...")
                    return@post
                }
                uploadPendingApk(
                    attempt = currentAttempt,
                    apkFile = apkFile,
                    onStatus = onStatus,
                    onBusyChanged = onBusyChanged,
                )
            }
        }
    }

    private fun uploadPendingApk(
        attempt: Int,
        apkFile: File,
        onStatus: (String) -> Unit,
        onBusyChanged: (Boolean) -> Unit,
    ) {
        if (!isCurrentAttempt(attempt)) return
        uploadStarted = true
        linkWaitJob?.cancel()
        linkWaitJob = null
        onStatus("Link ready. Uploading HUD APK through Hi Rokid...")
        val link = cxrLink
        if (link == null) {
            failDeployment(
                attempt = attempt,
                message = "Hi Rokid link is not available. Retry install.",
                onStatus = onStatus,
                onBusyChanged = onBusyChanged,
            )
            return
        }
        link.appUploadAndInstall(apkFile.absolutePath, object : IGlassAppCbk {
            override fun onInstallAppResult(success: Boolean) {
                mainHandler.post {
                    if (!isCurrentAttempt(attempt)) return@post
                    timeoutJob?.cancel()
                    timeoutJob = null
                    uploadReadyJob?.cancel()
                    uploadReadyJob = null
                    pendingUpload = null
                    uploadStarted = false
                    apkFile.delete()
                    onStatus(if (success) "HUD install succeeded." else "HUD install failed.")
                    onBusyChanged(false)
                }
            }

            override fun onUnInstallAppResult(success: Boolean) = Unit
            override fun onOpenAppResult(success: Boolean) = Unit
            override fun onStopAppResult(success: Boolean) = Unit
            override fun onGlassAppResume(resumed: Boolean) = Unit
            override fun onQueryAppResult(installed: Boolean) = Unit
        })
    }

    private fun failDeployment(
        attempt: Int,
        message: String,
        onStatus: (String) -> Unit,
        onBusyChanged: (Boolean) -> Unit,
    ) {
        if (!isCurrentAttempt(attempt)) return
        onStatus(message)
        onBusyChanged(false)
        cleanup()
    }

    private fun isCurrentAttempt(value: Int): Boolean {
        return value == attemptId
    }

    private fun bindGlobalHiRokidService(link: CXRLink, authToken: String): Boolean {
        return runCatching {
            val connection = findServiceConnection(link)
            val intent = Intent(MEDIA_SERVICE_ACTION)
                .setPackage(GLOBAL_AI_APP_PACKAGE)
                .putExtra(AUTH_TOKEN_EXTRA, authToken)
                .putExtra(AUTH_PACKAGE_EXTRA, appContext.packageName)
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.getOrElse {
            false
        }
    }

    private fun findServiceConnection(link: CXRLink): ServiceConnection {
        var type: Class<*>? = link.javaClass
        while (type != null) {
            val field = type.declaredFields.firstOrNull { field ->
                ServiceConnection::class.java.isAssignableFrom(field.type)
            }
            if (field != null) {
                field.isAccessible = true
                return field.get(link) as ServiceConnection
            }
            type = type.superclass
        }
        error("CXR-L ServiceConnection field not found")
    }

    private suspend fun stageApk(uri: Uri): File = withContext(Dispatchers.IO) {
        val dir = File(appContext.cacheDir, "hi-rokid-hud-upload").apply { mkdirs() }
        val file = File(dir, "jsos-hud-${System.currentTimeMillis()}.apk")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Cannot open selected APK")
        file
    }

    private fun readPackageName(apkFile: File): String {
        @Suppress("DEPRECATION")
        val info = appContext.packageManager.getPackageArchiveInfo(
            apkFile.absolutePath,
            PackageManager.GET_ACTIVITIES,
        )
        return info?.packageName?.takeIf { it.isNotBlank() }
            ?: error("Cannot read APK package name")
    }

    private fun postStatus(onStatus: (String) -> Unit, message: String) {
        mainHandler.post { onStatus(message) }
    }
}
