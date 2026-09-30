package com.rchdliang.copymanga_neo.testing

import android.content.SharedPreferences
import com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore
import com.rchdliang.copymanga_neo.data.remote.api.MangaApiService
import com.rchdliang.copymanga_neo.data.repository.MangaRepositoryImpl
import com.rchdliang.copymanga_neo.data.repository.OfficialAppContentSource
import java.lang.reflect.Proxy

/** 现有仓库测试使用默认网页配置，不访问 Android 存储或外部网络。 */
internal fun webRepositoryFixture(
    api: MangaApiService,
    www: MangaApiService = api
): MangaRepositoryImpl {
    val preferences = Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)
    ) { _, method, _ ->
        when (method.name) {
            "getString" -> null
            else -> error("Unexpected preferences call: ${method.name}")
        }
    } as SharedPreferences
    val settings = NetworkSettingsStore(preferences)
    return MangaRepositoryImpl(
        api, www, networkSettings = settings,
        officialContent = OfficialAppContentSource(api, settings)
    )
}
