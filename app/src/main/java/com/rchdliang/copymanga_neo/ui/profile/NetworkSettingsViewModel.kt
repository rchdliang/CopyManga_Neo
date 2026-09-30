package com.rchdliang.copymanga_neo.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.NetworkProbe
import com.rchdliang.copymanga_neo.domain.model.NetworkSelection
import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import com.rchdliang.copymanga_neo.domain.repository.NetworkSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NetworkSettingsUiState(
    val saved: NetworkSettings = NetworkSettings(),
    val draft: NetworkSettings = NetworkSettings(),
    val busy: Boolean = false,
    val probes: List<NetworkProbe> = emptyList(),
    val message: String? = null,
    val diagnosticReport: String? = null
) { val dirty: Boolean get() = saved != draft }

@HiltViewModel
class NetworkSettingsViewModel @Inject constructor(private val repository: NetworkSettingsRepository) : ViewModel() {
    val officialAppDefaults = repository.officialAppDefaults
    private val state = MutableStateFlow(NetworkSettingsUiState(repository.settings.value, repository.settings.value))
    val uiState = state.asStateFlow()
    private var operation: Job? = null
    private val handler = CoroutineExceptionHandler { _, error ->
        android.util.Log.e("NetworkSettings", "网络设置操作失败", error)
        state.update { it.copy(busy = false, message = "网络设置操作失败，请重试") }
    }
    init {
        viewModelScope.launch(handler) {
            repository.settings.collect { value ->
                state.update { it.copy(saved = value, draft = if (it.dirty) it.draft else value) }
            }
        }
    }
    fun edit(transform: (NetworkSettings) -> NetworkSettings) {
        if (!state.value.busy) state.update { it.copy(draft = transform(it.draft), probes = emptyList(), message = null) }
    }
    fun inspectDevice() = operate {
        repository.diagnoseOfficialApp().fold(
            { report -> state.update { it.copy(diagnosticReport = report) } },
            { failure(it) }
        )
    }

    fun closeDiagnosticReport() {
        state.update { it.copy(diagnosticReport = null) }
    }
    fun clearMessage() { state.update { it.copy(message = null) } }
    fun cancelWork() { operation?.cancel() }
    fun discard() { state.update { it.copy(draft = it.saved, probes = emptyList()) } }
    fun save() = operate {
        repository.save(state.value.draft).fold(
            { sync("网络设置已保存；请刷新页面。若更改了请求模式或 App 身份，请重新登录") },
            { state.update { s -> s.copy(message = it.message ?: "保存失败") } }
        )
    }
    fun reset() = operate { repository.reset().fold({ sync("已恢复默认网络设置") }, { failure(it) }) }
    fun rollback() = operate { repository.rollback().fold({ sync("已恢复上次网络设置") }, { failure(it) }) }
    fun discover() = operate {
        repository.discover().fold({ sync("已更新拷贝线路，请选择后保存") }, { failure(it) })
    }
    fun test() = operate {
        repository.test(state.value.draft).fold(
            { probes -> state.update { it.copy(probes = probes, message = "测速完成；连接耗时不代表业务接口可用") } },
            { failure(it) }
        )
    }
    fun chooseFastest() {
        val current = state.value
        if (current.busy) return
        val times = current.probes.mapNotNull { p -> p.latencyMillis?.let { p.host to it } }.toMap()
        val eligible = current.draft.routes.flatten().filter { it in times }
        if (eligible.isEmpty()) { state.update { it.copy(message = "请先获取线路并完成测速") }; return }
        val draft = if (current.draft.selection == NetworkSelection.ROUTE) {
            val route = current.draft.routes.indices.filter { i -> current.draft.routes[i].all { it in times } }
                .minByOrNull { i -> current.draft.routes[i].map { times.getValue(it) }.average() }
            if (route == null) { state.update { it.copy(message = "没有全部节点均可连接的线路，可改为固定节点") }; return }
            current.draft.copy(routeIndex = route)
        } else current.draft.copy(selection = NetworkSelection.FIXED, fixedHost = eligible.minBy { times.getValue(it) })
        state.update { it.copy(draft = draft, message = "已选择测速最快项，保存后生效") }
    }
    private fun sync(message: String) { state.update { it.copy(saved = repository.settings.value, draft = repository.settings.value, probes = emptyList(), message = message) } }
    private fun failure(error: Throwable) { state.update { it.copy(message = error.message ?: "操作失败，原配置已保留") } }
    private fun operate(block: suspend () -> Unit) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, message = null) }
        operation = viewModelScope.launch(handler) { try { block() } finally { state.update { it.copy(busy = false) } } }
    }
}
