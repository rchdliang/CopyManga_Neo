package com.rchdliang.copymanga_neo.multiplatform.app

import android.content.Context
import android.os.Build
import coil3.disk.DiskCache
import com.rchdliang.copymanga_neo.data.download.portable.DownloadRuntime
import com.rchdliang.copymanga_neo.data.download.portable.androidDownloadServices
import com.rchdliang.copymanga_neo.data.local.portable.platformDocumentStorage
import com.rchdliang.copymanga_neo.data.storage.platformCacheStorage
import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.data.repository.portable.AndroidSecureCredentialStorage
import com.rchdliang.copymanga_neo.data.image.portable.AndroidReaderImageIo
import com.rchdliang.copymanga_neo.data.export.portable.AndroidComicExportIo
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toPath

/** Android 与其他平台使用同一个数据图；仅在这里提供系统能力。 */
suspend fun createAndroidAppGraph(
    context: Context, scope: CoroutineScope, downloadRuntime: DownloadRuntime,
    beforeExport: suspend () -> Unit, tapString: String, debug: Boolean,
    startupTrace: StartupTrace = StartupTrace.None,
    onAppSettingsReady: (com.rchdliang.copymanga_neo.domain.model.AppSettings) -> Unit = {}
): PortableAppGraph {
    startupTrace.mark("graph_start")
    val app = context.applicationContext
    val root = app.filesDir.resolve("shared-data")
    val disk =
        DiskCache.Builder().directory(app.cacheDir.resolve("shared-images").absolutePath.toPath())
            .maxSizeBytes(256L * 1024 * 1024).build()
    return PortableAppGraph.create(
        PortableAppServices(
            documents = platformDocumentStorage(root.resolve("settings").absolutePath),
        cache = platformCacheStorage(root.resolve("content").absolutePath),
        secureCredentials = AndroidSecureCredentialStorage(app),
        deviceName = Build.ID, deviceModel = "${Build.MODEL}-${Build.DEVICE}",
        createHttpClient = { settings, timeout -> createAndroidHttpClient(settings, timeout) },
        proxyPolicy = androidProxyPolicy,
        downloadServices = { childScope, settings, client, _, networkSettings ->
            androidDownloadServices(
                app,
                childScope,
                settings,
                downloadRuntime,
                networkSettings,
                client
            )
        },
        comicExportIo = AndroidComicExportIo(app, root.resolve("content/downloads")),
        readerImageIo = AndroidReaderImageIo(app, disk),
        imageContext = app, imageDiskCache = disk, tapString = tapString, debugBuild = debug,
        beforeExport = beforeExport, startupTrace = startupTrace, onAppSettingsReady = onAppSettingsReady,
        webApiUserAgent = (System.getProperty("http.agent")?.takeIf { it.isNotBlank() }
            ?: "Dalvik/2.1.0 (Linux; U; Android)") + " com.copymanga.app"
    ), scope)
}
