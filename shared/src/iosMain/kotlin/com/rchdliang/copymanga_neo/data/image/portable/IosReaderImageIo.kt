@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.rchdliang.copymanga_neo.data.image.portable

import coil3.disk.DiskCache
import com.rchdliang.copymanga_neo.data.export.portable.IosExportDestinations
import com.rchdliang.copymanga_neo.domain.model.ReaderExportImage
import com.rchdliang.copymanga_neo.multiplatform.platform.IosDocumentBookmarks
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.*
import platform.Photos.*
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** App-sandbox preparation, coordinated document writes and original-byte Photos resource creation. */
class IosReaderImageIo(cacheDirectory: String, private val diskCache: DiskCache? = null) : ReaderImageIo {
    private val files = NSFileManager.defaultManager
    private val root = resolved("${cacheDirectory.trimEnd('/')}/reader_shared_images")
    init { require(root.startsWith('/') && root != "/reader_shared_images") { "需要应用私有缓存目录" } }

    override suspend fun prunePrepared(beforeMillis: Long) = withContext(Dispatchers.Default) {
        ensureRoot()
        files.contentsOfDirectoryAtPath(root, null)?.filterIsInstance<String>()?.forEach { name ->
            currentCoroutineContext().ensureActive()
            val path = "$root/$name"
            val attributes = files.attributesOfItemAtPath(path, null) ?: return@forEach
            val modified = attributes[NSFileModificationDate] as? NSDate
            if (attributes[NSFileType] == NSFileTypeRegular && modified != null && modified.timeIntervalSince1970 * 1000 < beforeMillis) {
                files.removeItemAtPath(path, null)
            }
        }
        Unit
    }
    override suspend fun readCached(source: String, maximumBytes: Int): ByteArray? = withContext(Dispatchers.Default) {
        readCoilReaderImageCache(diskCache, source, maximumBytes)
    }
    override suspend fun readLocal(source: String, maximumBytes: Int): ByteArray = withContext(Dispatchers.Default) {
        read(path(source), maximumBytes)
    }
    override suspend fun inspect(bytes: ByteArray): ReaderImageMetadata = withContext(Dispatchers.Default) { readerImageHeader(bytes) }
    override suspend fun publish(displayName: String, bytes: ByteArray): String = withContext(Dispatchers.Default) {
        ensureRoot()
        require(displayName.isNotBlank() && displayName.none { it == '/' || it == '\\' || it.code == 0 }) { "图片文件名无效" }
        val destination = "$root/$displayName"
        require(resolved(destination).substringBeforeLast('/') == root && !files.fileExistsAtPath(destination)) { "图片临时文件已存在" }
        currentCoroutineContext().ensureActive()
        check(data(bytes).writeToFile(destination, true)) { "图片临时文件准备失败" }
        destination
    }
    override suspend fun readPrepared(image: ReaderExportImage, maximumBytes: Int): ByteArray = withContext(Dispatchers.Default) {
        val source = path(image.filePath)
        require(source.substringBeforeLast('/') == root && source.substringAfterLast('/') == image.displayName) { "图片路径不属于临时导出目录" }
        read(source, maximumBytes)
    }
    override suspend fun saveToDocument(image: ReaderExportImage, destination: String, readValidatedBytes: suspend () -> ByteArray) = withContext(Dispatchers.Default) {
        // Consume the original picker reference; a restored bookmark can resolve to a moved URL.
        val reservation = IosExportDestinations.claim(destination)
        val lease = IosDocumentBookmarks.acquire(destination)
        val coordinator = NSFileCoordinator(filePresenter = null)
        try {
            val bytes = readValidatedBytes()
            val context = currentCoroutineContext()
            context.ensureActive()
            val payload = data(bytes)
            var written = false
            var failure: Exception? = null
            // No exception escapes an Objective-C callback. Validate identity within coordination,
            // immediately before atomic replacement, so an unrelated file is never truncated.
            coordinator.coordinateWritingItemAtURL(lease.url, NSFileCoordinatorWritingForReplacing, null) { coordinated ->
                try {
                    val selected = requireNotNull(coordinated) { "文档协调失败" }
                    reservation.validate(requireNotNull(selected.absoluteString))
                    context.ensureActive()
                    written = payload.writeToURL(selected, true)
                } catch (error: Exception) { failure = error }
            }
            failure?.let { throw it }
            check(written) { "无法确认图片已写入所选位置" }
        } catch (error: Exception) {
            // Roll back only this picker's still-empty device/inode. A successful replacement or
            // another writer's file has a different identity/content and must be left untouched.
            coordinator.coordinateWritingItemAtURL(lease.url, NSFileCoordinatorWritingForDeleting, null) { coordinated ->
                runCatching { coordinated?.absoluteString?.let { reservation.removeIfStillEmpty(it) } }
            }
            throw error
        } finally { lease.close() }
    }
    override suspend fun saveToGallery(image: ReaderExportImage, bytes: ByteArray) {
        // Avoid iOS terminating the app when a host omitted its mandatory usage-description key.
        check(NSBundle.mainBundle.objectForInfoDictionaryKey("NSPhotoLibraryAddUsageDescription") is String) {
            "此安装包未配置相册保存权限说明"
        }
        val status = suspendCancellableCoroutine<Long> { continuation ->
            PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { value ->
                if (continuation.isActive) continuation.resume(value)
            }
        }
        check(status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited) { "未获得相册写入权限" }
        currentCoroutineContext().ensureActive()
        val payload = withContext(Dispatchers.Default) { data(bytes) }
        suspendCancellableCoroutine<Unit> { continuation ->
            // Photos commits this transaction atomically; its API does not expose an in-flight cancel.
            PHPhotoLibrary.sharedPhotoLibrary().performChanges({
                val request = PHAssetCreationRequest.creationRequestForAsset()
                val options = PHAssetResourceCreationOptions().apply { originalFilename = image.displayName }
                request.addResourceWithType(PHAssetResourceTypePhoto, payload, options)
            }) { success, error ->
                if (continuation.isActive) {
                    if (success) continuation.resume(Unit)
                    else continuation.resumeWithException(IllegalStateException(error?.localizedDescription ?: "图片保存未完成"))
                }
            }
        }
    }

    private fun ensureRoot() { check(files.createDirectoryAtPath(root, true, null, null)) { "无法创建图片临时目录" } }
    private fun resolved(path: String) = NSString.create(string = path).stringByResolvingSymlinksInPath.trimEnd('/')
    private fun path(reference: String): String {
        val raw = if (reference.startsWith("file:")) {
            val url = requireNotNull(NSURL.URLWithString(reference))
            require(url.fileURL) { "图片地址不是本地文件" }
            requireNotNull(url.path)
        } else reference
        require(raw.startsWith('/')) { "图片地址不是本地文件" }
        return resolved(raw)
    }
    private suspend fun read(path: String, maximumBytes: Int): ByteArray {
        currentCoroutineContext().ensureActive()
        val attributes = files.attributesOfItemAtPath(path, null) ?: error("本地图片不存在，请重新下载")
        require(attributes[NSFileType] == NSFileTypeRegular) { "图片不是普通文件" }
        val size = (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: error("无法读取图片大小")
        require(size in 0..maximumBytes.toLong()) { "图片超过单张安全大小限制" }
        // Metadata is only an early rejection: the file can grow between stat and read.
        val input = NSFileHandle.fileHandleForReadingAtPath(path) ?: error("无法读取本地图片")
        val payload = try { input.readDataOfLength(maximumBytes.toULong() + 1uL) }
            finally { input.closeFile() }
        require(payload.length <= maximumBytes.toULong()) { "图片超过单张安全大小限制" }
        currentCoroutineContext().ensureActive()
        return ByteArray(payload.length.toInt()).also { bytes ->
            if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), payload.bytes, payload.length) }
        }
    }
    private fun data(bytes: ByteArray): NSData = if (bytes.isEmpty()) NSData.create(bytes = null, length = 0uL)
        else bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
}
