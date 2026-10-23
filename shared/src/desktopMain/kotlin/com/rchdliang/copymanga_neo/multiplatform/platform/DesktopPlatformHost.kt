package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.domain.model.UserActionCancelled

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rchdliang.copymanga_neo.data.export.portable.DesktopExportDestinations
import com.rchdliang.copymanga_neo.domain.model.ComicExportLocation
import com.rchdliang.copymanga_neo.domain.model.ReaderExportImage
import com.rchdliang.copymanga_neo.domain.repository.NetworkLogRepository
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackDispatcher
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.downloads.DownloadNotificationPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.export.ComicExportPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.novel.NovelReaderPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.profile.ProfilePlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.DesktopReaderKeyboard
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.ReaderPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.ScreenOrientationSetting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import java.awt.Component
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.StringReader
import java.net.URI
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.text.MutableAttributeSet
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.parser.ParserDelegator

/**
 * Native Windows/Linux UI IO. Share opens a chooser: copying is performed only when explicitly
 * selected, never silently reported as a system share. Failures remain failures in Result.
 * Desktop clipboards cannot universally disable history/previews of sensitive text.
 */
class DesktopPlatformHost(
    private val owner: Component? = null,
    private val windowPolicy: DesktopWindowPolicy? = null,
    private val locale: Locale = Locale.getDefault(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : UiPlatform, ReaderPlatform, DesktopReaderKeyboard, NovelReaderPlatform, ComicExportPlatform,
    ProfilePlatform, DownloadNotificationPlatform {
    override val imageQuality = if (System.getProperty("os.name").startsWith("Windows", true))
        com.rchdliang.copymanga_neo.multiplatform.ui.reader.windowsReaderImageQuality else null

    override val notificationSettingsUnavailableReason: String?
        get() = desktopNotificationSettingsUnavailableReason(
            System.getProperty("os.name"), GraphicsEnvironment.isHeadless())
    override val backDispatcher = UiBackDispatcher()
    private val mutableWarnings = MutableStateFlow<List<String>>(emptyList())
    /** Hosts should surface these capability warnings once, rather than promising unavailable OS effects. */
    val capabilityWarnings: StateFlow<List<String>> = mutableWarnings

    private fun warn(message: String) {
        if (message !in mutableWarnings.value) mutableWarnings.value = mutableWarnings.value + message
    }

    @Composable
    override fun ImmersiveEffect(orientation: ScreenOrientationSetting?) {
        // Reading orientation and immersive system bars are mobile concerns.
        // Desktop fullscreen is explicitly controlled by the window's F11 shortcut.
    }

    @Composable
    override fun KeepScreenOnEffect(enabled: Boolean) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(windowPolicy, lifecycle, enabled) {
            // Collect below STARTED too: pausing/minimizing must promptly cancel the active lease.
            lifecycle.currentStateFlow.collectLatest { state ->
                if (desktopKeepAwakeAllowed(enabled, state)) withContext(Dispatchers.IO) {
                    holdDesktopKeepAwakeLease(
                        acquire = { windowPolicy?.acquireKeepAwake() ?: DesktopAwakeInhibitor.acquire() },
                        warning = ::warn)
                }
            }
        }
    }

    @Composable
    override fun ReaderWindow(keepScreenOn: Boolean, controlsVisible: Boolean, lightBars: Boolean) {
        KeepScreenOnEffect(keepScreenOn)
    }

    override fun formatDate(epochMilliseconds: Long): String =
        DateTimeFormatter.ofPattern("yyyy-MM-dd", locale).withZone(zone)
            .format(Instant.ofEpochMilli(epochMilliseconds))
    override fun formatLineHeight(value: Float): String = String.format(locale, "%.2f 倍", value)
    override fun formatFileSize(bytes: Long): String {
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.coerceAtLeast(0).toDouble()
        var unit = 0
        while (value >= 1000 && unit < units.lastIndex) { value /= 1000; unit++ }
        return if (unit == 0) "${value.toLong()} B" else String.format(locale, "%.2f %s", value, units[unit])
    }

    override fun plainTextFromHtml(html: String): String {
        val result = StringBuilder()
        var hidden = 0
        val paragraphTags = setOf(HTML.Tag.P, HTML.Tag.DIV, HTML.Tag.H1, HTML.Tag.H2, HTML.Tag.H3,
            HTML.Tag.H4, HTML.Tag.H5, HTML.Tag.H6, HTML.Tag.BLOCKQUOTE)
        fun newline(count: Int) {
            if (result.isEmpty()) return
            val existing = result.reversed().takeWhile { it == '\n' }.length
            repeat((count - existing).coerceAtLeast(0)) { result.append('\n') }
        }
        ParserDelegator().parse(StringReader(html), object : HTMLEditorKit.ParserCallback() {
            override fun handleText(data: CharArray, pos: Int) { if (hidden == 0) result.append(data) }
            override fun handleStartTag(tag: HTML.Tag, attributes: MutableAttributeSet, pos: Int) {
                if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) hidden++
                if (hidden == 0 && tag in paragraphTags) newline(2)
                if (hidden == 0 && tag == HTML.Tag.LI) newline(1)
            }
            override fun handleEndTag(tag: HTML.Tag, pos: Int) {
                if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) hidden = (hidden - 1).coerceAtLeast(0)
                if (hidden == 0 && tag in paragraphTags) newline(2)
                if (hidden == 0 && tag == HTML.Tag.LI) newline(1)
            }
            override fun handleSimpleTag(tag: HTML.Tag, attributes: MutableAttributeSet, pos: Int) {
                if (hidden == 0 && tag == HTML.Tag.BR) result.append('\n')
            }
        }, true)
        return result.toString()
    }

    override suspend fun shareText(title: String, text: String): Result<Unit> = result {
        withContext(Dispatchers.Swing) {
            requireGui()
            val mail = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)
            val options = if (mail) arrayOf("复制到剪贴板", "用邮件分享", "取消") else arrayOf("复制到剪贴板", "取消")
            performDesktopTextShare(
                mailAvailable = mail,
                choose = { JOptionPane.showOptionDialog(owner, text, title, JOptionPane.DEFAULT_OPTION,
                    JOptionPane.PLAIN_MESSAGE, null, options, options[0]) },
                copy = { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) },
                mail = {
                    fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
                    Desktop.getDesktop().mail(URI("mailto:?subject=${encode(title)}&body=${encode(text)}"))
                },
            )
        }
    }

    override suspend fun readClipboardText(): Result<String?> = result {
        withContext(Dispatchers.Swing) {
            requireGui()
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) clipboard.getData(DataFlavor.stringFlavor) as? String else null
        }
    }
    override suspend fun copySensitiveText(label: String, text: String): Result<Unit> = result {
        withContext(Dispatchers.Swing) {
            requireGui()
            if (System.getProperty("os.name").startsWith("Windows", true)) {
                copyWindowsSensitiveText(owner, text)
                warn("已请求 Windows 不记录或同步此剪贴板内容；其他应用仍可能读取，请使用后及时清除")
            } else {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
                warn("桌面剪贴板无法保证隐藏敏感内容预览或阻止系统剪贴板历史，请使用后及时清除")
            }
        }
    }

    override suspend fun chooseImageDestination(image: ReaderExportImage): Result<String?> =
        createDocument(image.displayName, image.mimeType)

    override suspend fun shareImage(image: ReaderExportImage): Result<Unit> = result {
        val picture = withContext(Dispatchers.IO) {
            val context = currentCoroutineContext()
            readDesktopClipboardImage(path(image.filePath)) { context.ensureActive() }
        }
        var published = false
        try {
            withContext(Dispatchers.Swing) {
                requireGui()
                val message = "复制图片后，可粘贴到支持图片的应用。" +
                    if (picture.firstFrameOnly) "动画图片只复制第一帧；保存原图可保留动画。" else ""
                val selected = JOptionPane.showOptionDialog(owner, message, "分享图片",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, arrayOf("复制图片", "取消"), "复制图片")
                if (selected != 0) throw UserActionCancelled()
                currentCoroutineContext().ensureActive()
                Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageTransferable(picture.image), null)
                published = true // Clipboard owns the raster from here; do not flush it on return.
            }
        } finally { if (!published) picture.image.flush() }
    }

    override suspend fun openDocument(mimeTypes: List<String>): Result<String?> = result {
        withContext(Dispatchers.Swing) {
            requireGui()
            val chooser = JFileChooser().apply { configureMimeTypes(mimeTypes); dialogTitle = "选择文件" }
            if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath().toAbsolutePath().toString() else null
        }
    }

    override suspend fun createDocument(name: String, mimeType: String): Result<String?> = result {
        withContext(Dispatchers.Swing) { chooseDestination(name, listOf(mimeType), false) }
    }
    override suspend fun pickDestination(location: ComicExportLocation): Result<String?> = result {
        withContext(Dispatchers.Swing) { chooseDestination(location.name, listOf(location.mimeType), location.directory, archive = !location.directory) }
    }

    private fun chooseDestination(name: String, mimeTypes: List<String>, directory: Boolean, archive: Boolean = false): String? {
        requireGui()
        val chooser = JFileChooser().apply {
            dialogTitle = if (directory) "选择导出文件夹" else "保存文件"
            fileSelectionMode = if (directory) JFileChooser.DIRECTORIES_ONLY else JFileChooser.FILES_ONLY
            if (!directory) { selectedFile = java.io.File(name.substringAfterLast('/').substringAfterLast('\\')); configureMimeTypes(mimeTypes) }
        }
        while (chooser.showSaveDialog(owner) == JFileChooser.APPROVE_OPTION) {
            val selected = chooser.selectedFile.toPath().toAbsolutePath()
            if (directory) { require(Files.isDirectory(selected)) { "请选择已存在的文件夹" }; return selected.toString() }
            val target = resolveDesktopSavePath(selected, name)
            val reservation = if (archive) DesktopExportDestinations.capture(target) else null
            if (Files.exists(target) && JOptionPane.showConfirmDialog(owner, "文件已存在，是否替换？", "确认替换",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) continue
            require(!Files.isDirectory(target)) { "目标是文件夹，无法写入文件" }
            reservation?.let(DesktopExportDestinations::approve)
            return target.toString()
        }
        return null
    }

    override suspend fun readDocument(reference: String, maxBytes: Int): Result<ByteArray> = result {
        require(maxBytes >= 0) { "读取大小不能为负数" }
        withContext(Dispatchers.IO) { Files.newInputStream(path(reference)).use { it.readNBytes(maxBytes) } }
    }
    override suspend fun writeDocument(reference: String, bytes: ByteArray): Result<Unit> = result {
        withContext(Dispatchers.IO) { atomicOutput(path(reference)) { Files.newOutputStream(it).use { out -> out.write(bytes) } } }
    }
    override suspend fun exportNetworkLog(reference: String, repository: NetworkLogRepository): Result<Unit> = result {
        withContext(Dispatchers.IO) {
            atomicOutput(path(reference)) { temporary ->
                Files.newOutputStream(temporary).buffered().use { repository.export(it).getOrThrow() }
            }
        }
    }
    override suspend fun pickAvatarJpeg(): Result<ByteArray?> = result {
        val selected = openDocument(listOf("image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp")).getOrThrow()
            ?: return@result null
        withContext(Dispatchers.IO) {
            val bytes = Files.newInputStream(path(selected)).use { it.readNBytes(10 * 1024 * 1024 + 1) }
            require(bytes.size <= 10 * 1024 * 1024) { "头像原图不能超过10MiB" }
            val context = currentCoroutineContext()
            DesktopAvatarEncoder.jpeg(bytes) { context.ensureActive() }
        }
    }
    override suspend fun requestDownloadNotificationPermissionOnce() {
        // AWT desktop notifications have no runtime permission prompt. This does not claim a notification was sent.
    }
    override suspend fun openNotificationSettings(): Result<Unit> = result {
        withContext(Dispatchers.IO) {
            openDesktopNotificationSettings(System.getProperty("os.name"), GraphicsEnvironment.isHeadless())
        }
    }

    private fun requireGui() { check(!GraphicsEnvironment.isHeadless()) { "当前环境没有可用的图形桌面" } }
    private fun path(reference: String): Path = if (reference.startsWith("file:", true)) Path.of(URI(reference)) else Path.of(reference)
    private suspend fun atomicOutput(destination: Path, write: suspend (Path) -> Unit) {
        val absolute = destination.toAbsolutePath()
        val parent = requireNotNull(absolute.parent) { "保存路径没有父文件夹" }
        require(Files.isDirectory(parent)) { "保存文件夹不存在" }
        val temporary = Files.createTempFile(parent, ".copymanga-", ".tmp")
        try {
            write(temporary)
            currentCoroutineContext().ensureActive()
            try { Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (unsupported: AtomicMoveNotSupportedException) {
                if (Files.exists(absolute)) throw unsupported
                Files.move(temporary, absolute)
            }
        } finally { Files.deleteIfExists(temporary) }
    }
    private fun JFileChooser.configureMimeTypes(types: List<String>) {
        val extensions = types.flatMap { type -> when (type) {
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
        if (extensions.isNotEmpty()) fileFilter = FileNameExtensionFilter(extensions.joinToString(", "), *extensions.toTypedArray())
    }
}

private class ImageTransferable(private val image: Image) : Transferable {
    override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
    override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
    override fun getTransferData(flavor: DataFlavor): Any = if (isDataFlavorSupported(flavor)) image else throw UnsupportedFlavorException(flavor)
}
private suspend fun <T> result(block: suspend () -> T): Result<T> = try { Result.success(block()) }
catch (cancelled: CancellationException) { throw cancelled }
catch (error: Exception) { Result.failure(error) }
