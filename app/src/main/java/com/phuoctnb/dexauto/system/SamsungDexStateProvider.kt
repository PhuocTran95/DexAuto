package com.phuoctnb.dexauto.system

import android.content.Context
import android.util.Log

data class SamsungDexState(
    val enabled: Boolean,
    val dualMode: Boolean,
    val standaloneMode: Boolean
)

object SamsungDexStateProvider {
    fun currentState(context: Context): SamsungDexState? {
        val manager = context.applicationContext.getSystemService(DESKTOP_MODE_SERVICE) ?: return null
        return runCatching {
            val state = manager.javaClass
                .getDeclaredMethod(GET_DESKTOP_MODE_STATE)
                .invoke(manager)
            val stateClass = state.javaClass
            val enabledValue = stateClass.getDeclaredMethod(GET_ENABLED).invoke(state) as Int
            val displayType = stateClass.getDeclaredMethod(GET_DISPLAY_TYPE).invoke(state) as Int
            val enabled = enabledValue == stateClass.getDeclaredField(ENABLED).getInt(null)
            val dualMode = enabled &&
                displayType == stateClass.getDeclaredField(DISPLAY_TYPE_DUAL).getInt(null)
            val standaloneMode = enabled &&
                displayType == stateClass.getDeclaredField(DISPLAY_TYPE_STANDALONE).getInt(null)
            SamsungDexState(enabled, dualMode, standaloneMode)
        }.onFailure { error ->
            Log.w(TAG, "Samsung desktop mode state API is unavailable", error)
        }.getOrNull()
    }

    private const val TAG = "SamsungDexState"
    private const val DESKTOP_MODE_SERVICE = "desktopmode"
    private const val GET_DESKTOP_MODE_STATE = "getDesktopModeState"
    private const val GET_ENABLED = "getEnabled"
    private const val GET_DISPLAY_TYPE = "getDisplayType"
    private const val ENABLED = "ENABLED"
    private const val DISPLAY_TYPE_DUAL = "DISPLAY_TYPE_DUAL"
    private const val DISPLAY_TYPE_STANDALONE = "DISPLAY_TYPE_STANDALONE"
}
