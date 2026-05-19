package com.jsos.phone.glasses

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.jsos.phone.BuildConfig
import com.rokid.cxr.Caps
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.extend.callbacks.BluetoothStatusCallback
import com.rokid.cxr.client.extend.callbacks.PhotoResultCallback
import com.rokid.cxr.client.extend.listeners.BrightnessUpdateListener
import com.rokid.cxr.client.extend.listeners.CustomCmdListener
import com.rokid.cxr.client.utils.ValueUtil
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Manages Rokid CXR-M SDK initialization and lifecycle.
 *
 * Connection flow:
 * 1. initialize(context) - Get CxrApi singleton, set up listeners
 * 2. initBluetooth(device) - Start Bluetooth init with discovered device
 *    -> callback.onConnectionInfo(socketUuid, macAddress, rokidAccount, glassesType)
 * 3. connectBluetooth(socketUuid, macAddress) - Complete connection
 *    -> callback.onConnected()
 * 4. Keep Bluetooth connected for messages, wake controls, voice, and camera.
 *
 * SN verification: The SDK performs an AES-encrypted serial number check after
 * Bluetooth connects. On first attempt, SN_CHECK_FAILED is expected - we read
 * the glasses SN from the SDK via reflection, generate the correct encrypted
 * content, and reconnect automatically.
 */
object RokidSdkManager {

    private const val TAG = "RokidSdkManager"
    private const val BLUETOOTH_CLIENT_NAME = "JSOS Core"
    private const val PREFS_NAME = "jsos_rokid_prefs"
    private const val KEY_PREFERRED_GLASS_BRIGHTNESS = "preferred_glass_brightness"
    private const val DEFAULT_GLASS_BRIGHTNESS = 0
    const val GLASS_BRIGHTNESS_MIN = 0
    const val GLASS_BRIGHTNESS_MAX = 15

    private var isInitialized = false
    private var cxrApi: CxrApi? = null
    private var appContext: Context? = null

    // Connection state
    private var isBluetoothConnectedState = false

    // Saved connection info for reconnection
    private var savedSocketUuid: String? = null
    private var savedMacAddress: String? = null
    private var savedRokidAccount: String? = null
    private var savedDeviceName: String? = null

    // Track if we're in init phase (need to call connectBluetooth after getting info)
    private var pendingConnect = false

    private val _preferredGlassBrightness = MutableStateFlow(DEFAULT_GLASS_BRIGHTNESS)
    val preferredGlassBrightness: StateFlow<Int> = _preferredGlassBrightness.asStateFlow()

    // Last brightness reported by the glasses. The Core slider remains the preferred wake/apply value.
    private var lastKnownBrightness: Int = DEFAULT_GLASS_BRIGHTNESS

    // SN auto-generation: first attempt fails, we read the SN and retry
    private var snAutoRetryInProgress = false
    // Generated snEncryptContent for retry (stored after first SN_CHECK_FAILED)
    private var generatedSnEncryptContent: ByteArray? = null

    // Callbacks for glasses events
    var onGlassesConnected: (() -> Unit)? = null
    var onGlassesDisconnected: (() -> Unit)? = null
    var onMessageFromGlasses: ((String, Caps?) -> Unit)? = null
    var onConnectionInfo: ((name: String, mac: String, account: String, type: Int) -> Unit)? = null
    var onBluetoothFailed: ((String) -> Unit)? = null

    // AI scene callbacks (voice input via glasses long-press)
    var onAiKeyDown: (() -> Unit)? = null
    var onAiKeyUp: (() -> Unit)? = null
    var onAiExit: (() -> Unit)? = null

    // Photo capture callback
    var onPhotoResult: ((status: ValueUtil.CxrStatus?, photoBytes: ByteArray?) -> Unit)? = null

    private val bluetoothCallback = object : BluetoothStatusCallback {
        override fun onConnectionInfo(socketUuid: String?, macAddress: String?, rokidAccount: String?, deviceType: Int) {
            Log.i(TAG, "Connection info received from Rokid SDK (deviceType=$deviceType)")

            // Save for reconnection (both in memory and to SharedPreferences)
            savedSocketUuid = socketUuid
            savedMacAddress = macAddress
            savedRokidAccount = rokidAccount
            if (!socketUuid.isNullOrEmpty() && !macAddress.isNullOrEmpty()) {
                saveConnectionInfo(socketUuid, macAddress)
            }
            // Try to save device name from Bluetooth device
            try {
                val name = cxrApi?.let { api ->
                    val glassInfoField = api.javaClass.getDeclaredField("I")
                    glassInfoField.isAccessible = true
                    val glassInfo = glassInfoField.get(api)
                    glassInfo?.javaClass?.getMethod("getDeviceName")?.invoke(glassInfo) as? String
                }
                if (!name.isNullOrEmpty()) {
                    savedDeviceName = name
                    cachedDeviceName = name
                    saveDeviceName(name)
                    Log.i(TAG, "  device name received")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Could not read device name from GlassInfo: ${e.message}")
            }
            onConnectionInfo?.invoke(socketUuid ?: "", macAddress ?: "", rokidAccount ?: "", deviceType)

            // After initBluetooth, call connectBluetooth to complete the connection
            if (pendingConnect && !socketUuid.isNullOrEmpty() && !macAddress.isNullOrEmpty()) {
                Log.i(TAG, "Got connection info, now calling connectBluetooth...")
                pendingConnect = false
                connectBluetoothInternal(socketUuid, macAddress, rokidAccount ?: "")
            }
        }

        override fun onConnected() {
            Log.i(TAG, "=== onConnected === Bluetooth connected to glasses!")
            isBluetoothConnectedState = true
            pendingConnect = false
            snAutoRetryInProgress = false
            onGlassesConnected?.invoke()
        }

        override fun onInActiveConnected(socketUuid: String?, macAddress: String?) {
            Log.i(TAG, "Inactive Bluetooth connection callback received")
        }

        override fun onDisconnected() {
            Log.i(TAG, "=== onDisconnected === Bluetooth disconnected from glasses")
            isBluetoothConnectedState = false
            onGlassesDisconnected?.invoke()
        }

        override fun onFailed(errorCode: ValueUtil.CxrBluetoothErrorCode?) {
            Log.e(TAG, "=== onFailed === Bluetooth connection failed: $errorCode")
            isBluetoothConnectedState = false
            pendingConnect = false

            // SN_CHECK_FAILED means BT connected but SN verification failed.
            // Read the glasses SN from the SDK, generate encrypted content, and retry.
            if (errorCode == ValueUtil.CxrBluetoothErrorCode.SN_CHECK_FAILED && !snAutoRetryInProgress) {
                Log.i(TAG, "SN_CHECK_FAILED - attempting auto-recovery...")
                val glassesSn = readGlassesSnFromSdk()
                if (glassesSn != null && glassesSn.isNotEmpty()) {
                    val clientSecret = BuildConfig.ROKID_CLIENT_SECRET.replace("-", "")
                    val encrypted = generateSnEncryptContent(glassesSn, clientSecret)
                    if (encrypted != null) {
                        Log.i(TAG, "Generated snEncryptContent (${encrypted.size} bytes)")
                        generatedSnEncryptContent = encrypted
                        saveCachedSnEncryptContent(encrypted, glassesSn)
                        snAutoRetryInProgress = true
                        // Retry connection with correct snEncryptContent
                        val uuid = savedSocketUuid
                        val mac = savedMacAddress
                        if (!uuid.isNullOrEmpty() && !mac.isNullOrEmpty()) {
                            Log.i(TAG, "Retrying connectBluetooth with generated snEncryptContent...")
                            connectBluetoothInternal(uuid, mac, savedRokidAccount ?: "")
                            return
                        }
                    }
                }
                Log.e(TAG, "SN auto-recovery failed - could not read glasses SN or generate encrypted content")
            }

            snAutoRetryInProgress = false
            onBluetoothFailed?.invoke(errorCode?.name ?: "Unknown error")
        }
    }

    /**
     * Initialize the CxrApi singleton and set up listeners.
     * Registers the access key for SN verification during Bluetooth connection.
     */
    fun initialize(context: Context): Boolean {
        if (isInitialized) {
            Log.d(TAG, "SDK already initialized")
            return true
        }

        appContext = context.applicationContext
        loadPreferredGlassBrightness()

        // Load cached SN encrypt content from previous session
        loadCachedSnEncryptContent()

        // Load saved connection info for auto-reconnect
        loadSavedConnectionInfo()

        try {
            cxrApi = CxrApi.getInstance()

            // Register access key for SN verification (required for connectBluetooth)
            val accessKey = BuildConfig.ROKID_ACCESS_KEY
            if (accessKey.isNotEmpty()) {
                cxrApi?.updateRokidAccount(accessKey)
                Log.d(TAG, "Rokid account registered (accessKey length=${accessKey.length})")
            } else {
                Log.w(TAG, "No ROKID_ACCESS_KEY configured - SN verification may fail")
            }

            // Set up custom command listener to receive messages from glasses
            // The glasses sends via bridge.sendMessage(msgType, caps) where caps contains the actual data.
            // Here, cmd = the message type (e.g. "command"), and caps holds the content string at index 0.
            cxrApi?.setCustomCmdListener(object : CustomCmdListener {
                override fun onCustomCmd(cmd: String?, caps: Caps?) {
                    Log.d(TAG, "Received custom command from glasses: type=$cmd, caps=${caps != null}")
                    if (caps != null && caps.size() > 0) {
                        try {
                            val message = caps.at(0).getString()
                            Log.d(TAG, "Glasses message content received (${message.length} chars)")
                            onMessageFromGlasses?.invoke(message, caps)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to read message from Caps", e)
                            cmd?.let { onMessageFromGlasses?.invoke(it, caps) }
                        }
                    } else {
                        cmd?.let { onMessageFromGlasses?.invoke(it, caps) }
                    }
                }
            })

            // Set up AI event listener for glasses long-press voice activation
            cxrApi?.setAiEventListener(object : com.rokid.cxr.client.extend.listeners.AiEventListener {
                override fun onAiKeyDown() {
                    Log.i(TAG, "AI key pressed on glasses (long press)")
                    onAiKeyDown?.invoke()
                }
                override fun onAiKeyUp() {
                    Log.d(TAG, "AI key released on glasses")
                    onAiKeyUp?.invoke()
                }
                override fun onAiExit() {
                    Log.d(TAG, "AI scene exited on glasses")
                    onAiExit?.invoke()
                }
            })

            // Track glasses brightness changes so we can restore the user's
            // preferred level when waking the display from standby.
            cxrApi?.setBrightnessUpdateListener(object : BrightnessUpdateListener {
                override fun onBrightnessUpdated(brightness: Int) {
                    val sanitized = brightness.coerceIn(GLASS_BRIGHTNESS_MIN, GLASS_BRIGHTNESS_MAX)
                    Log.d(TAG, "Glasses brightness updated: $sanitized")
                    lastKnownBrightness = sanitized
                }
            })

            Log.d(TAG, "Rokid SDK initialized successfully")
            isInitialized = true
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Rokid SDK", e)
            return false
        }
    }

    /**
     * Initialize Bluetooth connection with a discovered device.
     * This triggers onConnectionInfo callback, then we automatically call
     * connectBluetooth to complete the connection.
     */
    fun initBluetooth(device: BluetoothDevice) {
        val context = appContext ?: run {
            Log.e(TAG, "SDK not initialized")
            return
        }

        try {
            Log.i(TAG, "Starting Bluetooth init for selected device")
            pendingConnect = true
            cxrApi?.initBluetooth(context, device, bluetoothCallback)
            Log.i(TAG, "initBluetooth called, waiting for onConnectionInfo callback...")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Bluetooth", e)
            pendingConnect = false
        }
    }

    /**
     * Connect using socketUuid and macAddress from onConnectionInfo.
     *
     * SDK signature: connectBluetooth(context, socketUuid, macAddress, bluetoothClientName, callback, snEncryptContent, clientSecret)
     *
     * The SDK performs an SN verification after BT connects:
     * 1. Gets glasses SN via getGlassInfo
     * 2. Decrypts snEncryptContent with clientSecret (AES/CBC/PKCS5Padding)
     * 3. Checks if decrypted text contains the glasses SN
     *
     * On first connect we pass empty snEncryptContent, which triggers SN_CHECK_FAILED.
     * The onFailed handler then reads the SN via reflection and auto-retries with
     * correctly generated encrypted content.
     */
    private fun connectBluetoothInternal(socketUuid: String, macAddress: String, rokidAccount: String = "") {
        val context = appContext ?: run {
            Log.e(TAG, "SDK not initialized")
            return
        }

        try {
            val clientSecret = BuildConfig.ROKID_CLIENT_SECRET

            Log.i(TAG, "Connecting via Bluetooth (autoRetry=$snAutoRetryInProgress, cachedSn=${generatedSnEncryptContent != null})")

            // Use cached snEncryptContent if available (from previous SN auto-recovery).
            // Only use dummy content on the very first connection attempt when we don't
            // know the glasses SN yet. This avoids a redundant two-pass flow on reconnects.
            val encryptContent = if (generatedSnEncryptContent != null) {
                Log.i(TAG, "Using cached snEncryptContent (${generatedSnEncryptContent!!.size} bytes)")
                generatedSnEncryptContent!!
            } else {
                Log.i(TAG, "First attempt - using dummy snEncryptContent (SN_CHECK_FAILED expected)")
                ByteArray(16)
            }

            cxrApi?.connectBluetooth(
                context,
                socketUuid,
                macAddress,
                BLUETOOTH_CLIENT_NAME,
                bluetoothCallback,
                encryptContent,
                clientSecret
            )
            Log.i(TAG, "connectBluetooth called, waiting for callback...")
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting via Bluetooth", e)
        }
    }

    /**
     * Connect to glasses via Bluetooth using saved connection info.
     */
    fun connectBluetooth(socketUuid: String, macAddress: String) {
        connectBluetoothInternal(socketUuid, macAddress)
    }

    // ============== SN Auto-Generation Helpers ==============

    /**
     * Read the glasses serial number from CxrApi's internal GlassInfo field (field I).
     * The SDK populates this in the getGlassInfo response handler, which runs
     * before the SN check - so even on SN_CHECK_FAILED, the SN is available.
     */
    private fun readGlassesSnFromSdk(): String? {
        return try {
            val api = cxrApi ?: return null
            // CxrApi stores GlassInfo in field 'I'
            val glassInfoField = api.javaClass.getDeclaredField("I")
            glassInfoField.isAccessible = true
            val glassInfo = glassInfoField.get(api) ?: run {
                Log.w(TAG, "GlassInfo field I is null")
                return null
            }
            // GlassInfo.getDeviceId() returns the serial number
            val getDeviceId = glassInfo.javaClass.getMethod("getDeviceId")
            val sn = getDeviceId.invoke(glassInfo) as? String
            Log.i(TAG, "Read glasses SN from SDK")
            sn
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read glasses SN from SDK via reflection", e)
            null
        }
    }

    /**
     * Generate snEncryptContent by encrypting the glasses SN using the same
     * algorithm the SDK uses for verification: AES/CBC/PKCS5Padding.
     *
     * Key = clientSecret bytes (32 chars = 32 bytes for AES-256)
     * IV = first 16 bytes of clientSecret
     */
    private fun generateSnEncryptContent(glassesSn: String, clientSecret: String): ByteArray? {
        return try {
            val keyBytes = clientSecret.toByteArray(Charsets.UTF_8)
            val key = SecretKeySpec(keyBytes, "AES")
            val iv = IvParameterSpec(keyBytes, 0, 16)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key, iv)
            cipher.doFinal(glassesSn.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate snEncryptContent", e)
            null
        }
    }


    // ============== SN Persistence ==============

    private const val SN_PREFS = "jsos_glasses_sn"
    private const val SN_KEY = "sn_encrypt_content"
    private const val SN_PLAIN_KEY = "sn_plain"
    private const val DEVICE_NAME_KEY = "device_name"
    private const val SOCKET_UUID_KEY = "socket_uuid"
    private const val MAC_ADDRESS_KEY = "mac_address"
    private var cachedSnPlain: String? = null
    private var cachedDeviceName: String? = null

    private fun saveCachedSnEncryptContent(encrypted: ByteArray, plainSn: String? = null) {
        val ctx = appContext ?: return
        val base64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        ctx.getSharedPreferences(SN_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(SN_KEY, base64)
            .apply {
                if (plainSn != null) {
                    putString(SN_PLAIN_KEY, plainSn)
                    cachedSnPlain = plainSn
                }
            }
            .apply()
        Log.i(TAG, "Saved SN encrypt content to SharedPreferences")
    }

    private fun loadCachedSnEncryptContent() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(SN_PREFS, Context.MODE_PRIVATE)
        val base64 = prefs.getString(SN_KEY, null) ?: return
        try {
            generatedSnEncryptContent = Base64.decode(base64, Base64.NO_WRAP)
            cachedSnPlain = prefs.getString(SN_PLAIN_KEY, null)
            cachedDeviceName = prefs.getString(DEVICE_NAME_KEY, null)
            Log.i(TAG, "Loaded cached SN encrypt content (${generatedSnEncryptContent!!.size} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cached SN encrypt content", e)
        }
    }

    private fun saveDeviceName(name: String) {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(SN_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(DEVICE_NAME_KEY, name)
            .apply()
    }

    /**
     * Save connection info (socketUuid, macAddress) for auto-reconnection.
     * Called after successful Bluetooth connection.
     */
    private fun saveConnectionInfo(socketUuid: String, macAddress: String) {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(SN_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(SOCKET_UUID_KEY, socketUuid)
            .putString(MAC_ADDRESS_KEY, macAddress)
            .apply()
        Log.i(TAG, "Saved connection info for auto-reconnect")
    }

    /**
     * Load saved connection info from SharedPreferences.
     * Called during SDK initialization.
     */
    private fun loadSavedConnectionInfo() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(SN_PREFS, Context.MODE_PRIVATE)
        savedSocketUuid = prefs.getString(SOCKET_UUID_KEY, null)
        savedMacAddress = prefs.getString(MAC_ADDRESS_KEY, null)
        if (savedSocketUuid != null && savedMacAddress != null) {
            Log.i(TAG, "Loaded saved connection info")
        }
    }

    /**
     * Check if we have saved connection info for auto-reconnection.
     */
    fun hasSavedConnectionInfo(): Boolean {
        return !savedSocketUuid.isNullOrEmpty() && !savedMacAddress.isNullOrEmpty()
    }

    /**
     * Clear the cached glasses serial number and connection info.
     * Call this if connecting to different glasses or if SN verification fails persistently.
     */
    fun clearCachedSn() {
        generatedSnEncryptContent = null
        cachedSnPlain = null
        cachedDeviceName = null
        savedSocketUuid = null
        savedMacAddress = null
        val ctx = appContext ?: return
        ctx.getSharedPreferences(SN_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(SN_KEY)
            .remove(SN_PLAIN_KEY)
            .remove(DEVICE_NAME_KEY)
            .remove(SOCKET_UUID_KEY)
            .remove(MAC_ADDRESS_KEY)
            .apply()
        Log.i(TAG, "Cleared cached SN and connection info")
    }

    /**
     * Check whether a cached glasses SN exists.
     */
    fun hasCachedSn(): Boolean = generatedSnEncryptContent != null

    /**
     * Get the cached plain-text glasses serial number, if available.
     */
    fun getCachedSn(): String? = cachedSnPlain

    /**
     * Get the cached device name (e.g., "Rokid Max 2"), if available.
     */
    fun getCachedDeviceName(): String? = cachedDeviceName

    /**
     * Send a custom command/message to the glasses via Bluetooth
     */
    fun sendToGlasses(command: String, caps: Caps = Caps()): Boolean {
        if (!isInitialized) {
            Log.e(TAG, "SDK not initialized")
            return false
        }

        if (!isBluetoothConnectedState) {
            Log.e(TAG, "Not connected to glasses via Bluetooth")
            return false
        }

        return try {
            caps.write(command)
            cxrApi?.sendCustomCmd("terminal", caps)
            Log.d(TAG, "Sent to glasses: ${command.length} chars")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message to glasses", e)
            false
        }
    }

    // ============== Status Methods ==============

    fun isReady(): Boolean = isInitialized

    fun isConnected(): Boolean {
        return try {
            cxrApi?.isBluetoothConnected ?: false
        } catch (e: Exception) {
            isBluetoothConnectedState
        }
    }

    fun getSavedMacAddress(): String? = savedMacAddress
    fun getSavedDeviceName(): String? = savedDeviceName
    fun getSavedSocketUuid(): String? = savedSocketUuid

    /**
     * Attempt to reconnect to previously connected glasses.
     *
     * Alternates between two strategies based on the attempt number:
     * - Odd attempts: initBluetooth with BluetoothDevice from saved MAC (full SDK init path,
     *   required on cold start when SDK has no internal BT state)
     * - Even attempts: connectBluetooth with saved socketUuid/macAddress (fast path,
     *   works when SDK state is still alive from a previous session)
     */
    fun reconnect(attempt: Int = 1): Boolean {
        val mac = savedMacAddress
        if (mac.isNullOrEmpty()) {
            Log.w(TAG, "No saved MAC address for reconnection")
            return false
        }

        if (cxrApi == null) {
            Log.e(TAG, "CxrApi is null - cannot reconnect")
            return false
        }

        val context = appContext ?: run {
            Log.e(TAG, "SDK not initialized - cannot reconnect")
            return false
        }

        // Alternate strategies: odd attempts use initBluetooth (full path),
        // even attempts use connectBluetooth (fast path)
        val useInitBluetooth = attempt % 2 == 1

        if (useInitBluetooth) {
            try {
                val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE)
                    as? android.bluetooth.BluetoothManager)?.adapter
                if (adapter != null && adapter.isEnabled) {
                    val device = adapter.getRemoteDevice(mac)
                    Log.i(TAG, "Reconnect attempt $attempt: initBluetooth")
                    pendingConnect = true
                    cxrApi?.initBluetooth(context, device, bluetoothCallback)
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "initBluetooth failed during reconnect: ${e.message}")
            }
        }

        // Fast path or fallback: connectBluetooth with saved credentials
        val socketUuid = savedSocketUuid
        if (!socketUuid.isNullOrEmpty()) {
            Log.i(TAG, "Reconnect attempt $attempt: connectBluetooth")
            connectBluetoothInternal(socketUuid, mac, savedRokidAccount ?: "")
            return true
        }

        Log.w(TAG, "No viable reconnection path")
        return false
    }

    /**
     * Disconnect from glasses
     */
    fun disconnect() {
        try {
            cxrApi?.deinitBluetooth()
            isBluetoothConnectedState = false
            Log.d(TAG, "Disconnected from glasses")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting", e)
        }
    }

    /**
     * Set audio as communication device (for voice input via glasses mic)
     */
    fun setCommunicationDevice() {
        cxrApi?.setCommunicationDevice()
    }

    /**
     * Clear communication device setting
     */
    fun clearCommunicationDevice() {
        cxrApi?.clearCommunicationDevice()
    }

    // --- AI Scene methods (for voice input via glasses long-press) ---

    /**
     * Send ASR (speech recognition) content to the glasses AI scene.
     * The glasses display this text in the AI scene UI.
     */
    fun sendAsrContent(content: String): ValueUtil.CxrStatus? {
        Log.d(TAG, "Skipping Rokid AI scene ASR overlay (${content.length} chars)")
        return null
    }

    /**
     * Notify glasses that ASR recognition returned no result.
     */
    fun notifyAsrNone(): ValueUtil.CxrStatus? {
        Log.d(TAG, "Notifying glasses: ASR none")
        return cxrApi?.notifyAsrNone()
    }

    /**
     * Notify glasses that ASR recognition had an error.
     */
    fun notifyAsrError(): ValueUtil.CxrStatus? {
        Log.d(TAG, "Notifying glasses: ASR error")
        return cxrApi?.notifyAsrError()
    }

    /**
     * Notify glasses that ASR recognition has ended.
     */
    fun notifyAsrEnd(): ValueUtil.CxrStatus? {
        Log.d(TAG, "Notifying glasses: ASR end")
        return cxrApi?.notifyAsrEnd()
    }

    /**
     * Send exit event to dismiss the AI scene on glasses.
     */
    fun sendExitEvent(): ValueUtil.CxrStatus? {
        Log.d(TAG, "Sending exit event to glasses AI scene")
        return cxrApi?.sendExitEvent()
    }

    /**
     * Send TTS content to the glasses AI scene (for displaying AI response text).
     */
    fun sendTtsContent(content: String): ValueUtil.CxrStatus? {
        Log.d(TAG, "Sending TTS content to glasses (${content.length} chars)")
        return cxrApi?.sendTtsContent(content)
    }

    /**
     * Notify glasses that TTS audio has finished.
     */
    fun notifyTtsAudioFinished(): ValueUtil.CxrStatus? {
        Log.d(TAG, "Notifying glasses: TTS finished")
        return cxrApi?.notifyTtsAudioFinished()
    }

    fun setPreferredGlassBrightness(brightness: Int): Boolean {
        val sanitized = brightness.coerceIn(GLASS_BRIGHTNESS_MIN, GLASS_BRIGHTNESS_MAX)
        _preferredGlassBrightness.value = sanitized
        savePreferredGlassBrightness(sanitized)
        return applyPreferredGlassBrightness()
    }

    fun getPreferredGlassBrightness(): Int = _preferredGlassBrightness.value

    fun applyPreferredGlassBrightness(): Boolean {
        if (!isInitialized || !isBluetoothConnectedState) {
            Log.d(TAG, "Cannot apply glasses brightness: init=$isInitialized, bt=$isBluetoothConnectedState")
            return false
        }
        val brightness = _preferredGlassBrightness.value.coerceIn(GLASS_BRIGHTNESS_MIN, GLASS_BRIGHTNESS_MAX)
        return try {
            cxrApi?.setGlassBrightness(brightness)
            lastKnownBrightness = brightness
            Log.i(TAG, "Applied glasses brightness: $brightness")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply glasses brightness", e)
            false
        }
    }

    private fun loadPreferredGlassBrightness() {
        val brightness = appContext
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.getInt(KEY_PREFERRED_GLASS_BRIGHTNESS, DEFAULT_GLASS_BRIGHTNESS)
            ?: DEFAULT_GLASS_BRIGHTNESS
        val sanitized = brightness.coerceIn(GLASS_BRIGHTNESS_MIN, GLASS_BRIGHTNESS_MAX)
        _preferredGlassBrightness.value = sanitized
        lastKnownBrightness = sanitized
    }

    private fun savePreferredGlassBrightness(brightness: Int) {
        appContext
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putInt(KEY_PREFERRED_GLASS_BRIGHTNESS, brightness)
            ?.apply()
    }
    // --- Screen off timeout & wake ---

    /**
     * Configure the glasses idle screen-off timeout via CXR-M SDK.
     * The hardware turns off the display after [seconds] of inactivity
     * and wakes on user interaction.
     */
    fun setScreenOffTimeout(seconds: Long): ValueUtil.CxrStatus? {
        Log.d(TAG, "Setting screen off timeout to ${seconds}s")
        return cxrApi?.setScreenOffTimeout(seconds)
    }

    /**
     * Wake the glasses display from standby by setting brightness and
     * resetting the screen-off timeout via CXR-M SDK.
     *
     * The Rokid micro-LED display is controlled from the phone side - Android
     * PowerManager on the glasses does NOT control it. This method uses
     * setGlassBrightness() to turn the display on and setScreenOffTimeout()
     * to reset the idle timer so it stays on for another 30 seconds.
     *
     * Safe to call repeatedly - setting brightness when already at that
     * level is effectively a no-op.
     */
    fun wakeGlassesScreen(): Boolean {
        if (!isInitialized || !isBluetoothConnectedState) {
            Log.d(TAG, "Cannot wake glasses screen: init=$isInitialized, bt=$isBluetoothConnectedState")
            return false
        }
        return try {
            val brightness = _preferredGlassBrightness.value.coerceIn(GLASS_BRIGHTNESS_MIN, GLASS_BRIGHTNESS_MAX)
            cxrApi?.setGlassBrightness(brightness)
            cxrApi?.setScreenOffTimeout(30)
            lastKnownBrightness = brightness
            Log.i(TAG, "Wake glasses screen: brightness=$brightness, timeout reset to 30s")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wake glasses screen", e)
            false
        }
    }

    // --- Camera methods (for AI photo capture via glasses camera) ---

    private val photoResultCallback = object : PhotoResultCallback {
        override fun onPhotoResult(status: ValueUtil.CxrStatus?, photo: ByteArray?) {
            Log.d(TAG, "Photo result: status=$status, bytes=${photo?.size}")
            onPhotoResult?.invoke(status, photo)
        }
    }

    fun openGlassCamera(width: Int = 1280, height: Int = 720, quality: Int = 75): ValueUtil.CxrStatus? {
        Log.d(TAG, "Opening glass camera: ${width}x${height} quality=$quality")
        return cxrApi?.openGlassCamera(width, height, quality)
    }

    fun takeGlassPhoto(width: Int = 1280, height: Int = 720, quality: Int = 75): ValueUtil.CxrStatus? {
        Log.d(TAG, "Taking glass photo: ${width}x${height} quality=$quality")
        return cxrApi?.takeGlassPhoto(width, height, quality, photoResultCallback)
    }

    fun takeGlassPhotoGlobal(width: Int = 1280, height: Int = 720, quality: Int = 75): ValueUtil.CxrStatus? {
        Log.d(TAG, "Taking glass photo (global): ${width}x${height} quality=$quality")
        return cxrApi?.takeGlassPhotoGlobal(width, height, quality, photoResultCallback)
    }

    /**
     * Cleanup SDK resources
     */
    fun cleanup() {
        if (!isInitialized) return

        try {
            cxrApi?.clearCommunicationDevice()
            cxrApi = null
            appContext = null
            isInitialized = false
            isBluetoothConnectedState = false
            Log.d(TAG, "Rokid SDK cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up Rokid SDK", e)
        }
    }
}
