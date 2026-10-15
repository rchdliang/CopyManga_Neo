package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import com.rchdliang.copymanga_neo.multiplatform.ui.components.ActiveRouteLaunchedEffect
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiLog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.time.Clock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.repository.NetworkLogRepository
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalToastHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NetworkLogViewModel constructor(
    private val repository: NetworkLogRepository,
    private val platform: ProfilePlatform,
    private val savedState: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    val state = repository.state
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    init {
        if (savedState.get<String>("pending_export_name") != null) chooseExport()
    }

    fun clearMessage() {
        mutableMessage.value = null
    }

    fun setEnabled(enabled: Boolean) = operate { repository.setEnabled(enabled) }
    fun clear() = operate {
        repository.clear().fold(
            { mutableMessage.value = "本地网络日志已清空" },
            { mutableMessage.value = "清空失败，请重试" }
        )
    }

    /** File selection and streaming remain in the retained model, including window recreation. */
    fun chooseExport() = operate {
        val name = savedState.get<String>("pending_export_name") ?: run {
            val date = Clock.System.now().toString().replace(Regex("[^0-9]"), "").take(14)
            "CopyManga-network-$date.jsonl"
        }
        savedState["pending_export_name"] = name
        try {
            val selected = platform.createDocument(name, "application/octet-stream").getOrThrow()
                ?: return@operate
            platform.exportNetworkLog(selected, repository).fold(
                { mutableMessage.value = "网络日志已导出" },
                { mutableMessage.value = "导出失败，请重试" }
            )
        } finally { savedState.remove<String>("pending_export_name") }
    }

    fun export(uri: String) = operate {
        platform.exportNetworkLog(uri, repository).fold(
            { mutableMessage.value = "网络日志已导出" },
            { mutableMessage.value = "导出失败，请重试" }
        )
    }

    private fun operate(block: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiLog.w("NetworkLog", "本地日志操作失败", e)
                mutableMessage.value = "日志操作失败，请重试"
            } finally {
                mutableBusy.value = false
            }
        }
    }
}

/** 数据管理中的本地日志开关及系统文件导出入口。 */
@Composable
internal fun NetworkLogCard(viewModel: NetworkLogViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val toast = LocalToastHost.current
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    ActiveRouteLaunchedEffect(message) {
        message?.let { toast.showToast(it); viewModel.clearMessage() }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "本地网络日志",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "记录网络请求",
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(state.enabled, viewModel::setEnabled, enabled = !busy)
            }
            Text(
                "开启记录，复现问题后导出。登录凭据已隐藏。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "已保存：${fixedDecimal(state.bytes / (1024.0 * 1024.0), 2)} MB",
                color = MaterialTheme.colorScheme.onSurface
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = viewModel::chooseExport, enabled = !busy && state.bytes > 0) {
                    Text("导出日志", color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = { confirmClear = true }, enabled = !busy && state.bytes > 0) {
                    Text("清空日志", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmClear) if (LocalRouteActive.current) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("清空本地网络日志？", color = MaterialTheme.colorScheme.onSurface) },
        confirmButton = {
            TextButton(onClick = { confirmClear = false; viewModel.clear() }) {
                Text("清空", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmClear = false }) {
                Text("取消", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}
