package com.phuoctnb.dexauto.system

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import com.phuoctnb.dexauto.R
import com.phuoctnb.dexauto.data.DexSessionApp
import com.phuoctnb.dexauto.data.DexSessionSnapshot
import com.phuoctnb.dexauto.data.PanelPosition
import com.phuoctnb.dexauto.data.PrivilegedBackend
import com.phuoctnb.dexauto.data.SavedLayout
import kotlin.math.roundToInt

class LayoutLauncher(
    private val context: Context,
    private val commandRunner: PrivilegedCommandRunner,
    private val boundsCalculator: LayoutBoundsCalculator = LayoutBoundsCalculator()
) {
    fun launch(
        layout: SavedLayout,
        backend: PrivilegedBackend,
        panelPosition: PanelPosition
    ): DexSessionSnapshot? {
        val targetDisplayId = PanelOverlayBounds.displayId
        val launchContext = targetDisplayId
            ?.let { DexDisplaySelector.displayById(context, it) }
            ?.let { context.createDisplayContext(it) }
            ?: context
        val workArea = calculateWorkArea(launchContext, panelPosition)

        val launchedApps = layout.packages.mapIndexedNotNull { index, packageName ->
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
            } ?: return@mapIndexedNotNull null
            val bounds = boundsCalculator.boundsFor(layout.type, index, workArea, layout.ratios)
            val options = ActivityOptions.makeBasic().apply {
                setLaunchBounds(bounds)
                targetDisplayId?.let { setLaunchDisplayId(it) }
            }
            launchApp(packageName, intent, options, launchContext, backend, index)
            DexSessionApp(packageName = packageName, bounds = Rect(bounds))
        }
        return launchedApps
            .takeIf { it.isNotEmpty() }
            ?.let { DexSessionSnapshot(workArea = Rect(workArea), apps = it) }
    }

    fun restore(
        snapshot: DexSessionSnapshot,
        targetDisplayId: Int,
        panelPosition: PanelPosition
    ) {
        val targetDisplay = DexDisplaySelector.displayById(context, targetDisplayId) ?: return
        val launchContext = context.createDisplayContext(targetDisplay)
        val currentWorkArea = calculateWorkArea(launchContext, panelPosition)

        snapshot.apps.forEachIndexed { index, app ->
            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
                )
            } ?: return@forEachIndexed
            val options = ActivityOptions.makeBasic().apply {
                setLaunchBounds(remapBounds(app.bounds, snapshot.workArea, currentWorkArea))
                setLaunchDisplayId(targetDisplayId)
            }
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { launchContext.startActivity(intent, options.toBundle()) }
                    .onFailure {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_launch_app_failed, app.packageName),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }, index * RESTORE_DELAY_MS)
        }
    }

    private fun launchApp(
        packageName: String,
        intent: Intent,
        options: ActivityOptions,
        launchContext: Context,
        backend: PrivilegedBackend,
        index: Int
    ) {
        Thread {
            forceStopPackageIfPossible(packageName, backend)
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { launchContext.startActivity(intent, options.toBundle()) }
                    .onFailure {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_launch_app_failed, packageName),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }, index * LAUNCH_DELAY_MS)
        }.start()
    }

    private fun forceStopPackageIfPossible(packageName: String, backend: PrivilegedBackend) {
        commandRunner.run("am force-stop $packageName", backend, timeoutMs = FORCE_STOP_TIMEOUT_MS)
    }

    private fun calculateWorkArea(context: Context, panelPosition: PanelPosition): Rect {
        val windowManager = context.getSystemService(WindowManager::class.java) ?: return Rect()
        return boundsCalculator.calculateWorkArea(
            maximumMetrics = windowManager.maximumWindowMetrics,
            currentWindowBounds = windowManager.currentWindowMetrics.bounds,
            panelBounds = PanelOverlayBounds.bounds,
            panelPosition = panelPosition
        )
    }

    fun currentWorkArea(displayId: Int, panelPosition: PanelPosition): Rect {
        val targetDisplay = DexDisplaySelector.displayById(context, displayId)
        val targetContext = targetDisplay?.let(context::createDisplayContext) ?: context
        return calculateWorkArea(targetContext, panelPosition)
    }

    private fun remapBounds(bounds: Rect, oldWorkArea: Rect, newWorkArea: Rect): Rect {
        if (oldWorkArea.width() <= 0 || oldWorkArea.height() <= 0) {
            return Rect(bounds)
        }
        fun mapX(value: Int): Int {
            val ratio = (value - oldWorkArea.left).toFloat() / oldWorkArea.width()
            return newWorkArea.left + (ratio * newWorkArea.width()).roundToInt()
        }
        fun mapY(value: Int): Int {
            val ratio = (value - oldWorkArea.top).toFloat() / oldWorkArea.height()
            return newWorkArea.top + (ratio * newWorkArea.height()).roundToInt()
        }
        val left = mapX(bounds.left).coerceIn(newWorkArea.left, newWorkArea.right - 1)
        val top = mapY(bounds.top).coerceIn(newWorkArea.top, newWorkArea.bottom - 1)
        val right = mapX(bounds.right).coerceIn(left + 1, newWorkArea.right)
        val bottom = mapY(bounds.bottom).coerceIn(top + 1, newWorkArea.bottom)
        return Rect(left, top, right, bottom)
    }

    private companion object {
        const val LAUNCH_DELAY_MS = 250L
        const val RESTORE_DELAY_MS = 350L
        const val FORCE_STOP_TIMEOUT_MS = 900L
    }
}
