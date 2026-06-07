package com.rchdliang.copymanga_neo.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rchdliang.copymanga_neo.ui.components.ComicCard

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadsScreen(
    onComicClick: (String) -> Unit = {},
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.activeDownloads.isEmpty() && uiState.downloadedComics.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无下载内容",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (uiState.activeDownloads.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (uiState.downloadedComics.isNotEmpty()) 0.4f else 1f)
            ) {
                Text(
                    text = "正在下载",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                uiState.activeDownloads.values.forEach { progressInfo ->
                    val task = uiState.activeTasks[progressInfo.comicPathWord]
                    DownloadingCard(
                        name = task?.name ?: progressInfo.comicPathWord,
                        cover = task?.cover,
                        progressInfo = progressInfo,
                        onCancel = { viewModel.cancelComic(progressInfo.comicPathWord) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (uiState.downloadedComics.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .weight(if (uiState.activeDownloads.isNotEmpty()) 0.6f else 1f)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "已下载（${uiState.downloadedComics.size} 部）",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = uiState.downloadedComics,
                        key = { it.pathWord },
                        contentType = { "downloaded_comic" }
                    ) { comic ->
                        ComicCard(
                            name = comic.name,
                            cover = comic.cover,
                            subtitle = "${comic.downloadedChapters}/${comic.totalChapters} 章",
                            onClick = { onComicClick(comic.pathWord) }
                        )
                    }
                }
            }
        }
    }
}
