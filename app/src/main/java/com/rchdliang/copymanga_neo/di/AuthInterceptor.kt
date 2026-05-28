package com.rchdliang.copymanga_neo.di

import com.rchdliang.copymanga_neo.data.local.AuthDataStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 认证拦截器
 * 1. 已登录状态下自动为请求添加 Authorization: Token xxx header
 *    若请求携带 X-No-Auth 标记头，则跳过追加。
 * 2. 响应返回 401 时自动清除本地登录状态和 Cookie
 */
class AuthInterceptor(
    private val authDataStore: AuthDataStore,
    private val cookieJar: PersistentCookieJar
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = authDataStore.getTokenSync()
        val originalRequest = chain.request()

        // 如果请求携带 X-No-Auth 标记，跳过 Authorization 追加，
        // 并从最终发出的请求中移除该标记头。
        val skipAuth = originalRequest.header("X-No-Auth") != null

        val request = when {
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

        val response = chain.proceed(request)

        // 如果收到 401 且本地记录为已登录，说明 Token 已失效
        if (response.code == 401 && token != null) {
            android.util.Log.w("AuthInterceptor", "Received 401, clearing auth and cookies")
            runBlocking {
                authDataStore.clearAuth()
            }
            cookieJar.clearAll()
        }

        return response
    }
}