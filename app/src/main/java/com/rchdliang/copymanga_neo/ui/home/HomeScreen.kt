package com.rchdliang.copymanga_neo.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDto

/**
 * 首页屏幕
 */

@ExperimentalMaterial3ExpressiveApi
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onComicClick: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        when {
            uiState.isLoading && uiState.recComicList.isEmpty() -> {
                LoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }

            (uiState.error != null && uiState.recComicList.isEmpty()) -> {
                Text(
                    text = "加载失败：${uiState.error}",
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
//                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    // 推荐漫画
                    if (uiState.recComicList.isNotEmpty()) {
                        item {
                            Text(
                                text = "漫画推荐",
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                            ComicGrid(
                                comics = uiState.recComicList,
                                onComicClick = onComicClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 热门漫画
                    if (uiState.hotComics.isNotEmpty()) {
                        item {
                            Text(
                                text = "热门更新",
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                            ComicGrid(
                                comics = uiState.hotComics,
                                onComicClick = onComicClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 最新漫画
                    if (uiState.newComics.isNotEmpty()) {
                        item {
                            Text(
                                text = "最新上架",
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                            ComicGrid(
                                comics = uiState.newComics,
                                onComicClick = onComicClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 完结漫画
                    if (uiState.finishComics.isNotEmpty()) {
                        item {
                            Text(
                                text = "完结漫画",
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                            ComicGrid(
                                comics = uiState.finishComics,
                                onComicClick = onComicClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
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
    LazyRow(
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ComicCard(
    comic: ComicDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.size(width = 120.dp, height = 180.dp)
    ) {
        Box(
            contentAlignment = Alignment.BottomStart,
            modifier = Modifier.fillMaxSize()
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest
                    .Builder(LocalContext.current)
                    .crossfade(true)
                    .data(comic.cover)
                    .build(),
                contentDescription = comic.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is AsyncImagePainter.State.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                imageVector = Icons.Default.BrokenImage,
                                contentDescription = "加载失败",
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    is AsyncImagePainter.State.Success -> {
                        Image(
                            painter = painter,
                            contentDescription = comic.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    else -> Unit
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text(
                        text = comic.name,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    comic.author?.firstOrNull()?.let { author ->
                        Text(
                            text = author.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }


        }

    }
}