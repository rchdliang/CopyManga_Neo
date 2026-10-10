package com.rchdliang.copymanga_neo.ui.profile

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme

@Composable
internal fun DataSettingsScreen(
    onNavigateBack: () -> Unit,
    settings: SettingsViewModel = hiltViewModel(),
    backup: SettingsBackupViewModel = hiltViewModel()
) {
    val state by settings.uiState.collectAsStateWithLifecycle()
    val backupState by backup.uiState.collectAsStateWithLifecycle()
    val toast = LocalToastHost.current
    var pendingImport by rememberSaveable { mutableStateOf<String?>(null) }
    val exporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let(backup::export)
        }
    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            pendingImport = uri?.toString()
        }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { settings.refreshStorage() }
    LaunchedEffect(state.message) { state.message?.let { toast.showToast(it); settings.clearMessage() } }
    LaunchedEffect(backupState.message) { backupState.message?.let { toast.showToast(it); backup.clearMessage() } }
    DataSettingsPage(
        state,
        backupState.busy,
        onNavigateBack,
        settings::refreshStorage,
        settings::clearImageCache,
        { exporter.launch("CopyManga-Neo-settings.json") },
        { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        settings::resetGlobalDefaults,
        logsContent = { NetworkLogCard() }
    )
    pendingImport?.let { selected ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("导入软件设置？", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "将覆盖软件偏好，不影响账号和漫画独立设置。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { pendingImport = null; backup.import(selected.toUri()) },
                    enabled = !backupState.busy && !state.isSaving
                ) {
                    Text("导入", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text(
                        "取消",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

private enum class DataSettingsAction { CLEAR_CACHE, RESET_DEFAULTS }

@Composable
internal fun DataSettingsPage(
    state: SettingsUiState = SettingsUiState(isLoading = false, storageLoading = false),
    backupBusy: Boolean = false,
    onNavigateBack: () -> Unit = {},
    onRefreshStorage: () -> Unit = {},
    onClearCache: () -> Unit = {},
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
    onResetDefaults: () -> Unit = {},
    logsContent: @Composable () -> Unit = {}
) {
    var showLogs by rememberSaveable { mutableStateOf(false) }
    var confirmation by rememberSaveable { mutableStateOf<DataSettingsAction?>(null) }
    BackHandler(enabled = showLogs) { showLogs = false }
    if (showLogs) {
        ProfileSettingsScaffold("诊断日志", { showLogs = false }) { logsContent() }
        return
    }
    val context = LocalContext.current
    val cache =
        state.storageUsage?.imageCacheBytes?.let { Formatter.formatShortFileSize(context, it) }
            ?: if (state.storageLoading) "正在统计" else "暂不可用"
    val enabled = !state.isLoading && !state.isSaving && !backupBusy
    ProfileSettingsScaffold(
        "数据管理", onNavigateBack, state.storageLoading,
        onRefresh = { if (!state.storageLoading && !state.clearingCache) onRefreshStorage() }
    ) {
        ProfileSectionTitle("图片缓存")
        ProfileListItem(
            Icons.Outlined.DeleteSweep, "清理图片缓存",
            if (state.clearingCache) "正在清理" else "$cache · 保留已下载漫画",
            index = 0, count = 1,
            enabled = !state.clearingCache && !state.storageLoading && state.storageUsage != null,
            onClick = { confirmation = DataSettingsAction.CLEAR_CACHE })
        state.storageError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(8.dp)
            )
        }
        ProfileSectionTitle("设置备份")
        Text(
            "仅备份软件偏好，不含登录凭据。",
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        if (backupBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        ProfileListItem(
            Icons.Outlined.FileUpload, "导出软件设置", "保存为备份文件",
            index = 0, count = 2, enabled = enabled, onClick = onExport
        )
        ProfileListItem(
            Icons.Outlined.FileDownload, "导入软件设置", "从备份文件恢复偏好",
            index = 1, count = 2, enabled = enabled, onClick = onImport
        )
        ProfileSectionTitle("维护")
        ProfileListItem(
            Icons.Outlined.Description, "诊断日志", "记录、导出与清理请求日志",
            index = 0, count = 2, onClick = { showLogs = true })
        ProfileListItem(
            Icons.Outlined.Restore, "恢复默认偏好", "恢复主题、下载和全局阅读默认值",
            index = 1, count = 2, enabled = enabled,
            onClick = { confirmation = DataSettingsAction.RESET_DEFAULTS })
        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
    confirmation?.let { action ->
        val clear = action == DataSettingsAction.CLEAR_CACHE
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = {
                Text(
                    if (clear) "清理图片缓存？" else "恢复默认偏好？",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    if (clear) "已下载漫画不受影响。"
                    else "账号、下载和漫画独立设置不变。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    if (clear) onClearCache() else onResetDefaults()
                }, enabled = enabled && !state.clearingCache) {
                    Text(if (clear) "清理" else "恢复", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }) {
                    Text(
                        "取消",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

@Preview(name = "数据管理 · 手机", showBackground = true)
@Composable
private fun DataSettingsPreview() {
    CopyManga_NeoTheme { DataSettingsPage() }
}
