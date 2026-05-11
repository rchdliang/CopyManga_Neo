package com.rchdliang.copymanga_neo.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDto

/**
 * 首页屏幕
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onComicClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading && uiState.comicList.isEmpty() -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            uiState.error != null && uiState.comicList.isEmpty() -> {
                Text(
                    text = "加载失败：${uiState.error}",
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            else -> {
                ComicGrid(
                    comics = uiState.comicList,
                    onComicClick = onComicClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun ComicGrid(
    comics: List<ComicDto>,
    onComicClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(20.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items(comics) { comic ->
            ComicCard(
                comic = comic,
                onClick = { onComicClick(comic.pathWord) }
            )
        }
    }
}

@Composable
private fun ComicCard(
    comic: ComicDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        AsyncImage(
            model = comic.cover,
            contentDescription = comic.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = comic.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold
        )
        comic.author?.firstOrNull()?.let { author ->
            Text(
                text = author.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Normal,
                color = androidx.compose.ui.graphics.Color.Gray
            )
        }
    }
}
