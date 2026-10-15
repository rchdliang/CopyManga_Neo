package com.rchdliang.copymanga_neo.multiplatform.platform

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Native process-scoped inhibitors; closing the lease releases only this reader's request. */
object DesktopAwakeInhibitor {
    /** Blocking startup: call off the UI thread. No settings or persistent power plan are changed. */
    fun acquire(): Result<AutoCloseable> = runCatching {
        val os = System.getProperty("os.name").lowercase()
        val command = when {
            os.startsWith("windows") -> listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", WINDOWS_SCRIPT)
            os.startsWith("linux") -> listOf("systemd-inhibit", "--what=idle:sleep", "--mode=block",
                "--who=CopyManga_Neo", "--why=Reader keep-screen-on", "sh", "-c", "printf 'READY\\n'; read -r release")
            os.startsWith("mac") -> listOf("caffeinate", "-d", "-i", "/bin/sh", "-c", "printf 'READY\\n'; read -r release")
            else -> error("当前桌面没有可用的原生休眠抑制器")
        }
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val readerExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "CopyManga-awake-startup").apply { isDaemon = true }
        }
        try {
            val line = readerExecutor.submit<String?> { process.inputStream.bufferedReader().readLine() }.get(6, TimeUnit.SECONDS)
            check(line == "READY" && process.isAlive) { "系统未授予屏幕常亮请求：${line ?: "启动失败"}" }
        } catch (failure: Exception) {
            stop(process)
            throw failure
        } finally { readerExecutor.shutdownNow() }
        val closed = AtomicBoolean(false)
        AutoCloseable {
            if (closed.compareAndSet(false, true)) {
                runCatching { process.outputStream.apply { write('\n'.code); flush(); close() } }
                if (!process.waitFor(2, TimeUnit.SECONDS)) stop(process)
                process.inputStream.close()
            }
        }
    }

    private fun stop(process: Process) {
        process.toHandle().descendants().forEach { it.destroy() }
        process.destroy()
        if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly()
    }

    private val WINDOWS_SCRIPT = """
        Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public static class CopyMangaPower { [DllImport("kernel32.dll", SetLastError=true)] public static extern uint SetThreadExecutionState(uint flags); }';
        ${'$'}previous = [CopyMangaPower]::SetThreadExecutionState([uint32]2147483651);
        if (${'$'}previous -eq 0) { throw 'SetThreadExecutionState failed'; }
        try { [Console]::Out.WriteLine('READY'); [Console]::Out.Flush(); [Console]::In.ReadLine() | Out-Null; }
        finally { [CopyMangaPower]::SetThreadExecutionState(${'$'}previous) | Out-Null; }
    """.trimIndent()
}
