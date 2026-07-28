package com.phuoctnb.dexauto.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.phuoctnb.dexauto.data.DexAutoRepository

class DexModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action.orEmpty()
        Log.i(TAG, "Received action=$action")
        when (action) {
            ACTION_ENTER_DEX,
            ACTION_ENTER_KNOX_DESKTOP -> {
                beginDexSession(context)
                markSavedSessionPending(context)
                startPanelIfReady(context)
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                val repository = DexAutoRepository(context)
                repository.saveDexSessionActive(false)
                if (SamsungDexStateProvider.currentState(context)?.let { it.enabled && it.dualMode } == true) {
                    beginDexSession(context)
                }
                startPanelIfReady(context)
            }

            Intent.ACTION_MY_PACKAGE_REPLACED -> startPanelIfReady(context)

            ACTION_EXIT_DEX,
            ACTION_EXIT_KNOX_DESKTOP -> {
                val now = SystemClock.elapsedRealtime()
                if (now - lastExitHandledAt < EXIT_EVENT_DEBOUNCE_MS) return
                lastExitHandledAt = now
                DexAutoRepository(context).saveDexSessionActive(false)
                context.sendBroadcast(
                    Intent(PanelOverlayService.ACTION_DEX_EXITED).setPackage(context.packageName)
                )
                captureLayoutAndStopPanel(context)
            }
        }
    }

    private fun beginDexSession(context: Context) {
        val repository = DexAutoRepository(context)
        if (!repository.loadDexSessionActive()) {
            repository.saveDexSessionActive(true)
            repository.savePanelAutoStartSuppressed(false)
            Log.i(TAG, "Started a new DeX session; automatic panel start is enabled")
        }
    }

    private fun markSavedSessionPending(context: Context) {
        val repository = DexAutoRepository(context)
        if (!repository.loadPreserveDexLayoutEnabled()) return
        val marked = repository.markDexSessionSnapshotPending()
        Log.i(TAG, "Marked saved DeX session pending on enter=$marked")
    }

    private fun startPanelIfReady(context: Context) {
        if (DexAutoRepository(context).loadPanelAutoStartSuppressed()) {
            Log.i(TAG, "Skip auto-start: panel was manually stopped for this DeX session")
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            Log.i(TAG, "Skip auto-start: overlay permission is not granted")
            return
        }
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PanelOverlayService::class.java)
                    .putExtra(PanelOverlayService.EXTRA_REQUIRE_EXTERNAL_DISPLAY, true)
                    .putExtra(PanelOverlayService.EXTRA_AUTOSTART_ATTEMPT, 0)
            )
        }.onFailure { error ->
            Log.w(TAG, "Failed to auto-start panel", error)
        }
    }

    private fun captureLayoutAndStopPanel(context: Context) {
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PanelOverlayService::class.java)
                    .setAction(PanelOverlayService.ACTION_CAPTURE_LAYOUT_AND_STOP)
            )
        }.onFailure { error ->
            Log.w(TAG, "Failed to capture layout before stopping panel", error)
            context.stopService(Intent(context, PanelOverlayService::class.java))
        }
    }

    private companion object {
        const val ACTION_ENTER_DEX = "com.samsung.android.desktopmode.action.ENTER_DESKTOP_MODE"
        const val ACTION_EXIT_DEX = "com.samsung.android.desktopmode.action.EXIT_DESKTOP_MODE"
        const val ACTION_ENTER_KNOX_DESKTOP = "android.app.action.ENTER_KNOX_DESKTOP_MODE"
        const val ACTION_EXIT_KNOX_DESKTOP = "android.app.action.EXIT_KNOX_DESKTOP_MODE"
        const val TAG = "DexModeReceiver"
        const val EXIT_EVENT_DEBOUNCE_MS = 2_000L
        var lastExitHandledAt = 0L
    }
}
