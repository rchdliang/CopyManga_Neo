package com.rchdliang.copymanga_neo.ui.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.data.download.DownloadTaskStatus
import com.rchdliang.copymanga_neo.ui.components.ComicCard

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadsScreen(
    onComicClick: (String) -> Unit = {},
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val hasActive = uiState.activeDownloads.isNotEmpty()
    val hasDownloaded = uiState.downloadedComics.isNotEmpty()
    val bottomPadding = if (uiState.isSelectionMode) 80.dp else 0.dp

    Box(modifier = Modifier.fillMaxSize()) {
        BackHandler(enabled = uiState.isSelectionMode) {
            viewModel.exitSelectionMode()
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(135.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = 8.dp,
                bottom = bottomPadding
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 选择模式顶栏
            if (uiState.isSelectionMode) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "selection_bar") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
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
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(
                                Icons.Default.SelectAll,
                                "全选",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { viewModel.invertSelection() }) {
                            Icon(
                                Icons.Default.Deselect,
                                "反选",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // "正在下载" 标题
            item(span = { GridItemSpan(maxLineSpan) }, key = "active_header") {
                Text(
                    text = "正在下载",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // 正在下载的卡片
            if (hasActive) {
                uiState.activeDownloads.entries.forEach { (pathWord, progressInfo) ->
                    val paused = pathWord in uiState.pausedComics
                    val isError = progressInfo.status == DownloadTaskStatus.ERROR
                    item(span = { GridItemSpan(maxLineSpan) }, key = "active_$pathWord") {
                        DownloadingCard(
                            name = progressInfo.name,
                            cover = progressInfo.cover,
                            progressInfo = progressInfo,
                            isPaused = paused,
                            onPauseResume = {
                                if (paused) viewModel.resumeComic(pathWord)
                                else viewModel.pauseComic(pathWord)
                            },
                            onCancel = { viewModel.cancelComic(pathWord) },
                            onRetry = if (isError) {
                                { viewModel.resumeComic(pathWord) }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }, key = "active_empty") {
                    Text(
                        text = "暂无下载任务",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            // 分隔间距
            item(span = { GridItemSpan(maxLineSpan) }, key = "spacer") {
                Spacer(Modifier.height(24.dp))
            }

            // "已下载" 标题
            item(span = { GridItemSpan(maxLineSpan) }, key = "downloaded_header") {
                Text(
                    text = "已下载（${uiState.downloadedComics.size} 部）",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }

            // 已下载漫画网格
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
                        onClick = {
                            if (uiState.isSelectionMode) {
                                viewModel.toggleSelection(comic.pathWord)
                            } else {
                                onComicClick(comic.pathWord)
                            }
                        },
                        onLongClick = {
                            viewModel.enterSelectionMode(comic.pathWord)
                        }
                    )
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }, key = "downloaded_empty") {
                    Text(
                        text = "暂无已下载内容",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }

        // 选择模式底栏
        if (uiState.isSelectionMode) {
            BottomAppBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                actions = {
                    Button(
                        onClick = { viewModel.deleteSelected() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("删除 ${uiState.selectedPathWords.size} 部")
                    }
                }
            )
        }
    }
}
