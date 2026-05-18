package com.rchdliang.copymanga_neo.di

import com.rchdliang.copymanga_neo.data.local.AuthDataStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 认证拦截器
 * 1. 已登录状态下自动为请求添加 authorization: Token xxx header
 * 2. 响应返回 401 时自动清除本地登录状态和 Cookie
 */
class AuthInterceptor(
    private val authDataStore: AuthDataStore,
    private val cookieJar: PersistentCookieJar
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = authDataStore.getTokenSync()
        val originalRequest = chain.request()

        val request = if (token != null) {
            originalRequest.newBuilder()
                .header("authorization", "Token $token")
                .build()
        } else {
            originalRequest
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
