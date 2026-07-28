package com.phuoctnb.dexauto.system

import android.app.ActivityOptions
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import com.phuoctnb.dexauto.R
import com.phuoctnb.dexauto.data.DexSessionApp
import com.phuoctnb.dexauto.data.DexSessionSnapshot
import com.phuoctnb.dexauto.data.PanelPosition
import com.phuoctnb.dexauto.data.PrivilegedBackend
import com.phuoctnb.dexauto.data.SavedLayout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

class LayoutLauncher(
    private val context: Context,
    private val commandRunner: PrivilegedCommandRunner,
    private val boundsCalculator: LayoutBoundsCalculator = LayoutBoundsCalculator(),
    private val taskLocator: DexTaskLocator = DexTaskLocator()
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activityManager = requireNotNull(
        context.getSystemService(ActivityManager::class.java)
    )
    private val launchRequestId = AtomicInteger()
    private val packagesLaunchedThisSession = ConcurrentHashMap.newKeySet<String>()

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
        val requestId = launchRequestId.incrementAndGet()
        val internalGapPx = (INTERNAL_APP_GAP_DP * launchContext.resources.displayMetrics.density)
            .roundToInt()

        val plannedApps = layout.packages.mapIndexedNotNull { index, packageName ->
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
            } ?: return@mapIndexedNotNull null
            val bounds = boundsCalculator.boundsFor(
                layout.type,
                index,
                workArea,
                layout.ratios,
                internalGapPx
            )
            val options = ActivityOptions.makeBasic().apply {
                setLaunchBounds(bounds)
                targetDisplayId?.let { setLaunchDisplayId(it) }
            }
            PlannedAppLaunch(
                sessionApp = DexSessionApp(packageName = packageName, bounds = Rect(bounds)),
                intent = intent,
                options = options
            )
        }
        launchApps(plannedApps, launchContext, backend, requestId)
        return plannedApps
            .map { it.sessionApp }
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

    private fun launchApps(
        plannedApps: List<PlannedAppLaunch>,
        launchContext: Context,
        backend: PrivilegedBackend,
        requestId: Int
    ) {
        Thread {
            val taskDump = commandRunner.runForOutput(
                DUMP_ACTIVITIES_COMMAND,
                backend,
                timeoutMs = TASK_DUMP_TIMEOUT_MS
            )
            val targetDisplayId = PanelOverlayBounds.displayId
            val taskState = if (taskDump.success && targetDisplayId != null) {
                taskLocator.parse(taskDump.output, targetDisplayId)
            } else {
                DexTaskState(taskIdsByPackage = emptyMap())
            }
            val previouslyLaunchedPackagePresent = plannedApps.any {
                it.sessionApp.packageName in packagesLaunchedThisSession
            }
            var privilegedFailure = !taskDump.success && previouslyLaunchedPackagePresent
            val launchActions = mutableListOf<LaunchAction>()

            for (plannedApp in plannedApps) {
                if (launchRequestId.get() != requestId) return@Thread
                val taskId = taskState.taskIdsByPackage[plannedApp.sessionApp.packageName]
                if (taskId == null) {
                    launchActions += LaunchAction.Start(plannedApp)
                    continue
                }
                val bounds = plannedApp.sessionApp.bounds
                val resized = commandRunner.run(
                    "am task resize $taskId ${bounds.left} ${bounds.top} " +
                        "${bounds.right} ${bounds.bottom}",
                    backend,
                    timeoutMs = TASK_RESIZE_TIMEOUT_MS
                )
                if (resized) {
                    launchActions += LaunchAction.MoveToFront(
                        taskId = taskId,
                        packageName = plannedApp.sessionApp.packageName
                    )
                } else {
                    privilegedFailure = true
                    launchActions += LaunchAction.Start(plannedApp)
                }
            }
            if (launchRequestId.get() != requestId) return@Thread
            if (privilegedFailure) {
                mainHandler.post {
                    if (launchRequestId.get() == requestId) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_layout_reposition_backend_required),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            launchActions.forEachIndexed { index, action ->
                mainHandler.postDelayed({
                    if (launchRequestId.get() != requestId) return@postDelayed
                    when (action) {
                        is LaunchAction.MoveToFront -> runCatching {
                            activityManager.moveTaskToFront(
                                action.taskId,
                                ActivityManager.MOVE_TASK_NO_USER_ACTION
                            )
                            packagesLaunchedThisSession += action.packageName
                        }.onFailure {
                            Log.w(TAG, "Unable to move task ${action.taskId} to front", it)
                        }
                        is LaunchAction.Start -> runCatching {
                            launchContext.startActivity(
                                action.plannedApp.intent,
                                action.plannedApp.options.toBundle()
                            )
                            packagesLaunchedThisSession +=
                                action.plannedApp.sessionApp.packageName
                        }.onFailure {
                            Log.w(
                                TAG,
                                "Unable to launch ${action.plannedApp.sessionApp.packageName}",
                                it
                            )
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.toast_launch_app_failed,
                                    action.plannedApp.sessionApp.packageName
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }, index * LAUNCH_DELAY_MS)
            }
        }.start()
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
        const val TAG = "LayoutLauncher"
        const val INTERNAL_APP_GAP_DP = 10f
        const val DUMP_ACTIVITIES_COMMAND = "dumpsys activity activities"
        const val LAUNCH_DELAY_MS = 250L
        const val RESTORE_DELAY_MS = 350L
        const val TASK_DUMP_TIMEOUT_MS = 8_000L
        const val TASK_RESIZE_TIMEOUT_MS = 2_000L
    }

    private data class PlannedAppLaunch(
        val sessionApp: DexSessionApp,
        val intent: Intent,
        val options: ActivityOptions
    )

    private sealed interface LaunchAction {
        data class MoveToFront(
            val taskId: Int,
            val packageName: String
        ) : LaunchAction

        data class Start(val plannedApp: PlannedAppLaunch) : LaunchAction
    }
}
