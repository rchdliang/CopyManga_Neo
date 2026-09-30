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
    val discoveredAt: Long = 0,
    val autoDiscover: Boolean = false,
    val proxyMode: ProxyMode = ProxyMode.SYSTEM,
    val proxyAddress: String = "",
    val requestMode: RequestMode = RequestMode.WEB,
    val officialApp: OfficialAppSettings = OfficialAppSettings()
)

data class NetworkProbe(val host: String, val latencyMillis: Long?, val error: String? = null)
