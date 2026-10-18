package com.rchdliang.copymanga_neo.multiplatform.app

import coil3.PlatformContext
import coil3.disk.DiskCache
import com.rchdliang.copymanga_neo.data.local.portable.platformDocumentStorage
import com.rchdliang.copymanga_neo.data.repository.portable.MemorySecureCredentialStorage
import com.rchdliang.copymanga_neo.data.download.portable.desktopDownloadServices
import com.rchdliang.copymanga_neo.data.export.portable.FileComicExportIo
import com.rchdliang.copymanga_neo.data.image.portable.FileReaderImageIo
import com.rchdliang.copymanga_neo.data.storage.platformCacheStorage
import com.rchdliang.copymanga_neo.multiplatform.platform.*
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toPath
import java.nio.file.Path

/** Desktop owns native capabilities; all repository assembly and restoration is shared. */
class DesktopGraph private constructor(
    private val graph: PortableAppGraph,
    private val buildInfo: com.rchdliang.copymanga_neo.multiplatform.ui.profile.AppBuildInfo
) : AutoCloseable {
    val repositories get() = graph.repositories
    val startupWarnings get() = graph.startupWarnings
    fun platforms(host: DesktopPlatformHost) = AppPlatforms(host, host, host, host, host, graph.imageLoader, host, buildInfo)
    override fun close() = graph.close()
    suspend fun shutdown() = graph.shutdown()
    companion object {
        suspend fun create(directory: Path, parentScope: CoroutineScope, tapString: String = ""): DesktopGraph {
            val buildInfo = desktopBuildInfo()
            val cacheDirectory = directory.resolve("cache")
            val secure = DesktopSecureCredentialStorage(directory.resolve("credentials"))
            val disk = DiskCache.Builder().directory(cacheDirectory.resolve("images").toString().toPath())
                .maxSizeBytes(256L * 1024 * 1024).build()
            val services = PortableAppServices(
                documents = platformDocumentStorage(directory.resolve("settings").toString()),
                cache = platformCacheStorage(cacheDirectory.toString()),
                secureCredentials = if (secure.persistent) secure else MemorySecureCredentialStorage(),
                deviceName = System.getProperty("os.name"), deviceModel = "Desktop-${System.getProperty("os.arch")}",
                createHttpClient = { settings, timeout -> createDesktopHttpClient(settings, timeout) },
                proxyPolicy = desktopProxyPolicy,
                downloadServices = { scope, settings, client, probe, _ ->
                    desktopDownloadServices(scope, settings, client, probe).let {
                        AppDownloadServices(it.runtime, it.networkPolicy, it.clientProvider)
                    }
                },
                comicExportIo = FileComicExportIo(cacheDirectory.resolve("downloads").toString()),
                readerImageIo = FileReaderImageIo(directory.resolve("image-exports").toString(), disk),
                imageContext = PlatformContext.INSTANCE, imageDiskCache = disk, tapString = tapString, debugBuild = buildInfo.debug,
                startupWarnings = if (secure.persistent) emptyList() else listOf(
                    "系统安全密钥环不可用，本次登录仅保留到应用关闭；不会明文保存密码或令牌。")
            )
            return DesktopGraph(PortableAppGraph.create(services, parentScope), buildInfo)
        }
    }
}
