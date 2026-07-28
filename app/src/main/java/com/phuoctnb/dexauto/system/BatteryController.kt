package com.phuoctnb.dexauto.system

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.phuoctnb.dexauto.R
import com.phuoctnb.dexauto.data.PrivilegedBackend

class BatteryController(
    private val context: Context,
    private val commandRunner: PrivilegedCommandRunner
) {
    fun applyLevel(requestedLevel: Int, backend: PrivilegedBackend) {
        val level = requestedLevel.coerceIn(1, 100)
        Thread {
            val success = commandRunner.run("dumpsys battery set level $level", backend)
            Handler(Looper.getMainLooper()).post {
                val message = if (success) {
                    context.getString(R.string.toast_battery_set_success, level)
                } else {
                    context.getString(R.string.toast_battery_set_failed)
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }
}
