package com.rchdliang.copymanga_neo.multiplatform.platform

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary

internal fun desktopNotificationSettingsUnavailableReason(osName: String, headless: Boolean): String? = when {
    headless -> "当前环境没有可用的图形桌面"
    !osName.startsWith("Windows", ignoreCase = true) ->
        "当前桌面没有统一的通知设置入口，请在系统设置中手动管理"
    else -> null
}

/** Only dispatches the fixed Settings URI. Does not change permissions or use a browser fallback. */
internal fun openDesktopNotificationSettings(
    osName: String,
    headless: Boolean,
    createApi: () -> WindowsSettingsApi = { JnaWindowsSettingsApi() },
) {
    desktopNotificationSettingsUnavailableReason(osName, headless)?.let { error(it) }
    try {
        val api = createApi()
        // No suspension between initialization, dispatch and cleanup: COM belongs to this thread.
        val initialized = api.initializeCom()
        check(initialized >= 0) { "无法初始化 Windows 设置入口，请手动打开系统设置中的通知页面" }
        try {
            val code = api.open("ms-settings:notifications")
            check(code > 32L) { "Windows 无法打开通知设置（错误码 $code），请手动打开系统设置中的通知页面" }
        } finally {
            // S_FALSE (already initialized) also requires balancing CoUninitialize.
            api.uninitializeCom()
        }
    } catch (error: LinkageError) {
        throw IllegalStateException("Windows 设置组件不可用，请手动打开系统设置中的通知页面", error)
    }
}

internal interface WindowsSettingsApi {
    fun initializeCom(): Int
    fun open(uri: String): Long
    fun uninitializeCom()
}

internal interface SettingsShell32 : StdCallLibrary {
    fun ShellExecuteW(owner: Pointer?, operation: WString, file: WString,
                      parameters: WString?, directory: WString?, show: Int): Pointer?
}

internal interface SettingsOle32 : StdCallLibrary {
    fun CoInitializeEx(reserved: Pointer?, flags: Int): Int
    fun CoUninitialize()
}

private class JnaWindowsSettingsApi : WindowsSettingsApi {
    private val shell = Native.load("shell32", SettingsShell32::class.java)
    private val ole = Native.load("ole32", SettingsOle32::class.java)
    override fun initializeCom() = ole.CoInitializeEx(null, 0x2 or 0x4) // STA | DISABLE_OLE1DDE
    override fun open(uri: String): Long = Pointer.nativeValue(
        shell.ShellExecuteW(null, WString("open"), WString(uri), null, null, 1)) // SW_SHOWNORMAL
    override fun uninitializeCom() = ole.CoUninitialize()
}
