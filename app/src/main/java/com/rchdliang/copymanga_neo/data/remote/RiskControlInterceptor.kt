package com.rchdliang.copymanga_neo.data.remote

import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore
import com.rchdliang.copymanga_neo.data.remote.util.ChapterCatalogDecryptor
import com.rchdliang.copymanga_neo.domain.repository.RiskControlException
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.inject.Inject

/** 识别 HTTP/JSON 210，以及 WWW 目录在 HTTP 成功时返回的全空风控内容。 */
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
            var response = chain.proceed(chain.request())
            val segments = chain.request().url.pathSegments
            val catalogPathWord = if (chain.request().isCopyWww() && segments.size == 3 &&
                segments[0] == "comicdetail" && segments[2] == "chapters"
            ) segments[1] else null
            val reason = try {
                // 目录密文不能截断。读完后还原响应体，保证仓库仍可正常解析。
                val body = if (catalogPathWord != null && response.isSuccessful) {
                    val original = response.body
                    val contentType = original.contentType()
                    val content = original.string()
                    response =
                        response.newBuilder().body(content.toResponseBody(contentType)).build()
                    content.trimStart()
                } else response.peekBody(64 * 1024).string().trimStart()
                val json =
                    if (body.startsWith("{")) JsonParser.parseString(body).asJsonObject else null
                if (response.code == 210 || json?.get("code")?.asInt == 210) {
                    json?.get("message")?.takeUnless { it.isJsonNull }?.asString
                        ?.takeIf { it.isNotBlank() } ?: "官方暂时限制了当前网络的访问"
                } else {
                    if (catalogPathWord != null && response.isSuccessful) {
                        val protocol = chain.request()
                            .tag(com.rchdliang.copymanga_neo.data.remote.util.WwwCatalogProtocol::class.java)
                            ?: error("目录请求缺少网页协议参数")
                        ChapterCatalogDecryptor.parseResponse(body, catalogPathWord, protocol.key)
                    }
                    null
                }
            } catch (e: RiskControlException) {
                e.reason
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
