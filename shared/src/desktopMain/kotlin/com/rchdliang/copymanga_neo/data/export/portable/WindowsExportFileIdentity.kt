package com.rchdliang.copymanga_neo.data.export.portable

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary
import java.io.IOException
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant

internal interface ExportFileKernel32 : StdCallLibrary {
    fun CreateFileW(path: WString, access: Int, sharing: Int, security: Pointer?, disposition: Int, flags: Int, template: Pointer?): Pointer?
    fun GetFileInformationByHandle(handle: Pointer, information: Pointer): Boolean
    fun GetFileInformationByHandleEx(handle: Pointer, informationClass: Int, information: Pointer, size: Int): Boolean
    fun CloseHandle(handle: Pointer): Boolean
    fun GetLastError(): Int
}

private object WindowsExportKernel {
    val api: ExportFileKernel32 = Native.load("kernel32", ExportFileKernel32::class.java)
}

/** JDK Windows path attributes can omit fileKey. Never replace that key with size/timestamps alone. */
internal fun windowsExportFileIdentity(path: Path): DesktopExportDestinations.Identity? = try {
    readWindowsExportFileIdentity(path, WindowsExportKernel.api)
} catch (error: LinkageError) {
    throw IOException("无法验证原文件身份，请选择新的文件名", error)
}

private data class WindowsFileKey(val volume: Long, val idLow: Long, val idHigh: Long)

internal fun readWindowsExportFileIdentity(path: Path, api: ExportFileKernel32): DesktopExportDestinations.Identity? {
    val absolute = path.toAbsolutePath().normalize().toString()
    // Extended-length syntax also works for UNC shares and avoids the Win32 MAX_PATH limit.
    val nativePath = when {
        absolute.startsWith("\\\\?\\") -> absolute
        absolute.startsWith("\\\\") -> "\\\\?\\UNC\\" + absolute.substring(2)
        else -> "\\\\?\\" + absolute
    }
    // Query metadata only, allow other applications' access, and inspect the link itself.
    val handle = api.CreateFileW(WString(nativePath), 0, 0x7, null, 3, 0x02200000, null)
    val invalidHandle = if (Native.POINTER_SIZE == 8) -1L else 0xffffffffL
    if (handle == null || Pointer.nativeValue(handle) == invalidHandle) {
        val error = api.GetLastError()
        if (error == 2 || error == 3) return null // FILE_NOT_FOUND / PATH_NOT_FOUND only.
        throw IOException("无法验证原文件身份 (Windows $error)，请重新选择导出位置")
    }
    try {
        // BY_HANDLE_FILE_INFORMATION consists of 13 DWORDs on both Windows architectures.
        Memory(52).use { info ->
            if (!api.GetFileInformationByHandle(handle, info)) throw IOException("无法读取原文件属性 (Windows ${api.GetLastError()})")
            val attributes = info.getInt(0)
            if (attributes and (0x10 or 0x400) != 0) throw IOException("请选择普通文件，不能替换目录或符号链接")
            // FileIdInfo supplies the full 128-bit ID, including on ReFS. Unsupported filesystems fail closed.
            Memory(24).use { id ->
                if (!api.GetFileInformationByHandleEx(handle, 18, id, 24)) throw IOException("此文件系统无法验证原文件身份，请选择新的文件名 (Windows ${api.GetLastError()})")
                val key = WindowsFileKey(id.getLong(0), id.getLong(8), id.getLong(16))
                return DesktopExportDestinations.Identity(
                    key,
                    (info.getInt(32).toLong() shl 32) or (info.getInt(36).toLong() and 0xffffffffL),
                    windowsFileTime(info.getLong(20)),
                    windowsFileTime(info.getLong(4)),
                )
            }
        }
    } finally {
        api.CloseHandle(handle)
    }
}

private fun windowsFileTime(ticks: Long): FileTime = FileTime.from(
    Instant.ofEpochSecond(ticks / 10_000_000 - 11_644_473_600, ticks % 10_000_000 * 100)
)
