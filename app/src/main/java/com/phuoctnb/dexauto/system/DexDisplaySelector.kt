package com.phuoctnb.dexauto.system

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display

object DexDisplaySelector {
    fun selectDisplay(context: Context): Display? {
        val displayManager = context.getSystemService(DisplayManager::class.java)
        val selected = samsungDexDesktopDisplay(displayManager)
            ?: preferredExternalDisplay(displayManager)
            ?: context.display
            ?: displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        if (selected != null) {
            logSelection(displayManager, selected)
        } else {
            logAvailableDisplays(displayManager, "No available display")
        }
        return selected
    }

    fun dexOrExternalDisplay(context: Context): Display? {
        val displayManager = context.getSystemService(DisplayManager::class.java)
        val selected = samsungDexDesktopDisplay(displayManager)
            ?: preferredExternalDisplay(displayManager)
        if (selected != null) {
            logSelection(displayManager, selected)
        } else {
            logAvailableDisplays(displayManager, "No DeX/external desktop display")
        }
        return selected
    }

    fun displayById(context: Context, displayId: Int): Display? {
        return context.getSystemService(DisplayManager::class.java)?.getDisplay(displayId)
    }

    fun dexDesktopDisplay(context: Context): Display? {
        val displayManager = context.getSystemService(DisplayManager::class.java)
        val selected = samsungDexDesktopDisplay(displayManager)
        if (selected != null) {
            logSelection(displayManager, selected)
        } else {
            logAvailableDisplays(displayManager, "No logical DeX desktop display")
        }
        return selected
    }

    fun isDexDesktopDisplay(context: Context, displayId: Int): Boolean {
        val displayManager = context.getSystemService(DisplayManager::class.java)
        return displayManager
            .getDisplays(SAMSUNG_DEX_DISPLAY_CATEGORY)
            .any { it.displayId == displayId && it.state == Display.STATE_ON } ||
            preferredExternalDisplay(displayManager)?.displayId == displayId
    }

    private fun preferredExternalDisplay(displayManager: DisplayManager): Display? {
        val activeExternalDisplays = displayManager.displays.filter {
            it.displayId != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON
        }
        if (activeExternalDisplays.isEmpty()) return null

        val presentationIds = displayManager
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .mapTo(mutableSetOf()) { it.displayId }

        // During DeX startup Samsung may expose both a physical HDMI presentation
        // display and the logical desktop display. Overlays must target the latter.
        val logicalDesktopDisplays = activeExternalDisplays.filterNot {
            it.displayId in presentationIds
        }
        return (logicalDesktopDisplays.ifEmpty { activeExternalDisplays })
            .maxWithOrNull(
                compareBy<Display> { it.desktopNameScore() }
                    .thenBy { it.areaScore() }
            )
    }

    private fun samsungDexDesktopDisplay(displayManager: DisplayManager): Display? {
        return displayManager
            .getDisplays(SAMSUNG_DEX_DISPLAY_CATEGORY)
            .filter { it.state == Display.STATE_ON }
            .maxByOrNull { it.areaScore() }
    }

    private fun logSelection(displayManager: DisplayManager, selected: Display) {
        logAvailableDisplays(
            displayManager,
            "Selected display id=${selected.displayId}, name=${selected.name}, state=${selected.state}"
        )
    }

    private fun logAvailableDisplays(displayManager: DisplayManager, prefix: String) {
        val presentationIds = displayManager
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .mapTo(mutableSetOf()) { it.displayId }
        Log.i(
            TAG,
            "$prefix; " +
                "available=${displayManager.displays.joinToString { display ->
                    "${display.displayId}:${display.name}:${display.state}:${display.areaScore()}:" +
                        "flags=${display.flags}:presentation=${display.displayId in presentationIds}"
                }}"
        )
    }

    private fun Display.desktopNameScore(): Int {
        val normalizedName = name.lowercase()
        return when {
            "dex" in normalizedName || "desktop" in normalizedName -> 3
            "hdmi" !in normalizedName -> 1
            else -> 0
        }
    }

    private fun Display.areaScore(): Long {
        val mode = mode ?: return 0L
        return mode.physicalWidth.toLong() * mode.physicalHeight.toLong()
    }

    private const val TAG = "DexDisplaySelector"
    const val SAMSUNG_DEX_DISPLAY_CATEGORY =
        "com.samsung.android.hardware.display.category.DESKTOP"
}
