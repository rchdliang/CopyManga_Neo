package com.rchdliang.copymanga_neo.multiplatform.platform

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAwakeInhibitorTest {
    @Test fun windowsCommandTransportsCSharpQuotesAsUtf16LeBase64() {
        val command = DesktopAwakeInhibitor.windowsKeepAwakeCommand()
        assertEquals(listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-OutputFormat", "Text", "-EncodedCommand"), command.take(6))
        assertEquals(7, command.size)
        assertTrue(command.last().matches(Regex("[A-Za-z0-9+/]+={0,2}")))
        val script = String(Base64.getDecoder().decode(command.last()), Charsets.UTF_16LE)
        assertEquals(DesktopAwakeInhibitor.WINDOWS_SCRIPT, script)
        assertTrue(script.contains("[DllImport(\"kernel32.dll\", SetLastError=true)]"))
        assertFalse(command.contains("-Command"))
        assertFalse(command.any { it.contains("ExecutionPolicy", ignoreCase = true) })
    }

    @Test fun startupErrorsTerminateBeforeReadyAndOutputUsesUtf8() {
        val script = DesktopAwakeInhibitor.WINDOWS_SCRIPT
        assertTrue(script.indexOf("\$ProgressPreference = 'SilentlyContinue'") < script.indexOf("Add-Type"))
        assertTrue(script.indexOf("\$ErrorActionPreference = 'Stop'") < script.indexOf("Add-Type"))
        assertTrue(script.contains("[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding(\$false)"))
        assertTrue(script.indexOf("if (\$previous -eq 0)") < script.indexOf("WriteLine('READY')"))
        assertTrue(script.contains("finally { [CopyMangaPower]::SetThreadExecutionState(\$previous)"))
    }

    @Test fun stderrClixmlDoesNotContaminateReadyHandshake() {
        val lease = DesktopAwakeInhibitor.acquire(helperCommand("progress")).getOrThrow()
        lease.close()
        lease.close()
    }

    @Test fun nativeFailureBeforeReadyRetainsDiagnosticAndFails() {
        val result = DesktopAwakeInhibitor.acquire(helperCommand("error"))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("native call failed"))
    }

    @Test fun readyOnStderrCannotAuthorizeLease() {
        val result = DesktopAwakeInhibitor.acquire(helperCommand("stderr-ready"))
        assertTrue(result.isFailure)
    }

    @Test fun silentHelperTimesOutInsteadOfHanging() {
        val result = DesktopAwakeInhibitor.acquire(helperCommand("silent"), startupTimeoutMillis = 100)
        assertTrue(result.isFailure)
    }

    @Test fun largeStderrIsDrainedWithoutBlockingReady() {
        val lease = DesktopAwakeInhibitor.acquire(helperCommand("noisy")).getOrThrow()
        lease.close()
    }

    @Test fun interruptedCloseTerminatesStubbornHelperAndPreservesInterrupt() {
        val pidFile = java.nio.file.Files.createTempFile("awake-close", ".pid").toFile()
        try {
            val lease = DesktopAwakeInhibitor.acquire(helperCommand("stubborn") + pidFile.absolutePath).getOrThrow()
            val pid = pidFile.readText().toLong()
            var interruptPreserved = false
            val closer = Thread {
                Thread.currentThread().interrupt()
                runCatching { lease.close() }
                interruptPreserved = Thread.currentThread().isInterrupted
            }
            closer.start()
            closer.join(5_000)
            assertFalse(closer.isAlive)
            assertTrue(interruptPreserved)
            awaitProcessExit(pid)
        } finally { pidFile.delete() }
    }

    @Test fun interruptedStartupTerminatesHelperAndPreservesInterrupt() {
        val pidFile = java.nio.file.Files.createTempFile("awake-start", ".pid").toFile()
        try {
            var interruptPreserved = false
            var failed = false
            val starter = Thread {
                failed = DesktopAwakeInhibitor.acquire(helperCommand("silent") + pidFile.absolutePath).isFailure
                interruptPreserved = Thread.currentThread().isInterrupted
            }
            starter.start()
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
            while (pidFile.length() == 0L && System.nanoTime() < deadline) Thread.sleep(10)
            starter.interrupt()
            starter.join(5_000)
            assertFalse(starter.isAlive)
            assertTrue(failed)
            assertTrue(interruptPreserved)
            assertTrue(pidFile.length() > 0L)
            awaitProcessExit(pidFile.readText().toLong())
        } finally { pidFile.delete() }
    }

    private fun awaitProcessExit(pid: Long) {
        val handle = ProcessHandle.of(pid).orElse(null) ?: return
        handle.onExit().get(5, java.util.concurrent.TimeUnit.SECONDS)
        assertFalse(handle.isAlive)
    }

    private fun helperCommand(mode: String): List<String> {
        val javaExecutable = java.io.File(System.getProperty("java.home"), "bin/java").absolutePath
        val classes = listOf(AwakeHandshakeHelper::class.java, Unit::class.java)
            .map { java.io.File(it.protectionDomain.codeSource.location.toURI()).absolutePath }
            .distinct().joinToString(java.io.File.pathSeparator)
        return listOf(javaExecutable, "-cp", classes, AwakeHandshakeHelper::class.java.name, mode)
    }

    /** Runs the real quote-sensitive Add-Type/startup/close path on Windows CI, not Linux. */
    @Test fun windowsNativeLeaseStartsAndCanBeReleasedTwice() {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return
        val lease = DesktopAwakeInhibitor.acquire().getOrThrow()
        try {
            lease.close()
        } finally {
            lease.close()
        }
    }
}

/** Real child-process protocol fixture, independent of PowerShell and the host's power settings. */
object AwakeHandshakeHelper {
    @JvmStatic fun main(args: Array<String>) {
        if (args.size > 1) java.io.File(args[1]).writeText(ProcessHandle.current().pid().toString())
        when (args[0]) {
            "progress" -> System.err.println("#< CLIXML\n<Objs><Obj S=\"progress\">Preparing modules</Obj></Objs>")
            "error" -> { System.err.println("native call failed"); return }
            "stderr-ready" -> { System.err.println("READY"); return }
            "silent" -> { Thread.sleep(30_000); return }
            "noisy" -> repeat(20_000) { System.err.println("PowerShell diagnostic output") }
        }
        System.err.flush()
        System.out.println("READY")
        System.out.flush()
        System.`in`.read()
        if (args[0] == "stubborn") Thread.sleep(30_000)
    }
}
