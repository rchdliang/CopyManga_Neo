package com.rchdliang.copymanga_neo.data.remote

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.domain.model.NetworkSelection
import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import com.rchdliang.copymanga_neo.domain.model.OfficialAppSettings
import com.rchdliang.copymanga_neo.domain.model.ProxyMode
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetSocketAddress
import java.net.Proxy

/** 只接受 HTTPS 主机，禁止把路径、凭据或非标准端口混入业务地址。 */
internal object NetworkSettingsCodec {
    fun host(value: String): String {
        val input = value.trim()
        require(input.isNotBlank()) { "域名不能为空" }
        val url = (if ("://" in input) input else "https://$input").toHttpUrlOrNull()
            ?: throw IllegalArgumentException("域名格式无效")
        require(url.scheme == "https" && url.port == 443 && url.username.isEmpty() &&
            url.password.isEmpty() && url.encodedPath == "/" && url.query == null && url.fragment == null
        ) { "请填写 HTTPS 域名，不要包含路径、账号或端口" }
        return url.host
    }

    fun proxy(value: String): Proxy {
        val input = value.trim()
        val url = (if ("://" in input) input else "http://$input").toHttpUrlOrNull()
            ?: throw IllegalArgumentException("代理地址格式无效")
        require(url.scheme == "http" && url.username.isEmpty() && url.password.isEmpty() &&
            url.encodedPath == "/" && url.query == null && url.fragment == null
        ) { "代理需为 HTTP 地址，例如 127.0.0.1:7890" }
        return Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(url.host, url.port))
    }

    fun normalize(value: NetworkSettings): NetworkSettings {
        com.rchdliang.copymanga_neo.domain.model.ThemeLoadingPolicy.validate(value)
        val routes = value.routes.take(20).map { route -> route.take(30).map(::host).distinct() }
            .filter { it.isNotEmpty() }.distinct()
        val fixed = value.fixedHost.takeIf { it.isNotBlank() }?.let(::host).orEmpty()
        require(value.version.trim().matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "请求版本格式无效" }
        if (value.proxyMode == ProxyMode.MANUAL) proxy(value.proxyAddress)
        require(value.selection != NetworkSelection.ROUTE || value.routeIndex in routes.indices) { "请先获取并选择线路" }
        require(value.selection != NetworkSelection.FIXED || fixed in routes.flatten()) { "请选择已发现的节点" }
        val app = value.officialApp.migrateLegacyVersion()
        require(app.version.matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "App 请求版本格式无效" }
        require(
            app.versionCode.toIntOrNull()
                ?.let { it > 0 } == true) { "版本号须为大于 0 的整数" }
        listOf(app.pseudoId, app.umString, app.device, app.deviceInfo, app.buildTime).forEach {
            require(it.length <= 256 && it.all { c -> c.code in 32..126 }) { "此字段最多 256 个字符，仅支持英文字母、数字、空格和半角符号。" }
        }
        return value.copy(
            officialApp = app.copy(apiHost = host(app.apiHost), loginHost = host(app.loginHost)),
            apiHost = host(value.apiHost),
            wwwHost = host(value.wwwHost),
            shareHosts = value.shareHosts.take(20).map(::host).distinct(),
            loginHost = value.loginHost.takeIf { it.isNotBlank() }?.let(::host).orEmpty(),
            version = com.rchdliang.copymanga_neo.domain.model.NetworkDefaults.normalizeWebVersion(value.version),
            routes = routes, fixedHost = fixed,
            proxyAddress = value.proxyAddress.trim())
    }

    /** 固定持久化字段名，避免正式版混淆后无法恢复配置。 */
    fun encode(value: NetworkSettings): String = JsonObject().apply {
        addProperty("themeFetchConcurrency", value.themeFetchConcurrency)
        addProperty("themeFetchIntervalMillis", value.themeFetchIntervalMillis)
        addProperty("requestMode", value.requestMode.name)
        add("officialApp", JsonObject().apply {
            val a = value.officialApp
            addProperty("apiHost", a.apiHost)
            addProperty("loginHost", a.loginHost)
            addProperty("version", a.version)
            addProperty("versionCode", a.versionCode)
            addProperty("pseudoId", a.pseudoId)
            addProperty("umString", a.umString)
            addProperty("device", a.device)
            addProperty("deviceInfo", a.deviceInfo)
            addProperty("buildTime", a.buildTime)
        })
        addProperty("apiHost", value.apiHost)
        addProperty("wwwHost", value.wwwHost)
        add("shareHosts", JsonArray().apply { value.shareHosts.forEach { add(it) } })
        addProperty("loginHost", value.loginHost)
        addProperty("version", value.version)
        addProperty("selection", value.selection.name)
        addProperty("routeIndex", value.routeIndex)
        addProperty("fixedHost", value.fixedHost)
        addProperty("discoveredAt", value.discoveredAt)
        addProperty("autoDiscover", value.autoDiscover)
        addProperty("proxyMode", value.proxyMode.name)
        addProperty("proxyAddress", value.proxyAddress)
        add("routes", JsonArray().apply {
            value.routes.forEach { hosts -> add(JsonArray().apply { hosts.forEach { add(it) } }) }
        })
    }.toString()

    fun decode(raw: String?): NetworkSettings {
        if (raw.isNullOrBlank()) return NetworkSettings()
        return try {
            val json = JsonParser.parseString(raw).asJsonObject
            val defaults = NetworkSettings()
            fun str(key: String, fallback: String) = json.get(key)?.takeUnless { it.isJsonNull }?.asString ?: fallback
            val app = json.getAsJsonObject("officialApp")
            val appDefaults = OfficialAppSettings()
            fun appStr(key: String, fallback: String = "") =
                app?.get(key)?.takeUnless { it.isJsonNull }?.asString ?: fallback
            normalize(NetworkSettings(
                themeFetchConcurrency = json.get("themeFetchConcurrency")?.asInt ?: defaults.themeFetchConcurrency,
                themeFetchIntervalMillis = json.get("themeFetchIntervalMillis")?.asInt ?: defaults.themeFetchIntervalMillis,
                requestMode = RequestMode.valueOf(str("requestMode", defaults.requestMode.name)),
                officialApp = OfficialAppSettings(
                    apiHost = appStr("apiHost", appDefaults.apiHost),
                    loginHost = appStr("loginHost", appDefaults.loginHost),
                    version = appStr("version", appDefaults.version),
                    versionCode = appStr("versionCode", appDefaults.versionCode),
                    pseudoId = appStr("pseudoId"),
                    umString = appStr("umString"),
                    device = appStr("device"),
                    deviceInfo = appStr("deviceInfo"),
                    buildTime = appStr("buildTime")
                ),
                apiHost = str("apiHost", defaults.apiHost), wwwHost = str("wwwHost", defaults.wwwHost),
                shareHosts = json.getAsJsonArray("shareHosts")?.map { it.asString } ?: emptyList(),
                loginHost = str("loginHost", ""), version = str("version", defaults.version),
                selection = NetworkSelection.valueOf(str("selection", "DEFAULT")),
                routeIndex = json.get("routeIndex")?.asInt ?: 0, fixedHost = str("fixedHost", ""),
                routes = json.getAsJsonArray("routes")?.map { row -> row.asJsonArray.map { it.asString } } ?: emptyList(),
                discoveredAt = json.get("discoveredAt")?.asLong ?: 0,
                autoDiscover = json.get("autoDiscover")?.asBoolean ?: false,
                proxyMode = ProxyMode.valueOf(str("proxyMode", "SYSTEM")), proxyAddress = str("proxyAddress", "")
            ))
        } catch (_: Exception) { NetworkSettings() }
    }

    /** 分享域名允许缺失，保留 WWW 回退；无效地址不会参与链接生成。 */
    fun parseShareHosts(body: String): List<String> {
        require(body.length <= 1024 * 1024) { "网络配置响应过大" }
        val json = JsonParser.parseString(body).asJsonObject
        require(json.get("code")?.asInt == 200) { "分享配置获取失败" }
        return json.getAsJsonObject("results")?.get("share")?.takeIf { it.isJsonArray }
            ?.asJsonArray?.take(20)
            ?.mapNotNull { item ->
                try {
                    host(item.asString)
                } catch (_: Exception) {
                    null
                }
            }?.distinct().orEmpty()
    }

    /** 错误页或空配置不能覆盖上次可用线路。 */
    fun parseRoutes(body: String): List<List<String>> {
        require(body.length <= 1024 * 1024) { "网络配置响应过大" }
        val json = try { JsonParser.parseString(body).asJsonObject }
        catch (_: Exception) { throw IllegalArgumentException("服务器没有返回网络配置，可能正在维护") }
        require(json.get("code")?.asInt == 200) { "网络配置获取失败" }
        val source = json.getAsJsonObject("results")?.getAsJsonArray("api")
            ?: throw IllegalArgumentException("响应中没有 API 线路")
        val routes = source.take(20).mapNotNull { row ->
            if (!row.isJsonArray) return@mapNotNull null
            row.asJsonArray.take(30).mapNotNull { item ->
                try { host(item.asString) } catch (_: Exception) { null }
            }.distinct().takeIf { it.isNotEmpty() }
        }.distinct()
        require(routes.isNotEmpty()) { "响应中没有可用的 HTTPS 节点" }
        return routes
    }
}
