package com.phuoctnb.dexauto.system

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import com.phuoctnb.dexauto.data.PrivilegedBackend
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class ShellCommandResult(
    val success: Boolean,
    val output: String
)

class PrivilegedCommandRunner(private val context: Context) {
    private var rootAvailable: Boolean? = null
    @Volatile private var shizukuShellBinder: IBinder? = null

    fun requestShizukuPermissionIfNeeded(requestCode: Int = SHIZUKU_PERMISSION_REQUEST_CODE): Boolean {
        if (!isShizukuBinderAlive()) return false
        if (hasShizukuPermission()) return true
        val canRequest = runCatching { !Shizuku.shouldShowRequestPermissionRationale() }
            .getOrElse {
                clearShizukuState()
                false
            }
        if (!canRequest) return false
        return runCatching {
            Shizuku.requestPermission(requestCode)
            true
        }.getOrElse {
            clearShizukuState()
            false
        }
    }

    fun requestRootPermissionIfNeeded(timeoutMs: Long = ROOT_PERMISSION_TIMEOUT_MS): Boolean {
        rootAvailable = null
        return hasRoot(timeoutMs)
    }

    fun hasBackend(backend: PrivilegedBackend): Boolean {
        return when (backend) {
            PrivilegedBackend.None -> false
            PrivilegedBackend.Root -> hasRoot()
            PrivilegedBackend.Shizuku -> hasShizukuPermission()
            PrivilegedBackend.RootAndShizuku -> hasShizukuPermission() || hasRoot()
        }
    }

    fun run(command: String, backend: PrivilegedBackend, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Boolean {
        return runForOutput(command, backend, timeoutMs).success
    }

    fun runForOutput(
        command: String,
        backend: PrivilegedBackend,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): ShellCommandResult {
        return when (backend) {
            PrivilegedBackend.None -> ShellCommandResult(false, "")
            PrivilegedBackend.Root -> runRoot(command, timeoutMs)
            PrivilegedBackend.Shizuku -> runShizuku(command, timeoutMs)
            PrivilegedBackend.RootAndShizuku -> {
                val shizukuResult = runShizuku(command, timeoutMs)
                if (shizukuResult.success) shizukuResult else runRoot(command, timeoutMs)
            }
        }
    }

    fun hasRoot(timeoutMs: Long = 700L): Boolean {
        rootAvailable?.let { return it }
        val available = runRoot("true", timeoutMs = timeoutMs).success
        rootAvailable = available
        return available
    }

    fun hasShizukuPermission(): Boolean {
        return isShizukuBinderAlive() &&
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
                .getOrElse {
                    clearShizukuState()
                    false
                }
    }

    private fun runRoot(command: String, timeoutMs: Long): ShellCommandResult {
        val process = runCatching {
            ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
        }.getOrNull() ?: return ShellCommandResult(false, "")
        return readProcess(process, timeoutMs)
    }

    private fun runShizuku(command: String, timeoutMs: Long): ShellCommandResult {
        if (!hasShizukuPermission()) return ShellCommandResult(false, "")
        val binder = shizukuShellBinder ?: bindShizukuShellService()
            ?: return ShellCommandResult(false, "")
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return runCatching {
            data.writeString(command)
            data.writeLong(timeoutMs)
            binder.transact(ShizukuShellService.TRANSACTION_RUN_COMMAND, data, reply, 0)
            reply.readException()
            ShellCommandResult(reply.readInt() == 1, reply.readString().orEmpty())
        }.getOrElse {
            clearShizukuState()
            ShellCommandResult(false, "")
        }.also {
            data.recycle()
            reply.recycle()
        }
    }

    private fun bindShizukuShellService(): IBinder? {
        val latch = CountDownLatch(1)
        var receivedBinder: IBinder? = null
        val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShizukuShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("shizuku_shell")
            .tag("dex_auto_shell")
            .version(2)
            .debuggable(false)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                receivedBinder = service
                shizukuShellBinder = service
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                shizukuShellBinder = null
            }
        }
        runCatching { Shizuku.bindUserService(args, connection) }.getOrElse {
            clearShizukuState()
            return null
        }
        val connected = runCatching { latch.await(2_000L, TimeUnit.MILLISECONDS) }.getOrDefault(false)
        return if (connected) receivedBinder else null
    }

    private fun clearShizukuState() {
        shizukuShellBinder = null
    }

    private fun readProcess(process: Process, timeoutMs: Long): ShellCommandResult {
        val output = AtomicReference("")
        val reader = Thread {
            output.set(runCatching { process.inputStream.bufferedReader().use { it.readText() } }.getOrDefault(""))
        }.apply { start() }
        val finished = runCatching { process.waitFor(timeoutMs, TimeUnit.MILLISECONDS) }.getOrDefault(false)
        if (!finished) {
            process.destroyForcibly()
            reader.join(500L)
            return ShellCommandResult(false, output.get())
        }
        reader.join(500L)
        return ShellCommandResult(process.exitValue() == 0, output.get())
    }

    private fun isShizukuBinderAlive(): Boolean {
        return runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    }

    companion object {
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 4100
        private const val DEFAULT_TIMEOUT_MS = 1_200L
        private const val ROOT_PERMISSION_TIMEOUT_MS = 15_000L
    }
}
