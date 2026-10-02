package com.rchdliang.copymanga_neo.di

import android.content.Context
import coil.ImageLoader
import coil.imageLoader
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** 阅读图片的超时单独配置，保持原有路由、代理和凭据边界策略。 */
@Singleton
class ReaderImageLoaders @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient
) {
    private val loaders = mutableMapOf<Int, ImageLoader>()

    @Synchronized
    fun get(timeoutSeconds: Int): ImageLoader = loaders.getOrPut(timeoutSeconds) {
        context.imageLoader.newBuilder().okHttpClient(
            client.newBuilder()
                .callTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS).build()
        ).build()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReaderImageLoaderEntryPoint {
    fun readerImageLoaders(): ReaderImageLoaders
}
