package com.rchdliang.copymanga_neo.multiplatform.platform

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAwakeInhibitorTest {
    @Test fun windowsCommandTransportsCSharpQuotesAsUtf16LeBase64() {
        val command = DesktopAwakeInhibitor.windowsKeepAwakeCommand()
        assertEquals(listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand"), command.take(4))
        assertEquals(5, command.size)
        assertTrue(command.last().matches(Regex("[A-Za-z0-9+/]+={0,2}")))
        val script = String(Base64.getDecoder().decode(command.last()), Charsets.UTF_16LE)
        assertEquals(DesktopAwakeInhibitor.WINDOWS_SCRIPT, script)
        assertTrue(script.contains("[DllImport(\"kernel32.dll\", SetLastError=true)]"))
        assertFalse(command.contains("-Command"))
        assertFalse(command.any { it.contains("ExecutionPolicy", ignoreCase = true) })
    }

    @Test fun startupErrorsTerminateBeforeReadyAndOutputUsesUtf8() {
        val script = DesktopAwakeInhibitor.WINDOWS_SCRIPT
        assertTrue(script.indexOf("\$ErrorActionPreference = 'Stop'") < script.indexOf("Add-Type"))
        assertTrue(script.contains("[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding(\$false)"))
        assertTrue(script.indexOf("if (\$previous -eq 0)") < script.indexOf("WriteLine('READY')"))
        assertTrue(script.contains("finally { [CopyMangaPower]::SetThreadExecutionState(\$previous)"))
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
