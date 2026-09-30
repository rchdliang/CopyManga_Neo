package com.rchdliang.copymanga_neo.data.remote

import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.BuildConfig
import com.rchdliang.copymanga_neo.data.local.OfficialAppIdentity
import com.rchdliang.copymanga_neo.domain.model.OfficialAppSettings
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

/** 只处理 App 模式 API 的请求字段。 */
class OfficialAppInterceptor @Inject constructor(private val identity: OfficialAppIdentity) :
    Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.header("X-Copy-Comment") != null) {
            return chain.proceed(original.newBuilder().removeHeader("X-Copy-Comment").build())
        }
        val settings = original.tag(NetworkRequestContext::class.java)?.settings
        if (!original.isCopyApi() || settings?.requestMode != RequestMode.OFFICIAL_APP) {
            return chain.proceed(original)
        }
        val response = chain.proceed(prepareRequest(original, settings.officialApp))
        // 实测存在 code=200/results=null；明确作为空响应失败，不宣称已确定风控原因。
        if (response.isSuccessful) {
            val json = try {
                JsonParser.parseString(response.peekBody(64 * 1024).string()).asJsonObject
            } catch (_: Exception) {
                null
            }
            if (json?.get("code")?.asInt == 200 && json.get("results")?.isJsonNull == true) {
                response.close()
                throw IOException("App 接口返回空结果，请检查登录会话和设备配置；未自动回退网页路径")
            }
        }
        return response
    }

    /** 正式请求与本机诊断共用；此方法仅构造请求，不打开网络连接。 */
    fun prepareRequest(
        original: Request,
        profile: OfficialAppSettings,
        timestampSeconds: Long = System.currentTimeMillis() / 1000
    ): Request {
        val app = identity.resolve(profile)
        val tapString = BuildConfig.OFFICIAL_APP_TAP_STRING.takeIf { it.isNotEmpty() }
            ?: throw IOException("当前安装包缺少 App 模式所需配置")
        val timestamp = timestampSeconds.toString()
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(tapString.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        }
        val signature = mac.doFinal(timestamp.toByteArray(Charsets.US_ASCII))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val url = original.url.newBuilder().removeAllQueryParameters("_t")
            .removeAllQueryParameters("_update").setQueryParameter("platform", "3")
        val path = original.url.encodedPath
        if (path == "/api/v3/h5/homeIndex") url.encodedPath("/api/v3/h5/homeIndex2")
        // HAR 中详情、目录和章节内容使用这些参数；状态 query 接口没有这两个参数。
        if (Regex("/api/v3/comic2/[^/]+$").matches(path) ||
            path.contains("/chapter2/") || path.endsWith("/chapters")
        ) {
            url.setQueryParameter("in_mainland", "false").setQueryParameter("request_id", "")
        }
        val builder = original.newBuilder().url(url.build())
        listOf(
            "Origin",
            "X-Requested-With",
            "Sec-Fetch-Site",
            "Sec-Fetch-Mode",
            "Sec-Fetch-Dest",
            "dnts"
        )
            .forEach(builder::removeHeader)
        mapOf(
            "User-Agent" to "COPY/${app.version}",
            "source" to "copyApp",
            "version" to app.version,
            "versioncode" to app.versionCode,
            "platform" to "3",
            "region" to "1",
            "webp" to "1",
            "Accept" to "application/json",
            "Accept-Language" to "",
            "Content-Type" to "application/x-www-form-urlencoded",
            "referer" to "com.copymanga.app-${app.version}",
            "device" to app.device,
            "deviceinfo" to app.deviceInfo,
            "pseudoid" to app.pseudoId,
            "umstring" to app.umString,
            "dt" to LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")),
            "X-auth-timestamp" to timestamp,
            "X-auth-signature" to signature
        ).forEach { (key, value) ->
            builder.header(key, value)
        }
        // gzip 交给 OkHttp 透明协商和解压，避免手工设置后收到未解压响应。
        builder.removeHeader("Accept-Encoding")
        if (original.header("Authorization") == null) builder.header("Authorization", "Token")
        val login = path == "/api/v3/login"
        if (!login && original.header("Authorization") == null) builder.removeHeader("X-No-Auth")
        (original.body as? FormBody)?.let { form ->
            val fields = linkedMapOf<String, String>()
            for (i in 0 until form.size) {
                if (form.name(i) != "_update") fields[form.name(i)] = form.value(i)
            }
            if (login) {
                fields.putAll(
                    mapOf(
                        "platform" to "3",
                        "authorization" to "Token ",
                        "version" to app.version,
                        "source" to "copyApp",
                        "region" to "1",
                        "webp" to "1",
                        "userAgent" to "COPY/${app.version}",
                        "isGoogle" to "false",
                        "pseudoID" to app.pseudoId,
                        "dt" to identity.loginDate,
                        "tapString" to tapString,
                        "umString" to app.umString,
                        "buildTime" to app.buildTime,
                        "versionCode" to app.versionCode,
                        "androidId" to app.device,
                        "referer" to "com.copymanga.app-${app.version}",
                        "deviceInfo" to app.deviceInfo
                    )
                )
                builder.header("Authorization", "Token").header("X-No-Auth", "1")
            }
            builder.method(original.method, FormBody.Builder().apply {
                fields.forEach { (key, value) -> add(key, value) }
            }.build())
        }
        return builder.build()
    }

}
