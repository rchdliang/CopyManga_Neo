package com.rchdliang.copymanga_neo.ui.profile

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.BuildConfig
import com.rchdliang.copymanga_neo.R
import com.rchdliang.copymanga_neo.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme

/** 设置中的应用信息与维护入口。 */
@Composable
internal fun AboutScreen(
    onNavigateBack: () -> Unit,
    settings: SettingsViewModel = hiltViewModel(),
    backup: SettingsBackupViewModel = hiltViewModel()
) {
    val state by settings.uiState.collectAsStateWithLifecycle()
    val backupState by backup.uiState.collectAsStateWithLifecycle()
    val toast = LocalToastHost.current
    var pendingImport by rememberSaveable { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(backup::export) }
    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            pendingImport = uri?.toString()
        }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { settings.refreshStorage() }
    LaunchedEffect(state.message) {
        state.message?.let { toast.showToast(it); settings.clearMessage() }
    }
    LaunchedEffect(backupState.message) {
        backupState.message?.let { toast.showToast(it); backup.clearMessage() }
    }
    AboutPage(
        state = state,
        backupBusy = backupState.busy,
        onNavigateBack = onNavigateBack,
        onRefreshStorage = settings::refreshStorage,
        onClearCache = settings::clearImageCache,
        onExport = { exporter.launch("CopyManga-Neo-settings.json") },
        onImport = {
            importer.launch(
                arrayOf(
                    "application/json",
                    "text/plain",
                    "application/octet-stream"
                )
            )
        },
        logsContent = { NetworkLogCard() }
    )
    pendingImport?.let { selected ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("导入软件设置？", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "将覆盖主题、仅 Wi-Fi 下载、全局阅读默认值和首页排行榜偏好。账号、网络配置及漫画独立设置保持不变。",
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
                    Text("取消", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}

@Composable
internal fun AboutPage(
    state: SettingsUiState = SettingsUiState(isLoading = false),
    backupBusy: Boolean = false,
    onNavigateBack: () -> Unit = {},
    onRefreshStorage: () -> Unit = {},
    onClearCache: () -> Unit = {},
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
    logsContent: @Composable () -> Unit = {}
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val cache =
        state.storageUsage?.imageCacheBytes?.let { Formatter.formatShortFileSize(context, it) }
            ?: if (state.storageLoading) "正在统计" else "统计失败"
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("关于", color = MaterialTheme.colorScheme.onSurface) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回设置")
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0)
        )
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            val identity: @Composable () -> Unit = { AppIdentityCard() }
            val tools: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(ProfileListDefaults.Gap)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AboutSectionTitle("存储", Modifier.weight(1f))
                        TextButton(
                            onClick = onRefreshStorage,
                            enabled = !state.storageLoading && !state.clearingCache
                        ) {
                            Text("刷新", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    ProfileListItem(
                        Icons.Outlined.DeleteSweep, "清理图片缓存",
                        if (state.clearingCache) "正在清理" else "$cache · 保留已下载漫画",
                        index = 0, count = 1,
                        enabled = !state.clearingCache && !state.storageLoading && state.storageUsage != null,
                        onClick = { confirmClear = true },
                        trailingContent = { AboutChevron() }
                    )
                    state.storageError?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    AboutSectionTitle("软件设置")
                    Text(
                        "备份主题、下载偏好、全局阅读默认值和首页排行榜偏好。",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (backupBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    ProfileListItem(
                        Icons.Outlined.FileUpload,
                        "导出软件设置",
                        "保存为文件，便于备份与迁移",
                        index = 0,
                        count = 2,
                        enabled = !backupBusy && !state.isLoading && !state.isSaving,
                        onClick = onExport,
                        trailingContent = { AboutChevron() }
                    )
                    ProfileListItem(
                        Icons.Outlined.FileDownload,
                        "导入软件设置",
                        "从备份文件恢复设置",
                        index = 1,
                        count = 2,
                        enabled = !backupBusy && !state.isLoading && !state.isSaving,
                        onClick = onImport,
                        trailingContent = { AboutChevron() }
                    )
                    AboutSectionTitle("日志")
                    logsContent()
                }
            }
            if (maxWidth >= 840.dp) {
                Row(
                    Modifier
                        .widthIn(max = 1040.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Box(Modifier.weight(0.85f)) { identity() }
                    Box(Modifier.weight(1.15f)) { tools() }
                }
            } else {
                Column(
                    Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    identity()
                    tools()
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清理图片缓存？", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "清理可重新获取的图片缓存，保留下载漫画、账号和全部设置。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmClear = false; onClearCache() },
                    enabled = !state.clearingCache
                ) {
                    Text("清理", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("取消", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}

@Composable
private fun AppIdentityCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                // 预览与实机共用前景资源，保留图标自身的留白与主题底色。
                Image(
                    painterResource(R.drawable.ic_launcher_foreground),
                    "应用图标",
                    Modifier.size(112.dp)
                )
            }
            Text(
                stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center
            )
            Text(
                "拷贝漫画第三方客户端", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
            )
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Text(
                if (BuildConfig.DEBUG) "调试版本" else "正式版本",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                BuildConfig.APPLICATION_ID, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AboutSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title, modifier.padding(horizontal = 8.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun AboutChevron() {
    Icon(
        Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Preview(name = "关于 · 手机", showBackground = true)
@Composable
private fun AboutPhonePreview() {
    CopyManga_NeoTheme { AboutPage() }
}

@Preview(name = "关于 · 深色平板", widthDp = 1100, heightDp = 850, showBackground = true)
@Composable
private fun AboutTabletPreview() {
    CopyManga_NeoTheme(darkTheme = true) { AboutPage() }
}
