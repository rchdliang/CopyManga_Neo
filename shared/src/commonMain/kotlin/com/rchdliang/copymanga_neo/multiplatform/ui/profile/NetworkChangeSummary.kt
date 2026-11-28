package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import com.rchdliang.copymanga_neo.data.local.portable.PortableNetworkValidation
import com.rchdliang.copymanga_neo.domain.model.NetworkDefaults
import com.rchdliang.copymanga_neo.domain.model.NetworkSelection
import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import com.rchdliang.copymanga_neo.domain.model.ProxyMode
import io.ktor.http.Url

internal fun networkChangeSummary(saved: NetworkSettings, draft: NetworkSettings): List<Pair<String, String>> = buildList {
    if (saved.requestMode != draft.requestMode) add("请求模式" to draft.requestMode.profileLabel)
    val address = draft.proxyAddress.trim()
    if (saved.proxyMode != draft.proxyMode || saved.proxyAddress.trim() != address) {
        val mode = when (draft.proxyMode) {
            ProxyMode.SYSTEM -> "跟随系统"
            ProxyMode.DIRECT -> "直连"
            ProxyMode.MANUAL -> if (address.isEmpty() || networkFieldError("代理地址", address) != null) "手动 · 地址待检查"
                else {
                    val url = Url(if ("://" in address) address else "http://$address")
                    "手动 · ${url.host}:${url.port}"
                }
        }
        if (saved.proxyMode == draft.proxyMode && draft.proxyMode != ProxyMode.MANUAL) {
            add("手动代理地址" to if (address.isEmpty()) "已清空" else "已更新")
        } else add("代理" to mode)
    }
    if (saved.themeFetchConcurrency != draft.themeFetchConcurrency || saved.themeFetchIntervalMillis != draft.themeFetchIntervalMillis) {
        add("漫画详情预加载" to "同时 ${draft.themeFetchConcurrency} 部 · 间隔 ${draft.themeFetchIntervalMillis} 毫秒")
    }

    fun hostChange(name: String, old: String, new: String, empty: String = "待检查"): Pair<String, String>? {
        val before = runCatching { PortableNetworkValidation.host(old) }.getOrNull()
        val after = runCatching { PortableNetworkValidation.host(new) }.getOrNull()
        if ((before ?: old.trim()) == (after ?: new.trim())) return null
        return name to (after ?: if (new.isBlank()) empty else "待检查")
    }
    hostChange("网页 API 域名", saved.apiHost, draft.apiHost)?.let { add(it) }
    hostChange("网页域名", saved.wwwHost, draft.wwwHost)?.let { add(it) }
    hostChange("网页登录域名", saved.loginHost, draft.loginHost, "跟随 API")?.let { add(it) }
    val webVersion = NetworkDefaults.normalizeWebVersion(draft.version)
    if (NetworkDefaults.normalizeWebVersion(saved.version) != webVersion) {
        add("网页请求版本" to if (networkFieldError("请求版本", webVersion) == null) webVersion else "待检查")
    }
    val oldApp = saved.officialApp.migrateLegacyVersion()
    val app = draft.officialApp.migrateLegacyVersion()
    hostChange("App API 域名", oldApp.apiHost, app.apiHost)?.let { add(it) }
    hostChange("App 登录域名", oldApp.loginHost, app.loginHost)?.let { add(it) }
    if (oldApp.version != app.version || oldApp.versionCode != app.versionCode) {
        val valid = networkFieldError("App 版本", app.version) == null && networkFieldError("版本号", app.versionCode) == null
        add("App 版本" to if (valid) "${app.version}（${app.versionCode}）" else "待检查")
    }
    val identity = buildList {
        if (oldApp.pseudoId != app.pseudoId) add("SDK 标识")
        if (oldApp.umString != app.umString) add("统计标识")
        if (oldApp.device != app.device) add("设备构建号")
        if (oldApp.deviceInfo != app.deviceInfo) add("设备型号")
        if (oldApp.buildTime != app.buildTime) add("构建日期")
    }
    if (identity.isNotEmpty()) add("设备身份" to identity.joinToString("、"))

    if (saved.selection != draft.selection) add("线路方式" to when (draft.selection) {
        NetworkSelection.DEFAULT -> "默认 API"
        NetworkSelection.ROUTE -> "线路内轮询"
        NetworkSelection.FIXED -> "固定节点"
    })
    if (saved.routeIndex != draft.routeIndex) add("所选线路" to "线路 ${draft.routeIndex + 1}")
    hostChange("固定节点", saved.fixedHost, draft.fixedHost, "未选择")?.let { add(it) }
    if (saved.autoDiscover != draft.autoDiscover) add("启动时检查线路更新" to if (draft.autoDiscover) "开启" else "关闭")
    // 还原旧配置也可能更换发现结果，与手动编辑的无效地址分开归一化。
    fun discovery(settings: NetworkSettings): NetworkSettings {
        val lists = NetworkSettings(routes = settings.routes, shareHosts = settings.shareHosts)
        return runCatching { PortableNetworkValidation.normalize(lists) }.getOrDefault(lists)
    }
    if (saved.routes != draft.routes || saved.shareHosts != draft.shareHosts) {
        val before = discovery(saved)
        val after = discovery(draft)
        if (before.routes != after.routes) add("线路列表" to "${after.routes.size} 条线路")
        if (before.shareHosts != after.shareHosts) add("分享域名" to "${after.shareHosts.size} 个域名")
    }
    if (saved.discoveredAt != draft.discoveredAt) add("线路获取记录" to if (draft.discoveredAt == 0L) "已清空" else "已更新")
}
