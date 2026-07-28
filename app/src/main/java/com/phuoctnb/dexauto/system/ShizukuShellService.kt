package com.phuoctnb.dexauto.system

import android.os.Binder
import android.os.Parcel
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit

class ShizukuShellService : Binder() {
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code != TRANSACTION_RUN_COMMAND) return super.onTransact(code, data, reply, flags)
        val command = data.readString().orEmpty()
        val timeoutMs = data.readLong()
        val result = runCommand(command, timeoutMs)
        reply?.writeNoException()
        reply?.writeInt(if (result.exitCode == 0) 1 else 0)
        reply?.writeString(result.output)
        return true
    }

    private fun runCommand(command: String, timeoutMs: Long): CommandResult {
        val process = runCatching {
            ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
        }.getOrNull() ?: return CommandResult(-1, "")
        val output = AtomicReference("")
        val reader = Thread {
            output.set(runCatching { process.inputStream.bufferedReader().use { it.readText() } }.getOrDefault(""))
        }.apply { start() }
        val finished = runCatching { process.waitFor(timeoutMs, TimeUnit.MILLISECONDS) }.getOrDefault(false)
        if (!finished) {
            process.destroyForcibly()
            reader.join(500L)
            return CommandResult(-1, output.get())
        }
        reader.join(500L)
        return CommandResult(process.exitValue(), output.get())
    }

    private data class CommandResult(val exitCode: Int, val output: String)

    companion object {
        const val TRANSACTION_RUN_COMMAND = FIRST_CALL_TRANSACTION + 1
    }
}
