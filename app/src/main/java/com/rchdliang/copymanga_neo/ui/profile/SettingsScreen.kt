package com.rchdliang.copymanga_neo.ui.profile

import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSliderState
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
import androidx.compose.ui.text.input.KeyboardType
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
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

@Composable
internal fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit = {},
    onNavigateToData: () -> Unit = {},
    embedded: Boolean = false,
    overviewLayout: (@Composable (
        appPreferences: @Composable () -> Unit
    ) -> Unit)? = null,
    initialDestination: SettingsDestination = SettingsDestination.OVERVIEW,
    onOpenDestination: ((SettingsDestination) -> Unit)? = null,
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
        overviewLayout = overviewLayout,
        embedded = embedded,
        initialDestination = initialDestination,
        onOpenDestination = onOpenDestination,
        state = state,
        onThemeChange = viewModel::setThemeMode,
        onWifiOnlyChange = viewModel::setWifiOnlyDownloads,
        onNovelIllustrationBlurChange = viewModel::setBlurNovelIllustrationPreviews,
        onImageConcurrencyChange = viewModel::setComicImageConcurrency,
        onDownloadLimitChange = viewModel::setComicDownloadLimitKiB,
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
    onNovelIllustrationBlurChange: (Boolean) -> Unit = {},
    onImageConcurrencyChange: (Int) -> Unit = {},
    onDownloadLimitChange: (Int) -> Unit = {},
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
    initialDestination: SettingsDestination = SettingsDestination.OVERVIEW,
    embedded: Boolean = false,
    overviewLayout: (@Composable (
        appPreferences: @Composable () -> Unit
    ) -> Unit)? = null,
    onOpenDestination: ((SettingsDestination) -> Unit)? = null
) {
    var destination by rememberSaveable { mutableStateOf(initialDestination) }
    var picker by rememberSaveable { mutableStateOf<SettingsPicker?>(null) }
    var resetReader by rememberSaveable { mutableStateOf(false) }
    var downloadNumber by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val enabled = !state.isLoading && !state.isSaving
    val reader = state.readerDefaults
    val direction =
        ReadingDirection.entries.firstOrNull { it.name == reader.readingDirection }?.label ?: "默认"
    val scale = PageScale.entries.firstOrNull { it.name == reader.pageScale }?.label ?: "默认"
    val orientation = ScreenOrientationSetting.fromName(reader.screenOrientation).label
    fun size(bytes: Long?) = bytes?.let { Formatter.formatShortFileSize(context, it) } ?: "正在统计"
    val goBack = {
        if (onOpenDestination != null || destination == SettingsDestination.OVERVIEW) onNavigateBack() else destination =
            SettingsDestination.OVERVIEW
    }
    BackHandler(enabled = onOpenDestination == null && destination != SettingsDestination.OVERVIEW) { goBack() }

    val content: @Composable ColumnScope.() -> Unit = {
        when (destination) {
            SettingsDestination.OVERVIEW -> {
                val appPreferences: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(ProfileListDefaults.Gap)) {
                        ProfileSectionTitle("阅读与下载")
                        ProfileListItem(
                            Icons.AutoMirrored.Outlined.MenuBook, "阅读设置",
                            "$direction · $scale", index = 0, count = 2,
                            onClick = {
                                onOpenDestination?.invoke(SettingsDestination.READING)
                                    ?: run { destination = SettingsDestination.READING }
                            })
                        ProfileListItem(
                            Icons.Outlined.Download,
                            "下载与存储",
                            if (state.settings.wifiOnlyDownloads) "仅 Wi-Fi 下载 · 文件管理" else "下载偏好与文件管理",
                            index = 1,
                            count = 2,
                            onClick = {
                                onOpenDestination?.invoke(SettingsDestination.DOWNLOADS)
                                    ?: run { destination = SettingsDestination.DOWNLOADS }
                            })
                        ProfileSectionTitle("外观")
                        ProfileListItem(
                            Icons.Outlined.Palette,
                            "主题",
                            state.settings.themeMode.label,
                            index = 0,
                            count = 1,
                            enabled = enabled,
                            onClick = { picker = SettingsPicker.THEME })
                        ProfileSectionTitle("应用管理")
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
                        state.error?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
                if (overviewLayout != null) {
                    overviewLayout(appPreferences)
                } else {
                    appPreferences()
                }
            }

            SettingsDestination.READING -> {
                Text(
                    "漫画默认值仅用于首次打开的漫画；轻小说插图设置对未单独切换的小说生效。",
                    Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                ProfileSectionTitle("漫画阅读默认值")
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
                ProfileSectionTitle("轻小说插图")
                ProfileListItem(
                    Icons.Outlined.VisibilityOff, "默认模糊插图预览",
                    "防止剧透；全屏查看始终清晰，单本小说可单独切换",
                    index = 0, count = 1, enabled = enabled,
                    onClick = { onNovelIllustrationBlurChange(!state.settings.blurNovelIllustrationPreviews) },
                    trailingContent = {
                        Switch(
                            checked = state.settings.blurNovelIllustrationPreviews,
                            onCheckedChange = onNovelIllustrationBlurChange,
                            enabled = enabled
                        )
                    }
                )
                ProfileSectionTitle("恢复")
                ProfileListItem(
                    Icons.Outlined.Restore, "恢复阅读默认值", "仅恢复全局默认值",
                    index = 0, count = 1, enabled = enabled, onClick = { resetReader = true })
            }

            SettingsDestination.DOWNLOADS -> {
                ProfileSectionTitle("下载偏好")
                ProfileListItem(
                    Icons.Outlined.Wifi, "仅 Wi-Fi 下载",
                    if (state.settings.wifiOnlyDownloads) "等待 Wi-Fi 连接后继续任务" else "允许使用当前网络下载",
                    index = 0, count = 3, enabled = enabled,
                    onClick = { onWifiOnlyChange(!state.settings.wifiOnlyDownloads) },
                    trailingContent = {
                        Switch(
                            state.settings.wifiOnlyDownloads,
                            onWifiOnlyChange,
                            enabled = enabled
                        )
                    })
                ProfileListItem(
                    Icons.Outlined.Download, "漫画图片并发数",
                    "${state.settings.comicImageConcurrency} 张",
                    index = 1, count = 3, enabled = enabled,
                    onClick = { downloadNumber = "concurrency" }
                )
                ProfileListItem(
                    Icons.Outlined.Download, "漫画图片总限速",
                    if (state.settings.comicDownloadLimitKiB == 0) "不限速" else
                        "${formatDownloadLimitMiB(state.settings.comicDownloadLimitKiB)} MiB/s",
                    index = 2, count = 3, enabled = enabled,
                    onClick = { downloadNumber = "bandwidth" }
                )
                Text(
                    "仅影响漫画图片，限速由并行下载共享。",
                    Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
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
                state.storageError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
        if (destination != SettingsDestination.OVERVIEW) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
            }
        }
    }

    if (embedded) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ProfileListDefaults.Gap),
            content = content
        )
    } else {
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
            }) else null,
            content = content
        )
    }

    downloadNumber?.let { kind ->
        if (kind == "concurrency") {
            DownloadConcurrencyDialog(
                initialValue = state.settings.comicImageConcurrency,
                enabled = enabled,
                onDismiss = { downloadNumber = null },
                onSave = {
                    onImageConcurrencyChange(it)
                    downloadNumber = null
                }
            )
        } else {
            DownloadLimitDialog(
                initialValue = state.settings.comicDownloadLimitKiB,
                enabled = enabled,
                onDismiss = { downloadNumber = null },
                onSave = {
                    onDownloadLimitChange(it)
                    downloadNumber = null
                }
            )
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
                "已阅读漫画的设置不变。",
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

@Composable
private fun DownloadLimitDialog(
    initialValue: Int,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var input by rememberSaveable {
        mutableStateOf(
            if (initialValue == 0) "" else formatDownloadLimitMiB(
                initialValue
            )
        )
    }
    val value = parseDownloadLimitKiB(input, initialValue)
    val valid = value != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("漫画图片总限速", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    enabled = enabled,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    label = {
                        Text(
                            "MiB/s",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    placeholder = {
                        Text(
                            "不限速",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                Text(
                    if (valid) "留空不限速，保存后生效。" else "请输入有效的正数，或留空。",
                    Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onSave) }, enabled = enabled && valid) {
                Text(
                    "保存",
                    color = if (enabled && valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "取消",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadConcurrencyDialog(
    initialValue: Int,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var value by rememberSaveable { mutableStateOf(initialValue.coerceIn(1, 10).toFloat()) }
    val slider = rememberSliderState(value = value, trackRange = 1f..10f, steps = 8)
    slider.value = value
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("漫画图片并发数", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Text(
                    "${value.roundToInt()} 张",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    state = slider,
                    onValueChange = {
                        value = it.roundToInt().toFloat()
                        slider.value = value
                    },
                    enabled = enabled
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    (1..10).forEach { tick ->
                        Text(
                            tick.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    "下一章节或暂停后继续时生效。",
                    Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.roundToInt()) }, enabled = enabled) {
                Text(
                    "保存",
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "取消",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

/** 展示使用 MiB/s，保留已有 KiB/s 偏好的精度与持久化格式。 */
private fun formatDownloadLimitMiB(limitKiB: Int): String =
    BigDecimal(limitKiB).divide(BigDecimal(1024), 3, RoundingMode.HALF_UP).stripTrailingZeros()
        .toPlainString()

private fun parseDownloadLimitKiB(input: String, initialValue: Int? = null): Int? {
    val normalized = input.trim().replace(',', '.')
    if (normalized.isEmpty()) return 0
    if (normalized.length > 24 || !normalized.matches(Regex("[0-9]*([.][0-9]{0,3})?"))) return null
    if (initialValue != null && initialValue > 0 && normalized == formatDownloadLimitMiB(
            initialValue
        )
    ) return initialValue
    val kib = normalized.toBigDecimalOrNull()?.multiply(BigDecimal(1024)) ?: return null
    if (kib <= BigDecimal.ZERO || kib > BigDecimal(Int.MAX_VALUE)) return null
    return kib.setScale(0, RoundingMode.HALF_UP).toInt().takeIf { it > 0 }
}

private val AppThemeMode.label: String
    get() = when (this) {
        AppThemeMode.SYSTEM -> "跟随系统"
        AppThemeMode.LIGHT -> "浅色"
        AppThemeMode.DARK -> "深色"
    }
