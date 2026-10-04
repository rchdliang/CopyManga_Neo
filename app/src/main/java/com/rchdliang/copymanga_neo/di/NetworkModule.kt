package com.rchdliang.copymanga_neo.di

import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.rchdliang.copymanga_neo.data.local.AuthDataStore
import com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore
import com.rchdliang.copymanga_neo.data.remote.CopyMangaNetworkConfig
import com.rchdliang.copymanga_neo.data.remote.NetworkBoundaryInterceptor
import com.rchdliang.copymanga_neo.data.remote.NetworkGenerationInterceptor
import com.rchdliang.copymanga_neo.data.remote.NetworkRoutingInterceptor
import com.rchdliang.copymanga_neo.data.remote.SettingsProxySelector
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

    /** 发表评论不能因连接重试或重定向而重复发送；其余网络策略与主客户端一致。 */
    @Provides
    @Singleton
    fun provideCommentApiService(@ApiRetrofit retrofit: Retrofit, client: OkHttpClient):
            com.rchdliang.copymanga_neo.data.remote.api.CommentApiService = retrofit.newBuilder()
        .client(
            client.newBuilder().retryOnConnectionFailure(false).followRedirects(false)
                .followSslRedirects(false)
                .addNetworkInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    // OkHttp 仍可能按 503 + Retry-After: 0 重放请求；发表操作明确禁止这种重试。
                    if (chain.request().method == "POST" && response.code == 503) {
                        response.newBuilder().removeHeader("Retry-After").build()
                    } else response
                }.build()
        )
        .build().create(com.rchdliang.copymanga_neo.data.remote.api.CommentApiService::class.java)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        cookieJar: PersistentCookieJar,
        riskControlInterceptor: com.rchdliang.copymanga_neo.data.remote.RiskControlInterceptor,
        networkSettings: NetworkSettingsStore,
        routing: NetworkRoutingInterceptor,
        generation: NetworkGenerationInterceptor,
        boundary: NetworkBoundaryInterceptor,
        officialApp: com.rchdliang.copymanga_neo.data.remote.OfficialAppInterceptor,
        localCalls: com.rchdliang.copymanga_neo.data.remote.LocalNetworkCallInterceptor,
        localExchanges: com.rchdliang.copymanga_neo.data.remote.LocalNetworkLogInterceptor
    ): OkHttpClient {

        return OkHttpClient.Builder()
            .addInterceptor(localCalls)
            .proxySelector(SettingsProxySelector({ networkSettings.settings.value }))
            .addInterceptor(routing)
            .addInterceptor(riskControlInterceptor)
            // 1. 添加请求头拦截器（模拟真实浏览器）
            .addInterceptor(HeadersInterceptor())
            .addInterceptor(officialApp)

            // 2. 添加认证拦截器（已登录时自动附加 Token header）
            .addInterceptor(authInterceptor)
            .addInterceptor(generation)
            .addNetworkInterceptor(boundary)
            .addNetworkInterceptor(localExchanges)

            // 3. 仅 WWW 请求使用持久化 Cookie
            .cookieJar(WwwCookieJar(cookieJar, networkSettings))

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

            .build().also { client ->
                networkSettings.onChange {
                    client.dispatcher.cancelAll()
                    client.connectionPool.evictAll()
                }
            }
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        authDataStore: AuthDataStore,
        cookieJar: PersistentCookieJar,
        networkSettings: NetworkSettingsStore
    ): AuthInterceptor {
        return AuthInterceptor(authDataStore, cookieJar, networkSettings)
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

    /** 禁止重放注册，防止超时后自动创建重复账号。 */
    @Provides
    @Singleton
    fun provideRegistrationApiService(@ApiRetrofit retrofit: Retrofit, client: OkHttpClient):
            com.rchdliang.copymanga_neo.data.remote.api.RegistrationApiService =
        retrofit.newBuilder().client(
            client.newBuilder().retryOnConnectionFailure(false)
                .followRedirects(false).followSslRedirects(false)
                .addNetworkInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    if (response.code == 503) response.newBuilder().removeHeader("Retry-After")
                        .build()
                    else response
                }.build()
        ).build()
            .create(com.rchdliang.copymanga_neo.data.remote.api.RegistrationApiService::class.java)

    /** 会员写入禁止连接重试、503 重放及重定向。 */
    @Provides
    @Singleton
    fun provideMemberWriteApiService(@ApiRetrofit retrofit: Retrofit, client: OkHttpClient):
            com.rchdliang.copymanga_neo.data.remote.api.MemberWriteApiService =
        retrofit.newBuilder().client(
            client.newBuilder().retryOnConnectionFailure(false)
                .followRedirects(false).followSslRedirects(false)
                .addNetworkInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    if (response.code == 503) response.newBuilder().removeHeader("Retry-After")
                        .build()
                    else response
                }.build()
        ).build()
            .create(com.rchdliang.copymanga_neo.data.remote.api.MemberWriteApiService::class.java)

    /** 下载授权会扣额度，连接失败、503 和重定向均不自动重放。 */
    @Provides
    @Singleton
    fun provideOfficialDownloadApiService(@ApiRetrofit retrofit: Retrofit, client: OkHttpClient):
            com.rchdliang.copymanga_neo.data.remote.api.OfficialDownloadApiService =
        retrofit.newBuilder().client(
            client.newBuilder().retryOnConnectionFailure(false)
                .followRedirects(false).followSslRedirects(false)
                .addNetworkInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    if (response.code == 503) response.newBuilder().removeHeader("Retry-After")
                        .build()
                    else response
                }.build()
        ).build()
            .create(com.rchdliang.copymanga_neo.data.remote.api.OfficialDownloadApiService::class.java)

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
