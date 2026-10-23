package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.ui.text.input.KeyboardType
import kotlin.math.roundToInt

import com.rchdliang.copymanga_neo.multiplatform.ui.components.ActiveRouteLaunchedEffect
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.model.AppThemeMode
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.PageScale
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.ReadingDirection
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.ScreenOrientationSetting

@Composable
internal fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit = {},
    onNavigateToData: () -> Unit = {},
    viewModel: SettingsViewModel,
    onManageDownloads: () -> Unit,
    initialDestination: SettingsDestination = SettingsDestination.OVERVIEW,
    embedded: Boolean = false,
    overviewLayout: (@Composable (appPreferences: @Composable () -> Unit) -> Unit)? = null,
    onOpenDestination: ((SettingsDestination) -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast = LocalToastHost.current
    val platform = requireProfilePlatform()
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshStorage() }
    ActiveRouteLaunchedEffect(state.message) {
        state.message?.let { toast.showToast(it); viewModel.clearMessage() }
    }
    SettingsPage(
        overviewLayout = overviewLayout,
        embedded = embedded,
        initialDestination = initialDestination,
        onOpenDestination = onOpenDestination,
        state = state,
        onThemeChange = viewModel::setThemeMode,
        onSimplifyChineseChange = viewModel::setSimplifyChinese,
        onDictionaryUpdate = viewModel::updateChineseDictionary,
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
        onManageDownloads = onManageDownloads,
        notificationSettingsUnavailableReason = platform.notificationSettingsUnavailableReason,
        onNotificationSettings = {
            scope.launch {
                platform.openNotificationSettings().onFailure {
                    toast.showToast(it.message ?: "无法打开系统通知设置")
                }
            }
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
    onSimplifyChineseChange: (Boolean) -> Unit = {},
    onDictionaryUpdate: () -> Unit = {},
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
    notificationSettingsUnavailableReason: String? = null,
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
    val enabled = !state.isLoading && !state.isSaving
    val reader = state.readerDefaults
    val direction =
        ReadingDirection.entries.firstOrNull { it.name == reader.readingDirection }?.label ?: "默认"
    val scale = PageScale.entries.firstOrNull { it.name == reader.pageScale }?.label ?: "默认"
    val orientation = ScreenOrientationSetting.fromName(reader.screenOrientation).label
    fun size(bytes: Long?) = bytes?.let { formatStorageBytes(it) } ?: "正在统计"
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
                            count = 2,
                            enabled = enabled,
                            onClick = { picker = SettingsPicker.THEME })
                        ProfileListItem(
                            Icons.Outlined.SwapHoriz, "繁体简化",
                            "由OpenCC提供简化能力",
                            index = 1, count = 2, enabled = enabled,
                            onClick = { onSimplifyChineseChange(!state.settings.simplifyChinese || !state.dictionaryReady) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = onDictionaryUpdate,
                                        enabled = enabled && !state.dictionaryWorking && !state.isDictionaryDownloading
                                    ) {
                                        if (state.dictionaryWorking || state.isDictionaryDownloading) {
                                            CircularProgressIndicator(
                                                Modifier.size(20.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else Icon(
                                            Icons.Outlined.Refresh,
                                            contentDescription = state.dictionaryVersion?.let { "更新词典，当前版本 $it" }
                                                ?: "下载或更新词典",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = state.settings.simplifyChinese,
                                        onCheckedChange = onSimplifyChineseChange,
                                        enabled = enabled
                                    )
                                }
                            }
                        )
                        ProfileSectionTitle("应用管理")
                        ProfileListItem(
                            Icons.Outlined.Storage, "数据管理", "图片缓存、设置备份与诊断日志",
                            index = 0, count = 3, onClick = onNavigateToData
                        )
                        ProfileListItem(
                            Icons.Outlined.Notifications,
                            "系统通知设置",
                            notificationSettingsUnavailableReason ?: "前往系统通知设置",
                            index = 1,
                            count = 3,
                            onClick = onNotificationSettings,
                            enabled = notificationSettingsUnavailableReason == null
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
                    "漫画：只用于新打开的作品。轻小说：未单独设置的作品沿用此项。",
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
                    "预览防剧透；全屏清晰，可按作品调整。",
                    index = 0, count = 1, enabled = enabled,
                    onClick = { onNovelIllustrationBlurChange(!state.settings.blurNovelIllustrationPreviews) },
                    trailingContent = {
                        Switch(
                            checked = state.settings.blurNovelIllustrationPreviews,
                            onCheckedChange = onNovelIllustrationBlurChange, enabled = enabled
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
                    Icons.Outlined.Download, "漫画下载总限速",
                    if (state.settings.comicDownloadLimitKiB == 0) "不限速" else
                        "${formatDownloadLimitMiB(state.settings.comicDownloadLimitKiB)} MiB/s",
                    index = 2, count = 3, enabled = enabled,
                    onClick = { downloadNumber = "bandwidth" }
                )
                Text(
                    "所有图片下载共用此上限。",
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
        if (LocalRouteActive.current) AlertDialog(
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
    if (LocalRouteActive.current) AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("漫画下载总限速", color = MaterialTheme.colorScheme.onSurface) },
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
    if (LocalRouteActive.current) AlertDialog(
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
                    value = value, valueRange = 1f..10f, steps = 8,
                    onValueChange = { value = it.roundToInt().toFloat() },
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
internal fun formatDownloadLimitMiB(limitKiB: Int): String {
    val rounded = (limitKiB.toLong() * 1000 + 512) / 1024
    val whole = rounded / 1000
    val remainder = rounded % 1000
    return if (remainder == 0L) whole.toString() else
        "$whole.${remainder.toString().padStart(3, '0').trimEnd('0')}"
}

/** 使用十进制逐位乘法，所有平台保持 HALF_UP，避免浮点在边界处舍入不同。 */
internal fun parseDownloadLimitKiB(input: String, initialValue: Int? = null): Int? {
    val normalized = input.trim().replace(',', '.')
    if (normalized.isEmpty()) return 0
    if (normalized.length > 24 || !normalized.matches(Regex("[0-9]*([.][0-9]{0,3})?"))) return null
    if (initialValue != null && initialValue > 0 && normalized == formatDownloadLimitMiB(
            initialValue
        )
    ) return initialValue
    val digits = normalized.replace(".", "")
    if (digits.isEmpty()) return null
    val decimals = if ('.' in normalized) normalized.substringAfter('.').length else 0
    var carry = 0
    val reversed = StringBuilder()
    for (digit in digits.reversed()) {
        val product = digit.digitToInt() * 1024 + carry
        reversed.append(product % 10); carry = product / 10
    }
    while (carry > 0) {
        reversed.append(carry % 10); carry /= 10
    }
    val scaled = reversed.reverse().toString().padStart(decimals + 1, '0')
    val whole =
        scaled.dropLast(decimals).trimStart('0').ifEmpty { "0" }.toLongOrNull() ?: return null
    val fraction = scaled.takeLast(decimals)
    if (whole > Int.MAX_VALUE || whole == Int.MAX_VALUE.toLong() && fraction.any { it != '0' }) return null
    val rounded = whole + if (fraction.firstOrNull()?.let { it >= '5' } == true) 1 else 0
    return rounded.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
}

private val AppThemeMode.label: String
    get() = when (this) {
        AppThemeMode.SYSTEM -> "跟随系统"
        AppThemeMode.LIGHT -> "浅色"
        AppThemeMode.DARK -> "深色"
    }
