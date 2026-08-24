package com.rchdliang.copymanga_neo.ui.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.data.download.DownloadProgressInfo
import com.rchdliang.copymanga_neo.data.download.DownloadTaskStatus
import com.rchdliang.copymanga_neo.domain.model.DownloadedComicInfo
import com.rchdliang.copymanga_neo.ui.components.ComicCard
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme

@Composable
fun DownloadsScreen(
    onComicClick: (String) -> Unit = {},
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DownloadsContent(
        uiState = uiState,
        onComicClick = onComicClick,
        onExitSelectionMode = viewModel::exitSelectionMode,
        onSelectAll = viewModel::selectAll,
        onInvertSelection = viewModel::invertSelection,
        onPause = viewModel::pauseComic,
        onResume = viewModel::resumeComic,
        onCancel = viewModel::cancelComic,
        onToggleSelection = viewModel::toggleSelection,
        onEnterSelectionMode = viewModel::enterSelectionMode,
        onDeleteSelected = viewModel::deleteSelected
    )
}

/**
 * 下载页纯 UI 内容，Preview 可直接传入下载状态而无需初始化数据库和 Hilt。
 */
@Composable
private fun DownloadsContent(
    uiState: DownloadsUiState,
    onComicClick: (String) -> Unit,
    onExitSelectionMode: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onEnterSelectionMode: (String) -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasActive = uiState.activeDownloads.isNotEmpty()
    val hasDownloaded = uiState.downloadedComics.isNotEmpty()
    val density = LocalDensity.current
    val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val isWide = windowWidth >= 840.dp
    val screenPadding = if (isWide) 24.dp else 16.dp
    val gridSpacing = if (isWide) 16.dp else 12.dp
    val cardMinWidth = if (isWide) 184.dp else 168.dp
    val bottomPadding = if (uiState.isSelectionMode) 104.dp else 24.dp

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        BackHandler(enabled = uiState.isSelectionMode) {
            onExitSelectionMode()
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(cardMinWidth),
            modifier = Modifier
                .widthIn(max = 1120.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = screenPadding,
                end = screenPadding,
                top = screenPadding,
                bottom = bottomPadding
            ),
            horizontalArrangement = Arrangement.spacedBy(gridSpacing),
            verticalArrangement = Arrangement.spacedBy(gridSpacing)
        ) {
            if (uiState.isSelectionMode) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "selection_bar") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onExitSelectionMode) {
                                Icon(
                                    Icons.Default.Close,
                                    "取消选择",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                "已选 ${uiState.selectedPathWords.size} 部",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = onSelectAll) {
                                Icon(
                                    Icons.Default.SelectAll,
                                    "全选",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = onInvertSelection) {
                                Icon(
                                    Icons.Default.Deselect,
                                    "反选",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }, key = "active_header") {
                DownloadsSectionHeader(
                    title = "正在下载",
                    countText = "${uiState.activeDownloads.size} 个任务"
                )
            }

            if (hasActive) {
                uiState.activeDownloads.entries.forEach { (pathWord, progressInfo) ->
                    val paused = pathWord in uiState.pausedComics
                    val isError = progressInfo.status == DownloadTaskStatus.ERROR
                    item(span = { GridItemSpan(maxLineSpan) }, key = "active_$pathWord") {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            DownloadingCard(
                                name = progressInfo.name,
                                cover = progressInfo.cover,
                                progressInfo = progressInfo,
                                isPaused = paused,
                                onPauseResume = {
                                    if (paused) onResume(pathWord)
                                    else onPause(pathWord)
                                },
                                onCancel = { onCancel(pathWord) },
                                onRetry = if (isError) {
                                    { onResume(pathWord) }
                                } else null,
                                modifier = Modifier
                                    .widthIn(max = 840.dp)
                                    .fillMaxWidth()
                            )
                        }
                    }
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }, key = "active_empty") {
                    EmptyDownloadSection(text = "暂无下载任务")
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }, key = "spacer") {
                Spacer(Modifier.height(8.dp))
            }

            item(span = { GridItemSpan(maxLineSpan) }, key = "downloaded_header") {
                DownloadsSectionHeader(
                    title = "已下载",
                    countText = "${uiState.downloadedComics.size} 部"
                )
            }

            if (hasDownloaded) {
                items(
                    items = uiState.downloadedComics,
                    key = { it.pathWord }
                ) { comic ->
                    ComicCard(
                        name = comic.name,
                        cover = comic.cover,
                        subtitle = "${comic.downloadedChapters}/${comic.totalChapters} 章",
                        isSelected = comic.pathWord in uiState.selectedPathWords,
                        sharedTransitionKey = "downloads:${comic.pathWord}",
                        comicPathWord = comic.pathWord,
                        onClick = {
                            if (uiState.isSelectionMode) {
                                onToggleSelection(comic.pathWord)
                            } else {
                                onComicClick(comic.pathWord)
                            }
                        },
                        onLongClick = {
                            onEnterSelectionMode(comic.pathWord)
                        }
                    )
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }, key = "downloaded_empty") {
                    EmptyDownloadSection(text = "暂无已下载内容")
                }
            }
        }

        if (uiState.isSelectionMode) {
            BottomAppBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 1120.dp)
                    .fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                actions = {
                    Button(
                        onClick = onDeleteSelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "删除 ${uiState.selectedPathWords.size} 部",
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun DownloadsSectionHeader(
    title: String,
    countText: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(10.dp))
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = CircleShape
        ) {
            Text(
                text = countText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun EmptyDownloadSection(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp)
        )
    }
}

private fun previewDownloadsUiState(selectionMode: Boolean = false): DownloadsUiState {
    val active = DownloadProgressInfo(
        comicPathWord = "preview-active",
        name = "异世界冒险手册",
        cover = null,
        completedChapters = 3,
        totalChapters = 12,
        overallProgress = 0.32f,
        currentChapterIndex = 4,
        currentChapterName = "第 4 话",
        currentChapterProgress = 0.68f,
        status = DownloadTaskStatus.DOWNLOADING,
        statusText = "正在下载图片 21/32",
        downloadedImages = 21,
        totalImages = 32,
        speedBytesPerSecond = 780 * 1024L
    )
    val downloaded = (1..6).map { index ->
        DownloadedComicInfo(
            pathWord = "preview-downloaded-${index}",
            name = listOf(
                "星海漫游指南",
                "雨夜侦探社",
                "魔法学院日常",
                "机械纪元",
                "山海食堂",
                "月下旅人"
            )[index - 1],
            cover = null,
            authorNames = "示例作者 $index",
            statusDisplay = if (index % 2 == 0) "已完结" else "连载中",
            lastUpdate = "2026-08-${10 + index}",
            lastChapterName = "第 ${40 + index} 话",
            brief = "用于 Android Studio Preview 的本地示例数据",
            totalChapters = 48,
            downloadedChapters = 8 * index,
            downloadAt = index.toLong()
        )
    }
    return DownloadsUiState(
        downloadedComics = downloaded,
        activeDownloads = mapOf(active.comicPathWord to active),
        isSelectionMode = selectionMode,
        selectedPathWords = if (selectionMode) {
            downloaded.take(2).map { it.pathWord }.toSet()
        } else {
            emptySet()
        }
    )
}

@Preview(
    showBackground = true,
    name = "下载 - 手机",
    widthDp = 411,
    heightDp = 891
)
@Composable
private fun DownloadsScreenPreview() {
    CopyManga_NeoTheme {
        DownloadsContent(
            uiState = previewDownloadsUiState(),
            onComicClick = {},
            onExitSelectionMode = {},
            onSelectAll = {},
            onInvertSelection = {},
            onPause = {},
            onResume = {},
            onCancel = {},
            onToggleSelection = {},
            onEnterSelectionMode = {},
            onDeleteSelected = {}
        )
    }
}

@Preview(
    showBackground = true,
    name = "下载 - 平板横屏",
    widthDp = 1280,
    heightDp = 800
)
@Composable
private fun DownloadsTabletPreview() {
    CopyManga_NeoTheme {
        DownloadsContent(
            uiState = previewDownloadsUiState(),
            onComicClick = {},
            onExitSelectionMode = {},
            onSelectAll = {},
            onInvertSelection = {},
            onPause = {},
            onResume = {},
            onCancel = {},
            onToggleSelection = {},
            onEnterSelectionMode = {},
            onDeleteSelected = {}
        )
    }
}

@Preview(showBackground = true, name = "下载 - 多选管理", widthDp = 411, heightDp = 891)
@Composable
private fun DownloadsSelectionPreview() {
    CopyManga_NeoTheme {
        DownloadsContent(
            uiState = previewDownloadsUiState(selectionMode = true),
            onComicClick = {},
            onExitSelectionMode = {},
            onSelectAll = {},
            onInvertSelection = {},
            onPause = {},
            onResume = {},
            onCancel = {},
            onToggleSelection = {},
            onEnterSelectionMode = {},
            onDeleteSelected = {}
        )
    }
}
