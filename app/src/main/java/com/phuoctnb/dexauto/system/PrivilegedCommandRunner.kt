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
    val output: String,
    val failure: ShellCommandFailure? = null
)

enum class ShellCommandFailure {
    BACKEND_NOT_SELECTED,
    ROOT_PROCESS_UNAVAILABLE,
    SHIZUKU_BINDER_UNAVAILABLE,
    SHIZUKU_PERMISSION_DENIED,
    SHIZUKU_USER_SERVICE_BIND_FAILED,
    SHIZUKU_TRANSACTION_FAILED,
    COMMAND_TIMEOUT,
    COMMAND_FAILED
}

class PrivilegedCommandRunner(private val context: Context) {
    private var rootAvailable: Boolean? = null
    @Volatile private var shizukuShellBinder: IBinder? = null
    private val shizukuBindLock = Any()

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
            PrivilegedBackend.None -> ShellCommandResult(
                success = false,
                output = "",
                failure = ShellCommandFailure.BACKEND_NOT_SELECTED
            )
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

    fun waitForShizukuBinder(timeoutMs: Long = SHIZUKU_READY_TIMEOUT_MS): Boolean {
        if (isShizukuBinderAlive()) return true

        val latch = CountDownLatch(1)
        val listener = Shizuku.OnBinderReceivedListener { latch.countDown() }
        Shizuku.addBinderReceivedListenerSticky(listener)
        return try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS) && isShizukuBinderAlive()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } finally {
            Shizuku.removeBinderReceivedListener(listener)
        }
    }

    private fun runRoot(command: String, timeoutMs: Long): ShellCommandResult {
        val process = runCatching {
            ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
        }.getOrNull() ?: return ShellCommandResult(
            success = false,
            output = "",
            failure = ShellCommandFailure.ROOT_PROCESS_UNAVAILABLE
        )
        return readProcess(process, timeoutMs)
    }

    private fun runShizuku(command: String, timeoutMs: Long): ShellCommandResult {
        if (!waitForShizukuBinder()) {
            return ShellCommandResult(
                success = false,
                output = "",
                failure = ShellCommandFailure.SHIZUKU_BINDER_UNAVAILABLE
            )
        }
        if (!hasShizukuPermission()) {
            return ShellCommandResult(
                success = false,
                output = "",
                failure = ShellCommandFailure.SHIZUKU_PERMISSION_DENIED
            )
        }
        val binder = usableShizukuShellBinder() ?: bindShizukuShellService()
            ?: return ShellCommandResult(
                success = false,
                output = "",
                failure = ShellCommandFailure.SHIZUKU_USER_SERVICE_BIND_FAILED
            )
        transactShizukuCommand(binder, command, timeoutMs)?.let { return it }

        clearShizukuState(binder)
        val reboundBinder = bindShizukuShellService()
            ?: return ShellCommandResult(
                success = false,
                output = "",
                failure = ShellCommandFailure.SHIZUKU_USER_SERVICE_BIND_FAILED
            )
        return transactShizukuCommand(reboundBinder, command, timeoutMs)
            ?: ShellCommandResult(
                success = false,
                output = "",
                failure = ShellCommandFailure.SHIZUKU_TRANSACTION_FAILED
            )
    }

    private fun transactShizukuCommand(
        binder: IBinder,
        command: String,
        timeoutMs: Long
    ): ShellCommandResult? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return runCatching {
            data.writeString(command)
            data.writeLong(timeoutMs)
            if (!binder.transact(ShizukuShellService.TRANSACTION_RUN_COMMAND, data, reply, 0)) {
                return@runCatching null
            }
            reply.readException()
            val success = reply.readInt() == 1
            ShellCommandResult(
                success = success,
                output = reply.readString().orEmpty(),
                failure = if (success) null else ShellCommandFailure.COMMAND_FAILED
            )
        }.getOrNull().also {
            data.recycle()
            reply.recycle()
        }
    }

    private fun bindShizukuShellService(): IBinder? {
        synchronized(shizukuBindLock) {
            usableShizukuShellBinder()?.let { return it }
            val latch = CountDownLatch(1)
            var receivedBinder: IBinder? = null
            val args = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, ShizukuShellService::class.java.name)
            )
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
                    clearShizukuState(receivedBinder)
                }
            }
            runCatching { Shizuku.bindUserService(args, connection) }.getOrElse {
                clearShizukuState()
                return null
            }
            val connected = runCatching {
                latch.await(SHIZUKU_BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }.getOrDefault(false)
            return if (connected) receivedBinder else usableShizukuShellBinder()
        }
    }

    private fun usableShizukuShellBinder(): IBinder? {
        return shizukuShellBinder?.takeIf { it.isBinderAlive }
    }

    private fun clearShizukuState(expectedBinder: IBinder? = null) {
        if (expectedBinder == null || shizukuShellBinder === expectedBinder) {
            shizukuShellBinder = null
        }
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
            return ShellCommandResult(
                success = false,
                output = output.get(),
                failure = ShellCommandFailure.COMMAND_TIMEOUT
            )
        }
        reader.join(500L)
        val success = process.exitValue() == 0
        return ShellCommandResult(
            success = success,
            output = output.get(),
            failure = if (success) null else ShellCommandFailure.COMMAND_FAILED
        )
    }

    private fun isShizukuBinderAlive(): Boolean {
        return runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    }

    companion object {
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 4100
        private const val DEFAULT_TIMEOUT_MS = 1_200L
        private const val ROOT_PERMISSION_TIMEOUT_MS = 15_000L
        private const val SHIZUKU_READY_TIMEOUT_MS = 8_000L
        private const val SHIZUKU_BIND_TIMEOUT_MS = 5_000L
    }
}
