package com.phuoctnb.dexauto.system

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityWindowInfo
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * One UI 8 may cascade a newly created freeform task after applying its requested size. This
 * service is an optional, non-privileged fallback: it moves an already correctly sized window by
 * dragging its desktop caption. It never requests key-event filtering.
 */
class DexAutoAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val repositionRequestId = AtomicInteger()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility reposition service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        repositionRequestId.incrementAndGet()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun reposition(
        displayId: Int,
        targets: List<WindowTarget>,
        onComplete: (Boolean) -> Unit
    ) {
        val requestId = repositionRequestId.incrementAndGet()
        Log.i(TAG, "Reposition request=$requestId display=$displayId targets=${targets.size}")
        moveNext(
            requestId = requestId,
            displayId = displayId,
            targets = targets,
            index = 0,
            lookupAttempt = 0,
            dragAttempt = 0,
            broughtToFront = false,
            onComplete = onComplete
        )
    }

    private fun moveNext(
        requestId: Int,
        displayId: Int,
        targets: List<WindowTarget>,
        index: Int,
        lookupAttempt: Int,
        dragAttempt: Int,
        broughtToFront: Boolean,
        onComplete: (Boolean) -> Unit
    ) {
        if (repositionRequestId.get() != requestId) return
        if (index >= targets.size) {
            completeRequest(requestId, true, onComplete)
            return
        }

        val target = targets[index]
        if (!broughtToFront && bringToFront(target.packageName, displayId)) {
            mainHandler.postDelayed({
                moveNext(
                    requestId,
                    displayId,
                    targets,
                    index,
                    lookupAttempt,
                    dragAttempt,
                    true,
                    onComplete
                )
            }, BRING_TO_FRONT_DELAY_MS)
            return
        }
        val currentBounds = findApplicationWindowBounds(
            displayId,
            target.packageName,
            target.bounds
        )
        if (currentBounds == null) {
            if (lookupAttempt < MAX_WINDOW_LOOKUP_ATTEMPTS) {
                mainHandler.postDelayed({
                    moveNext(
                        requestId,
                        displayId,
                        targets,
                        index,
                        lookupAttempt + 1,
                        dragAttempt,
                        broughtToFront,
                        onComplete
                    )
                }, WINDOW_LOOKUP_DELAY_MS)
            } else {
                Log.w(TAG, "No window found for ${target.packageName} on display $displayId")
                completeRequest(requestId, false, onComplete)
            }
            return
        }

        if (!sameSize(currentBounds, target.bounds)) {
            Log.w(
                TAG,
                "Window size differs for ${target.packageName}: " +
                    "actual=$currentBounds expected=${target.bounds}"
            )
            completeRequest(requestId, false, onComplete)
            return
        }
        val deltaX = target.bounds.left - currentBounds.left
        val deltaY = target.bounds.top - currentBounds.top
        if (abs(deltaX) <= POSITION_TOLERANCE_PX && abs(deltaY) <= POSITION_TOLERANCE_PX) {
            Log.i(TAG, "Window ${target.packageName} is at target: $currentBounds")
            moveNext(requestId, displayId, targets, index + 1, 0, 0, false, onComplete)
            return
        }
        if (dragAttempt >= MAX_DRAG_ATTEMPTS) {
            Log.w(
                TAG,
                "Window did not reach target after $dragAttempt drags for " +
                    "${target.packageName}: actual=$currentBounds expected=${target.bounds}"
            )
            completeRequest(requestId, false, onComplete)
            return
        }

        val startX = currentBounds.exactCenterX()
        val startY = currentBounds.top + CAPTION_DRAG_OFFSET_PX
        val dragDeltaY = if (
            dragAttempt == 0 &&
            target.bounds.top < SNAP_SAFE_TOP_PX &&
            abs(deltaY) >= SNAP_SAFE_MIN_VERTICAL_DELTA_PX
        ) {
            SNAP_SAFE_TOP_PX - currentBounds.top
        } else {
            deltaY
        }
        val path = Path().apply {
            moveTo(startX, startY.toFloat())
            lineTo(startX + deltaX, (startY + dragDeltaY).toFloat())
        }
        val gesture = GestureDescription.Builder()
            .setDisplayId(displayId)
            .addStroke(GestureDescription.StrokeDescription(path, 0L, DRAG_DURATION_MS))
            .build()
        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(
                        TAG,
                        "Caption drag completed for ${target.packageName}: " +
                            "delta=($deltaX,$dragDeltaY) attempt=${dragAttempt + 1}"
                    )
                    mainHandler.postDelayed({
                        moveNext(
                            requestId,
                            displayId,
                            targets,
                            index,
                            0,
                            dragAttempt + 1,
                            broughtToFront,
                            onComplete
                        )
                    }, AFTER_DRAG_DELAY_MS)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Caption drag cancelled for ${target.packageName}")
                    completeRequest(requestId, false, onComplete)
                }
            },
            mainHandler
        )
        if (!dispatched) {
            Log.w(TAG, "Caption drag was rejected for ${target.packageName}")
            completeRequest(requestId, false, onComplete)
        }
    }

    private fun bringToFront(packageName: String, displayId: Int): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        } ?: return false
        val options = ActivityOptions.makeBasic().apply {
            setLaunchDisplayId(displayId)
        }
        return runCatching { startActivity(intent, options.toBundle()) }
            .onFailure { Log.w(TAG, "Unable to bring $packageName to front", it) }
            .isSuccess
    }

    private fun completeRequest(
        requestId: Int,
        success: Boolean,
        onComplete: (Boolean) -> Unit
    ) {
        if (repositionRequestId.compareAndSet(requestId, requestId + 1)) {
            onComplete(success)
        }
    }

    private fun findApplicationWindowBounds(
        displayId: Int,
        packageName: String,
        expectedBounds: Rect
    ): Rect? {
        val expectedTitles = applicationTitles(packageName)
        val applicationWindows = windowsOnAllDisplays[displayId]
            .orEmpty()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val packageWindows = applicationWindows
            .filter { window ->
                window.root?.packageName?.toString() == packageName ||
                    window.title?.toString()?.normalizeTitle() in expectedTitles
            }
        val directMatch = packageWindows
            .map { window -> Rect().also(window::getBoundsInScreen) }
            .minByOrNull { bounds ->
                abs(bounds.width() - expectedBounds.width()) +
                    abs(bounds.height() - expectedBounds.height())
            }
        if (directMatch != null && sameSize(directMatch, expectedBounds)) {
            return directMatch
        }

        // Samsung may expose only a modal dialog for the app and hide its main accessibility
        // window. The desktop caption remains available as a separate package-less window, so
        // pair the dialog with the caption whose inferred task bounds contain it.
        val packageBounds = packageWindows
            .map { window -> Rect().also(window::getBoundsInScreen) }
            .maxByOrNull(Rect::width)
            ?: return directMatch
        val captionBounds = applicationWindows
            .asSequence()
            .map { window -> Rect().also(window::getBoundsInScreen) }
            .filter { bounds ->
                abs(bounds.width() - expectedBounds.width()) <= SIZE_TOLERANCE_PX &&
                    bounds.height() in 1..MAX_CAPTION_HEIGHT_PX
            }
            .filter { bounds ->
                packageBounds.centerX() in bounds.left..bounds.right &&
                    packageBounds.top >= bounds.top &&
                    packageBounds.bottom <= bounds.top + expectedBounds.height()
            }
            .minByOrNull { bounds -> abs(bounds.top - packageBounds.top) }
        if (captionBounds != null) {
            val inferredBounds = Rect(
                captionBounds.left,
                captionBounds.top,
                captionBounds.left + expectedBounds.width(),
                captionBounds.top + expectedBounds.height()
            )
            Log.i(TAG, "Inferred task bounds for $packageName from caption: $inferredBounds")
            return inferredBounds
        }
        return directMatch
    }

    private fun applicationTitles(packageName: String): Set<String> {
        val applicationTitle = runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrNull()
        val activityTitle = runCatching {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
                ?: return@runCatching null
            packageManager.resolveActivity(intent, 0)?.loadLabel(packageManager)?.toString()
        }.getOrNull()
        return listOfNotNull(applicationTitle, activityTitle)
            .mapTo(mutableSetOf()) { it.normalizeTitle() }
    }

    private fun String.normalizeTitle(): String = trim().lowercase()

    private fun sameSize(actual: Rect, expected: Rect): Boolean {
        return abs(actual.width() - expected.width()) <= SIZE_TOLERANCE_PX &&
            abs(actual.height() - expected.height()) <= SIZE_TOLERANCE_PX
    }

    data class WindowTarget(val packageName: String, val bounds: Rect)

    companion object {
        private const val TAG = "DexAutoAccessibility"
        private const val CAPTION_DRAG_OFFSET_PX = 20
        private const val SNAP_SAFE_TOP_PX = 100
        private const val SNAP_SAFE_MIN_VERTICAL_DELTA_PX = 80
        private const val POSITION_TOLERANCE_PX = 8
        private const val SIZE_TOLERANCE_PX = 16
        private const val MAX_CAPTION_HEIGHT_PX = 80
        private const val MAX_WINDOW_LOOKUP_ATTEMPTS = 8
        private const val MAX_DRAG_ATTEMPTS = 5
        private const val WINDOW_LOOKUP_DELAY_MS = 350L
        private const val AFTER_DRAG_DELAY_MS = 800L
        private const val BRING_TO_FRONT_DELAY_MS = 500L
        private const val DRAG_DURATION_MS = 350L

        @Volatile
        private var instance: DexAutoAccessibilityService? = null

        fun isEnabled(context: Context): Boolean {
            val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
            return manager
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info ->
                    val serviceInfo = info.resolveInfo?.serviceInfo
                    serviceInfo?.packageName == context.packageName &&
                        serviceInfo.name == DexAutoAccessibilityService::class.java.name
                }
        }

        fun requestReposition(
            displayId: Int,
            targets: List<WindowTarget>,
            onComplete: (Boolean) -> Unit
        ): Boolean {
            val service = instance ?: return false
            service.mainHandler.post {
                service.reposition(
                    displayId = displayId,
                    targets = targets.map { it.copy(bounds = Rect(it.bounds)) },
                    onComplete = onComplete
                )
            }
            return true
        }
    }
}
