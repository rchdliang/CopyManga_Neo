@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.rchdliang.copymanga_neo.multiplatform.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.rchdliang.copymanga_neo.data.export.portable.IosExportDestinations
import com.rchdliang.copymanga_neo.domain.model.ComicExportLocation
import com.rchdliang.copymanga_neo.domain.model.nativeShareResult
import com.rchdliang.copymanga_neo.domain.model.ReaderExportImage
import com.rchdliang.copymanga_neo.domain.platform.NetworkLogOutputStream
import com.rchdliang.copymanga_neo.domain.repository.NetworkLogRepository
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackDispatcher
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.downloads.DownloadNotificationPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.export.ComicExportPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.novel.NovelReaderPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.profile.ProfilePlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.ReaderPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.ScreenOrientationSetting
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.CoreGraphics.*
import platform.CoreFoundation.*
import platform.ImageIO.*
import platform.Foundation.*
import platform.UIKit.*
import platform.UniformTypeIdentifiers.*
import platform.UserNotifications.*
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.math.max

/** The UIKit root owns status-bar/orientation preferences and returns a restoration lease. */
fun interface IosReaderWindowPolicy {
    fun acquire(hiddenBars: Boolean, lightBars: Boolean, orientation: ScreenOrientationSetting?): Result<AutoCloseable>
}

/** UIKit host. Keep this instance alive for the graph lifetime, then close security-scoped URLs. */
class IosPlatformHost(
    private val presenter: () -> UIViewController,
    private val windowPolicy: IosReaderWindowPolicy? = null
) : UiPlatform, ReaderPlatform, NovelReaderPlatform, ComicExportPlatform,
    ProfilePlatform, DownloadNotificationPlatform, AutoCloseable {
    override val notificationSettingsUnavailableReason: String? = null
    override val backDispatcher = UiBackDispatcher()
    override fun batteryStatuses() = iosReaderBatteryStatuses()
    private val warnings = MutableStateFlow<List<String>>(emptyList())
    val capabilityWarnings: StateFlow<List<String>> = warnings
    private val securityScopedUrls = mutableListOf<NSURL>()
    private val delegates = mutableSetOf<NSObject>()
    private var awakeLeases = 0
    private var previousIdleDisabled = false
    private fun warn(message: String) { if (message !in warnings.value) warnings.value += message }

    @Composable override fun ImmersiveEffect(orientation: ScreenOrientationSetting?) {
        DisposableEffect(orientation) {
            val lease = if (orientation == null) null else acquireWindow(true, false, orientation)
            onDispose { lease?.close() }
        }
    }
    @Composable override fun KeepScreenOnEffect(enabled: Boolean) {
        DisposableEffect(enabled) {
            val lease = if (enabled) acquireAwake() else null
            onDispose { lease?.close() }
        }
    }
    @Composable override fun ReaderWindow(keepScreenOn: Boolean, controlsVisible: Boolean, lightBars: Boolean) {
        DisposableEffect(controlsVisible, lightBars) {
            val lease = acquireWindow(!controlsVisible, lightBars, null)
            onDispose { lease?.close() }
        }
        KeepScreenOnEffect(keepScreenOn)
    }
    private fun acquireWindow(hidden: Boolean, light: Boolean, orientation: ScreenOrientationSetting?): AutoCloseable? {
        val policy = windowPolicy
        if (policy == null) { warn("iOS宿主尚未连接状态栏与屏幕方向回调；系统栏与方向保持系统设置"); return null }
        return policy.acquire(hidden, light, orientation).onFailure { warn(it.message ?: "无法调整阅读窗口") }.getOrNull()
    }
    private fun acquireAwake(): AutoCloseable {
        val application = UIApplication.sharedApplication
        if (awakeLeases++ == 0) { previousIdleDisabled = application.idleTimerDisabled; application.idleTimerDisabled = true }
        var closed = false
        return AutoCloseable { if (!closed) { closed = true; if (--awakeLeases == 0) application.idleTimerDisabled = previousIdleDisabled } }
    }
    override fun close() {
        securityScopedUrls.forEach { it.stopAccessingSecurityScopedResource() }; securityScopedUrls.clear()
        if (awakeLeases > 0) { awakeLeases = 0; UIApplication.sharedApplication.idleTimerDisabled = previousIdleDisabled }
    }
    override fun formatDate(epochMilliseconds: Long): String = NSDateFormatter().apply {
        dateFormat = "yyyy-MM-dd"; locale = NSLocale.currentLocale; timeZone = NSTimeZone.localTimeZone
    }.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMilliseconds / 1000.0))
    override fun formatLineHeight(value: Float) = decimal(value.toDouble()) + " 倍"
    override fun formatFileSize(bytes: Long): String {
        val units = listOf("B", "KB", "MB", "GB", "TB"); var n = bytes.coerceAtLeast(0).toDouble(); var index = 0
        while (n >= 1000 && index < units.lastIndex) { n /= 1000; index++ }
        return (if (index == 0) n.toLong().toString() else decimal(n)) + " " + units[index]
    }
    private fun decimal(value: Double): String = NSNumberFormatter().apply {
        minimumFractionDigits = 2u; maximumFractionDigits = 2u; usesGroupingSeparator = false; locale = NSLocale.currentLocale
    }.stringFromNumber(NSNumber(value)) ?: value.toString()
    override fun plainTextFromHtml(html: String): String {
        // Strip all resource-bearing markup before Foundation's HTML decoder can load it.
        val text = html.replace(Regex("<(script|style)[^>]*>.*?</\\1\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(?:p|div|h[1-6]|blockquote)\\s*>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<[^>]*>"), "")
        val safe = text.replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
        return NSAttributedString.create(data = safe.encodeToByteArray().asData(),
            options = mapOf(NSDocumentTypeDocumentAttribute to NSHTMLTextDocumentType, NSCharacterEncodingDocumentAttribute to NSUTF8StringEncoding),
            documentAttributes = null, error = null)?.string ?: text
    }

    override suspend fun shareText(title: String, text: String) = share(listOf(text), title)
    override suspend fun shareImage(image: ReaderExportImage) = share(listOf(fileUrl(image.filePath)), image.displayName)
    private suspend fun share(items: List<Any>, title: String): Result<Unit> = onMain {
        suspendCancellableCoroutine { continuation ->
            val parent = presenter()
            val controller = UIActivityViewController(activityItems = items, applicationActivities = null)
            controller.title = title
            controller.popoverPresentationController?.apply { sourceView = parent.view; sourceRect = parent.view.bounds }
            controller.completionWithItemsHandler = { _, completed, _, error ->
                if (continuation.isActive) continuation.resume(nativeShareResult(completed, error?.localizedDescription))
            }
            continuation.invokeOnCancellation { NSOperationQueue.mainQueue.addOperationWithBlock { controller.dismissViewControllerAnimated(true, null) } }
            parent.presentViewController(controller, animated = true, completion = null)
        }
    }
    override suspend fun readClipboardText(): Result<String?> = onMain { Result.success(UIPasteboard.generalPasteboard.string) }
    override suspend fun copySensitiveText(label: String, text: String): Result<Unit> = onMain {
        // Local-only and short expiration avoid Universal Clipboard transmission and persistent history.
        UIPasteboard.generalPasteboard.setItems(listOf(mapOf("public.utf8-plain-text" to text)),
            options = mapOf(UIPasteboardOptionLocalOnly to true, UIPasteboardOptionExpirationDate to NSDate.dateWithTimeIntervalSinceNow(60.0)))
        Result.success(Unit)
    }
    override suspend fun openDocument(mimeTypes: List<String>): Result<String?> {
        val types = mimeTypes.mapNotNull { if (it == "image/*") UTTypeImage else UTType.typeWithMIMEType(it) }.ifEmpty { listOf(UTTypeData) }
        return onMain { pick(UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = false)) }
    }
    override suspend fun createDocument(name: String, mimeType: String): Result<String?> = runCatchingSuspend {
        val directory = NSTemporaryDirectory() + "copymanga-picker-" + NSUUID().UUIDString
        check(NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)) { "无法建立导出临时目录" }
        val source = directory + "/" + name.substringAfterLast('/').substringAfterLast('\\').ifBlank { "export" }
        check(NSData().writeToFile(source, atomically = true)) { "无法建立导出文件" }
        try { onMain { pick(UIDocumentPickerViewController(forExportingURLs = listOf(NSURL.fileURLWithPath(source)), asCopy = false)) }.getOrThrow() }
        finally { NSFileManager.defaultManager.removeItemAtPath(directory, null) }
    }
    override suspend fun chooseImageDestination(image: ReaderExportImage) = createDocument(image.displayName, image.mimeType)
        .mapCatching { selected -> selected?.also(IosExportDestinations::reserve) }
    override suspend fun pickDestination(location: ComicExportLocation) = if (location.directory)
        onMain { pick(UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder), asCopy = false)) }
        else createDocument(location.name, location.mimeType).mapCatching { selected -> selected?.also(IosExportDestinations::reserve) }

    private suspend fun pick(controller: UIDocumentPickerViewController): Result<String?> = onMain {
        suspendCancellableCoroutine { continuation ->
            val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                    delegates.remove(this)
                    if (!continuation.isActive) return
                    val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                    if (url != null) {
                        if (url.startAccessingSecurityScopedResource()) securityScopedUrls += url
                        IosDocumentBookmarks.remember(url).onFailure { warn(it.message ?: "无法保存文件位置权限，重启后请重新选择") }
                    }
                    if (continuation.isActive) continuation.resume(Result.success(url?.absoluteString))
                }
                override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                    delegates.remove(this); if (continuation.isActive) continuation.resume(Result.success(null))
                }
            }
            delegates += delegate; controller.delegate = delegate; controller.allowsMultipleSelection = false
            continuation.invokeOnCancellation { NSOperationQueue.mainQueue.addOperationWithBlock { delegates.remove(delegate); controller.dismissViewControllerAnimated(true, null) } }
            presenter().presentViewController(controller, animated = true, completion = null)
        }
    }
    override suspend fun readDocument(reference: String, maxBytes: Int): Result<ByteArray> = runCatchingSuspend {
        require(maxBytes >= 0) { "读取大小不能为负数" }
        withContext(Dispatchers.Default) {
            IosDocumentBookmarks.acquire(reference).use { lease ->
                var result: Result<ByteArray>? = null
                NSFileCoordinator(filePresenter = null).coordinateReadingItemAtURL(lease.url, 0u, null) { coordinated ->
                    result = runCatching {
                        val path = requireNotNull(coordinated?.path) { "文件提供方未返回可读位置" }
                        val handle = NSFileHandle.fileHandleForReadingAtPath(path) ?: error("无法读取所选文件")
                        try { requireNotNull(handle.readDataUpToLength(maxBytes.toULong(), null)) { "读取所选文件失败" }.copyBytes() }
                        finally { handle.closeAndReturnError(null) }
                    }
                }
                requireNotNull(result) { "文件提供方无法协调读取，请重新选择文件" }.getOrThrow()
            }
        }
    }
    override suspend fun writeDocument(reference: String, bytes: ByteArray): Result<Unit> = runCatchingSuspend {
        withContext(Dispatchers.Default) {
            IosDocumentBookmarks.acquire(reference).use { lease ->
                var written = false
                NSFileCoordinator(filePresenter = null).coordinateWritingItemAtURL(lease.url, NSFileCoordinatorWritingForReplacing, null) { coordinated ->
                    if (coordinated != null) written = bytes.asData().writeToURL(coordinated, atomically = true)
                }
                check(written) { "文件提供方无法完成写入，请重新选择保存位置" }
            }
        }
    }
    override suspend fun exportNetworkLog(reference: String, repository: NetworkLogRepository): Result<Unit> = runCatchingSuspend {
        withContext(Dispatchers.Default) {
            val temporary = NSURL.fileURLWithPath(NSTemporaryDirectory() + "copymanga-log-" + NSUUID().UUIDString)
            try {
                val sink = IosLogOutput(temporary)
                try { repository.export(sink).getOrThrow() } finally { sink.close() }
                IosDocumentBookmarks.acquire(reference).use { lease ->
                    var copied: Result<Unit>? = null
                    val context = currentCoroutineContext()
                    NSFileCoordinator(filePresenter = null).coordinateWritingItemAtURL(lease.url, NSFileCoordinatorWritingForReplacing, null) { coordinated ->
                        copied = runCatching {
                            val destination = requireNotNull(coordinated) { "文件提供方未返回可写位置" }
                            val input = NSFileHandle.fileHandleForReadingAtPath(requireNotNull(temporary.path)) ?: error("日志暂存文件不可读")
                            val output = IosLogOutput(destination)
                            try {
                                while (true) {
                                    context.ensureActive()
                                    val chunk = requireNotNull(input.readDataUpToLength(65536u, null)) { "读取日志暂存失败" }
                                    if (chunk.length == 0uL) break
                                    output.write(chunk.copyBytes())
                                }
                            } finally { try { output.close() } finally { input.closeAndReturnError(null) } }
                        }
                    }
                    requireNotNull(copied) { "文件提供方无法协调日志导出" }.getOrThrow()
                }
            } finally { NSFileManager.defaultManager.removeItemAtURL(temporary, null) }
        }
    }
    override suspend fun pickAvatarJpeg(): Result<ByteArray?> = runCatchingSuspend {
        val reference = openDocument(listOf("image/*")).getOrThrow() ?: return@runCatchingSuspend null
        val bytes = readDocument(reference, 10 * 1024 * 1024 + 1).getOrThrow()
        require(bytes.size <= 10 * 1024 * 1024) { "头像原图不能超过10MiB" }
        withContext(Dispatchers.Main.immediate) {
            val image = avatarThumbnail(bytes)
            val width = image.size.useContents { width }; val height = image.size.useContents { height }
            require(width > 0 && height > 0) { "头像尺寸无效" }
            val ratio = minOf(1.0, 1024.0 / max(width, height)); val size = CGSizeMake(max(1.0, width * ratio), max(1.0, height * ratio))
            UIGraphicsBeginImageContextWithOptions(size, true, 1.0)
            try {
                UIColor.whiteColor.setFill(); UIRectFill(CGRectMake(0.0, 0.0, width * ratio, height * ratio))
                image.drawInRect(CGRectMake(0.0, 0.0, width * ratio, height * ratio))
                val normalized = UIGraphicsGetImageFromCurrentImageContext() ?: error("头像缩放失败")
                UIImageJPEGRepresentation(normalized, 0.9)?.copyBytes() ?: error("JPEG编码失败")
            } finally { UIGraphicsEndImageContext() }
        }
    }
    override suspend fun openNotificationSettings(): Result<Unit> = onMain {
        suspendCancellableCoroutine { continuation ->
            UIApplication.sharedApplication.openURL(NSURL.URLWithString(UIApplicationOpenSettingsURLString)!!, emptyMap<Any?, Any>()) { opened ->
                if (continuation.isActive) continuation.resume(if (opened) Result.success(Unit) else Result.failure(IllegalStateException("无法打开通知设置")))
            }
        }
    }
    override suspend fun requestDownloadNotificationPermissionOnce() {
        val defaults = NSUserDefaults.standardUserDefaults; val key = "copymanga-download-notification-requested"
        if (defaults.boolForKey(key)) return
        defaults.setBool(true, key)
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound) { granted, error ->
            if (!granted) warn(error?.localizedDescription ?: "未获通知权限，下载状态仍显示在应用内")
        }
    }
}

internal fun fileUrl(reference: String): NSURL = if (reference.startsWith("file:"))
    requireNotNull(NSURL.URLWithString(reference)) else NSURL.fileURLWithPath(reference)
private suspend fun <T> onMain(block: suspend () -> Result<T>): Result<T> = runCatchingSuspend { withContext(Dispatchers.Main.immediate) { block().getOrThrow() } }
private suspend inline fun <T> runCatchingSuspend(block: () -> T): Result<T> = try { Result.success(block()) }
    catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { Result.failure(error) }

/** ImageIO downsamples before raster allocation and applies the encoded EXIF transform. */
private fun avatarThumbnail(bytes: ByteArray): UIImage {
    val data = CFBridgingRetain(bytes.asData())
    val maximum = CFBridgingRetain(NSNumber(1024))
    try {
        val source = CGImageSourceCreateWithData(data?.reinterpret(), null) ?: error("无法解码头像图片")
        try {
            val thumbnail = withIosCfDictionary(listOf(
                kCGImageSourceCreateThumbnailFromImageAlways to kCFBooleanTrue,
                kCGImageSourceCreateThumbnailWithTransform to kCFBooleanTrue,
                kCGImageSourceThumbnailMaxPixelSize to maximum
            )) { CGImageSourceCreateThumbnailAtIndex(source, 0u, it) } ?: error("头像缩略图生成失败")
            try { return UIImage.imageWithCGImage(thumbnail) }
            finally { CGImageRelease(thumbnail) }
        } finally { CFRelease(source) }
    } finally { CFBridgingRelease(maximum); CFBridgingRelease(data) }
}

private class IosLogOutput(url: NSURL) : NetworkLogOutputStream() {
    private val output = NSOutputStream(uRL = url, append = false).apply { open() }
    override fun write(value: Int) = write(byteArrayOf(value.toByte()))
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
        if (length == 0) return
        bytes.usePinned { pinned ->
            var done = 0
            while (done < length) {
                val count = output.write(pinned.addressOf(offset + done).reinterpret(), (length - done).toULong())
                check(count > 0) { output.streamError?.localizedDescription ?: "写入日志失败" }
                done += count.toInt()
            }
        }
    }
    override fun close() { output.close(); check(output.streamError == null) { output.streamError?.localizedDescription ?: "关闭日志文件失败" } }
}
