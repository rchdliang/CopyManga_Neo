package com.rchdliang.copymanga_neo.data.local

import android.content.Context
import com.rchdliang.copymanga_neo.data.remote.NetworkSettingsCodec
import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import com.rchdliang.copymanga_neo.domain.model.NetworkSelection
import com.rchdliang.copymanga_neo.domain.model.ProxyMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 同步恢复网络快照，首个请求不会先使用默认代理；写入由仓库在 I/O 线程执行。 */
@Singleton
class NetworkSettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("network_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(NetworkSettingsCodec.decode(prefs.getString("current", null)))
    val settings = state.asStateFlow()
    private val onChange = mutableListOf<() -> Unit>()
    @Volatile var generation: Long = 0
        private set

    @Synchronized
    fun save(value: NetworkSettings, rememberPrevious: Boolean = true) {
        val normalized = NetworkSettingsCodec.normalize(value)
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

    @Synchronized
    fun onChange(listener: () -> Unit) { onChange.add(listener) }

    /** 配置切换与响应副作用互斥，过期请求不能清除登录或重新触发风控。 */
    @Synchronized
    fun ifCurrent(expectedGeneration: Long?, block: () -> Unit) {
        if (expectedGeneration == null || expectedGeneration == generation) block()
    }

    private fun connectionKey(value: NetworkSettings): List<Any> = listOf(
        value.apiHost, value.wwwHost, value.loginHost, value.version, value.selection,
        when (value.selection) {
            NetworkSelection.DEFAULT -> listOf(value.apiHost)
            NetworkSelection.FIXED -> listOf(value.fixedHost)
            NetworkSelection.ROUTE -> value.routes.getOrNull(value.routeIndex).orEmpty()
        }, value.proxyMode, if (value.proxyMode == ProxyMode.MANUAL) value.proxyAddress else ""
    )

    @Synchronized
    fun rollback() {
        val previous = prefs.getString("previous", null) ?: throw IOException("没有可恢复的上次配置")
        save(NetworkSettingsCodec.decode(previous))
    }
}
