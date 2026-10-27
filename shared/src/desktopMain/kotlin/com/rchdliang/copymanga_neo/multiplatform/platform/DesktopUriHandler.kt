package com.rchdliang.copymanga_neo.multiplatform.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import java.net.URI

/** Windows 网页链接交给系统默认浏览器，其余平台和协议沿用 Compose 的处理方式。 */
@Composable
fun DesktopUriHandlerProvider(content: @Composable () -> Unit) {
    val fallback = LocalUriHandler.current
    val handler = remember(fallback) {
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            object : UriHandler {
                override fun openUri(uri: String) {
                    val parsed = URI(uri)
                    if (parsed.scheme.equals("http", true) || parsed.scheme.equals("https", true)) {
                        openWindowsWebUri(parsed)
                    } else {
                        fallback.openUri(uri)
                    }
                }
            }
        } else fallback
    }
    CompositionLocalProvider(LocalUriHandler provides handler, content = content)
}

private fun openWindowsWebUri(uri: URI) {
    require(!uri.host.isNullOrBlank()) { "Invalid web address" }
    try {
        val ole = Native.load("ole32", SettingsOle32::class.java)
        val result = ole.CoInitializeEx(null, 0x2 or 0x4)
        // RPC_E_CHANGED_MODE means COM is already initialized with another apartment model.
        check(result >= 0 || result == -2147417850) { "COM initialization failed: $result" }
        try {
            val shell = Native.load("shell32", SettingsShell32::class.java)
            val code = Pointer.nativeValue(shell.ShellExecuteW(null, WString("open"), WString(uri.toASCIIString()), null, null, 1))
            check(code > 32) { "Windows could not open the web address: $code" }
        } finally {
            if (result >= 0) ole.CoUninitialize()
        }
    } catch (error: LinkageError) {
        throw IllegalStateException("Windows browser integration unavailable", error)
    }
}
