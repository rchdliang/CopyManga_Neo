package com.rchdliang.copymanga_neo

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okhttp3.OkHttpClient
import com.rchdliang.copymanga_neo.domain.repository.NetworkSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application类，用于初始化Hilt依赖注入框架
 * 同时配置 Coil 全局图片加载和缓存机制
 */
@HiltAndroidApp
class CopyMangaApplication : Application(), ImageLoaderFactory {
    var dataRestoreMessage: String? = null
        private set

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        // 先完成恢复/回滚，随后 Hilt 和后台任务才能读取持久化数据。
        dataRestoreMessage = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            val reset = com.rchdliang.copymanga_neo.data.local.security.applyRequestedCredentialReset(base)
            val restored = com.rchdliang.copymanga_neo.data.backup.AndroidUserDataBackup(base).recover()
            listOfNotNull(if (reset) "本机登录信息已重置，请重新登录" else null, restored)
                .joinToString("；").takeIf { it.isNotBlank() }
        }
    }
    @Inject lateinit var networkClient: OkHttpClient
    @Inject lateinit var networkSettings: NetworkSettingsRepository

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            networkSettings.refreshIfNeeded()
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(networkClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // 使用可用内存的 25% 作为内存缓存
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05) // 使用磁盘空间的 5% 作为磁盘缓存
                    .build()
            }
            .respectCacheHeaders(false) // 强制使用缓存，即使服务器要求不缓存（可选，取决于需求）
            .crossfade(true) // 淡入淡出效果
            .build()
    }
}
