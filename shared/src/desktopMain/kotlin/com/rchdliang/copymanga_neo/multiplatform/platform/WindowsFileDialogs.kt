package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.data.export.portable.DesktopExportDestinations
import com.sun.jna.*
import com.sun.jna.Function
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.Executors

/** Windows Common Item Dialog 直接绑定 Tao HWND，在独立 STA 线程中运行模态消息循环。 */
internal object WindowsFileDialogs {
    private val dialogLock = Mutex()
    private val dispatcher = Executors.newSingleThreadExecutor { action ->
        Thread(action, "copymanga-file-dialog").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val ole by lazy { Native.load("ole32", FileDialogOle32::class.java) }
    private val user by lazy { Native.load("user32", FileDialogUser32::class.java) }

    suspend fun choose(owner: Long, kind: String, name: String, types: List<String>, archive: Boolean): String? =
        dialogLock.withLock {
            require(owner != 0L) { "应用窗口尚未就绪" }
            require(kind in setOf("open", "save", "folder"))
            withContext(dispatcher) {
                // 同一 STA 线程完成创建、Show、释放与 CoUninitialize，不跨线程使用 COM 指针。
                checkHr(ole.CoInitializeEx(null, 0x2 or 0x4))
                try {
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val selected = show(owner, kind, name, types) ?: return@withContext null
                        currentCoroutineContext().ensureActive()
                        val path = Path.of(selected).toAbsolutePath()
                        if (kind == "folder") {
                            require(Files.isDirectory(path)) { "请选择已存在的文件夹" }
                            return@withContext path.toString()
                        }
                        if (kind == "open") {
                            require(Files.isRegularFile(path)) { "所选文件不存在" }
                            return@withContext path.toString()
                        }
                        val target = resolveDesktopSavePath(path, name)
                        require(!Files.isDirectory(target)) { "目标是文件夹，无法写入文件" }
                        // 覆盖批准前记录身份；导出提交时再次校验，防止确认后文件被替换。
                        val reservation = if (archive) DesktopExportDestinations.capture(target) else null
                        if (Files.exists(target) && user.MessageBoxW(Pointer(owner),
                                WString("文件已存在，是否替换？\n$target"), WString("确认替换"),
                                0x4 or 0x30 or 0x100) != 6) continue
                        currentCoroutineContext().ensureActive()
                        reservation?.let(DesktopExportDestinations::approve)
                        return@withContext target.toString()
                    }
                    @Suppress("UNREACHABLE_CODE")
                    null
                } finally { ole.CoUninitialize() }
            }
        }

    private fun show(owner: Long, kind: String, name: String, types: List<String>): String? {
        val clsid = if (kind == "save") "c0b4e2f3-ba21-4773-8dba-335ec946eb8b"
            else "dc1c5a9c-e88a-4dde-a5a1-60f82a20aef7"
        val result = PointerByReference()
        guid(clsid).use { classId ->
            guid("42f85136-db7e-439c-85f1-e4075d135fc8").use { interfaceId ->
                checkHr(ole.CoCreateInstance(classId, null, 1, interfaceId, result))
            }
        }
        val dialog = requireNotNull(result.value)
        val allocations = mutableListOf<Memory>()
        try {
            val options = IntByReference()
            checkHr(call(dialog, 10, options)) // IFileDialog.GetOptions
            var flags = options.value or 0x40 or 0x8 or 0x800 // filesystem, no changed directory, existing path
            flags = flags and 0x2.inv() // 由应用在记录文件身份后确认覆盖
            if (kind == "folder") flags = flags or 0x20
            if (kind == "open") flags = flags or 0x1000
            checkHr(call(dialog, 9, flags)) // SetOptions
            checkHr(call(dialog, 17, WString(when (kind) {
                "save" -> "保存文件"; "folder" -> "选择导出文件夹"; else -> "选择文件"
            })))
            if (kind != "folder") {
                val extensions = extensions(types)
                if (extensions.isNotEmpty()) {
                    val filters = listOf("支持的文件" to extensions.joinToString(";") { "*.$it" }, "所有文件" to "*.*")
                    val specs = Memory((filters.size * 2 * Native.POINTER_SIZE).toLong()).also(allocations::add)
                    filters.forEachIndexed { index, (label, pattern) ->
                        listOf(label, pattern).forEachIndexed { field, value ->
                            val text = Memory(((value.length + 1) * Native.WCHAR_SIZE).toLong()).also(allocations::add)
                            text.setWideString(0, value)
                            specs.setPointer(((index * 2 + field) * Native.POINTER_SIZE).toLong(), text)
                        }
                    }
                    checkHr(call(dialog, 4, filters.size, specs)) // SetFileTypes
                }
                if (kind == "save") {
                    val filename = name.substringAfterLast('/').substringAfterLast('\\')
                    checkHr(call(dialog, 15, WString(filename))) // SetFileName
                    filename.substringAfterLast('.', "").takeIf(String::isNotEmpty)?.let {
                        checkHr(call(dialog, 22, WString(it))) // SetDefaultExtension
                    }
                }
            }
            val shown = call(dialog, 3, Pointer(owner)) // IModalWindow.Show
            if (shown == 0x800704c7.toInt()) return null // HRESULT_FROM_WIN32(ERROR_CANCELLED)
            checkHr(shown)
            val itemReference = PointerByReference()
            checkHr(call(dialog, 20, itemReference)) // GetResult
            val item = requireNotNull(itemReference.value)
            try {
                val pathReference = PointerByReference()
                checkHr(call(item, 5, 0x80058000.toInt(), pathReference)) // IShellItem.GetDisplayName(SIGDN_FILESYSPATH)
                val path = requireNotNull(pathReference.value)
                return try { path.getWideString(0) } finally { ole.CoTaskMemFree(path) }
            } finally { call(item, 2) } // IUnknown.Release
        } finally {
            call(dialog, 2)
            allocations.asReversed().forEach(Memory::close)
        }
    }

    /** COM vtable 顺序以 Windows SDK ShObjIdl_core.h 的 IFileDialog / IShellItem 为准。 */
    private fun call(instance: Pointer, slot: Int, vararg args: Any?): Int {
        val function = instance.getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE)
        return Function.getFunction(function, Function.ALT_CONVENTION).invokeInt(arrayOf(instance, *args))
    }

    private fun checkHr(value: Int) {
        check(value >= 0) { "Windows 文件对话框失败（HRESULT ${value.toUInt().toString(16)}）" }
    }

    private fun guid(value: String): Memory {
        val id = UUID.fromString(value)
        val bytes = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putInt((id.mostSignificantBits ushr 32).toInt())
            .putShort((id.mostSignificantBits ushr 16).toShort())
            .putShort(id.mostSignificantBits.toShort())
            .order(ByteOrder.BIG_ENDIAN).putLong(id.leastSignificantBits).array()
        return Memory(16).apply { write(0, bytes, 0, bytes.size) }
    }

    private fun extensions(types: List<String>): List<String> = types.flatMap { when (it) {
        "application/json" -> listOf("json")
        "text/plain" -> listOf("txt", "log")
        "application/zip" -> listOf("zip")
        "application/vnd.comicbook+zip" -> listOf("cbz")
        "image/jpeg" -> listOf("jpg", "jpeg")
        "image/png" -> listOf("png")
        "image/gif" -> listOf("gif")
        "image/bmp" -> listOf("bmp")
        "image/webp" -> listOf("webp")
        "image/*" -> listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
        else -> emptyList()
    } }.distinct()
}

internal interface FileDialogOle32 : StdCallLibrary {
    fun CoInitializeEx(reserved: Pointer?, flags: Int): Int
    fun CoUninitialize()
    fun CoCreateInstance(clsid: Pointer, outer: Pointer?, context: Int, iid: Pointer, result: PointerByReference): Int
    fun CoTaskMemFree(memory: Pointer)
}

internal interface FileDialogUser32 : StdCallLibrary {
    fun MessageBoxW(owner: Pointer, text: WString, caption: WString, type: Int): Int
}

/** 在 Tao 事件循环建立前报告启动失败，不创建 AWT 事件线程。 */
fun showWindowsLaunchFailure(message: String) {
    Native.load("user32", FileDialogUser32::class.java).MessageBoxW(
        Pointer(0), WString(message), WString("CopyManga Neo"), 0x10)
}
