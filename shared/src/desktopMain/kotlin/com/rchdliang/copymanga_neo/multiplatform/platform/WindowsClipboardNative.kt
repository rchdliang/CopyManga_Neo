package com.rchdliang.copymanga_neo.multiplatform.platform

import com.sun.jna.IntegerType
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary
import java.awt.Component
import java.awt.Window
import javax.swing.SwingUtilities

/** Invoked only on Windows and the AWT event thread, using the already realized application window. */
internal fun copyWindowsSensitiveText(owner: Component?, text: String) {
    check(SwingUtilities.isEventDispatchThread()) { "Windows 剪贴板操作需要应用事件线程" }
    val window = owner as? Window ?: owner?.let(SwingUtilities::getWindowAncestor)
    check(window != null && window.isDisplayable) { "应用窗口尚未就绪，未复制敏感内容" }
    try { WindowsSensitiveClipboard(JnaWindowsClipboard(window)).copy(text) }
    catch (error: LinkageError) { throw IllegalStateException("Windows 剪贴板保护组件不可用，未使用普通剪贴板回退", error) }
}

internal class ClipboardSizeT(value: Long = 0) : IntegerType(Native.SIZE_T_SIZE, value, true) {
    override fun toByte(): Byte = toLong().toByte()
    override fun toShort(): Short = toLong().toShort()
}
internal interface ClipboardUser32 : StdCallLibrary {
    fun RegisterClipboardFormatW(name: WString): Int
    fun OpenClipboard(owner: Pointer): Boolean
    fun EmptyClipboard(): Boolean
    fun SetClipboardData(format: Int, memory: Pointer): Pointer?
    fun CloseClipboard(): Boolean
}
internal interface ClipboardKernel32 : StdCallLibrary {
    fun GlobalAlloc(flags: Int, size: ClipboardSizeT): Pointer?
    fun GlobalLock(memory: Pointer): Pointer?
    fun GlobalUnlock(memory: Pointer): Boolean
    fun GlobalFree(memory: Pointer): Pointer?
    fun SetLastError(error: Int)
    fun GetLastError(): Int
}

private class JnaWindowsClipboard(window: Window) : WindowsClipboardApi {
    private val user = Native.load("user32", ClipboardUser32::class.java)
    private val kernel = Native.load("kernel32", ClipboardKernel32::class.java)
    override val owner = Pointer.nativeValue(Native.getWindowPointer(window))
    private val allocations = mutableMapOf<Long, Int>()

    override fun registerFormat(name: String) = user.RegisterClipboardFormatW(WString(name))
    override fun open() = user.OpenClipboard(Pointer(owner))
    override fun empty() = user.EmptyClipboard()
    override fun close() = user.CloseClipboard()
    private fun unlock(memory: Pointer) {
        // A zero return also means the last lock was released successfully; inspect LastError.
        kernel.SetLastError(0)
        val remainsLocked = kernel.GlobalUnlock(memory)
        check(!remainsLocked && kernel.GetLastError() == 0) { "无法释放剪贴板内存锁" }
    }
    override fun allocate(bytes: ByteArray): Long {
        val memory = checkNotNull(kernel.GlobalAlloc(0x0002, ClipboardSizeT(bytes.size.toLong()))) { "无法分配剪贴板内存" }
        val handle = Pointer.nativeValue(memory)
        allocations[handle] = bytes.size
        try {
            val pointer = checkNotNull(kernel.GlobalLock(memory)) { "无法锁定剪贴板内存" }
            try { pointer.write(0, bytes, 0, bytes.size) } finally { unlock(memory) }
            return handle
        } catch (error: Throwable) {
            try { wipeAndFree(handle) } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
            throw error
        }
    }
    override fun publish(format: Int, handle: Long): Boolean {
        val result = user.SetClipboardData(format, Pointer(handle)) ?: return false
        if (Pointer.nativeValue(result) == 0L) return false
        allocations.remove(handle)
        return true
    }
    override fun wipeAndFree(handle: Long) {
        val size = allocations.remove(handle) ?: error("不能释放已转交 Windows 的剪贴板内存")
        val memory = Pointer(handle)
        var failure: Throwable? = null
        try {
            val pointer = checkNotNull(kernel.GlobalLock(memory)) { "无法擦除未发布的剪贴板内存" }
            try { pointer.clear(size.toLong()) } finally { unlock(memory) }
        } catch (error: Throwable) { failure = error }
        try { check(kernel.GlobalFree(memory) == null) { "无法释放未发布的剪贴板内存" } }
        catch (error: Throwable) { if (failure == null) failure = error else failure.addSuppressed(error) }
        failure?.let { throw it }
    }
}
