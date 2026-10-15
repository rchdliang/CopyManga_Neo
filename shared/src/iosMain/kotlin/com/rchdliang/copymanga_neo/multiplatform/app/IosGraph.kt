package com.rchdliang.copymanga_neo.multiplatform.app

import coil3.PlatformContext
import coil3.disk.DiskCache
import com.rchdliang.copymanga_neo.data.download.portable.iosDownloadServices
import com.rchdliang.copymanga_neo.data.export.portable.FileComicExportIo
import com.rchdliang.copymanga_neo.data.image.portable.IosReaderImageIo
import com.rchdliang.copymanga_neo.data.local.portable.platformDocumentStorage
import com.rchdliang.copymanga_neo.data.storage.platformCacheStorage
import com.rchdliang.copymanga_neo.multiplatform.platform.*
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toPath
import platform.Foundation.*
import platform.UIKit.UIDevice

class IosGraph private constructor(val core: PortableAppGraph, val host: IosPlatformHost) : AutoCloseable {
    val repositories get() = core.repositories
    val platforms = AppPlatforms(host, host, host, host, host, core.imageLoader, host)
    val startupWarnings get() = core.startupWarnings
    override fun close() { core.close(); host.close() }
    companion object {
        fun applicationDirectory(): String {
            val support = NSFileManager.defaultManager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask)
                .firstOrNull() as? NSURL ?: error("无法定位应用资料目录")
            return "${requireNotNull(support.path)}/CopyMangaNeo"
        }
        suspend fun create(
            directory: String, scope: CoroutineScope, host: IosPlatformHost,
            tapString: String = "", debugBuild: Boolean = false
        ): IosGraph {
            val root = directory.trimEnd('/'); require(root.startsWith('/') && root.isNotEmpty())
            val cacheDirectory = "$root/cache"
            val disk = DiskCache.Builder().directory("$cacheDirectory/images".toPath()).maxSizeBytes(256L * 1024 * 1024).build()
            val services = PortableAppServices(
                documents = platformDocumentStorage("$root/settings"), cache = platformCacheStorage(cacheDirectory),
                secureCredentials = IosSecureCredentialStorage(), deviceName = UIDevice.currentDevice.systemName,
                deviceModel = UIDevice.currentDevice.model, createHttpClient = ::createIosHttpClient, proxyPolicy = iosProxyPolicy,
                downloadServices = { appScope, settings, client, probe ->
                    iosDownloadServices(appScope, settings, client, probe).let { AppDownloadServices(it.runtime, it.networkPolicy, it.clientProvider) }
                },
                comicExportIo = FileComicExportIo("$cacheDirectory/downloads"),
                readerImageIo = IosReaderImageIo("$root/image-exports", disk), imageContext = PlatformContext.INSTANCE,
                imageDiskCache = disk, tapString = tapString, debugBuild = debugBuild
            )
            return IosGraph(PortableAppGraph.create(services, scope), host)
        }
    }
}
