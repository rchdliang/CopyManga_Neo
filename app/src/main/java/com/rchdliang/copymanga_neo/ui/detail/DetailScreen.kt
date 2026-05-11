package com.rchdliang.copymanga_neo.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.rchdliang.copymanga_neo.data.remote.dto.ChapterDto

/**
 * 漫画详情屏幕
 */
@Composable
fun DetailScreen(
    pathWord: String,
    viewModel: DetailViewModel = hiltViewModel(),
    onChapterClick: (String, String, String) -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(pathWord) {
        viewModel.loadDetail(pathWord)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading && uiState.comicDetail == null -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            uiState.error != null && uiState.comicDetail == null -> {
                Text(
                    text = "加载失败：${uiState.error}",
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            uiState.comicDetail != null -> {
                DetailContent(
                    comicDetail = uiState.comicDetail!!,
                    chapters = uiState.chapters,
                    onChapterClick = onChapterClick,
                    onNavigateBack = onNavigateBack,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    comicDetail: com.rchdliang.copymanga_neo.data.remote.dto.ComicDetailDto,
    chapters: List<ChapterDto>,
    onChapterClick: (String, String, String) -> Unit,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // 封面和标题
            AsyncImage(
                model = comicDetail.cover,
                contentDescription = comicDetail.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = comicDetail.name,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )

            comicDetail.author?.let { authors ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "作者：${authors.joinToString { author -> author.name }}",
                    color = androidx.compose.ui.graphics.Color.Gray
                )
            }

            comicDetail.description?.let { desc ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = desc,
                    maxLines = 5
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "章节列表",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(chapters) { chapter ->
            ChapterItem(
                chapter = chapter,
                onClick = { onChapterClick(chapter.uuid, comicDetail.pathWord, chapter.name) }
            )
        }
    }
}

@Composable
private fun ChapterItem(
    chapter: ChapterDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = chapter.name,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp)
    )
}
