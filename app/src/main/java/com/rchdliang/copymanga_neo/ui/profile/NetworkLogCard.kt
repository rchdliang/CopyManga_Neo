package com.rchdliang.copymanga_neo.ui.profile

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.repository.NetworkLogRepository
import com.rchdliang.copymanga_neo.ui.components.LocalToastHost
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class NetworkLogViewModel @Inject constructor(
    private val repository: NetworkLogRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val state = repository.state
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
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

    fun export(uri: Uri) = operate {
        withContext(Dispatchers.IO) {
            val output =
                context.contentResolver.openOutputStream(uri, "wt") ?: error("无法打开导出文件")
            output.use { repository.export(it) }.fold(
                { mutableMessage.value = "网络日志已导出" },
                { mutableMessage.value = "导出失败，请重试" }
            )
        }
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
                android.util.Log.w("NetworkLog", "本地日志操作失败", e)
                mutableMessage.value = "日志操作失败，请重试"
            } finally {
                mutableBusy.value = false
            }
        }
    }
}

/** 网络设置中的本地日志开关及系统文件导出入口。 */
@Composable
internal fun NetworkLogCard(viewModel: NetworkLogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val toast = LocalToastHost.current
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val exporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri != null) viewModel.export(uri)
        }
    LaunchedEffect(message) {
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
                "出问题前开启记录，复现后关闭并导出。日志仅保存在本机，不会自动上传；Token、Cookie 和密码等凭据会隐藏。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "保留最近约 20 MB。接口和网页正文最多记录 2 MiB，超出会标记截断；登录、个人资料和图片正文不记录。目录密文保持原样。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "已保存：${String.format(Locale.ROOT, "%.2f", state.bytes / (1024.0 * 1024.0))} MB",
                color = MaterialTheme.colorScheme.onSurface
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    val date = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
                    exporter.launch("CopyManga-network-$date.jsonl")
                }, enabled = !busy && state.bytes > 0) {
                    Text("导出日志", color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = { confirmClear = true }, enabled = !busy && state.bytes > 0) {
                    Text("清空日志", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
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
