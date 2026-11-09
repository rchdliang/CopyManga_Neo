package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.data.export.portable.DesktopExportDestinations
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.dialogs.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Files
import java.nio.file.Path

/** FileKit 直接使用 Cocoa 面板并切到 AppKit 主线程，不创建 Swing 对话框。 */
internal object MacFileDialogs {
    private val lock = Mutex()

    suspend fun choose(kind: String, name: String, types: List<String>, archive: Boolean): String? = lock.withLock {
        require(kind in setOf("open", "save", "folder"))
        val filename = name.substringAfterLast('/').substringAfterLast('\\')
        val extension = filename.substringAfterLast('.', "").takeIf(String::isNotEmpty)
        val selected = when (kind) {
            "folder" -> FileKit.openDirectoryPicker(dialogSettings = FileKitDialogSettings(title = "选择导出文件夹"))
            "open" -> FileKit.openFilePicker(type = FileKitType.File(*desktopFileExtensions(types).toTypedArray()),
                dialogSettings = FileKitDialogSettings(title = "选择文件"))
            else -> FileKit.openFileSaver(suggestedName = filename.substringBeforeLast('.', filename),
                defaultExtension = extension, allowedExtensions = extension?.let(::setOf),
                dialogSettings = FileKitDialogSettings(title = "保存文件"))
        } ?: return@withLock null
        currentCoroutineContext().ensureActive()
        val path = Path.of(selected.path).toAbsolutePath()
        when (kind) {
            "folder" -> require(Files.isDirectory(path)) { "请选择已存在的文件夹" }
            "open" -> require(Files.isRegularFile(path)) { "所选文件不存在" }
            else -> {
                require(!Files.isDirectory(path)) { "目标是文件夹，无法写入文件" }
                // 在最终覆盖确认前记录身份；提交时沿用导出仓库的替换检查。
                val reservation = if (archive) DesktopExportDestinations.capture(path) else null
                if (Files.exists(path) && !confirmMacFileReplacement(path.toString())) return@withLock null
                currentCoroutineContext().ensureActive()
                reservation?.let(DesktopExportDestinations::approve)
            }
        }
        path.toString()
    }
}

internal fun desktopFileExtensions(types: List<String>): List<String> = types.flatMap { when (it) {
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
