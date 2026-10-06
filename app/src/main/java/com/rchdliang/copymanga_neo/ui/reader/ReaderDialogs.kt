package com.rchdliang.copymanga_neo.ui.reader

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rchdliang.copymanga_neo.ui.components.LocalToastHost
import kotlinx.coroutines.CancellationException
import java.io.File

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
    extraSettings: @Composable () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "本漫画的阅读设置",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                leadingSettings()
                Text(
                    "修改只作用于这部漫画。恢复默认值会采用当前全局阅读设置。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                PreferenceSection("屏幕方向") {
                    ScreenOrientationSetting.entries.forEach { option ->
                        FilterChip(
                            onClick = { onScreenOrientationChange(option) },
                            label = {
                                Text(
                                    option.label,
                                    color = if (screenOrientation == option) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            selected = screenOrientation == option
                        )
                    }
                }
                PreferenceSection("阅读方向") {
                    ReadingDirection.entries.forEach { option ->
                        FilterChip(
                            onClick = { onReadingDirectionChange(option) },
                            label = {
                                Text(
                                    option.label,
                                    color = if (readingDirection == option) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            selected = readingDirection == option
                        )
                    }
                }
                PreferenceSection("页面缩放") {
                    PageScale.entries.forEach { option ->
                        FilterChip(
                            onClick = { onPageScaleChange(option) },
                            label = {
                                Text(
                                    option.label,
                                    color = if (pageScale == option) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            selected = pageScale == option,
                        )
                    }
                }
                extraSettings()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "确定",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onResetPreferences) {
                Text(
                    "恢复默认值",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}

@Composable
private fun PreferenceSection(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
    Spacer(modifier = Modifier.height(12.dp))
}


@Composable
fun ReaderImageActionDialog(
    title: String,
    currentImageUrl: String?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: ReaderImageActionsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val toastHost = LocalToastHost.current
    val state by viewModel.uiState.collectAsState()
    val currentDismiss by rememberUpdatedState(onDismiss)
    val saveAsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            viewModel.saveDocument(result.data?.data.takeIf { result.resultCode == Activity.RESULT_OK })
        }
    LaunchedEffect(state.pending) {
        val pending = viewModel.claimPending() ?: return@LaunchedEffect
        try {
            when (pending.action) {
                ReaderImageAction.SAVE_AS -> saveAsLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = pending.image.mimeType
                    putExtra(Intent.EXTRA_TITLE, pending.image.displayName)
                })

                ReaderImageAction.SHARE -> {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.reader.images",
                        File(pending.image.filePath)
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = pending.image.mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData =
                            ClipData.newUri(context.contentResolver, pending.image.displayName, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "分享图片").apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                    viewModel.shareLaunched()
                    currentDismiss()
                }

                ReaderImageAction.SAVE -> Unit
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w("ReaderImageActions", "无法打开保存或分享界面", error)
            viewModel.launchFailed("无法打开保存或分享界面，请检查是否有可用的应用")
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            toastHost.showToast(it)
            val close = state.closeAfterMessage
            viewModel.clearMessage()
            if (close) currentDismiss()
        }
    }

    AlertDialog(
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
