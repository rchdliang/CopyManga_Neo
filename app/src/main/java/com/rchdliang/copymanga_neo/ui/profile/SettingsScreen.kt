package com.rchdliang.copymanga_neo.ui.profile

import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.MainActivity
import com.rchdliang.copymanga_neo.data.download.DownloadNotifications
import com.rchdliang.copymanga_neo.domain.model.AppThemeMode
import com.rchdliang.copymanga_neo.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.ui.reader.PageScale
import com.rchdliang.copymanga_neo.ui.reader.ReadingDirection
import com.rchdliang.copymanga_neo.ui.reader.ScreenOrientationSetting

@Composable
internal fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToNetwork: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toastHost = LocalToastHost.current
    var notifications by remember { mutableStateOf(NotificationSettingsStatus()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notifications = readNotificationSettings(context)
        viewModel.refreshStorage()
    }
    LaunchedEffect(state.message) {
        state.message?.let { toastHost.showToast(it); viewModel.clearMessage() }
    }
    SettingsPage(
        state = state,
        onThemeChange = viewModel::setThemeMode,
        onWifiOnlyChange = viewModel::setWifiOnlyDownloads,
        onNavigateBack = onNavigateBack,
        onNavigateToNetwork = onNavigateToNetwork,
        onNavigateToAbout = onNavigateToAbout,
        onDirectionChange = viewModel::setReadingDirection,
        onScaleChange = viewModel::setPageScale,
        onOrientationChange = viewModel::setScreenOrientation,
        onReaderReset = viewModel::resetReaderDefaults,
        onGlobalReset = viewModel::resetGlobalDefaults,
        onRefreshStorage = viewModel::refreshStorage,
        onManageDownloads = {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .setAction(DownloadNotifications.OPEN_DOWNLOADS)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        },
        notifications = notifications,
        onNotificationSettings = {
            if (!openNotificationSettings(context, it)) toastHost.showToast("无法打开系统通知设置")
        }
    )
}

private enum class SettingsPicker { THEME, DIRECTION, SCALE, ORIENTATION }
private enum class SettingsConfirmation { RESET_READER, RESET_GLOBAL }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsPage(
    state: SettingsUiState = SettingsUiState(isLoading = false),
    onThemeChange: (AppThemeMode) -> Unit = {},
    onWifiOnlyChange: (Boolean) -> Unit = {},
    onNavigateBack: () -> Unit,
    onNavigateToNetwork: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onDirectionChange: (String) -> Unit = {},
    onScaleChange: (String) -> Unit = {},
    onOrientationChange: (String) -> Unit = {},
    onReaderReset: () -> Unit = {},
    onGlobalReset: () -> Unit = {},
    onRefreshStorage: () -> Unit = {},
    onManageDownloads: () -> Unit = {},
    notifications: NotificationSettingsStatus = NotificationSettingsStatus(),
    onNotificationSettings: (NotificationSettingsTarget) -> Unit = {}
) {
    var picker by rememberSaveable { mutableStateOf<SettingsPicker?>(null) }
    var confirmation by rememberSaveable { mutableStateOf<SettingsConfirmation?>(null) }
    val context = LocalContext.current
    val enabled = !state.isLoading && !state.isSaving
    val reader = state.readerDefaults
    fun size(bytes: Long?) = bytes?.let { Formatter.formatShortFileSize(context, it) } ?: "正在统计"
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("设置", color = MaterialTheme.colorScheme.onSurface) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0)
        )
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.storageLoading,
            onRefresh = { if (!state.storageLoading && !state.clearingCache) onRefreshStorage() },
            state = pullState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = state.storageLoading,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(ProfileListDefaults.Gap)
            ) {
                SettingsSectionTitle("网络设置")
                ProfileListItem(
                    Icons.Outlined.Wifi,
                    "网络设置",
                    "线路、域名与代理",
                    index = 0, count = 1, onClick = onNavigateToNetwork
                )
                SettingsSectionTitle("外观与下载")
                ProfileListItem(
                    Icons.Outlined.Palette,
                    "主题",
                    state.settings.themeMode.label,
                    index = 0,
                    count = 2,
                    enabled = enabled,
                    onClick = { picker = SettingsPicker.THEME })
                ProfileListItem(
                    Icons.Outlined.Wifi, "仅 Wi-Fi 下载",
                    if (state.settings.wifiOnlyDownloads) "离开 Wi-Fi 后等待，重新连接后继续" else "允许使用当前网络下载",
                    index = 1, count = 2, enabled = enabled,
                    onClick = { onWifiOnlyChange(!state.settings.wifiOnlyDownloads) },
                    trailingContent = {
                        Switch(
                            checked = state.settings.wifiOnlyDownloads,
                            onCheckedChange = onWifiOnlyChange,
                            enabled = enabled,
                            modifier = Modifier.semantics { contentDescription = "仅 Wi-Fi 下载" })
                    })
                Text(
                    "下载队列会自动保存。重新打开应用后，可在下载页继续未完成的任务；已完整下载的章节会保留。",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SettingsSectionTitle("阅读默认值")
                Text(
                    "用于首次阅读的漫画。已阅读漫画保留自己的设置，可在阅读器内单独修改或恢复。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
                ProfileListItem(
                    Icons.Outlined.SwapHoriz,
                    "阅读方向",
                    ReadingDirection.entries.first { it.name == reader.readingDirection }.label,
                    index = 0,
                    count = 3,
                    enabled = enabled,
                    onClick = { picker = SettingsPicker.DIRECTION })
                ProfileListItem(
                    Icons.Outlined.AspectRatio,
                    "页面缩放",
                    PageScale.entries.first { it.name == reader.pageScale }.label,
                    index = 1,
                    count = 3,
                    enabled = enabled,
                    onClick = { picker = SettingsPicker.SCALE })
                ProfileListItem(
                    Icons.Outlined.ScreenRotation,
                    "屏幕方向",
                    ScreenOrientationSetting.fromName(reader.screenOrientation).label,
                    index = 2,
                    count = 3,
                    enabled = enabled,
                    onClick = { picker = SettingsPicker.ORIENTATION })
                TextButton(
                    onClick = { confirmation = SettingsConfirmation.RESET_READER },
                    enabled = enabled
                ) {
                    Text("恢复阅读默认值", color = MaterialTheme.colorScheme.primary)
                }

                SettingsSectionTitle("存储")
                ProfileListItem(
                    Icons.Outlined.Download, "漫画文件",
                    "${size(state.storageUsage?.downloadBytes)} · 管理已下载漫画与任务",
                    index = 0, count = 1, onClick = onManageDownloads
                )
                Text(
                    "设备可用空间：${size(state.storageUsage?.availableBytes)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(8.dp)
                )
                state.storageError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                SettingsSectionTitle("通知")
                ProfileListItem(
                    Icons.Outlined.Notifications,
                    "系统通知设置",
                    if (notifications.appEnabled) "已允许通知" else "通知已关闭，点击开启",
                    index = 0,
                    count = 4,
                    onClick = { onNotificationSettings(NotificationSettingsTarget.APP) })
                ProfileListItem(
                    Icons.Outlined.Download,
                    "下载进度通知",
                    if (notifications.progressEnabled) "已开启 · 管理进度通知显示" else "已关闭 · 点击管理",
                    index = 1,
                    count = 4,
                    onClick = { onNotificationSettings(NotificationSettingsTarget.PROGRESS) })
                ProfileListItem(
                    Icons.Outlined.NotificationsActive,
                    "下载完成提醒",
                    if (notifications.completionEnabled) "已开启 · 管理声音与振动" else "已关闭 · 点击管理",
                    index = 2,
                    count = 4,
                    onClick = { onNotificationSettings(NotificationSettingsTarget.COMPLETED) })
                ProfileListItem(
                    Icons.Outlined.NotificationsActive, "实时通知",
                    when {
                        !notifications.liveSupported -> "需要 Android 16 或更新版本"
                        !notifications.appEnabled || !notifications.progressEnabled -> "请先开启下载进度通知"
                        notifications.liveEnabled -> "系统已允许 · 管理状态栏胶囊"
                        else -> "系统未允许 · 点击管理"
                    },
                    index = 3, count = 4, enabled = notifications.liveSupported,
                    onClick = { onNotificationSettings(NotificationSettingsTarget.LIVE) })

                SettingsSectionTitle("关于")
                ProfileListItem(
                    Icons.Outlined.Info, "关于 CopyManga Neo",
                    "版本信息、缓存、日志与设置备份",
                    index = 0, count = 1, onClick = onNavigateToAbout
                )

                TextButton(
                    onClick = { confirmation = SettingsConfirmation.RESET_GLOBAL },
                    enabled = enabled,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("恢复全局默认值", color = MaterialTheme.colorScheme.primary)
                }
                state.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
    picker?.let { current ->
        val (title, options, selected) = when (current) {
            SettingsPicker.THEME -> Triple(
                "主题",
                AppThemeMode.entries.map { it.name to it.label },
                state.settings.themeMode.name
            )

            SettingsPicker.DIRECTION -> Triple(
                "默认阅读方向",
                ReadingDirection.entries.map { it.name to it.label },
                reader.readingDirection
            )

            SettingsPicker.SCALE -> Triple(
                "默认页面缩放",
                PageScale.entries.map { it.name to it.label },
                reader.pageScale
            )

            SettingsPicker.ORIENTATION -> Triple(
                "默认屏幕方向",
                ScreenOrientationSetting.entries.map { it.name to it.label },
                reader.screenOrientation
            )
        }
        AlertDialog(
            onDismissRequest = { picker = null },
            title = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(Modifier.selectableGroup()) {
                    options.forEach { (value, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selected == value,
                                    enabled = enabled,
                                    role = Role.RadioButton,
                                    onClick = {
                                        when (current) {
                                            SettingsPicker.THEME -> onThemeChange(
                                                AppThemeMode.valueOf(
                                                    value
                                                )
                                            )

                                            SettingsPicker.DIRECTION -> onDirectionChange(value)
                                            SettingsPicker.SCALE -> onScaleChange(value)
                                            SettingsPicker.ORIENTATION -> onOrientationChange(value)
                                        }
                                        picker = null
                                    }
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selected == value,
                                onClick = null,
                                enabled = enabled
                            )
                            Text(
                                label, color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picker = null }) {
                    Text("取消", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
    confirmation?.let { action ->
        val (title, message, button) = when (action) {
            SettingsConfirmation.RESET_READER -> Triple(
                "恢复阅读默认值？",
                "全局阅读默认值将恢复为从右到左、匹配屏幕、系统默认方向。各部漫画已保存的设置保持不变。",
                "恢复"
            )

            SettingsConfirmation.RESET_GLOBAL -> Triple(
                "恢复全局默认值？",
                "恢复主题为跟随系统、关闭仅 Wi-Fi 下载，并恢复全局阅读默认值。保留漫画独立设置、黑名单、下载与账号；系统通知设置由系统管理。",
                "恢复"
            )
        }
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    when (action) {
                        SettingsConfirmation.RESET_READER -> onReaderReset()
                        SettingsConfirmation.RESET_GLOBAL -> onGlobalReset()
                    }
                    confirmation = null
                }) { Text(button, color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }) {
                    Text(
                        "取消",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            })
    }
}

@Composable
private fun SettingsSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title, modifier = modifier.padding(horizontal = 8.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary
    )
}

private val AppThemeMode.label: String
    get() = when (this) {
        AppThemeMode.SYSTEM -> "跟随系统"
        AppThemeMode.LIGHT -> "浅色"
        AppThemeMode.DARK -> "深色"
    }
