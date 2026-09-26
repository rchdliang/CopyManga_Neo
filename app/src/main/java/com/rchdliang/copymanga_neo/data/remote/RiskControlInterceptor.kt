package com.rchdliang.copymanga_neo.data.remote

import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.domain.repository.RiskControlException
import com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/** 在 DTO 反序列化之前识别 HTTP 210 和 HTTP 200 + JSON code=210。 */
class RiskControlInterceptor @Inject constructor(
    private val gate: RiskControlGate,
    private val networkSettings: NetworkSettingsStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!chain.request().isCopyApi() && !chain.request().isCopyWww()) {
            return chain.proceed(chain.request())
        }
        val call = chain.call()
        gate.register(call)
        try {
            val response = chain.proceed(chain.request())
            val reason = try {
                val body = response.peekBody(64 * 1024).string().trimStart()
                val json =
                    if (body.startsWith("{")) JsonParser.parseString(body).asJsonObject else null
                if (response.code == 210 || json?.get("code")?.asInt == 210) {
                    json?.get("message")?.takeUnless { it.isJsonNull }?.asString
                        ?.takeIf { it.isNotBlank() } ?: "官方暂时限制了当前网络的访问"
                } else null
            } catch (_: Exception) {
                if (response.code == 210) "官方暂时限制了当前网络的访问" else null
            }
            if (reason != null) {
                networkSettings.ifCurrent(chain.request().tag(NetworkRequestContext::class.java)?.generation) {
                    gate.block(reason, call)
                }
                response.close()
                throw RiskControlException(reason)
            }
            return response
        } finally {
            gate.unregister(call)
        }
    }
}
