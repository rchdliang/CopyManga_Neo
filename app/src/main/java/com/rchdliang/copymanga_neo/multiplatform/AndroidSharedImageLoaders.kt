package com.rchdliang.copymanga_neo.multiplatform

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okio.Path.Companion.toPath
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Coil3 shares the original routing/authentication/risk client, never Coil2's open disk journal. */
@Singleton
class AndroidSharedImageLoaders @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient
) {
    private val loaders = mutableMapOf<Int, ImageLoader>()
    private val memory = MemoryCache.Builder().maxSizePercent(context, 0.25).build()
    private val disk = DiskCache.Builder()
        .directory(context.cacheDir.resolve("image_cache_shared").absolutePath.toPath())
        .maxSizePercent(0.05).build()

    val diskSize: Long get() = disk.size
    fun openSnapshot(key: String): DiskCache.Snapshot? = disk.openSnapshot(key)
    fun clearCaches() { memory.clear(); disk.clear() }

    @Synchronized
    fun get(timeoutSeconds: Int): ImageLoader = loaders.getOrPut(timeoutSeconds) {
        val timed = client.newBuilder()
            .callTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS).build()
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { timed })) }
            .memoryCache(memory).diskCache(disk).crossfade(true).build()
    }
}
