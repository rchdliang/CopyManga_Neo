package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import com.rchdliang.copymanga_neo.domain.model.NetworkSelection
import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import com.rchdliang.copymanga_neo.domain.model.ProxyMode
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkChangeSummaryTest {
    @Test fun equivalentHostsRoutesAndLegacyVersionsDoNotProduceChanges() {
        val saved = NetworkSettings(routes = listOf(listOf("example.test")), shareHosts = listOf("share.test"))
        val equivalent = saved.copy(apiHost = " https://${saved.apiHost}/ ", loginHost = "  ", version = "2026.08.24",
            officialApp = saved.officialApp.copy(version = "3.1.0", versionCode = "84"),
            routes = listOf(listOf("https://example.test/", "example.test")), shareHosts = listOf("share.test", "https://share.test/"))
        assertTrue(networkChangeSummary(saved, equivalent).isEmpty())
    }

    @Test fun modeProxyAndPreloadShowTheProposedValuesWithoutLeakingPrivateFields() {
        val saved = NetworkSettings()
        val draft = saved.copy(requestMode = RequestMode.WEB, proxyMode = ProxyMode.MANUAL,
            proxyAddress = "http://127.0.0.1:8080", themeFetchConcurrency = 4, themeFetchIntervalMillis = 750)
        assertEquals(listOf("请求模式" to "网页模式", "代理" to "手动 · 127.0.0.1:8080",
            "漫画详情预加载" to "同时 4 部 · 间隔 750 毫秒"), networkChangeSummary(saved, draft))

        val invalid = draft.copy(proxyAddress = "http://account:secret@127.0.0.1:8080",
            wwwHost = "https://www.example.test/private?token=secret", apiHost = "https://${saved.apiHost}/",
            officialApp = saved.officialApp.copy(pseudoId = "secret-sdk-id", umString = "secret-stat-id",
                device = "secret-build", deviceInfo = "secret-model", buildTime = "secret-date"))
        val summary = networkChangeSummary(saved, invalid).toMap()
        assertEquals("手动 · 地址待检查", summary["代理"])
        assertEquals("待检查", summary["网页域名"])
        assertFalse("网页 API 域名" in summary)
        assertEquals("SDK 标识、统计标识、设备构建号、设备型号、构建日期", summary["设备身份"])
        assertFalse(summary.toString().contains("secret"))
        assertFalse(summary.toString().contains("account"))
    }

    @Test fun advancedSettingsIncludeInactiveModesAndDiscoveredRoutesWithoutExposingLists() {
        val saved = NetworkSettings(loginHost = "old.test")
        val next = saved.copy(loginHost = "", version = "2027.01", selection = NetworkSelection.FIXED,
            fixedHost = "new.test", routeIndex = 1, autoDiscover = true, discoveredAt = 1,
            routes = listOf(listOf("old.test"), listOf("new.test")), shareHosts = listOf("share.test"),
            officialApp = saved.officialApp.copy(apiHost = "api.test", loginHost = "login.test", version = "3.2.0", versionCode = "90"))
        assertEquals(mapOf("网页登录域名" to "跟随 API", "网页请求版本" to "2027.01", "App API 域名" to "api.test",
            "App 登录域名" to "login.test", "App 版本" to "3.2.0（90）", "线路方式" to "固定节点", "所选线路" to "线路 2",
            "固定节点" to "new.test", "启动时检查线路更新" to "开启", "线路列表" to "2 条线路",
            "分享域名" to "1 个域名", "线路获取记录" to "已更新"), networkChangeSummary(saved, next).toMap())
    }
}
