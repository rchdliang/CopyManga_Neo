package com.rchdliang.copymanga_neo.multiplatform.app

import coil3.PlatformContext
import com.rchdliang.copymanga_neo.data.download.portable.browserDownloadServices
import com.rchdliang.copymanga_neo.data.export.portable.BrowserComicExportIo
import com.rchdliang.copymanga_neo.data.image.portable.BrowserReaderImageIo
import com.rchdliang.copymanga_neo.data.local.portable.platformDocumentStorage
import com.rchdliang.copymanga_neo.data.repository.portable.MemorySecureCredentialStorage
import com.rchdliang.copymanga_neo.data.storage.platformCacheStorage
import com.rchdliang.copymanga_neo.multiplatform.platform.*
import kotlinx.coroutines.CoroutineScope

class BrowserGraph private constructor(val core: PortableAppGraph, val host: BrowserPlatformHost) : AutoCloseable {
    val repositories get() = core.repositories
    val platforms = AppPlatforms(host, host, host, host, host, core.imageLoader, host)
    val startupWarnings get() = core.startupWarnings
    override fun close() = core.close()
    companion object {
        suspend fun create(
            scope: CoroutineScope, host: BrowserPlatformHost, namespace: String = "copymanga-neo",
            tapString: String = "", debugBuild: Boolean = false
        ): BrowserGraph {
            require(namespace.matches(Regex("[A-Za-z0-9_.-]{1,50}")))
            val cacheNamespace = "$namespace-cache"
            val services = PortableAppServices(
                documents = platformDocumentStorage("$namespace-settings"), cache = platformCacheStorage(cacheNamespace),
                secureCredentials = MemorySecureCredentialStorage(), deviceName = "Browser", deviceModel = "WebAssembly",
                createHttpClient = ::createBrowserHttpClient, proxyPolicy = browserProxyPolicy,
                downloadServices = { appScope, settings, client, probe, _ ->
                    browserDownloadServices(appScope, settings, client, probe).let { AppDownloadServices(it.runtime, it.networkPolicy, it.clientProvider) }
                },
                comicExportIo = BrowserComicExportIo(cacheNamespace),
                readerImageIo = BrowserReaderImageIo("$namespace-image-exports", host::chooseImageDestination),
                imageContext = PlatformContext.INSTANCE, tapString = tapString, debugBuild = debugBuild,
                startupWarnings = listOf(
                    "浏览器登录仅保留到页面关闭；不会明文保存密码或令牌。",
                    "浏览器请求受站点跨域和受限请求头规则约束；下载只在页面可见时进行。"
                )
            )
            return BrowserGraph(PortableAppGraph.create(services, scope), host)
        }
    }
}
