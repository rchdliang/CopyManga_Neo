package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.repository.ErrorLogRepository
import com.rchdliang.copymanga_neo.multiplatform.ui.components.ActiveRouteLaunchedEffect
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalToastHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock

class ErrorLogViewModel(
    private val repository: ErrorLogRepository,
    private val platform: ProfilePlatform,
    private val savedState: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    val state = repository.state
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()

    init {
        if (savedState.get<String>("pending_error_export") != null) chooseExport()
    }
    fun clearMessage() { mutableMessage.value = null }
    fun refresh() = operate { repository.refresh() }
    fun clear() = operate {
        repository.clear().fold(
            { mutableMessage.value = "本地错误与崩溃日志已清空" },
            { mutableMessage.value = "清空失败，请重试" }
        )
    }
    fun chooseExport() = operate {
        val name = savedState.get<String>("pending_error_export") ?: run {
            val date = Clock.System.now().toString().replace(Regex("[^0-9]"), "").take(14)
            "CopyManga-errors-$date.jsonl"
        }
        savedState["pending_error_export"] = name
        try {
            val selection = platform.createDocument(name, "application/octet-stream")
            selection.fold(
                onSuccess = { reference ->
                    if (reference != null) repository.export().fold(
                        onSuccess = { bytes ->
                            platform.writeDocument(reference, bytes).fold(
                                { mutableMessage.value = "错误与崩溃日志已导出" },
                                { mutableMessage.value = "导出失败，请重试" }
                            )
                        },
                        onFailure = { mutableMessage.value = "读取日志失败，请重试" }
                    )
                },
                onFailure = { mutableMessage.value = "无法选择导出位置，请重试" }
            )
        } finally { savedState.remove<String>("pending_error_export") }
    }

    private fun operate(block: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        viewModelScope.launch {
            try { block() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutableMessage.value = "错误日志操作失败，请重试" }
            finally { mutableBusy.value = false }
        }
    }
}

/** 默认记录，不需要在闪退前手动开启；导出和清空均由用户发起。 */
@Composable
internal fun ErrorLogCard(viewModel: ErrorLogViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val toast = LocalToastHost.current
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    ActiveRouteLaunchedEffect(Unit) { viewModel.refresh() }
    ActiveRouteLaunchedEffect(message) {
        message?.let { toast.showToast(it); viewModel.clearMessage() }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("错误与崩溃日志", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "自动保存错误信息和异常堆栈，重新打开应用后仍可导出。仅保存在本机，最多保留约 1 MB。",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("已保存：${formatStorageBytes(state.bytes)}", color = MaterialTheme.colorScheme.onSurface)
            Text("日志可能包含文件路径等诊断信息，分享前请检查内容。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    if (confirmClear && LocalRouteActive.current) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("清空错误与崩溃日志？", color = MaterialTheme.colorScheme.onSurface) },
        text = { Text("历史记录将被删除，之后发生的错误仍会自动记录。", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
