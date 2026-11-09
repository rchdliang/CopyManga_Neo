package com.rchdliang.copymanga_neo.multiplatform.platform

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.ptr.NativeLongByReference

/** Cocoa 剪贴板只在 Tao UI 线程调用，不初始化 AWT/AppKit 事件线程。 */
internal object MacClipboard {
    private val appKit by lazy { NativeLibrary.getInstance("AppKit") }
    private val objc by lazy { NativeLibrary.getInstance("objc") }
    private fun classOf(name: String): Pointer = requireNotNull(objc.getFunction("objc_getClass").invokePointer(arrayOf(name)))
    private fun selector(name: String): Pointer = requireNotNull(objc.getFunction("sel_registerName").invokePointer(arrayOf(name)))
    private fun send(receiver: Pointer, name: String, vararg args: Any?): Pointer = requireNotNull(
        objc.getFunction("objc_msgSend").invokePointer(arrayOf(receiver, selector(name), *args)))
    private fun string(value: String): Pointer = send(classOf("NSString"), "stringWithUTF8String:", value)
    private fun <T> withPool(block: () -> T): T {
        appKit // 确保 NSPasteboard 所在框架已加载。
        val pool = send(classOf("NSAutoreleasePool"), "new")
        try { return block() }
        finally { objc.getFunction("objc_msgSend").invokeVoid(arrayOf(pool, selector("drain"))) }
    }

    fun put(bytes: ByteArray, type: String, sensitive: Boolean = false) = withPool {
        val board = send(classOf("NSPasteboard"), "generalPasteboard")
        objc.getFunction("objc_msgSend").invokeLong(arrayOf(board, selector("clearContents")))
        fun write(data: ByteArray, flavor: String) {
            Memory(data.size.coerceAtLeast(1).toLong()).use { memory ->
                if (data.isNotEmpty()) memory.write(0, data, 0, data.size)
                val value = send(classOf("NSData"), "dataWithBytes:length:", memory, data.size.toLong())
                check(objc.getFunction("objc_msgSend").invokeInt(arrayOf(board, selector("setData:forType:"), value, string(flavor))) != 0) {
                    "macOS 剪贴板写入失败"
                }
            }
        }
        write(bytes, type)
        if (sensitive) {
            write(byteArrayOf(), "org.nspasteboard.ConcealedType")
            write(byteArrayOf(), "org.nspasteboard.TransientType")
        }
    }

    fun text(): String? = withPool {
        val board = send(classOf("NSPasteboard"), "generalPasteboard")
        val value = objc.getFunction("objc_msgSend").invokePointer(arrayOf(board, selector("stringForType:"), string("public.utf8-plain-text")))
            ?: return@withPool null
        send(value, "UTF8String").getString(0, "UTF-8")
    }
}

/** 系统级提示可在 Tao 循环建立前使用；固定按钮不执行外部脚本。 */
fun showMacLaunchFailure(message: String) { macAlert("CopyManga Neo", message, "好", null) }

internal fun confirmMacFileReplacement(path: String): Boolean =
    macAlert("确认替换", "文件已存在，是否替换？\n$path", "替换", "取消")

private fun macAlert(title: String, message: String, confirm: String, cancel: String?): Boolean {
    val core = Native.load("CoreFoundation", MacCoreFoundation::class.java)
    val strings = listOf(title, message, confirm).plus(listOfNotNull(cancel)).map {
        requireNotNull(core.CFStringCreateWithCString(null, it.toByteArray(Charsets.UTF_8) + 0.toByte(), 0x08000100))
    }
    try {
        val response = NativeLongByReference()
        check(core.CFUserNotificationDisplayAlert(0.0, NativeLong(2), null, null, null,
            strings[0], strings[1], strings[2], strings.getOrNull(3), null, response) == 0) { "macOS 提示窗口不可用" }
        return response.value.toLong() and 3L == 0L
    } finally { strings.forEach(core::CFRelease) }
}

internal interface MacCoreFoundation : Library {
    fun CFStringCreateWithCString(allocator: Pointer?, text: ByteArray, encoding: Int): Pointer?
    fun CFRelease(value: Pointer)
    fun CFUserNotificationDisplayAlert(timeout: Double, flags: NativeLong, icon: Pointer?, sound: Pointer?, localization: Pointer?,
        title: Pointer, message: Pointer, defaultButton: Pointer, alternateButton: Pointer?, otherButton: Pointer?, response: NativeLongByReference): Int
}
