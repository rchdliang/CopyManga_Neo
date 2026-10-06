package com.rchdliang.copymanga_neo.di

import com.rchdliang.copymanga_neo.data.local.AuthDataStore
import com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore
import com.rchdliang.copymanga_neo.data.remote.NetworkRequestContext
import com.rchdliang.copymanga_neo.data.remote.isCopyApi
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * 认证拦截器
 * 1. 已登录状态下自动为请求添加 Authorization: Token xxx header
 *    若请求携带 X-No-Auth 标记头，则跳过追加。
 * 2. 响应返回 401 时自动清除本地登录状态和 Cookie
 */
class AuthInterceptor(
    private val authDataStore: AuthDataStore,
    private val networkSettings: NetworkSettingsStore? = null
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val context = originalRequest.tag(NetworkRequestContext::class.java)
        val mode = context?.settings?.requestMode ?: authDataStore.getRequestModeSync()
        val generation = authDataStore.getAuthGeneration()
        val token = authDataStore.getTokenForModeSync(mode)

        // 如果请求携带 X-No-Auth 标记，跳过 Authorization 追加，
        // 并从最终发出的请求中移除该标记头。
        val skipAuth = originalRequest.header("X-No-Auth") != null

        val request = when {
            !originalRequest.isCopyApi() -> originalRequest.newBuilder()
                .removeHeader("Authorization").removeHeader("X-No-Auth").build()
            token != null && !skipAuth -> {
                originalRequest.newBuilder()
                    .header("Authorization", "Token $token")
                    .build()
            }

            skipAuth -> {
                originalRequest.newBuilder()
                    .removeHeader("X-No-Auth")
                    .build()
            }

            else -> {
                originalRequest
            }
        }

        // Web 会员接口与阅读进度上报需要登录；无凭据时直接停止，不发送匿名请求。
        val path = request.url.encodedPath
        if (originalRequest.isCopyApi() && context?.settings?.requestMode == RequestMode.WEB &&
            (path.startsWith("/api/v3/member/") || path.contains("/chapter2/")) &&
            request.header("Authorization").isNullOrBlank()
        ) {
            throw IOException("请先登录")
        }

        val response = chain.proceed(request)

        // 如果收到 401 且本地记录为已登录，说明 Token 已失效
        if (response.code == 401 && token != null && !skipAuth && originalRequest.isCopyApi()) {
            val clear = {
                if (authDataStore.clearAuthSyncIfTokenMatches(token, mode, generation)) {
                    android.util.Log.w("AuthInterceptor", "Received 401, clearing auth and cookies")
                }
            }
            if (networkSettings == null) clear()
            else networkSettings.ifCurrent(originalRequest.tag(NetworkRequestContext::class.java)?.generation, clear)
        }

        return response
    }
}
