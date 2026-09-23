package com.rchdliang.copymanga_neo.di

import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.rchdliang.copymanga_neo.data.local.AuthDataStore
import com.rchdliang.copymanga_neo.data.remote.CopyMangaNetworkConfig
import com.rchdliang.copymanga_neo.data.remote.api.MangaApiService
import com.rchdliang.copymanga_neo.data.remote.api.SystemApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
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
    private const val TIMEOUT_SECONDS = 10L

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        cookieJar: PersistentCookieJar,
        riskControlInterceptor: com.rchdliang.copymanga_neo.data.remote.RiskControlInterceptor
    ): OkHttpClient {

        return OkHttpClient.Builder()
            .addInterceptor(riskControlInterceptor)
            // 1. 添加请求头拦截器（模拟真实浏览器）
            .addInterceptor(HeadersInterceptor())

            // 2. 添加认证拦截器（已登录时自动附加 Token header）
            .addInterceptor(authInterceptor)

            // 3. 添加 Cookie 管理（持久化存储）
            .cookieJar(cookieJar)

            // 4. 添加请求日志拦截器（记录 URL / 请求头 / 状态码 / 耗时）
            .addInterceptor(RequestLoggingInterceptor())

            // 5. 连接池配置
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))

            // 6. 重试机制
            .retryOnConnectionFailure(true)

            // 7. 超时配置
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)

            .build()
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        authDataStore: AuthDataStore,
        cookieJar: PersistentCookieJar
    ): AuthInterceptor {
        return AuthInterceptor(authDataStore, cookieJar)
    }

    /**
     * 提供 API 专用的 Retrofit 实例（用于 api.copy4000.com）
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
            .baseUrl(CopyMangaNetworkConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    /**
     * 提供 MangaApiService（使用 API 埃名）
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

    /**
     * 提供 WWW 专用的 Retrofit 实例（用于 www.copy3000.com）
     */
    @Provides
    @Singleton
    @WwwRetrofit
    fun provideWwwRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(CopyMangaNetworkConfig.WWW_BASE_URL)
            .client(okHttpClient)
            .build()
    }

    /**
     * 提供 MangaApiService（使用 WWW 域名，用于章节 HTML 等 www 域请求）
     */
    @Provides
    @Singleton
    @WwwRetrofit
    fun provideMangaWwwApiService(@WwwRetrofit retrofit: Retrofit): MangaApiService {
        return retrofit.create(MangaApiService::class.java)
    }
}
