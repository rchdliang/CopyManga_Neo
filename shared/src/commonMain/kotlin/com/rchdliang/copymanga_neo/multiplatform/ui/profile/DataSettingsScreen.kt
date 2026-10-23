package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import com.rchdliang.copymanga_neo.multiplatform.ui.components.ActiveRouteLaunchedEffect
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme

@Composable
internal fun DataSettingsScreen(
    onNavigateBack: () -> Unit,
    settings: SettingsViewModel,
    backup: SettingsBackupViewModel,
    logs: NetworkLogViewModel
) {
    val state by settings.uiState.collectAsStateWithLifecycle()
    val backupState by backup.uiState.collectAsStateWithLifecycle()
    val toast = LocalToastHost.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { settings.refreshStorage() }
    ActiveRouteLaunchedEffect(state.message) { state.message?.let { toast.showToast(it); settings.clearMessage() } }
    ActiveRouteLaunchedEffect(backupState.message) { backupState.message?.let { toast.showToast(it); backup.clearMessage() } }
    DataSettingsPage(
        state,
        backupState.busy || backupState.picking,
        onNavigateBack,
        settings::refreshStorage,
        settings::clearImageCache,
        backup::chooseExport,
        backup::chooseImport,
        settings::resetGlobalDefaults,
        onCredentialExportPasswordChange = settings::setCredentialExportPassword,
        onExportUserData = backup::chooseExportUserData,
        onImportUserData = backup::chooseImportUserData,
        onCancelRestore = backup::cancelRestore,
        restorePending = backupState.restorePending,
        onClearNovelCache = settings::clearNovelCache,
        logsContent = { NetworkLogCard(logs) }
    )
    backupState.pendingImport?.let {
        if (LocalRouteActive.current) AlertDialog(
            onDismissRequest = backup::dismissImport,
            title = { Text(if (backupState.pendingImportUserData) "恢复用户数据？" else "导入软件设置？", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    if (backupState.pendingImportUserData) "重启后将用备份覆盖个人数据，重启前新增的数据也会被覆盖。建议先备份当前数据。登录状态与下载文件不受影响。"
                    else "将恢复软件偏好并合并本地历史、进度和书签。较新的阅读位置会保留，不包含登录凭据和缓存正文。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = backup::confirmImport,
                    enabled = !backupState.busy && !state.isSaving
                ) {
                    Text("导入", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = backup::dismissImport) {
                    Text(
                        "取消",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

private enum class DataSettingsAction { CLEAR_CACHE, CLEAR_NOVEL_CACHE, RESET_DEFAULTS }

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
    onClearNovelCache: () -> Unit = {},
    onCredentialExportPasswordChange: (Boolean) -> Unit = {},
    onExportUserData: () -> Unit = {},
    onImportUserData: () -> Unit = {},
    onCancelRestore: () -> Unit = {},
    restorePending: Boolean = false,
    logsContent: @Composable () -> Unit = {}
) {
    var showLogs by rememberSaveable { mutableStateOf(false) }
    var confirmation by rememberSaveable { mutableStateOf<DataSettingsAction?>(null) }
    BackHandler(enabled = showLogs) { showLogs = false }
    if (showLogs) {
        ProfileSettingsScaffold("诊断日志", { showLogs = false }) { logsContent() }
        return
    }
    val cache =
        state.storageUsage?.imageCacheBytes?.let { formatStorageBytes(it) }
            ?: if (state.storageLoading) "正在统计" else "暂不可用"
    val enabled = !state.isLoading && !state.isSaving && !backupBusy
    val novelCache =
        state.storageUsage?.novelCacheBytes?.let { formatStorageBytes(it) }
            ?: if (state.storageLoading) "正在统计" else "暂不可用"
    ProfileSettingsScaffold(
        "数据管理", onNavigateBack, state.storageLoading,
        onRefresh = { if (!state.storageLoading && !state.clearingCache) onRefreshStorage() }
    ) {
        ProfileSectionTitle("缓存与下载")
        ProfileListItem(
            Icons.Outlined.DeleteSweep, "清理图片缓存",
            if (state.clearingCache && !state.clearingNovelCache) "正在清理" else "$cache · 保留下载内容",
            index = 0, count = 2,
            enabled = !state.clearingCache && !state.storageLoading && state.storageUsage != null,
            onClick = { confirmation = DataSettingsAction.CLEAR_CACHE })
        ProfileListItem(
            Icons.Outlined.DeleteSweep, "清理小说缓存",
            if (state.clearingNovelCache) "正在清理" else "$novelCache · ${state.storageUsage?.novelCachedVolumes ?: 0} 卷正文及插图",
            index = 1, count = 2,
            enabled = !state.clearingCache && !state.storageLoading && state.storageUsage != null,
            onClick = { confirmation = DataSettingsAction.CLEAR_NOVEL_CACHE }
        )
        state.storageUsage?.let { usage ->
            Text(
                "已下载漫画 ${
                    formatStorageBytes(usage.downloadBytes)
                } · 可用空间 ${formatStorageBytes(usage.availableBytes)}",
                Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        state.storageError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(8.dp)
            )
        }
        ProfileSectionTitle("用户数据备份")
        Text(
            "个人数据备份：标注、历史、书签、进度、偏好与屏蔽标签。\n不含登录凭据、下载文件和网络配置。\n不兼容原 Android 版备份。",
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        ProfileListItem(Icons.Outlined.FileUpload, "导出用户数据", "保存游客及各账号的个人数据",
            index = 0, count = 2, enabled = enabled, onClick = onExportUserData)
        ProfileListItem(Icons.Outlined.FileDownload, "恢复用户数据", "校验后暂存，重启时覆盖恢复",
            index = 1, count = 2, enabled = enabled, onClick = onImportUserData)
        if (restorePending) {
            Text("已安排恢复，请完全退出并重新打开应用。重启前仍可取消。",
                Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onCancelRestore, enabled = !backupBusy) {
                Text("取消待恢复的数据", color = MaterialTheme.colorScheme.primary)
            }
        }
        ProfileSectionTitle("设置备份")
        Text(
            "备份软件偏好、漫画/小说本地历史和书签。保留游客及各账号记录；不含登录凭据、下载文件和小说缓存。",
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        if (backupBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        ProfileListItem(
            Icons.Outlined.FileUpload, "导出设置与阅读数据", "保存为备份文件",
            index = 0, count = 2, enabled = enabled, onClick = onExport
        )
        ProfileListItem(
            Icons.Outlined.FileDownload, "导入设置与阅读数据", "恢复当前版本备份，合并阅读记录",
            index = 1, count = 2, enabled = enabled, onClick = onImport
        )
        ProfileSectionTitle("登录凭据")
        ProfileListItem(
            Icons.Outlined.Key, "导出时包含密码", "仅导出登录凭据时包含已记住的密码",
            index = 0, count = 1, enabled = !state.isLoading && !state.isSaving,
            onClick = { onCredentialExportPasswordChange(!state.settings.includePasswordInCredentialExport) },
            trailingContent = {
                Switch(
                    checked = state.settings.includePasswordInCredentialExport,
                    onCheckedChange = onCredentialExportPasswordChange,
                    enabled = !state.isLoading && !state.isSaving
                )
            }
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
        val clear = action != DataSettingsAction.RESET_DEFAULTS
        val clearNovel = action == DataSettingsAction.CLEAR_NOVEL_CACHE
        if (LocalRouteActive.current) AlertDialog(
            onDismissRequest = { confirmation = null },
            title = {
                Text(
                    if (clearNovel) "清理全部小说缓存？" else if (clear) "清理图片缓存？" else "恢复默认偏好？",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    if (clearNovel) "将停止小说下载，并清除所有账号的小说离线内容与缓存。阅读记录、书签和漫画下载保留。再次离线阅读需重新下载。"
                    else if (clear) "已下载漫画和小说不受影响。"
                    else "账号、下载和漫画独立设置不变。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    when (action) {
                        DataSettingsAction.CLEAR_CACHE -> onClearCache()
                        DataSettingsAction.CLEAR_NOVEL_CACHE -> onClearNovelCache()
                        DataSettingsAction.RESET_DEFAULTS -> onResetDefaults()
                    }
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
