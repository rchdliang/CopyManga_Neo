package com.rchdliang.copymanga_neo.multiplatform

import android.content.Context
import com.rchdliang.copymanga_neo.BuildConfig
import com.rchdliang.copymanga_neo.data.download.DownloadService
import com.rchdliang.copymanga_neo.data.export.ComicExportService
import com.rchdliang.copymanga_neo.data.download.portable.DownloadRuntime
import com.rchdliang.copymanga_neo.multiplatform.app.PortableAppGraph
import com.rchdliang.copymanga_neo.multiplatform.app.createAndroidAppGraph
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** 应用级唯一数据容器，界面重建不会关闭后台下载。 */
class AndroidAppContainer(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutable = MutableStateFlow<Result<PortableAppGraph>?>(null)
    val startup = mutable.asStateFlow()
    val current: PortableAppGraph? get() = mutable.value?.getOrNull()
    val downloads = ForegroundServiceGate(start = { DownloadService.start(app) })
    val exports = ForegroundServiceGate(start = { ComicExportService.start(app) })
    private var initialization: Job? = null

    init {
        start()
    }

    @Synchronized
    fun start() {
        if (initialization?.isActive == true || current != null) return
        mutable.value = null
        initialization = scope.launch {
            try {
                val graph = createAndroidAppGraph(
                    app,
                    scope,
                    object : DownloadRuntime {
                        override fun onWorkAvailable() {
                            DownloadService.start(app)
                        }

                        override suspend fun awaitReady() = downloads.awaitReady()
                    },
                    beforeExport = { exports.awaitReady() },
                    tapString = BuildConfig.OFFICIAL_APP_TAP_STRING,
                    debug = BuildConfig.DEBUG
                )
                mutable.value = Result.success(graph)
                scope.launch {
                    try {
                        graph.repositories.network.refreshIfNeeded()
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) { /* 自动发现失败不撤销已初始化的数据图。 */
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                mutable.value = Result.failure(error)
            }
        }
    }

    suspend fun awaitGraph(): PortableAppGraph =
        startup.filterNotNull().first().fold({ it }, { throw it })
}

val Context.sharedContainer: AndroidAppContainer
    get() = (applicationContext as com.rchdliang.copymanga_neo.CopyMangaApplication).container
