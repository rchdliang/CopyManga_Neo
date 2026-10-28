package com.rchdliang.copymanga_neo.data.remote

import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.BuildConfig
import com.rchdliang.copymanga_neo.data.local.OfficialAppIdentity
import com.rchdliang.copymanga_neo.domain.model.OfficialAppSettings
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

/** 只处理 App 模式 API 的请求字段。 */
class OfficialAppInterceptor internal constructor(
    private val resolveIdentity: (OfficialAppSettings) -> OfficialAppSettings,
    private val sessionDate: () -> String,
    private val signingKey: () -> String
) : Interceptor {
    @Inject
    constructor(identity: OfficialAppIdentity) : this(
        identity::resolve,
        { identity.loginDate },
        { BuildConfig.OFFICIAL_APP_TAP_STRING }
    )

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
        // 写入由仓库按业务 code 检查；读取接口仍需完整数据。
        val emptyWriteResult = permitsEmptyResult(original.method, original.url.encodedPath)
        if (response.isSuccessful && !emptyWriteResult) {
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
        val app = resolveIdentity(profile)
        val tapString = signingKey().takeIf { it.isNotEmpty() }
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
        if (path == "/api/v3/search/comic" && original.url.queryParameter("q_type") == null) {
            url.setQueryParameter("q_type", "")
        }
        // HAR 中详情、目录和章节内容使用这些参数；状态 query 接口没有这两个参数。
        if (Regex("/api/v3/comic2/[^/]+$").matches(path) ||
            path.contains("/chapter2/") || path.endsWith("/chapters") ||
            Regex("/api/v3/book/[^/]+(?:/volume/[^/]+)?$").matches(path)
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
            "region" to "0",
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
        // multipart 的 boundary 必须由请求体生成，不能使用 App 普通表单的 Content-Type。
        if (original.body is okhttp3.MultipartBody) builder.removeHeader("Content-Type")
        if (original.header("Authorization") == null) builder.header("Authorization", "Token")
        val login = path == "/api/v3/login"
        if (!login && original.header("Authorization") == null) builder.removeHeader("X-No-Auth")
        (original.body as? FormBody)?.let { form ->
            // 重复 ids 必须保留；Map 会丢失批量删除中的其他条目。
            val fields = mutableListOf<Pair<String, String>>()
            for (i in 0 until form.size) {
                if (form.name(i) != "_update") fields += form.name(i) to form.value(i)
            }
            val register = path == "/api/v3/register"
            if (login || register || requiresMemberForm(original.method, path)) {
                val common = mapOf(
                    "platform" to "3",
                    "authorization" to if (login || register) "Token " else
                        (original.header("Authorization")
                            ?: throw IOException("请先登录后操作")),
                    "version" to app.version,
                    "source" to "copyApp",
                    "region" to "0",
                    "webp" to "1",
                    "userAgent" to "COPY/${app.version}",
                    "isGoogle" to "false",
                    "pseudoID" to app.pseudoId,
                    "dt" to sessionDate(),
                    "tapString" to tapString,
                    "umString" to app.umString,
                    "buildTime" to app.buildTime,
                    "versionCode" to app.versionCode,
                    "androidId" to app.device,
                    "referer" to "com.copymanga.app-${app.version}",
                    "deviceInfo" to app.deviceInfo
                )
                fields.removeAll { it.first in common }
                fields.addAll(common.toList())
                if (login || register) builder.header("Authorization", "Token")
                    .header("X-No-Auth", "1")
            }
            builder.method(original.method, memberFormBody(original.method, path, fields))
        }
        return builder.build()
    }

    /** 方法和路径同时匹配，避免把读取或尚未验证的接口当作成功空结果。 */
    private fun permitsEmptyResult(method: String, path: String): Boolean = when (method) {
        "POST" -> path in setOf(
            "/api/v3/logout",
            "/api/v3/member/collect/comic",
            "/api/v3/member/collect/book",
            "/api/v3/member/comment",
            "/api/v3/member/bookcomment",
            "/api/v3/member/roast",
            "/api/v3/member/comment/report",
            "/api/v3/member/bookcomment/report",
            "/api/v3/member/roast/report",
            "/api/v3/member/update/info"
        )

        "DELETE" -> path in setOf("/api/v3/member/browse/comics", "/api/v3/member/comment", "/api/v3/member/roast")
        else -> false
    }

    private fun requiresMemberForm(method: String, path: String): Boolean =
        (method == "POST" && path in setOf(
            "/api/v3/member/collect/comic",
            "/api/v3/member/collect/book",
            "/api/v3/member/comment",
            "/api/v3/member/bookcomment",
            "/api/v3/member/roast",
            "/api/v3/member/comment/report",
            "/api/v3/member/bookcomment/report",
            "/api/v3/member/roast/report",
            "/api/v3/member/update/info"
        )) || (method == "DELETE" && path in setOf(
            "/api/v3/member/collect/comics",
            "/api/v3/member/collect/books",
            "/api/v3/member/browse/comics",
            "/api/v3/member/comment",
            "/api/v3/member/roast"
        ))

    /** App 漫画与小说顶层评论使用裸 reply_id；其余字段仍由 FormBody 编码。 */
    private fun memberFormBody(
        method: String,
        path: String,
        fields: List<Pair<String, String>>
    ): RequestBody {
        val form = FormBody.Builder().apply {
            fields.forEach { (key, value) -> add(key, value) }
        }.build()
        if (method != "POST" || path !in setOf("/api/v3/member/bookcomment", "/api/v3/member/comment") ||
            fields.none { it.first == "reply_id" && it.second.isEmpty() }
        ) return form
        return (0 until form.size).joinToString("&") { index ->
            val name = form.encodedName(index)
            if (form.name(index) == "reply_id" && form.value(index).isEmpty()) name
            else "$name=${form.encodedValue(index)}"
        }.toByteArray(Charsets.UTF_8).toRequestBody(form.contentType())
    }
}
