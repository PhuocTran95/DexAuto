package com.phuoctnb.dexauto.system

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.phuoctnb.dexauto.data.DexAutoRepository
import com.phuoctnb.dexauto.data.DexSessionSnapshot
import com.phuoctnb.dexauto.data.PanelPosition
import com.phuoctnb.dexauto.data.PrivilegedBackend

class DexSessionLayoutController(
    private val context: Context,
    private val repository: DexAutoRepository,
    private val commandRunner: PrivilegedCommandRunner,
    private val layoutLauncher: LayoutLauncher,
    private val parser: DexTaskSnapshotParser = DexTaskSnapshotParser()
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun captureAndThen(
        displayId: Int,
        workArea: Rect,
        backend: PrivilegedBackend,
        pendingRestore: Boolean,
        onComplete: () -> Unit
    ) {
        Thread {
            runCatching {
                val result = commandRunner.runForOutput(
                    dumpActivitiesCommand(displayId),
                    backend,
                    CAPTURE_TIMEOUT_MS
                )
                val parsedApps = if (result.success) {
                    parser.parse(result.output, displayId, context.packageName)
                } else {
                    emptyList()
                }
                val apps = if (parsedApps.isNotEmpty()) {
                    parsedApps
                        .filter { context.packageManager.getLaunchIntentForPackage(it.packageName) != null }
                } else {
                    emptyList()
                }

                if (apps.isEmpty()) {
                    if (pendingRestore) {
                        repository.markDexSessionSnapshotPending()
                    }
                    Log.w(
                        TAG,
                        "No visible DeX app tasks captured; backend=$backend success=${result.success} " +
                            "outputChars=${result.output.length} parsed=${parsedApps.size}"
                    )
                } else {
                    repository.saveDexSessionSnapshot(
                        DexSessionSnapshot(
                            workArea = Rect(workArea),
                            apps = apps
                        ),
                        pendingRestore = pendingRestore
                    )
                    Log.i(
                        TAG,
                        "Captured ${apps.size} visible DeX app tasks on display=$displayId " +
                            "pendingRestore=$pendingRestore: " +
                            apps.joinToString { "${it.packageName}:${it.bounds.flattenToString()}" }
                    )
                }
            }.onFailure { error ->
                if (pendingRestore) {
                    repository.markDexSessionSnapshotPending()
                }
                Log.w(TAG, "Failed to capture visible DeX app tasks", error)
            }
            mainHandler.post(onComplete)
        }.start()
    }

    fun restoreIfPending(displayId: Int, panelPosition: PanelPosition) {
        val snapshot = repository.loadPendingDexSessionSnapshot()
        if (snapshot == null) {
            Log.i(
                TAG,
                "No pending DeX layout snapshot; enabled=${repository.loadPreserveDexLayoutEnabled()} " +
                    "snapshotStored=${repository.hasDexSessionSnapshot()}"
            )
            return
        }
        repository.consumeDexSessionSnapshot()
        layoutLauncher.restore(snapshot, displayId, panelPosition)
        Log.i(TAG, "Restoring ${snapshot.apps.size} DeX app tasks on display=$displayId")
    }

    private fun dumpActivitiesCommand(displayId: Int): String {
        return "dumpsys activity activities | " +
            "sed -n '/^Display #$displayId /,/^Display #[0-9][0-9]* /p'"
    }

    private companion object {
        const val TAG = "DexSessionLayout"
        const val CAPTURE_TIMEOUT_MS = 8_000L
    }
}
