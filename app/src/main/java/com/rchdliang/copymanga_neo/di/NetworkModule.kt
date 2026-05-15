package com.rchdliang.copymanga_neo.di

import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.rchdliang.copymanga_neo.data.remote.api.MangaApiService
import com.rchdliang.copymanga_neo.data.remote.api.SystemApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt 网络模块
 * 提供 Retrofit 和 OkHttp 相关依赖
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // 主域名（用于网页请求和 Origin）
    private const val WWW_BASE_URL = "https://www.copy20.com/"

    // API 域名
    private const val API_BASE_URL = "https://api.2026copy.com/"
    
    private const val TIMEOUT_SECONDS = 30L
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        // 配置日志拦截器，使用自定义 Logger 确保在 Logcat 中清晰显示
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            // 使用 "OkHttp" 作为标签，方便在 Logcat 中过滤
            // 如果日志过长，Log.d 可能会截断，但通常 BODY 级别是必需的
            android.util.Log.d("OkHttp", message)
        }.apply {
            level = if (com.rchdliang.copymanga_neo.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            // 1. 添加请求头拦截器（模拟真实浏览器）
            .addInterceptor(HeadersInterceptor())

            // 2. 添加 Cookie 管理（匿名模式）
            .cookieJar(AnonymousCookieJar())

            // 3. 添加日志拦截器
            .addInterceptor(loggingInterceptor)

            // 4. 连接池配置
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))

            // 5. 重试机制
            .retryOnConnectionFailure(true)

            // 6. 超时配置
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)

            .build()
    }

    /**
     * 提供 API 专用的 Retrofit 实例（用于 api.2026copy.com）
     * 使用 LENIENT 模式解析 JSON，允许非标准格式
     */
    @Provides
    @Singleton
    @ApiRetrofit
    fun provideApiRetrofit(okHttpClient: OkHttpClient): Retrofit {
        val gson = GsonBuilder()
            .setStrictness(Strictness.LENIENT)
            .create()
        
        return Retrofit.Builder()
            .baseUrl(API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    /**
     * 提供 MangaApiService（使用 API 域名）
     */
    @Provides
    @Singleton
    fun provideMangaApiService(@ApiRetrofit retrofit: Retrofit): MangaApiService {
        return retrofit.create(MangaApiService::class.java)
    }

    /**
     * 提供 SystemApiService（使用 API 域名）
     */
    @Provides
    @Singleton
    fun provideSystemApiService(@ApiRetrofit retrofit: Retrofit): SystemApiService {
        return retrofit.create(SystemApiService::class.java)
    }
}
