package com.rchdliang.copymanga_neo.ui.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CommentsDisabled
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.ReaderBookmark
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import kotlin.math.roundToInt

@Composable
fun ReaderTopBar(
    chapterName: String, bookmarked: Boolean, onBack: () -> Unit,
    onBookmark: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier
) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                chapterName,
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onBookmark) {
                Icon(
                    if (bookmarked) Icons.Filled.BookmarkAdded else Icons.Outlined.BookmarkAdd,
                    if (bookmarked) "移除本页书签" else "添加本页书签",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Default.Settings,
                    "阅读设置",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** 每一页对应一个吸附点；拖动只预览，松手后一次跳转。 */
@Composable
fun ReaderBottomBar(
    totalPages: Int, sliderValue: Float,
    onSliderValueChange: (Float) -> Unit, onSliderValueChangeFinished: () -> Unit,
    onPrevious: () -> Unit, onNext: () -> Unit, onCatalog: () -> Unit,
    onBookmarks: () -> Unit, onComments: () -> Unit, commentsEnabled: Boolean,
    commentCount: Int?,
    autoRunning: Boolean, onAuto: () -> Unit, modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val sliderState = remember(totalPages) {
        SliderState(
            value = sliderValue,
            steps = (totalPages - 2).coerceAtLeast(0),
            trackRange = 0f..maxOf(totalPages - 1, 1).toFloat()
        )
    }
    LaunchedEffect(sliderValue) { sliderState.value = sliderValue }
    Surface(
        modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 3.dp
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    (sliderValue.roundToInt() + 1).coerceAtMost(totalPages).toString(),
                    modifier = Modifier.widthIn(min = 28.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    state = sliderState, enabled = totalPages > 1,
                    modifier = Modifier.weight(1f),
                    onValueChange = { value ->
                        val page = value.roundToInt().coerceIn(0, (totalPages - 1).coerceAtLeast(0))
                        if (page != sliderValue.roundToInt()) haptic.performHapticFeedback(
                            HapticFeedbackType.SegmentFrequentTick
                        )
                        onSliderValueChange(page.toFloat())
                    }, onValueChangeFinished = onSliderValueChangeFinished
                )
                Text(
                    totalPages.toString(),
                    modifier = Modifier.widthIn(min = 28.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ReaderControlButton(Icons.Default.SkipPrevious, "上一章", onPrevious)
                ReaderControlButton(Icons.AutoMirrored.Filled.List, "目录", onCatalog)
                ReaderControlButton(
                    if (autoRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (autoRunning) "暂停" else "自动", onAuto
                )
                ReaderControlButton(Icons.Default.Bookmarks, "书签", onBookmarks)
                // 角标作为按钮的兄弟组件，避免被 IconButton 的圆形边界裁切。
                Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    IconButton(
                        onClick = onComments, enabled = commentsEnabled,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            if (commentsEnabled) Icons.AutoMirrored.Filled.Comment else Icons.Filled.CommentsDisabled,
                            if (!commentsEnabled) "章节评论已关闭" else commentCount?.let { "章节评论，共 $it 条" }
                                ?: "章节评论",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (commentsEnabled) 1f else 0.38f)
                        )
                    }
                    if (commentsEnabled && commentCount != null) Badge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Text(
                            commentCount.coerceAtLeast(0).toString(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                ReaderControlButton(Icons.Default.SkipNext, "下一章", onNext)
            }
        }
    }
}

@Composable
private fun ReaderControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    action: () -> Unit
) {
    IconButton(onClick = action) {
        Icon(
            icon,
            label,
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ReaderToolsOptions(
    settings: ReaderToolsSettings,
    onChange: ((ReaderToolsSettings) -> ReaderToolsSettings) -> Unit
) {
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text(
        "阅读体验（全局）",
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall
    )
    ReaderSettingSwitch(
        "章末显示章节评论",
        settings.inlineComments
    ) { enabled -> onChange { it.copy(inlineComments = enabled) } }
    ReaderSettingSwitch(
        "跨章节连续阅读",
        settings.continuousChapters
    ) { enabled -> onChange { it.copy(continuousChapters = enabled) } }
    ReaderSettingSwitch("音量键翻页", settings.volumeKeys) { enabled ->
        onChange {
            it.copy(
                volumeKeys = enabled
            )
        }
    }
    Text(
        "自动阅读间隔：${settings.autoIntervalSeconds} 秒",
        color = MaterialTheme.colorScheme.onSurface
    )
    ReaderChoiceRow(
        listOf(3, 5, 8, 12, 20),
        settings.autoIntervalSeconds,
        "秒"
    ) { seconds -> onChange { it.copy(autoIntervalSeconds = seconds) } }
    Text(
        "图片失败自动重试：${settings.imageRetries} 次",
        color = MaterialTheme.colorScheme.onSurface
    )
    ReaderChoiceRow(listOf(0, 1, 2, 3), settings.imageRetries, "次") { count ->
        onChange {
            it.copy(
                imageRetries = count
            )
        }
    }
    Text("图片超时：${settings.imageTimeoutSeconds} 秒", color = MaterialTheme.colorScheme.onSurface)
    ReaderChoiceRow(
        listOf(10, 15, 30, 60),
        settings.imageTimeoutSeconds,
        "秒"
    ) { seconds -> onChange { it.copy(imageTimeoutSeconds = seconds) } }
    Text("向前预加载：${settings.preloadPages} 页", color = MaterialTheme.colorScheme.onSurface)
    ReaderChoiceRow(listOf(0, 1, 2, 3), settings.preloadPages, "页") { count ->
        onChange {
            it.copy(
                preloadPages = count
            )
        }
    }
}

@Composable
private fun ReaderSettingSwitch(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(checked = checked, onCheckedChange = change)
    }
}

@Composable
private fun ReaderChoiceRow(
    choices: List<Int>,
    selected: Int,
    unit: String,
    change: (Int) -> Unit
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        choices.forEach { value ->
            FilterChip(
                selected = value == selected, onClick = { change(value) },
                label = {
                    Text(
                        "$value$unit",
                        color = if (value == selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                })
        }
    }
}

@Composable
fun ReaderCatalogDialog(
    state: ReaderCatalogUiState, current: String, onSelect: (String) -> Unit,
    onRetry: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("章节目录", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { message ->
                    TextButton(onClick = onRetry) {
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                LazyColumn(Modifier.heightIn(max = 440.dp)) {
                    items(state.chapters, key = { it.uuid }) { chapter ->
                        TextButton(onClick = { onSelect(chapter.uuid) }) {
                            Text(
                                chapter.name,
                                color = if (chapter.uuid == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "关闭",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        })
}

@Composable
fun ReaderBookmarksDialog(
    bookmarks: List<ReaderBookmark>,
    onSelect: (ReaderBookmark) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本漫画的书签", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            if (bookmarks.isEmpty()) Text(
                "还没有书签，点击顶栏书签图标收藏当前页。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else LazyColumn(Modifier.heightIn(max = 440.dp)) {
                items(bookmarks, key = { "${it.chapterUuid}:${it.page}" }) { mark ->
                    TextButton(onClick = { onSelect(mark) }) {
                        Text(
                            "${mark.chapterName} · 第 ${mark.page + 1} 页",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "关闭",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        })
}
