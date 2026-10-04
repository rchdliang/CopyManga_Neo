package com.rchdliang.copymanga_neo.data.remote

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.data.local.LocalNetworkLogStore
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import javax.inject.Inject

private data class LogCapture(val generation: Long, val id: String)

/** 最外层记录被风控闸门、认证或连接失败中止的请求。 */
class LocalNetworkCallInterceptor @Inject constructor(private val store: LocalNetworkLogStore) :
    Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val generation = store.beginCapture() ?: return chain.proceed(chain.request())
        val capture = LogCapture(generation, UUID.randomUUID().toString())
        val request = chain.request().newBuilder().tag(LogCapture::class.java, capture).build()
        try {
            return chain.proceed(request)
        } catch (e: Exception) {
            val entry = NetworkLogRedaction.entry("call_failure", capture.id)
            entry.addProperty("url", NetworkLogRedaction.url(request.url))
            entry.addProperty("method", request.method)
            // 异常消息可能含完整 URL 或凭据，仅保存异常类型。
            entry.addProperty("exception", e.javaClass.simpleName)
            store.append(generation, entry)
            throw e
        }
    }
}

/** 网络层位于 Cookie、UA 等自动头之后，保留每次实际网络交换的原始响应。 */
class LocalNetworkLogInterceptor @Inject constructor(private val store: LocalNetworkLogStore) :
    Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val capture = request.tag(LogCapture::class.java) ?: return chain.proceed(request)
        val exchange = UUID.randomUUID().toString()
        val start = NetworkLogRedaction.entry("request", capture.id)
        start.addProperty("exchange_id", exchange)
        start.addProperty("url", NetworkLogRedaction.url(request.url))
        start.addProperty("method", request.method)
        start.add("headers", NetworkLogRedaction.headers(request.headers))
        start.addProperty("body_bytes", runCatching { request.body?.contentLength() }.getOrNull())
        (request.body as? FormBody)?.let { form ->
            val fields = JsonArray()
            for (index in 0 until form.size) fields.add(JsonObject().apply {
                addProperty("name", form.name(index))
                addProperty(
                    "value",
                    if (NetworkLogRedaction.sensitive(form.name(index))) "[REDACTED]" else form.value(
                        index
                    )
                )
            })
            start.add("form", fields)
        }
        if (request.body != null && request.body !is FormBody) start.addProperty(
            "body_note",
            "非表单请求体未记录"
        )
        store.append(capture.generation, start)
        val started = System.nanoTime()
        val response = chain.proceed(request)
        val result = NetworkLogRedaction.entry("response", capture.id)
        result.addProperty("exchange_id", exchange)
        result.addProperty("status", response.code)
        result.addProperty("protocol", response.protocol.toString())
        result.addProperty("elapsed_ms", (System.nanoTime() - started) / 1_000_000)
        result.add("headers", NetworkLogRedaction.headers(response.headers))
        result.addProperty("body_bytes", response.body.contentLength())
        try {
            val type = response.body.contentType()
            val text =
                type?.type == "text" || type?.subtype?.let { "json" in it || "xml" in it || "javascript" in it } == true
            if (request.url.encodedPath in setOf("/api/v3/login", "/api/v3/register") ||
                request.url.encodedPath.startsWith("/api/v3/member/info")
            ) {
                result.addProperty("body_note", "登录和个人资料响应正文已隐藏")
            } else if (text) {
                val limit = 2 * 1024 * 1024
                val encoding = response.header("Content-Encoding").orEmpty()
                val raw = response.peekBody(limit.toLong() + 1).bytes()
                val bytes = when {
                    encoding.equals(
                        "gzip",
                        true
                    ) -> GZIPInputStream(raw.inputStream()).use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (output.size() <= limit) {
                            val count =
                                input.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }

                    encoding.isBlank() || encoding.equals("identity", true) -> raw
                    else -> null
                }
                if (bytes == null) result.addProperty("body_note", "不支持的内容编码：$encoding")
                else {
                    val truncated = bytes.size > limit
                    result.addProperty("body_truncated", truncated)
                    result.addProperty("body_representation", "解压后的文本，未解密业务密文")
                    val body = String(
                        bytes,
                        0,
                        minOf(bytes.size, limit),
                        type.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                    )
                    if (type.subtype.contains("json") || body.trimStart()
                            .startsWith("{")
                    ) {
                        if (truncated) result.addProperty(
                            "body_note",
                            "JSON 超过记录上限，未保存不完整正文"
                        )
                        else try {
                            result.add(
                                "body",
                                NetworkLogRedaction.json(JsonParser.parseString(body))
                            )
                        } catch (_: Exception) {
                            result.addProperty("body_note", "JSON 格式异常，保留脱敏后的文本")
                            result.addProperty("body_text", NetworkLogRedaction.text(body))
                        }
                    } else result.addProperty("body", NetworkLogRedaction.text(body))
                }
            } else result.addProperty("body_note", "二进制响应仅记录元数据")
        } catch (e: Exception) {
            result.addProperty("body_capture_error", e.javaClass.simpleName)
        }
        store.append(capture.generation, result)
        return response
    }
}

/** 保留字段名和非凭据信息；响应 results 中的目录密文保持原样。 */
internal object NetworkLogRedaction {
    private val secrets = setOf(
        "authorization",
        "proxyauthorization",
        "password",
        "passwd",
        "pwd",
        "token",
        "accesstoken",
        "refreshtoken",
        "cookie",
        "setcookie",
        "salt",
        "tapstring",
        "xauthsignature",
        "signature",
        "apikey",
        "clientsecret",
        "device",
        "deviceinfo",
        "androidid",
        "pseudoid",
        "umstring"
    )
    private val textFields = Regex("\"([^\"]+)\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\"|[^,}\\s]+)")
    fun text(value: String): String = textFields.replace(value) { match ->
        if (sensitive(match.groupValues[1])) "\"${match.groupValues[1]}\":\"[REDACTED]\"" else match.value
    }

    fun sensitive(name: String): Boolean {
        val normalized = name.lowercase().filter { it.isLetterOrDigit() }
        return normalized in secrets || normalized.endsWith("token") || normalized.endsWith("password")
    }

    fun entry(event: String, id: String) = JsonObject().apply {
        addProperty("timestamp_ms", System.currentTimeMillis())
        addProperty("event", event)
        addProperty("call_id", id)
    }

    fun url(url: HttpUrl): String = url.newBuilder().apply {
        for (name in url.queryParameterNames) if (sensitive(name)) setQueryParameter(
            name,
            "[REDACTED]"
        )
        username("").password("").fragment(null)
    }.build().toString()

    fun headers(headers: Headers) = JsonArray().apply {
        for ((name, value) in headers) add(JsonObject().apply {
            addProperty("name", name)
            addProperty(
                "value", when {
                    name.equals("Cookie", true) -> value.split(';').joinToString("; ") {
                        val key = it.substringBefore('=').trim()
                        "$key=${if (key == "webp") it.substringAfter('=') else "[REDACTED]"}"
                    }

                    sensitive(name) -> "[REDACTED]"
                    name.equals("Location", true) || name.equals(
                        "Referer",
                        true
                    ) -> runCatching { url(value.toHttpUrl()) }.getOrDefault("[URL OMITTED]")

                    else -> value
                }
            )
        })
    }

    fun json(value: JsonElement): JsonElement = when {
        value.isJsonObject -> JsonObject().apply {
            for ((key, child) in value.asJsonObject.entrySet()) {
                if (sensitive(key)) addProperty(key, "[REDACTED]") else add(key, json(child))
            }
        }

        value.isJsonArray -> JsonArray().apply { value.asJsonArray.forEach { add(json(it)) } }
        else -> value
    }
}
