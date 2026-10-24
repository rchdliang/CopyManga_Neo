package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import com.rchdliang.copymanga_neo.multiplatform.ui.components.ActiveRouteLaunchedEffect
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiLog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalToastHost
import kotlinx.coroutines.CancellationException

@Composable
fun ReaderSettingsDialog(
    readingDirection: ReadingDirection,
    pageScale: PageScale,
    screenOrientation: ScreenOrientationSetting,
    onReadingDirectionChange: (ReadingDirection) -> Unit,
    onPageScaleChange: (PageScale) -> Unit,
    onScreenOrientationChange: (ScreenOrientationSetting) -> Unit,
    onDismiss: () -> Unit,
    onResetPreferences: () -> Unit = {},
    leadingSettings: @Composable () -> Unit = {},
    turnSettings: @Composable () -> Unit = {},
    loadingSettings: @Composable () -> Unit = {},
    pairingSettings: @Composable () -> Unit = {},
    helpSettings: @Composable () -> Unit = {},
    qualitySettings: (@Composable () -> Unit)? = null
) {
    var pages by remember { mutableStateOf<List<ReaderSettingsPage>>(emptyList()) }
    val section = pages.lastOrNull()
    val navigate: (ReaderSettingsPage?) -> Unit = { page ->
        pages = if (page == null) pages.dropLast(1) else pages + page
    }
    val back = { readerSettingsBack(pages.isNotEmpty(), { navigate(null) }, onDismiss) }
    // 子页始终消费最新状态，避免点击入口时捕获旧的设置快照。
    val displayContent by rememberUpdatedState<@Composable () -> Unit> {
        ReaderDisplaySettings(readingDirection, pageScale, screenOrientation,
            onReadingDirectionChange, onPageScaleChange, onScreenOrientationChange, onResetPreferences, leadingSettings)
    }
    val turns by rememberUpdatedState(turnSettings)
    val loading by rememberUpdatedState(loadingSettings)
    val quality by rememberUpdatedState(qualitySettings)
    if (LocalRouteActive.current) AlertDialog(
        onDismissRequest = back,
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.94f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(section?.title ?: "阅读设置", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            CompositionLocalProvider(LocalReaderSettingsNavigation provides navigate) {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (section != null) section.content() else {
                        ReaderSettingsOverview(qualitySettings != null,
                            display = { navigate(ReaderSettingsPage("显示设置") { displayContent() }) },
                            turns = { navigate(ReaderSettingsPage("翻页设置") { turns() }) },
                            quality = { navigate(ReaderSettingsPage("画质增强") { quality?.invoke() }) },
                            loading = { navigate(ReaderSettingsPage("图片加载") { loading() }) },
                            pairing = pairingSettings, help = helpSettings)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = back) {
                Text(if (section == null) "完成" else "返回", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
fun ReaderImageActionDialog(
    title: String,
    currentImageUrl: String?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: ReaderImageActionsViewModel,
    platform: ReaderPlatform
) {
    val toastHost = LocalToastHost.current
    val state by viewModel.uiState.collectAsState()
    val currentDismiss by rememberUpdatedState(onDismiss)
    ActiveRouteLaunchedEffect(state.pending, viewModel, platform) {
        viewModel.launchPending(platform)
    }
    ActiveRouteLaunchedEffect(state.dismissRequested) {
        if (state.dismissRequested) {
            viewModel.consumeDismissRequest()
            currentDismiss()
        }
    }
    ActiveRouteLaunchedEffect(state.message) {
        state.message?.let {
            toastHost.showToast(it)
            val close = state.closeAfterMessage
            viewModel.clearMessage()
            if (close) currentDismiss()
        }
    }

    if (LocalRouteActive.current) AlertDialog(
        onDismissRequest = { viewModel.cancel(); onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !state.busy,
            dismissOnClickOutside = !state.busy
        ),
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                ActionRow(
                    icon = Icons.Default.Refresh,
                    label = "刷新",
                    enabled = !state.busy,
                    onClick = onRefresh
                )
                ActionRow(
                    icon = Icons.Default.Share,
                    label = "分享图片",
                    enabled = !state.busy && currentImageUrl != null
                ) {
                    viewModel.perform(ReaderImageAction.SHARE, currentImageUrl, title)
                }
                ActionRow(
                    icon = Icons.Default.Save,
                    label = "保存到相册",
                    enabled = !state.busy && currentImageUrl != null
                ) {
                    viewModel.perform(ReaderImageAction.SAVE, currentImageUrl, title)
                }
                ActionRow(
                    icon = Icons.Default.SaveAlt,
                    label = "另存为…",
                    enabled = !state.busy && currentImageUrl != null
                ) {
                    viewModel.perform(ReaderImageAction.SAVE_AS, currentImageUrl, title)
                }
                if (state.busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        "正在准备或保存图片…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { viewModel.cancel(); onDismiss() }) {
                Text("取消", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
        )
    }
}
