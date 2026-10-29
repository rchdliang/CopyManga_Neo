package com.rchdliang.copymanga_neo.multiplatform.ui.downloads

import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive
import androidx.compose.runtime.*
import com.rchdliang.copymanga_neo.multiplatform.ui.components.ConfirmRemovalDialog
import com.rchdliang.copymanga_neo.multiplatform.ui.components.ActiveRouteLaunchedEffect
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.model.DownloadKind
import com.rchdliang.copymanga_neo.domain.model.DownloadProgressInfo
import com.rchdliang.copymanga_neo.domain.model.DownloadTaskStatus
import com.rchdliang.copymanga_neo.domain.model.DownloadedComicInfo
import com.rchdliang.copymanga_neo.domain.model.comicDownloadKey
import com.rchdliang.copymanga_neo.domain.model.displayBookTitle
import com.rchdliang.copymanga_neo.multiplatform.ui.components.ComicCard
import com.rchdliang.copymanga_neo.multiplatform.ui.components.EmptyContent
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.multiplatform.ui.components.NovelSharedTransitionProvider
import com.rchdliang.copymanga_neo.multiplatform.ui.components.novelTransitionPath
import com.rchdliang.copymanga_neo.multiplatform.ui.export.ComicExportDialogs
import com.rchdliang.copymanga_neo.multiplatform.ui.export.ComicExportViewModel
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme

@Composable
fun DownloadsScreen(
    onComicClick: (String) -> Unit = {},
    viewModel: DownloadsViewModel,
    exportViewModel: ComicExportViewModel,
    onExportChapters: (List<String>) -> Unit,
    onDiscover: () -> Unit = {},
    onNovelClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmRemoval by remember(uiState.selectedKeys) { mutableStateOf(false) }
    val selectedBooks = uiState.downloadedBooks.filter { it.key in uiState.selectedKeys }
    val selectedChapters = selectedBooks.filterIsInstance<DownloadedBookItem.Comic>().sumOf { it.info.downloadedChapters }
    val selectedVolumes = selectedBooks.filterIsInstance<DownloadedBookItem.Novel>().sumOf { it.info.volumes.size }
    if (confirmRemoval && LocalRouteActive.current) ConfirmRemovalDialog(
        "删除所选下载？", "将删除 ${uiState.selectedKeys.size} 部作品，共 $selectedChapters 章漫画、$selectedVolumes 卷小说的离线内容。再次离线阅读需要重新下载。",
        onConfirm = { confirmRemoval = false; viewModel.deleteSelected() },
        onDismiss = { confirmRemoval = false }
    )
    val exportState by exportViewModel.progress.collectAsStateWithLifecycle()
    val toast = LocalToastHost.current
    ActiveRouteLaunchedEffect(uiState.message) {
        uiState.message?.let { toast.showToast(it); viewModel.clearMessage() }
    }
    ComicExportDialogs(exportViewModel)

    DownloadsContent(
        uiState = uiState,
        onReload = viewModel::reload,
        onDiscover = onDiscover,
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
        onDeleteSelected = { confirmRemoval = true },
        onExportSelected = {
            val comics = uiState.selectedComicPathWords.toList()
            viewModel.exitSelectionMode()
            onExportChapters(comics)
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
    onReload: () -> Unit = {},
    modifier: Modifier = Modifier,
    onExportSelected: () -> Unit = {},
    exportingComics: Set<String> = emptySet(),
    onDiscover: () -> Unit = {},
    onNovelClick: (String) -> Unit = {}
) {
    com.rchdliang.copymanga_neo.multiplatform.ui.components.ComicPreloadEffect(
        uiState.downloadedBooks.filterIsInstance<DownloadedBookItem.Comic>()
            .map { it.info.pathWord } +
                uiState.activeDownloads.values.filter { it.kind == DownloadKind.COMIC }
                    .map { it.comicPathWord })
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

    if (!hasActive && !hasDownloaded && !uiState.isLoading && uiState.loadError == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 1120.dp).fillMaxSize().padding(screenPadding)
            ) {
                DownloadsSectionHeader(title = "已下载", countText = "0 部")
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyDownloadSection(
                        onDiscover = onDiscover,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            }
        }
        return
    }

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

            if (uiState.isLoading || uiState.loadError != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "restoration") {
                    Column {
                    Text(
                        uiState.loadError ?: "正在恢复下载记录…",
                        color = if (uiState.loadError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                    if (uiState.loadError != null) androidx.compose.material3.TextButton(onClick = onReload, enabled = !uiState.isLoading) { Text("重新读取") }
                    }
                }
            }
            if (hasActive) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "active_header") {
                    DownloadsSectionHeader(
                        title = "正在下载",
                        countText = "${activeDownloads.size} 个任务"
                    )
                }
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
                item(span = { GridItemSpan(maxLineSpan) }, key = "spacer") {
                    Spacer(Modifier.height(8.dp))
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }, key = "downloaded_header") {
                DownloadsSectionHeader(
                    title = "已下载",
                    countText = when {
                        uiState.isLoading -> "正在读取"
                        uiState.loadError != null && !hasDownloaded -> "数据未就绪"
                        else -> "$downloadedCount 部"
                    }
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
                                allowPendingContent = true,
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
                    if (!uiState.isLoading && uiState.loadError == null) {
                        EmptyDownloadSection(onDiscover = onDiscover)
                    }
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
    onDiscover: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmptyContent(
        icon = Icons.Outlined.Download,
        title = "还没有下载的作品",
        description = "在作品详情页下载漫画章节或小说分卷，即可离线阅读。",
        modifier = modifier
    ) {
        FilledTonalButton(onClick = onDiscover) {
            Text("去找作品", color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
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

@Preview(showBackground = true, name = "下载 - 空状态手机", widthDp = 411, heightDp = 891)
@Preview(showBackground = true, name = "下载 - 空状态桌面", widthDp = 1280, heightDp = 800)
@Composable
private fun DownloadsEmptyPreview() {
    CopyManga_NeoTheme(dynamicColor = false) {
        DownloadsContent(
            uiState = DownloadsUiState(isLoading = false),
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
