package com.jsos.glasses.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal class R08LauncherNavigator(
    private val service: AccessibilityService,
    private val gestures: R08GestureDispatcher
) {
    private val handler = Handler(Looper.getMainLooper())
    private var queuedSteps = 0
    private var queuedForward = true
    private var stepInFlight = false
    private var stepReleaseAt = 0L

    fun isActive(): Boolean {
        return findLauncherAppCarousel()?.isVisibleToUser == true || findLauncherViewPager() != null
    }

    fun hasVisibleAppCarousel(): Boolean {
        return findLauncherAppCarousel()?.isVisibleToUser == true
    }

    fun move(forward: Boolean, steps: Int = 1) {
        val appRecycler = findLauncherAppCarousel()
        if (appRecycler != null && appRecycler.isVisibleToUser) {
            enqueueLauncherSwipes(forward, steps)
            return
        }
        if (!performLauncherPageScroll(forward)) {
            Log.d(TAG, "launcher page did not accept scroll forward=$forward")
        }
    }

    fun activateCenter(): Boolean {
        val appRecycler = findLauncherAppCarousel()
        if (appRecycler != null && appRecycler.isVisibleToUser) {
            if (clickOrTapCenterNode(appRecycler, longPress = false)) {
                return true
            }
        }

        val target = findClickableNearestScreenCenter(service.rootInActiveWindow)
            ?: findFocusedClickable(service.rootInActiveWindow)
        if (target == null) {
            val metrics = service.resources.displayMetrics
            gestures.tap(metrics.widthPixels / 2f, metrics.heightPixels * 0.32f)
            return true
        }
        if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            val rect = Rect().also(target::getBoundsInScreen)
            Log.d(TAG, "clicked launcher center bounds=$rect")
            return true
        }
        val rect = Rect().also(target::getBoundsInScreen)
        if (!rect.isEmpty) {
            gestures.tap(rect.centerX().toFloat(), rect.centerY().toFloat())
            return true
        }
        return false
    }

    fun longPressCenter(): Boolean {
        val appRecycler = findLauncherAppCarousel()
        if (appRecycler != null && appRecycler.isVisibleToUser) {
            if (clickOrTapCenterNode(appRecycler, longPress = true)) {
                return true
            }
        }
        val target = findClickableNearestScreenCenter(service.rootInActiveWindow)
            ?: findFocusedClickable(service.rootInActiveWindow)
        if (target == null) {
            val metrics = service.resources.displayMetrics
            gestures.longPress(metrics.widthPixels / 2f, metrics.heightPixels * 0.32f)
            return true
        }
        if (target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
            return true
        }
        val rect = Rect().also(target::getBoundsInScreen)
        if (!rect.isEmpty) {
            gestures.longPress(rect.centerX().toFloat(), rect.centerY().toFloat())
            return true
        }
        return false
    }

    private fun clickOrTapCenterNode(appRecycler: AccessibilityNodeInfo, longPress: Boolean): Boolean {
        val target = findClickableNearestScreenCenter(appRecycler)
        if (target != null) {
            val action = if (longPress) {
                AccessibilityNodeInfo.ACTION_LONG_CLICK
            } else {
                AccessibilityNodeInfo.ACTION_CLICK
            }
            if (target.performAction(action)) {
                val rect = Rect().also(target::getBoundsInScreen)
                Log.d(TAG, "activated launcher app bounds=$rect long=$longPress")
                return true
            }
            val rect = Rect().also(target::getBoundsInScreen)
            if (!rect.isEmpty) {
                if (longPress) {
                    gestures.longPress(rect.centerX().toFloat(), rect.centerY().toFloat())
                } else {
                    gestures.tap(rect.centerX().toFloat(), rect.centerY().toFloat())
                }
                return true
            }
        }

        val bounds = Rect().also(appRecycler::getBoundsInScreen)
        if (bounds.isEmpty) return false
        if (longPress) {
            gestures.longPress(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        } else {
            gestures.tap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }
        return true
    }

    private fun findLauncherAppCarousel(): AccessibilityNodeInfo? {
        return findNodeByViewId(service.rootInActiveWindow, LAUNCHER_APP_RECYCLER_ID, TraversalBudget())
    }

    private fun findLauncherViewPager(): AccessibilityNodeInfo? {
        return findNodeByViewId(service.rootInActiveWindow, LAUNCHER_VIEWPAGER_ID, TraversalBudget())
    }

    private fun findNodeByViewId(
        node: AccessibilityNodeInfo?,
        viewId: String,
        budget: TraversalBudget
    ): AccessibilityNodeInfo? {
        if (node == null || budget.exhausted()) return null
        budget.visit()
        if (viewId == node.viewIdResourceName) return node
        for (index in 0 until node.childCount) {
            val match = findNodeByViewId(node.getChild(index), viewId, budget)
            if (match != null) return match
        }
        return null
    }

    private fun findFocusedClickable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isFocused && node.isClickable && node.isEnabled) return node
        for (index in 0 until node.childCount) {
            val match = findFocusedClickable(node.getChild(index))
            if (match != null) return match
        }
        return null
    }

    private fun findClickableNearestScreenCenter(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectLauncherClickables(root, nodes, TraversalBudget())
        if (nodes.isEmpty()) return null
        val metrics = service.resources.displayMetrics
        val centerX = metrics.widthPixels / 2f
        return nodes.minByOrNull { node ->
            val rect = Rect().also(node::getBoundsInScreen)
            abs(rect.centerX() - centerX) + abs(rect.centerY() - metrics.heightPixels * 0.31f) * 0.4f
        }
    }

    private fun collectLauncherClickables(
        node: AccessibilityNodeInfo?,
        out: MutableList<AccessibilityNodeInfo>,
        budget: TraversalBudget
    ) {
        if (node == null || budget.exhausted()) return
        budget.visit()
        val rect = Rect().also(node::getBoundsInScreen)
        val appBand = rect.centerY() in 135..270
        val iconLike = rect.width() in 55..130 && rect.height() in 55..130
        if (appBand && iconLike && node.isVisibleToUser && node.isClickable && node.isEnabled) {
            out += node
        }
        for (index in 0 until node.childCount) {
            collectLauncherClickables(node.getChild(index), out, budget)
        }
    }

    private fun performLauncherPageScroll(forward: Boolean): Boolean {
        val viewPager = findLauncherViewPager()
        if (performScroll(viewPager, forward)) {
            return true
        }
        return tryScrollTree(service.rootInActiveWindow, forward, TraversalBudget())
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
            Log.d(TAG, "launcher accessibility scroll forward=$forward")
            return true
        }
        return false
    }

    private fun supports(node: AccessibilityNodeInfo?, actionId: Int): Boolean {
        return node?.actionList?.any { it.id == actionId } == true
    }

    private fun enqueueLauncherSwipes(forward: Boolean, steps: Int) {
        val safeSteps = steps.coerceIn(1, MAX_QUEUED_STEPS)
        if (stepInFlight && queuedForward != forward) {
            queuedSteps = 0
        }
        queuedForward = forward
        queuedSteps = min(MAX_QUEUED_STEPS, queuedSteps + safeSteps)
        drainLauncherQueue()
    }

    private fun drainLauncherQueue() {
        if (stepInFlight || queuedSteps <= 0) return
        val appRecycler = findLauncherAppCarousel()
        if (appRecycler == null || !appRecycler.isVisibleToUser) {
            queuedSteps = 0
            return
        }

        val forward = queuedForward
        val burstSteps = min(MAX_BURST_STEPS, queuedSteps)
        queuedSteps -= burstSteps
        stepInFlight = true
        stepReleaseAt = SystemClock.uptimeMillis() + stepDuration(burstSteps) + STEP_QUEUE_GAP_MS

        val submitted = dispatchLauncherSwipe(appRecycler, forward, burstSteps, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                finishLauncherStep()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                finishLauncherStep()
            }
        })
        if (!submitted) {
            stepInFlight = false
            stepReleaseAt = 0L
            queuedSteps = 0
        } else {
            handler.postDelayed(::finishLauncherStep, stepDuration(burstSteps) + 420L)
        }
    }

    private fun finishLauncherStep() {
        if (!stepInFlight) return
        val waitMs = stepReleaseAt - SystemClock.uptimeMillis()
        if (waitMs > 0L) {
            handler.postDelayed(::finishLauncherStep, waitMs)
            return
        }
        stepInFlight = false
        stepReleaseAt = 0L
        if (queuedSteps > 0) {
            handler.postDelayed(::drainLauncherQueue, STEP_QUEUE_GAP_MS)
        }
    }

    private fun dispatchLauncherSwipe(
        appRecycler: AccessibilityNodeInfo,
        forward: Boolean,
        steps: Int,
        callback: GestureResultCallback
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val metrics = service.resources.displayMetrics
        val bounds = Rect().also(appRecycler::getBoundsInScreen)
        val y = if (bounds.isEmpty) metrics.heightPixels * 0.31f else bounds.centerY().toFloat()
        val left = if (bounds.isEmpty) metrics.widthPixels * 0.08f else bounds.left.toFloat()
        val right = if (bounds.isEmpty) metrics.widthPixels * 0.92f else bounds.right.toFloat()
        val width = right - left
        val centerX = (left + right) * 0.5f
        val singleStep = max(metrics.widthPixels * 0.18f, width * LAUNCHER_STEP_FRACTION)
        val distance = min(width * 0.88f, singleStep * max(1, steps))
        val startX = if (forward) centerX + distance * 0.5f else centerX - distance * 0.5f
        val endX = if (forward) centerX - distance * 0.5f else centerX + distance * 0.5f
        val path = Path().apply {
            moveTo(startX, y)
            lineTo(endX, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, stepDuration(steps)))
            .build()
        val submitted = service.dispatchGesture(gesture, callback, null)
        Log.d(
            TAG,
            "launcherSwipe forward=$forward submitted=$submitted steps=$steps bounds=$bounds startX=$startX endX=$endX y=$y"
        )
        return submitted
    }

    private fun stepDuration(steps: Int): Long {
        return if (steps <= 1) STEP_DURATION_MS else BOOST_DURATION_MS
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

    companion object {
        private const val TAG = "JSOSR08Launcher"
        private const val LAUNCHER_APP_RECYCLER_ID = "com.rokid.os.sprite.launcher:id/app_recycler"
        private const val LAUNCHER_VIEWPAGER_ID = "com.rokid.os.sprite.launcher:id/viewpager"
        private const val MAX_TRAVERSED_NODES = 90
        private const val TREE_BUDGET_MS = 55L
        private const val LAUNCHER_STEP_FRACTION = 0.27f
        private const val STEP_DURATION_MS = 220L
        private const val BOOST_DURATION_MS = 260L
        private const val STEP_QUEUE_GAP_MS = 45L
        private const val MAX_BURST_STEPS = 3
        private const val MAX_QUEUED_STEPS = 6
    }
}


