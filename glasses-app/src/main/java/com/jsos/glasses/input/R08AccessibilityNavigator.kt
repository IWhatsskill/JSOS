package com.jsos.glasses.input

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.media.AudioManager
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jsos.glasses.rokid.RokidArCommands
import kotlin.math.abs
import kotlin.math.roundToInt

internal class R08AccessibilityNavigator(
    private val service: AccessibilityService
) {
    private val gestures = R08GestureDispatcher(service)
    private val launcherNavigator = R08LauncherNavigator(service, gestures)
    private var bluetoothMediaBrowser: MediaBrowser? = null
    private var bluetoothMediaBrowserConnectRequested = false
    private var bluetoothMediaController: MediaController? = null
    private var bluetoothPlaybackState: Int? = null
    private var musicPagePlayPauseStartState: Int? = null
    private var currentPackageName: String = ""
    private var currentClassName: String = ""

    fun setCurrentWindow(packageName: String, className: String) {
        currentPackageName = packageName
        currentClassName = className
        Log.d(TAG, "window package=$packageName class=$className")
        if (isRokidLauncherMusicPageActive()) {
            ensureBluetoothMediaController()
        }
    }

    fun moveForward() {
        if (launcherNavigator.isActive()) {
            launcherNavigator.move(forward = true)
            return
        }
        if (handleRokidSettingControl(delta = 1)) return
        if (moveFocus(forward = true)) return
        if (tryScroll(forward = true)) return
        gestures.verticalSwipe(forward = true)
    }

    fun moveBackward() {
        if (launcherNavigator.isActive()) {
            launcherNavigator.move(forward = false)
            return
        }
        if (handleRokidSettingControl(delta = -1)) return
        if (moveFocus(forward = false)) return
        if (tryScroll(forward = false)) return
        gestures.verticalSwipe(forward = false)
    }

    fun activate() {
        if (activateCameraShutter()) {
            return
        }
        if (isRokidLauncherMusicPageActive() && dispatchMusicPagePlayPause()) {
            return
        }
        if (isRokidLauncherActive() && !isRokidLauncherAppCarouselActive() && dispatchBluetoothPlayPause()) {
            return
        }
        if (launcherNavigator.isActive() && launcherNavigator.activateCenter()) {
            return
        }
        var current = findCurrentFocus()
        if (current == null) {
            val nodes = collectCandidates()
            if (nodes.isNotEmpty()) {
                current = nodes.first()
                focusNode(current)
            }
        }
        val clickable = findClickable(current)
        if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.d(TAG, "clicked focused node")
            return
        }
        val bounds = Rect()
        current?.getBoundsInScreen(bounds)
        if (!bounds.isEmpty) {
            gestures.tap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        } else {
            val metrics = service.resources.displayMetrics
            gestures.tap(metrics.widthPixels / 2f, metrics.heightPixels / 2f)
        }
    }

    fun back() {
        if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
            gestures.horizontalSwipe(forward = false)
        }
    }

    fun longPress() {
        if (launcherNavigator.isActive() && launcherNavigator.longPressCenter()) {
            return
        }
        var current = findCurrentFocus()
        if (current == null) {
            val nodes = collectCandidates()
            if (nodes.isNotEmpty()) {
                current = nodes.first()
                focusNode(current)
            }
        }
        val longClickable = findLongClickable(current)
        if (longClickable != null && longClickable.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
            Log.d(TAG, "long-clicked focused node")
            return
        }
        val bounds = Rect()
        current?.getBoundsInScreen(bounds)
        if (!bounds.isEmpty) {
            gestures.longPress(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        } else {
            val metrics = service.resources.displayMetrics
            gestures.longPress(metrics.widthPixels / 2f, metrics.heightPixels / 2f)
        }
    }

    fun openRokidAiAssist() {
        if (RokidArCommands.openAiAssist(service)) {
            return
        }
        longPress()
    }

    fun takeRokidPhoto() {
        if (RokidArCommands.takePhoto(service)) {
            return
        }
        Log.w(TAG, "Rokid photo scene request failed")
    }

    fun isRokidLauncherActive(): Boolean {
        return launcherNavigator.isActive()
    }

    fun isRokidLauncherAppCarouselActive(): Boolean {
        return launcherNavigator.hasVisibleAppCarousel()
    }

    fun isRokidLauncherMusicPageActive(): Boolean {
        return currentPackageName == ROKID_LAUNCHER_PACKAGE &&
            currentClassName == ROKID_LAUNCHER_MUSIC_PAGE_CLASS
    }

    fun isRokidCameraPageActive(): Boolean {
        if (currentPackageName != ROKID_ASSISTSERVER_PACKAGE) return false
        if (currentClassName == ROKID_CAMERA_PAGE_CLASS) return true
        val root = service.rootInActiveWindow ?: return false
        if (root.packageName?.toString() != ROKID_ASSISTSERVER_PACKAGE) return false
        return CAMERA_PAGE_VIEW_IDS.any { id ->
            root.findAccessibilityNodeInfosByViewId(id).any { node ->
                node.isVisibleToUser && node.isEnabled
            }
        }
    }

    fun isPackageActive(packageName: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        return root.packageName?.toString() == packageName
    }

    fun noteMusicPagePlayPauseKeyDown() {
        ensureBluetoothMediaController()
        musicPagePlayPauseStartState = currentBluetoothPlaybackState()
        Log.d(TAG, "music page tap start state=${stateName(musicPagePlayPauseStartState)}")
    }

    fun release() {
        bluetoothMediaController?.unregisterCallback(bluetoothControllerCallback)
        bluetoothMediaController = null
        bluetoothPlaybackState = null
        bluetoothMediaBrowser?.let { browser ->
            if (browser.isConnected || bluetoothMediaBrowserConnectRequested) {
                try {
                    browser.disconnect()
                } catch (_: RuntimeException) {
                    // Already disconnected by the framework.
                }
            }
        }
        bluetoothMediaBrowser = null
        bluetoothMediaBrowserConnectRequested = false
    }

    private fun activateCameraShutter(): Boolean {
        if (!isRokidCameraPageActive()) return false
        val intent = Intent(ROKID_ASSIST_COMMAND_ACTION).apply {
            putExtra("cmd_type", "control_scene")
            putExtra("scene", ROKID_SCENE_TAKE_PICTURE)
            putExtra("open", "true")
        }
        service.sendBroadcast(intent)
        Log.d(TAG, "camera shutter broadcast scene=$ROKID_SCENE_TAKE_PICTURE")
        return true
    }

    private fun handleRokidSettingControl(delta: Int): Boolean {
        if (currentPackageName != ROKID_LAUNCHER_PACKAGE) {
            return false
        }
        return when {
            currentClassName.endsWith(".page.volume.SettingVolumeActivity") -> {
                adjustVolume(delta)
                true
            }
            currentClassName.endsWith(".page.brightness.SettingBrightnessActivity") -> {
                adjustBrightness(delta)
                true
            }
            else -> false
        }
    }

    private fun dispatchMusicPagePlayPause(): Boolean {
        ensureBluetoothMediaController()
        val startState = musicPagePlayPauseStartState
        musicPagePlayPauseStartState = null
        val currentState = currentBluetoothPlaybackState()
        val controls = bluetoothMediaController?.transportControls
            ?: return dispatchMusicPagePlayPauseFallback(startState)
        when {
            isPlaying(startState) && isPlaying(currentState) -> {
                controls.pause()
                Log.d(TAG, "music page media command=pause start=${stateName(startState)} current=${stateName(currentState)}")
            }
            isPlaying(startState) && !isPlaying(currentState) -> {
                Log.d(TAG, "music page media command=none start=${stateName(startState)} current=${stateName(currentState)}")
            }
            !isPlaying(startState) && isPlaying(currentState) -> {
                Log.d(TAG, "music page media command=none start=${stateName(startState)} current=${stateName(currentState)}")
            }
            else -> {
                controls.play()
                Log.d(TAG, "music page media command=play start=${stateName(startState)} current=${stateName(currentState)}")
            }
        }
        return true
    }

    private fun dispatchMusicPagePlayPauseFallback(startState: Int?): Boolean {
        val audioManager = service.getSystemService(AudioManager::class.java) ?: return false
        val keyCode = if (isPlaying(startState)) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY
        val downTime = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0))
        Log.d(TAG, "music page media fallback key=${KeyEvent.keyCodeToString(keyCode)} start=${stateName(startState)}")
        return true
    }

    private fun dispatchBluetoothPlayPause(): Boolean {
        ensureBluetoothMediaController()
        val currentState = currentBluetoothPlaybackState()
        val controls = bluetoothMediaController?.transportControls
            ?: return dispatchMusicPagePlayPauseFallback(currentState)
        if (isPlaying(currentState)) {
            controls.pause()
            Log.d(TAG, "bluetooth media toggle command=pause current=${stateName(currentState)}")
        } else {
            controls.play()
            Log.d(TAG, "bluetooth media toggle command=play current=${stateName(currentState)}")
        }
        return true
    }

    private fun currentBluetoothPlaybackState(): Int? {
        bluetoothMediaController?.playbackState?.state?.let { state ->
            bluetoothPlaybackState = state
            return state
        }
        return bluetoothPlaybackState
    }

    private fun ensureBluetoothMediaController() {
        val existingBrowser = bluetoothMediaBrowser
        if (existingBrowser != null) {
            if (!existingBrowser.isConnected && !bluetoothMediaBrowserConnectRequested) {
                bluetoothMediaBrowserConnectRequested = true
                try {
                    existingBrowser.connect()
                } catch (error: RuntimeException) {
                    bluetoothMediaBrowserConnectRequested = false
                    Log.w(TAG, "bluetooth media connect failed")
                }
            }
            return
        }
        val browser = MediaBrowser(
            service,
            ComponentName(BLUETOOTH_MEDIA_PACKAGE, BLUETOOTH_MEDIA_BROWSER_CLASS),
            bluetoothConnectionCallback,
            null
        )
        bluetoothMediaBrowser = browser
        bluetoothMediaBrowserConnectRequested = true
        try {
            browser.connect()
        } catch (error: RuntimeException) {
            bluetoothMediaBrowserConnectRequested = false
            Log.w(TAG, "bluetooth media connect failed")
        }
    }

    private val bluetoothConnectionCallback = object : MediaBrowser.ConnectionCallback() {
        override fun onConnected() {
            bluetoothMediaBrowserConnectRequested = false
            val token = bluetoothMediaBrowser?.sessionToken ?: return
            bluetoothMediaController?.unregisterCallback(bluetoothControllerCallback)
            bluetoothMediaController = MediaController(service, token).also { controller ->
                controller.registerCallback(bluetoothControllerCallback)
                bluetoothPlaybackState = controller.playbackState?.state
            }
            Log.d(TAG, "bluetooth media connected state=${stateName(bluetoothPlaybackState)}")
        }

        override fun onConnectionSuspended() {
            bluetoothMediaBrowserConnectRequested = false
            bluetoothMediaController?.unregisterCallback(bluetoothControllerCallback)
            bluetoothMediaController = null
            Log.d(TAG, "bluetooth media suspended")
        }

        override fun onConnectionFailed() {
            bluetoothMediaBrowserConnectRequested = false
            bluetoothMediaController?.unregisterCallback(bluetoothControllerCallback)
            bluetoothMediaController = null
            Log.w(TAG, "bluetooth media connection failed")
        }
    }

    private val bluetoothControllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            bluetoothPlaybackState = state?.state
            Log.d(TAG, "bluetooth playback state=${stateName(bluetoothPlaybackState)}")
        }
    }

    private fun isPlaying(state: Int?): Boolean {
        return state == PlaybackState.STATE_PLAYING ||
            state == PlaybackState.STATE_BUFFERING ||
            state == PlaybackState.STATE_FAST_FORWARDING ||
            state == PlaybackState.STATE_REWINDING
    }

    private fun stateName(state: Int?): String {
        return when (state) {
            PlaybackState.STATE_NONE -> "none"
            PlaybackState.STATE_STOPPED -> "stopped"
            PlaybackState.STATE_PAUSED -> "paused"
            PlaybackState.STATE_PLAYING -> "playing"
            PlaybackState.STATE_FAST_FORWARDING -> "fast_forwarding"
            PlaybackState.STATE_REWINDING -> "rewinding"
            PlaybackState.STATE_BUFFERING -> "buffering"
            PlaybackState.STATE_ERROR -> "error"
            PlaybackState.STATE_CONNECTING -> "connecting"
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "skipping_previous"
            PlaybackState.STATE_SKIPPING_TO_NEXT -> "skipping_next"
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> "skipping_queue"
            null -> "unknown"
            else -> state.toString()
        }
    }

    private fun adjustVolume(delta: Int) {
        val audioManager = service.getSystemService(AudioManager::class.java) ?: return
        val min = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val next = (current + delta).coerceIn(min, max)
        if (next == current) return
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
        Log.d(TAG, "volume current=$current next=$next")
    }

    private fun adjustBrightness(delta: Int) {
        try {
            val resolver = service.contentResolver
            val currentRaw = Settings.System.getInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS,
                BRIGHTNESS_MIN_RAW
            ).coerceIn(BRIGHTNESS_MIN_RAW, BRIGHTNESS_MAX_RAW)
            val currentStep = (currentRaw.toFloat() * BRIGHTNESS_STEPS / BRIGHTNESS_MAX_RAW)
                .roundToInt()
                .coerceIn(BRIGHTNESS_MIN_STEP, BRIGHTNESS_STEPS)
            val nextStep = (currentStep + delta).coerceIn(BRIGHTNESS_MIN_STEP, BRIGHTNESS_STEPS)
            if (nextStep == currentStep) return
            val nextRaw = (nextStep.toFloat() * BRIGHTNESS_MAX_RAW / BRIGHTNESS_STEPS)
                .roundToInt()
                .coerceAtLeast(BRIGHTNESS_MIN_RAW)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, nextRaw)
            Log.d(TAG, "brightness raw=$currentRaw/$nextRaw step=$currentStep/$nextStep")
        } catch (error: SecurityException) {
            Log.w(TAG, "brightness write denied")
        } catch (error: RuntimeException) {
            Log.w(TAG, "brightness write failed")
        }
    }

    private fun moveFocus(forward: Boolean): Boolean {
        val start = SystemClock.uptimeMillis()
        val nodes = collectCandidates()
        if (nodes.isEmpty()) {
            logSlow("collect-empty", start)
            return false
        }
        val current = findCurrentFocus()
        val index = indexOf(nodes, current)
        if (index < 0) {
            val target = if (forward) nodes.first() else nodes.last()
            val focused = focusNode(target)
            logSlow("move-no-current", start)
            return focused
        }
        val nextIndex = index + if (forward) 1 else -1
        if (nextIndex in nodes.indices) {
            val focused = focusNode(nodes[nextIndex])
            logSlow("move-next", start)
            return focused
        }
        if (tryScrollFrom(current, forward)) {
            logSlow("scroll-current", start)
            return true
        }
        val wrap = if (forward) nodes.first() else nodes.last()
        val focused = focusNode(wrap)
        logSlow("move-wrap", start)
        return focused
    }

    private fun focusNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        clearAccessibilityFocus()
        val inputFocused = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val accessibilityFocused = node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        val rect = Rect().also(node::getBoundsInScreen)
        Log.d(TAG, "focus input=$inputFocused a11y=$accessibilityFocused bounds=$rect")
        return inputFocused || accessibilityFocused
    }

    private fun collectCandidates(): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectCandidates(service.rootInActiveWindow, nodes, TraversalBudget())
        return nodes.sortedWith(NodeComparator())
    }

    private fun collectCandidates(
        node: AccessibilityNodeInfo?,
        out: MutableList<AccessibilityNodeInfo>,
        budget: TraversalBudget
    ) {
        if (node == null || budget.exhausted()) return
        budget.visit()
        if (isCandidate(node)) {
            out += node
        }
        for (index in 0 until node.childCount) {
            collectCandidates(node.getChild(index), out, budget)
        }
    }

    private fun isCandidate(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser || !node.isEnabled) return false
        val rect = Rect().also(node::getBoundsInScreen)
        if (rect.width() < MIN_NODE_SIZE || rect.height() < MIN_NODE_SIZE) return false
        val metrics = service.resources.displayMetrics
        val huge = rect.width() > metrics.widthPixels * 0.95f && rect.height() > metrics.heightPixels * 0.85f
        if (huge && !node.isClickable && !supports(node, AccessibilityNodeInfo.ACTION_CLICK)) {
            return false
        }
        if (node.isClickable || supports(node, AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        if (node.isScrollable || isContainerClass(node)) {
            return false
        }
        if (hasActionableDescendant(node, TraversalBudget())) {
            return false
        }
        return node.isFocusable || supports(node, AccessibilityNodeInfo.ACTION_FOCUS)
    }

    private fun findCurrentFocus(): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        return root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
    }

    private fun clearAccessibilityFocus() {
        val root = service.rootInActiveWindow ?: return
        root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
    }

    private fun indexOf(nodes: List<AccessibilityNodeInfo>, current: AccessibilityNodeInfo?): Int {
        if (current == null) return -1
        val currentRect = Rect().also(current::getBoundsInScreen)
        val currentText = current.text?.toString()
        val currentDesc = current.contentDescription?.toString()
        return nodes.indexOfFirst { candidate ->
            val rect = Rect().also(candidate::getBoundsInScreen)
            rect == currentRect &&
                currentText == candidate.text?.toString() &&
                currentDesc == candidate.contentDescription?.toString()
        }
    }

    private fun tryScroll(forward: Boolean): Boolean {
        val current = findCurrentFocus()
        return tryScrollFrom(current, forward) || tryScrollTree(service.rootInActiveWindow, forward, TraversalBudget())
    }

    private fun tryScrollFrom(node: AccessibilityNodeInfo?, forward: Boolean): Boolean {
        var cursor = node
        while (cursor != null) {
            if (performScroll(cursor, forward)) {
                return true
            }
            cursor = cursor.parent
        }
        return false
    }

    private fun tryScrollTree(
        node: AccessibilityNodeInfo?,
        forward: Boolean,
        budget: TraversalBudget
    ): Boolean {
        if (node == null || budget.exhausted()) return false
        budget.visit()
        if (performScroll(node, forward)) return true
        for (index in 0 until node.childCount) {
            if (tryScrollTree(node.getChild(index), forward, budget)) return true
        }
        return false
    }

    private fun performScroll(node: AccessibilityNodeInfo?, forward: Boolean): Boolean {
        if (node == null) return false
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        if ((node.isScrollable || supports(node, action)) && node.performAction(action)) {
            Log.d(TAG, "scroll forward=$forward")
            return true
        }
        return false
    }

    private fun findClickable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var cursor = node
        while (cursor != null) {
            if ((cursor.isClickable || supports(cursor, AccessibilityNodeInfo.ACTION_CLICK)) && cursor.isEnabled) {
                return cursor
            }
            cursor = cursor.parent
        }
        return null
    }

    private fun findLongClickable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var cursor = node
        while (cursor != null) {
            if ((cursor.isLongClickable || supports(cursor, AccessibilityNodeInfo.ACTION_LONG_CLICK)) && cursor.isEnabled) {
                return cursor
            }
            cursor = cursor.parent
        }
        return null
    }

    private fun supports(node: AccessibilityNodeInfo?, actionId: Int): Boolean {
        return node?.actionList?.any { it.id == actionId } == true
    }

    private fun isContainerClass(node: AccessibilityNodeInfo): Boolean {
        val name = node.className?.toString() ?: return false
        return name.contains("RecyclerView") ||
            name.contains("GridView") ||
            name.contains("ListView") ||
            name.contains("ViewPager") ||
            name.contains("ScrollView")
    }

    private fun hasActionableDescendant(node: AccessibilityNodeInfo?, budget: TraversalBudget): Boolean {
        if (node == null || budget.exhausted()) return false
        budget.visit()
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            if (child.isVisibleToUser &&
                child.isEnabled &&
                (child.isClickable || supports(child, AccessibilityNodeInfo.ACTION_CLICK))
            ) {
                return true
            }
            if (hasActionableDescendant(child, budget)) {
                return true
            }
        }
        return false
    }

    private fun logSlow(phase: String, startMs: Long) {
        val elapsed = SystemClock.uptimeMillis() - startMs
        if (elapsed > 80) {
            Log.d(TAG, "$phase tookMs=$elapsed")
        }
    }

    private class TraversalBudget {
        private val deadline = SystemClock.uptimeMillis() + TREE_BUDGET_MS
        private var visited = 0

        fun visit() {
            visited++
        }

        fun exhausted(): Boolean {
            return visited >= MAX_TRAVERSED_NODES || SystemClock.uptimeMillis() > deadline
        }
    }

    private class NodeComparator : Comparator<AccessibilityNodeInfo> {
        override fun compare(left: AccessibilityNodeInfo, right: AccessibilityNodeInfo): Int {
            val a = Rect().also(left::getBoundsInScreen)
            val b = Rect().also(right::getBoundsInScreen)
            val topDelta = a.top - b.top
            if (abs(topDelta) > 18) return topDelta
            val leftDelta = a.left - b.left
            if (leftDelta != 0) return leftDelta
            val heightDelta = a.height() - b.height()
            if (heightDelta != 0) return heightDelta
            return a.width() - b.width()
        }
    }

    companion object {
        private const val TAG = "JSOSR08Navigator"
        private const val ROKID_LAUNCHER_PACKAGE = "com.rokid.os.sprite.launcher"
        private const val ROKID_LAUNCHER_MUSIC_PAGE_CLASS =
            "com.rokid.os.sprite.launcher.page.music.MusicPageActivity"
        private const val ROKID_ASSISTSERVER_PACKAGE = "com.rokid.os.sprite.assistserver"
        private const val ROKID_ASSIST_COMMAND_ACTION = "com.rokid.os.master.assist.server.cmd"
        private const val ROKID_SCENE_TAKE_PICTURE = "take_picture"
        private const val ROKID_CAMERA_PAGE_CLASS =
            "com.rokid.os.sprite.assist.media.page.CameraActivity"
        private val CAMERA_PAGE_VIEW_IDS = listOf(
            "com.rokid.os.sprite.assistserver:id/preview_content_lay",
            "com.rokid.os.sprite.assistserver:id/preview_texture_view"
        )
        private const val BLUETOOTH_MEDIA_PACKAGE = "com.android.bluetooth"
        private const val BLUETOOTH_MEDIA_BROWSER_CLASS =
            "com.android.bluetooth.avrcpcontroller.BluetoothMediaBrowserService"
        private const val MIN_NODE_SIZE = 6
        private const val MAX_TRAVERSED_NODES = 90
        private const val TREE_BUDGET_MS = 55L
        private const val BRIGHTNESS_MIN_STEP = 1
        private const val BRIGHTNESS_STEPS = 15
        private const val BRIGHTNESS_MIN_RAW = 1
        private const val BRIGHTNESS_MAX_RAW = 255
    }
}
