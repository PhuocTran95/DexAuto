package com.phuoctnb.dexauto.system

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
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
            val result = commandRunner.runForOutput(
                "dumpsys battery set level $level",
                backend,
                timeoutMs = BATTERY_COMMAND_TIMEOUT_MS
            )
            if (!result.success) {
                Log.w(
                    TAG,
                    "Unable to set battery level using $backend: ${result.output.take(LOG_OUTPUT_LIMIT)}"
                )
            }
            Handler(Looper.getMainLooper()).post {
                val message = batteryResultMessage(result, level)
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun batteryResultMessage(result: ShellCommandResult, level: Int): String {
        if (result.success) {
            return context.getString(R.string.toast_battery_set_success, level)
        }
        val messageRes = when (result.failure) {
            ShellCommandFailure.BACKEND_NOT_SELECTED ->
                R.string.toast_battery_backend_not_selected
            ShellCommandFailure.ROOT_PROCESS_UNAVAILABLE ->
                R.string.toast_battery_root_unavailable
            ShellCommandFailure.SHIZUKU_BINDER_UNAVAILABLE ->
                R.string.toast_battery_shizuku_binder_unavailable
            ShellCommandFailure.SHIZUKU_PERMISSION_DENIED ->
                R.string.toast_battery_shizuku_permission_denied
            ShellCommandFailure.SHIZUKU_USER_SERVICE_BIND_FAILED ->
                R.string.toast_battery_shizuku_service_bind_failed
            ShellCommandFailure.SHIZUKU_TRANSACTION_FAILED ->
                R.string.toast_battery_shizuku_transaction_failed
            ShellCommandFailure.COMMAND_TIMEOUT ->
                R.string.toast_battery_command_timeout
            ShellCommandFailure.COMMAND_FAILED, null ->
                R.string.toast_battery_command_failed
        }
        return context.getString(messageRes)
    }

    private companion object {
        const val TAG = "BatteryController"
        const val LOG_OUTPUT_LIMIT = 500
        const val BATTERY_COMMAND_TIMEOUT_MS = 5_000L
    }
}
