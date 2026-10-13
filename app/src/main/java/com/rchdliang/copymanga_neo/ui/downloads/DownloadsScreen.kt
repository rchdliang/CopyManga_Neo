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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.model.ComicExportRequest
import com.rchdliang.copymanga_neo.domain.model.DownloadKind
import com.rchdliang.copymanga_neo.domain.model.DownloadProgressInfo
import com.rchdliang.copymanga_neo.domain.model.DownloadTaskStatus
import com.rchdliang.copymanga_neo.domain.model.DownloadedComicInfo
import com.rchdliang.copymanga_neo.domain.model.comicDownloadKey
import com.rchdliang.copymanga_neo.domain.model.displayBookTitle
import com.rchdliang.copymanga_neo.ui.components.ComicCard
import com.rchdliang.copymanga_neo.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.ui.components.NovelSharedTransitionProvider
import com.rchdliang.copymanga_neo.ui.components.novelTransitionPath
import com.rchdliang.copymanga_neo.ui.export.ComicExportDialogs
import com.rchdliang.copymanga_neo.ui.export.ComicExportViewModel
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme

@Composable
fun DownloadsScreen(
    onComicClick: (String) -> Unit = {},
    viewModel: DownloadsViewModel = hiltViewModel(),
    exportViewModel: ComicExportViewModel = hiltViewModel(),
    onNovelClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exportState by exportViewModel.progress.collectAsStateWithLifecycle()
    val toast = LocalToastHost.current
    LaunchedEffect(uiState.message) {
        uiState.message?.let { toast.showToast(it); viewModel.clearMessage() }
    }
    ComicExportDialogs(exportViewModel)

    DownloadsContent(
        uiState = uiState,
        onComicClick = onComicClick,
        onNovelClick = onNovelClick,
        onExitSelectionMode = viewModel::exitSelectionMode,
        onSelectAll = viewModel::selectAll,
        onInvertSelection = viewModel::invertSelection,
        onPause = viewModel::pauseTask,
        onResume = viewModel::resumeTask,
        onCancel = viewModel::cancelTask,
        onToggleSelection = viewModel::toggleSelection,
        onEnterSelectionMode = viewModel::enterSelectionMode,
        onDeleteSelected = viewModel::deleteSelected,
        onExportSelected = {
            val request = ComicExportRequest(uiState.selectedComicPathWords.toList())
            viewModel.exitSelectionMode()
            exportViewModel.prepare(request)
        },
        exportingComics = if (exportState.running) exportState.plan?.comics?.map { it.pathWord }
            ?.toSet().orEmpty() else emptySet()
    )
}

/**
 * 下载页纯 UI 内容，Preview 可直接传入下载状态而无需初始化数据库和 Hilt。
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    modifier: Modifier = Modifier,
    onExportSelected: () -> Unit = {},
    exportingComics: Set<String> = emptySet(),
    onNovelClick: (String) -> Unit = {}
) {
    val activeDownloads = uiState.activeDownloads
    val downloadedCount = uiState.downloadedBooks.size
    val selectedComics = uiState.selectedComicPathWords
    val hasActive = activeDownloads.isNotEmpty()
    val hasDownloaded = downloadedCount > 0
    val gridState = rememberLazyGridState()
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
            state = gridState,
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
                                "已选 ${uiState.selectedKeys.size} 部",
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
                    countText = "${activeDownloads.size} 个任务"
                )
            }

            if (hasActive) {
                activeDownloads.entries.forEach { (pathWord, progressInfo) ->
                    val paused =
                        progressInfo.status == DownloadTaskStatus.PAUSED || progressInfo.status == DownloadTaskStatus.PAUSED_BY_RISK
                    val isError = progressInfo.status == DownloadTaskStatus.ERROR
                    item(span = { GridItemSpan(maxLineSpan) }, key = "active_$pathWord") {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            DownloadingCard(
                                name = "${if (progressInfo.kind == DownloadKind.NOVEL) "轻小说" else "漫画"} · ${progressInfo.name}",
                                cover = progressInfo.cover,
                                progressInfo = progressInfo,
                                unitLabel = if (progressInfo.kind == DownloadKind.NOVEL) "卷" else "章",
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
//                                    .widthIn(max = 840.dp)
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
                    countText = "$downloadedCount 部"
                )
            }

            if (hasDownloaded) {
                items(uiState.downloadedBooks, key = { it.key }) { item ->
                    when (item) {
                        is DownloadedBookItem.Novel -> {
                            val downloaded = item.info
                            val book = downloaded.book
                            val complete = downloaded.volumes.count { it.offlineReady }
                            NovelSharedTransitionProvider {
                                ComicCard(
                                    name = displayBookTitle(book.path, book.name),
                                    cover = book.cover,
                                    subtitle = "轻小说 · 本地 ${downloaded.volumes.size} 卷 · 完整 $complete 卷",
                                    isSelected = item.key in uiState.selectedKeys,
                                    sharedTransitionKey = if (uiState.isSelectionMode) null else "novel:downloads:${book.path}",
                                    comicPathWord = novelTransitionPath(book.path),
                                    onClick = {
                                        if (uiState.isSelectionMode) onToggleSelection(item.key)
                                        else onNovelClick(book.path)
                                    },
                                    onLongClick = { onEnterSelectionMode(item.key) }
                                )
                            }
                        }

                        is DownloadedBookItem.Comic -> {
                            val comic = item.info
                            ComicCard(
                                name = comic.name, cover = comic.cover,
                                subtitle = "漫画 · 本地 ${comic.downloadedChapters} 章",
                                isSelected = item.key in uiState.selectedKeys,
                                sharedTransitionKey = if (uiState.isSelectionMode) null else "downloads:${comic.pathWord}",
                                comicPathWord = comic.pathWord,
                                onClick = {
                                    if (uiState.isSelectionMode) onToggleSelection(item.key)
                                    else onComicClick(comic.pathWord)
                                },
                                onLongClick = { onEnterSelectionMode(item.key) }
                            )
                        }
                    }
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
                    if (selectedComics.isNotEmpty()) Button(
                        onClick = onExportSelected,
                        enabled = exportingComics.isEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp, end = 4.dp)
                    ) {
                        Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "导出 ${selectedComics.size} 部漫画",
                            color = LocalContentColor.current
                        )
                    }
                    Button(
                        onClick = onDeleteSelected,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp, end = 16.dp),
                        enabled = uiState.selectedKeys.isNotEmpty() && selectedComics.none { it in exportingComics },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "删除 ${uiState.selectedKeys.size} 部",
                            color = LocalContentColor.current
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
        downloadedBooks = downloaded.map { DownloadedBookItem.Comic(it) },
        activeDownloads = mapOf(active.comicPathWord to active),
        isSelectionMode = selectionMode,
        selectedKeys = if (selectionMode) {
            downloaded.take(2).map { comicDownloadKey(it.pathWord) }.toSet()
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
