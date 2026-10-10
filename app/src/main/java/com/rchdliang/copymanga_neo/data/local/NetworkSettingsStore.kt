package com.rchdliang.copymanga_neo.data.local

import android.content.Context
import android.content.SharedPreferences
import com.rchdliang.copymanga_neo.data.remote.NetworkSettingsCodec
import com.rchdliang.copymanga_neo.domain.model.NetworkSelection
import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import com.rchdliang.copymanga_neo.domain.model.ProxyMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 同步恢复网络快照，首个请求不会先使用默认代理；写入由仓库在 I/O 线程执行。 */
@Singleton
class NetworkSettingsStore internal constructor(
    private val prefs: SharedPreferences,
    private val identity: OfficialAppIdentity
) {
    @Inject
    constructor(@ApplicationContext context: Context, identity: OfficialAppIdentity) :
            this(context.getSharedPreferences("network_settings", Context.MODE_PRIVATE), identity)

    private val state = MutableStateFlow(restore())

    /** 初次启动即保存本机生成的标识；旧配置的空身份字段也补为实际值。 */
    private fun restore(): NetworkSettings {
        val raw = prefs.getString("current", null)
        val saved = NetworkSettingsCodec.decode(raw)
        val resolved = saved.copy(officialApp = identity.resolve(saved.officialApp))
        if (raw == null || resolved != saved) {
            if (!prefs.edit().putString("current", NetworkSettingsCodec.encode(resolved))
                    .commit()
            ) {
                throw IOException("无法保存网络配置")
            }
        }
        return resolved
    }

    val settings = state.asStateFlow()
    private val onChange = mutableListOf<() -> Unit>()

    @Volatile
    var generation: Long = 0
        private set

    @Synchronized
    fun save(value: NetworkSettings, rememberPrevious: Boolean = true) {
        val normalized =
            NetworkSettingsCodec.normalize(value.copy(officialApp = identity.resolve(value.officialApp)))
        if (normalized == state.value) return
        val connectionChanged = connectionKey(normalized) != connectionKey(state.value)
        val editor = prefs.edit().putString("current", NetworkSettingsCodec.encode(normalized))
        if (rememberPrevious) editor.putString("previous", NetworkSettingsCodec.encode(state.value))
        if (!editor.commit()) throw IOException("无法保存网络设置")
        if (connectionChanged) generation++
        state.value = normalized
        if (connectionChanged) onChange.forEach { it() }
    }

    @Synchronized
    fun snapshot(): Pair<Long, NetworkSettings> = generation to state.value

    /** 导入校验完成后只能修改校验时的配置，避免覆盖用户期间保存的新设置。 */
    @Synchronized
    fun saveIfCurrent(expectedGeneration: Long, value: NetworkSettings): Boolean {
        if (generation != expectedGeneration) return false
        save(value)
        return true
    }

    @Synchronized
    fun onChange(listener: () -> Unit) {
        onChange.add(listener)
    }

    /** 配置切换与响应副作用互斥，过期请求不能清除登录或重新触发风控。 */
    @Synchronized
    fun ifCurrent(expectedGeneration: Long?, block: () -> Unit) {
        if (expectedGeneration == null || expectedGeneration == generation) block()
    }

    private fun connectionKey(value: NetworkSettings): List<Any> = listOf(
        value.requestMode,
        value.officialApp,
        value.apiHost,
        value.wwwHost,
        value.loginHost,
        value.version,
        value.selection,
        when (value.selection) {
            NetworkSelection.DEFAULT -> listOf(value.apiHost)
            NetworkSelection.FIXED -> listOf(value.fixedHost)
            NetworkSelection.ROUTE -> value.routes.getOrNull(value.routeIndex).orEmpty()
        }, value.proxyMode, if (value.proxyMode == ProxyMode.MANUAL) value.proxyAddress else ""
    )

    @Synchronized
    fun previousSettings(): NetworkSettings {
        val raw = prefs.getString("previous", null) ?: throw IOException("没有可恢复的上次配置")
        return NetworkSettingsCodec.decode(raw)
    }

    @Synchronized
    fun rollback() {
        val previous =
            prefs.getString("previous", null) ?: throw IOException("没有可恢复的上次配置")
        save(NetworkSettingsCodec.decode(previous))
    }
}
