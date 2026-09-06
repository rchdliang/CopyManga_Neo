package com.rchdliang.copymanga_neo.di

import android.util.Log
import com.rchdliang.copymanga_neo.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * 请求日志拦截器
 * 每请求一行摘要：方法 + URL → 请求头关键字段 → 响应状态码 + 耗时
 *
 * 输出示例：
 *   → GET https://api.copy4000.com/api/v3/h5/homeIndex?_t=1716890000000
 *     Headers: [Authorization: Token xxx, version: 2026.08.24, region: 1, platform: 1, Accept: application/json]
 *   ← 200 (234ms)
 */
class RequestLoggingInterceptor : Interceptor {

    companion object {
        private const val TAG = "OkHttp"
    }

    /**
     * 请求头中值得记录的关键业务字段。
     * 只展示这些字段的值，避免日志被 Cookie / UA 等长字符串淹没。
     */
    private val interestingHeaders = setOf(
        "authorization", "version", "platform", "region", "webp",
        "accept", "content-type", "x-requested-with", "referer", "origin",
        "sec-fetch-site", "sec-fetch-mode", "sec-fetch-dest"
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // --- 请求日志 ---
        val method = request.method
        val url = request.url.toString()

        if (BuildConfig.DEBUG) {
            val headerParts = mutableListOf<String>()
            request.headers.forEach { (name, value) ->
                if (name.lowercase() in interestingHeaders) {
                    // 对 Authorization 做脱敏：不显示 token 值
                    val displayValue =
                        if (name.equals("Authorization", ignoreCase = true)) {
                            "Token ***"
                        } else {
                            value
                        }
                    headerParts.add("$name: $displayValue")
                }
            }

            val headersSummary = if (headerParts.isNotEmpty()) {
                "\n  Headers: [${headerParts.joinToString(", ")}]"
            } else {
                ""
            }

            Log.d(TAG, "→ $method $url$headersSummary")
        }

        // --- 执行请求 ---
        val startNs = System.nanoTime()
        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: IOException) {
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
            Log.w(TAG, "✕ $method $url  FAIL (${elapsedMs}ms) — ${e.message}")
            throw e
        }

        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000

        // --- 响应日志 ---
        if (BuildConfig.DEBUG) {
            val code = response.code
            val message = response.message
            val status = if (response.isSuccessful) "✓" else "⚠"
            Log.d(TAG, "$status $method $url  ← $code $message (${elapsedMs}ms)")
        }

        return response
    }
}
