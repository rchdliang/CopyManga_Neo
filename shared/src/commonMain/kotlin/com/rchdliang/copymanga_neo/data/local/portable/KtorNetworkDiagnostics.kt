package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.data.remote.portable.PortableRiskControlGate
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.RiskControlException
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import kotlin.time.TimeSource

class KtorNetworkDiagnostics(
    private val clientFactory: (NetworkSettings) -> HttpClient,
    private val risk: PortableRiskControlGate,
    private val hasTapString: () -> Boolean
) : NetworkDiagnostics {
    override suspend fun discovery(settings: NetworkSettings): String {
        val client = clientFactory(settings).config { followRedirects = false; expectSuccess = false }
        try {
            var last: Exception? = null
            for (host in listOf(settings.apiHost, settings.wwwHost).distinct()) {
                currentCoroutineContext().ensureActive()
                val job = currentCoroutineContext().job
                risk.register(job)
                try {
                    val (status, body) = client.prepareGet("https://$host/api/v3/system/network21") {
                        parameter("platform", 1)
                        header("version", settings.version)
                        header("platform", "1")
                        header("Origin", "https://${settings.wwwHost}")
                        header("User-Agent", com.rchdliang.copymanga_neo.data.remote.portable.CopyMangaNetworkConfig.WEB_API_USER_AGENT)
                    }.execute { response -> response.status.value to readBounded(response, 1024 * 1024) }
                    val code = runCatching { Json.parseToJsonElement(body).jsonObject["code"]?.jsonPrimitive?.intOrNull }.getOrNull()
                    if (status == 210 || code == 210) {
                        risk.block("网络配置接口触发访问限制，请稍后再试", job)
                        throw RiskControlException("网络配置接口触发访问限制，请稍后再试")
                    }
                    check(status in 200..299) { "网络配置请求失败：HTTP $status" }
                    return body
                } catch (cancel: CancellationException) { throw cancel }
                catch (limited: RiskControlException) { throw limited }
                catch (error: Exception) { last = error }
                finally { risk.unregister(job) }
            }
            throw last ?: IllegalStateException("没有取得线路配置")
        } finally { client.close() }
    }
    override suspend fun probe(settings: NetworkSettings): List<NetworkProbe> = supervisorScope {
        val client = clientFactory(settings).config { followRedirects = false; expectSuccess = false }
        val hosts = (if (settings.requestMode == RequestMode.OFFICIAL_APP) listOf(settings.officialApp.apiHost, settings.officialApp.loginHost)
            else listOf(settings.apiHost, settings.wwwHost, settings.loginHost) + settings.routes.flatten()).filter { it.isNotBlank() }.distinct().take(60)
        val semaphore = Semaphore(3)
        try { hosts.map { host -> async {
            semaphore.withPermit {
                val started = TimeSource.Monotonic.markNow()
                val job = currentCoroutineContext().job
                risk.register(job)
                try { withTimeout(10_000) { client.head("https://$host/") }; NetworkProbe(host, started.elapsedNow().inWholeMilliseconds) }
                catch (cancel: CancellationException) { if (cancel is TimeoutCancellationException) NetworkProbe(host, null, "连接超时") else throw cancel }
                catch (error: Exception) { NetworkProbe(host, null, "连接失败或超时") }
                finally { risk.unregister(job) }
            }
        } }.awaitAll() } finally { client.close() }
    }
    override fun officialReport(settings: NetworkSettings): String = buildString {
        appendLine("请求模式：${settings.requestMode}")
        appendLine("API：${settings.officialApp.apiHost}")
        appendLine("登录域名：${settings.officialApp.loginHost}")
        appendLine("协议版本：${settings.officialApp.version} (${settings.officialApp.versionCode})")
        appendLine("签名配置：${if (hasTapString()) "已配置" else "缺失 COPYMANGA_TAP_STRING"}")
        appendLine("代理模式：${settings.proxyMode}")
        appendLine("身份字段：${if (settings.officialApp.pseudoId.isNotBlank() && settings.officialApp.umString.isNotBlank()) "已生成" else "未完整生成"}")
        append("报告不包含令牌、密码、签名密钥或设备标识原文。")
    }
    private suspend fun readBounded(response: HttpResponse, limit: Int): String {
        val channel = response.bodyAsChannel()
        val chunks = mutableListOf<ByteArray>()
        var total = 0
        while (true) {
            val bytes = ByteArray(minOf(8192, limit - total + 1))
            val count = channel.readAvailable(bytes)
            if (count < 0) break
            if (count == 0) continue
            total += count
            if (total > limit) { channel.cancel(); error("网络配置响应过大") }
            chunks += bytes.copyOf(count)
        }
        val joined = ByteArray(total)
        var offset = 0
        for (chunk in chunks) { chunk.copyInto(joined, offset); offset += chunk.size }
        return joined.decodeToString()
    }
}
