package com.rchdliang.copymanga_neo.ui.profile

import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
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
    onNavigateToAbout: () -> Unit = {},
    onNavigateToData: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toast = LocalToastHost.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshStorage() }
    LaunchedEffect(state.message) {
        state.message?.let { toast.showToast(it); viewModel.clearMessage() }
    }
    SettingsPage(
        state = state,
        onThemeChange = viewModel::setThemeMode,
        onWifiOnlyChange = viewModel::setWifiOnlyDownloads,
        onNavigateBack = onNavigateBack,
        onNavigateToAbout = onNavigateToAbout,
        onNavigateToData = onNavigateToData,
        onDirectionChange = viewModel::setReadingDirection,
        onScaleChange = viewModel::setPageScale,
        onOrientationChange = viewModel::setScreenOrientation,
        onReaderReset = viewModel::resetReaderDefaults,
        onRefreshStorage = viewModel::refreshStorage,
        onManageDownloads = {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .setAction(DownloadNotifications.OPEN_DOWNLOADS)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        },
        onNotificationSettings = {
            if (!openNotificationSettings(context)) toast.showToast("无法打开系统通知设置")
        }
    )
}

internal enum class SettingsDestination { OVERVIEW, READING, DOWNLOADS }
private enum class SettingsPicker { THEME, DIRECTION, SCALE, ORIENTATION }

/** 首页只呈现分类；阅读和下载进入各自页面，通知直接交给系统设置。 */
@Composable
internal fun SettingsPage(
    state: SettingsUiState = SettingsUiState(isLoading = false),
    onThemeChange: (AppThemeMode) -> Unit = {},
    onWifiOnlyChange: (Boolean) -> Unit = {},
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit = {},
    onNavigateToData: () -> Unit = {},
    onDirectionChange: (String) -> Unit = {},
    onScaleChange: (String) -> Unit = {},
    onOrientationChange: (String) -> Unit = {},
    onReaderReset: () -> Unit = {},
    onRefreshStorage: () -> Unit = {},
    onManageDownloads: () -> Unit = {},
    onNotificationSettings: () -> Unit = {},
    initialDestination: SettingsDestination = SettingsDestination.OVERVIEW
) {
    var destination by rememberSaveable { mutableStateOf(initialDestination) }
    var picker by rememberSaveable { mutableStateOf<SettingsPicker?>(null) }
    var resetReader by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val enabled = !state.isLoading && !state.isSaving
    val reader = state.readerDefaults
    val direction =
        ReadingDirection.entries.firstOrNull { it.name == reader.readingDirection }?.label ?: "默认"
    val scale = PageScale.entries.firstOrNull { it.name == reader.pageScale }?.label ?: "默认"
    val orientation = ScreenOrientationSetting.fromName(reader.screenOrientation).label
    fun size(bytes: Long?) = bytes?.let { Formatter.formatShortFileSize(context, it) } ?: "正在统计"
    val goBack = {
        if (destination == SettingsDestination.OVERVIEW) onNavigateBack() else destination =
            SettingsDestination.OVERVIEW
    }
    BackHandler(enabled = destination != SettingsDestination.OVERVIEW) { goBack() }

    ProfileSettingsScaffold(
        title = when (destination) {
            SettingsDestination.OVERVIEW -> "设置"
            SettingsDestination.READING -> "阅读设置"
            SettingsDestination.DOWNLOADS -> "下载与存储"
        },
        onNavigateBack = goBack,
        isRefreshing = state.storageLoading,
        onRefresh = if (destination == SettingsDestination.DOWNLOADS) ({
            if (!state.storageLoading && !state.clearingCache) onRefreshStorage()
        }) else null
    ) {
        when (destination) {
            SettingsDestination.OVERVIEW -> {
                ProfileSectionTitle("外观")
                ProfileListItem(
                    Icons.Outlined.Palette,
                    "主题",
                    state.settings.themeMode.label,
                    index = 0,
                    count = 1,
                    enabled = enabled,
                    onClick = { picker = SettingsPicker.THEME })
                ProfileSectionTitle("阅读与下载")
                ProfileListItem(
                    Icons.AutoMirrored.Outlined.MenuBook, "阅读设置",
                    "$direction · $scale", index = 0, count = 2,
                    onClick = { destination = SettingsDestination.READING })
                ProfileListItem(
                    Icons.Outlined.Download, "下载与存储",
                    if (state.settings.wifiOnlyDownloads) "仅 Wi-Fi 下载 · 文件管理" else "下载偏好与文件管理",
                    index = 1, count = 2, onClick = { destination = SettingsDestination.DOWNLOADS })
                ProfileSectionTitle("管理")
                ProfileListItem(
                    Icons.Outlined.Storage, "数据管理", "图片缓存、设置备份与诊断日志",
                    index = 0, count = 3, onClick = onNavigateToData
                )
                ProfileListItem(
                    Icons.Outlined.Notifications, "系统通知设置", "前往 Android 系统设置",
                    index = 1, count = 3, onClick = onNotificationSettings
                )
                ProfileListItem(
                    Icons.Outlined.Info, "关于 CopyManga Neo", "版本与应用信息",
                    index = 2, count = 3, onClick = onNavigateToAbout
                )
            }

            SettingsDestination.READING -> {
                Text(
                    "应用于首次阅读的漫画。已阅读漫画保留各自的设置，可在阅读器中单独修改。",
                    Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                ProfileSectionTitle("阅读默认值")
                ProfileListItem(
                    Icons.Outlined.SwapHoriz,
                    "阅读方向",
                    direction,
                    index = 0,
                    count = 3,
                    enabled = enabled,
                    onClick = { picker = SettingsPicker.DIRECTION })
                ProfileListItem(
                    Icons.Outlined.AspectRatio,
                    "页面缩放",
                    scale,
                    index = 1,
                    count = 3,
                    enabled = enabled,
                    onClick = { picker = SettingsPicker.SCALE })
                ProfileListItem(
                    Icons.Outlined.ScreenRotation,
                    "屏幕方向",
                    orientation,
                    index = 2,
                    count = 3,
                    enabled = enabled,
                    onClick = { picker = SettingsPicker.ORIENTATION })
                ProfileSectionTitle("恢复")
                ProfileListItem(
                    Icons.Outlined.Restore, "恢复阅读默认值", "保留漫画已保存的独立设置",
                    index = 0, count = 1, enabled = enabled, onClick = { resetReader = true })
            }

            SettingsDestination.DOWNLOADS -> {
                ProfileSectionTitle("下载偏好")
                ProfileListItem(
                    Icons.Outlined.Wifi, "仅 Wi-Fi 下载",
                    if (state.settings.wifiOnlyDownloads) "等待 Wi-Fi 连接后继续任务" else "允许使用当前网络下载",
                    index = 0, count = 1, enabled = enabled,
                    onClick = { onWifiOnlyChange(!state.settings.wifiOnlyDownloads) },
                    trailingContent = {
                        Switch(
                            state.settings.wifiOnlyDownloads,
                            onWifiOnlyChange,
                            enabled = enabled
                        )
                    })
                ProfileSectionTitle("存储")
                ProfileListItem(
                    Icons.Outlined.Download, "管理下载文件",
                    "${size(state.storageUsage?.downloadBytes)} · 已下载漫画与任务",
                    index = 0, count = 1, onClick = onManageDownloads
                )
                Text(
                    "设备可用空间：${size(state.storageUsage?.availableBytes)}",
                    Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "下载队列会自动保存。重新打开应用后，可在下载页继续未完成的任务。",
                    Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                state.storageError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
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
                            RadioButton(selected == value, onClick = null, enabled = enabled)
                            Text(
                                label,
                                Modifier.padding(start = 16.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picker = null }) {
                    Text(
                        "取消",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
    }
    if (resetReader) AlertDialog(
        onDismissRequest = { resetReader = false },
        title = { Text("恢复阅读默认值？", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Text(
                "恢复为从右到左、匹配屏幕和系统默认方向，各部漫画已保存的设置保持不变。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = { resetReader = false; onReaderReset() }, enabled = enabled) {
                Text("恢复", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = { resetReader = false }) {
                Text(
                    "取消",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

private val AppThemeMode.label: String
    get() = when (this) {
        AppThemeMode.SYSTEM -> "跟随系统"
        AppThemeMode.LIGHT -> "浅色"
        AppThemeMode.DARK -> "深色"
    }
