package com.rchdliang.copymanga_neo.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    val hasActive = uiState.activeDownloads.isNotEmpty()
    val hasDownloaded = uiState.downloadedComics.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "正在下载",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (hasActive) {
            uiState.activeDownloads.entries.forEach { (pathWord, progressInfo) ->
                val paused = pathWord in uiState.pausedComics
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
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Text(
                text = "暂无下载任务",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Text(
            text = "已下载（${uiState.downloadedComics.size} 部）",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (hasDownloaded) {
            val columns = 3
            val rows = uiState.downloadedComics.chunked(columns)
            rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { comic ->
                        ComicCard(
                            name = comic.name,
                            cover = comic.cover,
                            subtitle = "${comic.downloadedChapters}/${comic.totalChapters} 章",
                            onClick = { onComicClick(comic.pathWord) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        } else {
            Text(
                text = "暂无已下载内容",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
