package com.rchdliang.copymanga_neo.multiplatform.platform

import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Native process-scoped inhibitors; closing the lease releases only this reader's request. */
object DesktopAwakeInhibitor {
    /** Blocking startup: call off the UI thread. No settings or persistent power plan are changed. */
    fun acquire(): Result<AutoCloseable> = runCatching {
        val os = System.getProperty("os.name").lowercase()
        val command = when {
            os.startsWith("windows") -> windowsKeepAwakeCommand()
            os.startsWith("linux") -> listOf("systemd-inhibit", "--what=idle:sleep", "--mode=block",
                "--who=CopyManga_Neo", "--why=Reader keep-screen-on", "sh", "-c", "printf 'READY\\n'; read -r release")
            os.startsWith("mac") -> listOf("caffeinate", "-d", "-i", "/bin/sh", "-c", "printf 'READY\\n'; read -r release")
            else -> error("当前桌面没有可用的原生休眠抑制器")
        }
        acquire(command).getOrThrow()
    }

    /** stdout is a protocol channel. PowerShell progress/errors must never become READY. */
    internal fun acquire(command: List<String>, startupTimeoutMillis: Long = 6_000): Result<AutoCloseable> = runCatching {
        val process = ProcessBuilder(command).redirectErrorStream(false).start()
        val readerExecutor = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "CopyManga-awake-startup").apply { isDaemon = true }
        }
        val diagnostics = StringBuffer()
        val errorReader = readerExecutor.submit {
            runCatching {
                process.errorStream.reader(Charsets.UTF_8).use { reader ->
                    val buffer = CharArray(1024)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        synchronized(diagnostics) {
                            // Continue draining after the cap to avoid blocking the helper process.
                            val keep = minOf(count, 8192 - diagnostics.length)
                            if (keep > 0) diagnostics.append(buffer, 0, keep)
                        }
                    }
                }
            }
        }
        fun cleanup() {
            // Also release the native lease if close/startup was interrupted while waiting.
            if (process.isAlive) runCatching { stop(process) }
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
            readerExecutor.shutdownNow()
        }
        try {
            val line = readerExecutor.submit<String?> {
                process.inputStream.bufferedReader(Charsets.UTF_8).readLine()
            }.get(startupTimeoutMillis, TimeUnit.MILLISECONDS)
            check(line == "READY" && process.isAlive) { "未收到有效的 READY 握手：${line ?: "进程已退出"}" }
        } catch (failure: Exception) {
            val interrupted = failure is InterruptedException || Thread.currentThread().isInterrupted
            try {
                stop(process)
                runCatching { errorReader.get(1, TimeUnit.SECONDS) }
            } finally {
                cleanup()
                if (interrupted) Thread.currentThread().interrupt()
            }
            val detail = diagnostics.toString().trim().takeIf { it.isNotEmpty() }
            throw IllegalStateException("无法启动屏幕常亮保持：${detail ?: failure.message ?: "启动超时"}", failure)
        }
        val closed = AtomicBoolean(false)
        AutoCloseable {
            if (closed.compareAndSet(false, true)) {
                var interrupted = false
                try {
                    runCatching { process.outputStream.apply { write('\n'.code); flush(); close() } }
                    if (!process.waitFor(2, TimeUnit.SECONDS)) stop(process)
                } catch (failure: InterruptedException) {
                    interrupted = true
                    throw failure
                } finally {
                    cleanup()
                    if (interrupted) Thread.currentThread().interrupt()
                }
            }
        }
    }

    private fun stop(process: Process) {
        process.toHandle().descendants().forEach { it.destroy() }
        process.destroy()
        try {
            if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly()
        } catch (_: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Windows PowerShell reparses -Command through the native command line. Embedded C#
     * quotes (DllImport("kernel32.dll")) must not take part in that parsing: encode the
     * complete script using the UTF-16LE format required by -EncodedCommand instead.
     */
    internal fun windowsKeepAwakeCommand(): List<String> = listOf(
        "powershell.exe", "-NoProfile", "-NonInteractive", "-OutputFormat", "Text", "-EncodedCommand",
        Base64.getEncoder().encodeToString(WINDOWS_SCRIPT.toByteArray(Charsets.UTF_16LE))
    )

    internal val WINDOWS_SCRIPT = """
        ${'$'}ErrorActionPreference = 'Stop';
        ${'$'}ProgressPreference = 'SilentlyContinue';
        [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding(${'$'}false);
        Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public static class CopyMangaPower { [DllImport("kernel32.dll", SetLastError=true)] public static extern uint SetThreadExecutionState(uint flags); }';
        ${'$'}previous = [CopyMangaPower]::SetThreadExecutionState([uint32]2147483651);
        if (${'$'}previous -eq 0) { throw 'SetThreadExecutionState failed'; }
        try { [Console]::Out.WriteLine('READY'); [Console]::Out.Flush(); [Console]::In.ReadLine() | Out-Null; }
        finally { [CopyMangaPower]::SetThreadExecutionState(${'$'}previous) | Out-Null; }
    """.trimIndent()
}
