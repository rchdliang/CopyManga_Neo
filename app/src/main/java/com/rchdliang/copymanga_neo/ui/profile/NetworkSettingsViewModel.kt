package com.rchdliang.copymanga_neo.ui.profile

import com.rchdliang.copymanga_neo.data.remote.NetworkSettingsCodec
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
    val saving: Boolean = false,
    val error: String? = null,
    val probes: List<NetworkProbe> = emptyList(),
    val message: String? = null,
    val diagnosticReport: String? = null
) {
    val dirty: Boolean get() = saved != draft && runCatching {
        NetworkSettingsCodec.normalize(saved) != NetworkSettingsCodec.normalize(draft)
    }.getOrDefault(true)
}

@HiltViewModel
class NetworkSettingsViewModel @Inject constructor(private val repository: NetworkSettingsRepository) :
    ViewModel() {
    val officialAppDefaults = repository.officialAppDefaults
    private val state = MutableStateFlow(
        NetworkSettingsUiState(
            repository.settings.value,
            repository.settings.value
        )
    )
    val uiState = state.asStateFlow()
    private var operation: Job? = null
    private val handler = CoroutineExceptionHandler { _, error ->
        android.util.Log.e("NetworkSettings", "网络设置操作失败", error)
        state.update { it.copy(busy = false, saving = false, error = "网络设置操作失败，请重试") }
    }

    init {
        viewModelScope.launch(handler) {
            repository.settings.collect { value ->
                state.update { it.copy(saved = value, draft = if (it.dirty) it.draft else value) }
            }
        }
    }

    fun edit(transform: (NetworkSettings) -> NetworkSettings) {
        if (!state.value.busy) state.update {
            it.copy(
                draft = transform(it.draft),
                probes = emptyList(),
                message = null, error = null
            )
        }
    }

    fun randomizePseudoId() = edit {
        it.copy(officialApp = it.officialApp.copy(pseudoId = repository.generatePseudoId()))
    }

    fun randomizeUmString() = edit {
        it.copy(officialApp = it.officialApp.copy(umString = repository.generateUmString()))
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

    fun clearMessage() {
        state.update { it.copy(message = null) }
    }

    fun cancelWork() {
        if (!state.value.saving) operation?.cancel()
    }

    fun discard() {
        state.update { it.copy(draft = it.saved, probes = emptyList(), error = null) }
    }

    fun save(onSuccess: () -> Unit = {}) = operate {
        state.update { it.copy(saving = true) }
        try {
            val validation = runCatching { NetworkSettingsCodec.normalize(state.value.draft) }
            if (validation.isFailure) {
                val message = validation.exceptionOrNull()?.message
                // Only our known validation messages are suitable for the form.
                state.update { it.copy(error = networkValidationMessage(message)) }
                return@operate
            }
            repository.save(validation.getOrThrow()).fold(
                { sync("已保存"); onSuccess() },
                { failure(it) }
            )
        } finally { state.update { it.copy(saving = false) } }
    }

    fun reset() = edit { NetworkSettings(officialApp = officialAppDefaults) }

    fun rollback() = operate {
        repository.previousSettings().fold(
            { previous -> state.update { it.copy(draft = previous, probes = emptyList(), message = "已载入，保存后生效") } },
            { state.update { it.copy(error = "无法载入上次配置，请检查是否有已保存的历史配置") } }
        )
    }

    fun discover() = operate {
        repository.discover().fold({ sync("线路已更新") }, { failure(it) })
    }

    fun test() = operate {
        repository.test(state.value.draft).fold(
            { probes -> state.update { it.copy(probes = probes, message = "测速完成") } },
            { failure(it) }
        )
    }

    fun chooseFastest() {
        val current = state.value
        if (current.busy) return
        val times = current.probes.mapNotNull { p -> p.latencyMillis?.let { p.host to it } }.toMap()
        val eligible = current.draft.routes.flatten().filter { it in times }
        if (eligible.isEmpty()) {
            state.update { it.copy(message = "请先获取线路并完成测速") }; return
        }
        val draft = if (current.draft.selection == NetworkSelection.ROUTE) {
            val route =
                current.draft.routes.indices.filter { i -> current.draft.routes[i].all { it in times } }
                    .minByOrNull { i ->
                        current.draft.routes[i].map { times.getValue(it) }.average()
                    }
            if (route == null) {
                state.update { it.copy(message = "没有全部节点均可连接的线路，可改为固定节点") }; return
            }
            current.draft.copy(routeIndex = route)
        } else current.draft.copy(
            selection = NetworkSelection.FIXED,
            fixedHost = eligible.minBy { times.getValue(it) })
        state.update { it.copy(draft = draft, message = "已选择，请保存") }
    }

    private fun sync(message: String) {
        state.update {
            it.copy(
                saved = repository.settings.value,
                draft = repository.settings.value,
                probes = emptyList(),
                message = message, error = null
            )
        }
    }

    private fun failure(error: Throwable) {
        state.update { it.copy(error = "操作失败，请检查连接后重试；当前编辑内容已保留") }
    }

    private fun operate(block: suspend () -> Unit) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, message = null, error = null) }
        operation = viewModelScope.launch(handler) {
            try {
                block()
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }
}

internal fun networkValidationMessage(message: String?): String = when (message) {
    "域名不能为空", "域名格式无效", "请填写 HTTPS 域名，不要包含路径、账号或端口",
    "请求版本格式无效", "App 请求版本格式无效", "版本号须为大于 0 的整数",
    "此字段最多 256 个字符，仅支持英文字母、数字、空格和半角符号。",
    "代理地址格式无效", "代理需为 HTTP 地址，例如 127.0.0.1:7890",
    "请先获取并选择线路", "请选择已发现的节点" -> message
    else -> "配置格式无效，请检查域名、代理及预加载数值"
}
