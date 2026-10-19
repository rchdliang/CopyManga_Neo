package com.rchdliang.copymanga_neo.domain.model

enum class NetworkSelection { DEFAULT, ROUTE, FIXED }
enum class ProxyMode { SYSTEM, MANUAL, DIRECT }
enum class RequestMode { WEB, OFFICIAL_APP }

/** App 协议版本来自 HAR；空身份字段表示使用本机或已持久化的随机默认值。 */
data class OfficialAppSettings(
    val apiHost: String = "api.copy202601.com",
    val loginHost: String = "api.copy202602.com",
    val version: String = "3.0.9",
    val versionCode: String = "83",
    val pseudoId: String = "",
    val umString: String = "",
    val device: String = "",
    val deviceInfo: String = "",
    val buildTime: String = ""
)

/** 与 App 会话一起迁移的请求身份；不包含域名、代理、签名密钥或密码。 */
data class AppCredentialIdentity(
    val version: String,
    val versionCode: String,
    val pseudoId: String,
    val umString: String,
    val device: String,
    val deviceInfo: String,
    val buildTime: String
) {
    fun applyTo(settings: OfficialAppSettings): OfficialAppSettings = settings.copy(
        version = version, versionCode = versionCode, pseudoId = pseudoId,
        umString = umString, device = device, deviceInfo = deviceInfo, buildTime = buildTime
    )
}

object NetworkDefaults {
    const val API_HOST = "api.copy4000.com"
    const val WWW_HOST = "www.copy3000.com"
    const val WEB_VERSION = "2026.08.24"
}

/** 网络配置单独持久化；登录域名留空时跟随 API。 */
data class NetworkSettings(
    val apiHost: String = NetworkDefaults.API_HOST,
    val wwwHost: String = NetworkDefaults.WWW_HOST,
    val loginHost: String = "",
    val version: String = NetworkDefaults.WEB_VERSION,
    val selection: NetworkSelection = NetworkSelection.DEFAULT,
    val routeIndex: Int = 0,
    val fixedHost: String = "",
    val routes: List<List<String>> = emptyList(),
    val shareHosts: List<String> = emptyList(),
    val discoveredAt: Long = 0,
    val autoDiscover: Boolean = false,
    val proxyMode: ProxyMode = ProxyMode.SYSTEM,
    val proxyAddress: String = "",
    val requestMode: RequestMode = RequestMode.OFFICIAL_APP,
    val officialApp: OfficialAppSettings = OfficialAppSettings(),
    val themeFetchConcurrency: Int = ThemeLoadingPolicy.DEFAULT_CONCURRENCY,
    val themeFetchIntervalMillis: Int = ThemeLoadingPolicy.DEFAULT_INTERVAL_MILLIS
)

data class NetworkProbe(val host: String, val latencyMillis: Long?, val error: String? = null)

object ThemeLoadingPolicy {
    const val DEFAULT_CONCURRENCY = 3
    const val DEFAULT_INTERVAL_MILLIS = 500
    const val MAX_CONCURRENCY = 6
    const val MAX_INTERVAL_MILLIS = 5000

    fun validate(settings: NetworkSettings) {
        require(settings.themeFetchConcurrency in 1..MAX_CONCURRENCY) { "黑名单加载并发数须为 1–6" }
        require(settings.themeFetchIntervalMillis in 0..MAX_INTERVAL_MILLIS) { "黑名单请求间隔须为 0–5000 毫秒" }
    }
}
