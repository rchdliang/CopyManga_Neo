package com.rchdliang.copymanga_neo.ui.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rchdliang.copymanga_neo.data.remote.dto.AuthorDto
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDto
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme

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
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = { viewModel.loadComics() },
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullToRefreshState,
                isRefreshing = uiState.isLoading,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        when {
            (uiState.error != null && uiState.recComicList.isEmpty()) -> {
                Text(
                    text = "加载失败：${uiState.error}",
                    modifier = Modifier.align(Alignment.Center)
                )
                TextButton(
                    onClick = { viewModel.loadComics() }
                ) { Text("重试") }
            }

            else -> {
                val onComicClickRemembered = remember(onComicClick) { onComicClick }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
//                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    // 推荐漫画
                    if (uiState.recComicList.isNotEmpty()) {
                        item(key = "rec_title", contentType = "section_title") {
                            Text(
                                text = "漫画推荐",
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                        }
                        item(key = "rec_grid", contentType = "comic_grid") {
                            ComicGrid(
                                comics = uiState.recComicList,
                                onComicClick = onComicClickRemembered,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 热门漫画
                    if (uiState.hotComics.isNotEmpty()) {
                        item(key = "hot_title", contentType = "section_title") {
                            Text(
                                text = "热门更新",
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                        }
                        item(key = "hot_grid", contentType = "comic_grid") {
                            ComicGrid(
                                comics = uiState.hotComics,
                                onComicClick = onComicClickRemembered,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 最新漫画
                    if (uiState.newComics.isNotEmpty()) {
                        item(key = "new_title", contentType = "section_title") {
                            Text(
                                text = "最新上架",
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                        }
                        item(key = "new_grid", contentType = "comic_grid") {
                            ComicGrid(
                                comics = uiState.newComics,
                                onComicClick = onComicClickRemembered,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 完结漫画
                    if (uiState.finishComics.isNotEmpty()) {
                        item(key = "finish_title", contentType = "section_title") {
                            Text(
                                text = "完结漫画",
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                        }
                        item(key = "finish_grid", contentType = "comic_grid") {
                            ComicGrid(
                                comics = uiState.finishComics,
                                onComicClick = onComicClickRemembered,
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
        items(
            items = comics,
            key = { it.pathWord },
            contentType = { "comic_card" }
        ) { comic ->
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
        modifier = modifier.size(width = 120.dp, height = 180.dp)
    ) {
        Box(
            contentAlignment = Alignment.BottomStart,
            modifier = Modifier.fillMaxSize()
        ) {
            AsyncImage(
                model = ImageRequest
                    .Builder(LocalContext.current)
                    .crossfade(true)
                    .data(comic.cover)
                    .build(),
                contentDescription = comic.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

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

@Preview(showBackground = true, name = "漫画卡片")
@Composable
private fun ComicCardPreview() {
    CopyManga_NeoTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            ComicCard(
                comic = ComicDto(
                    name = "示例漫画名称",
                    pathWord = "test-comic",
                    cover = "https://example.com/cover.jpg",
                    author = listOf(AuthorDto("作者名称", "author-path")),
                    females = emptyList(),
                    males = emptyList(),
                    theme = emptyList(),
                    popular = 1000,
                    authorAlias = "作者别名"
                ),
                onClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "首页 - 多分区漫画行")
@Composable
private fun HomeSectionsPreview() {
    CopyManga_NeoTheme {
        val mockComics = remember {
            (1..8).map { index ->
                ComicDto(
                    name = when (index) {
                        1 -> "冒险者的奇幻旅途"
                        2 -> "都市灵异事件簿"
                        3 -> "剑与魔法的学院"
                        4 -> "星际迷航记"
                        5 -> "美食大冒险"
                        6 -> "异世界悠闲生活"
                        7 -> "暗影猎手传说"
                        else -> "机械纪元2077"
                    },
                    pathWord = "home-comic-$index",
                    cover = "https://example.com/cover$index.jpg",
                    author = listOf(AuthorDto("作者$index", "author-$index")),
                    females = emptyList(),
                    males = emptyList(),
                    theme = emptyList(),
                    popular = 1000 * index,
                    authorAlias = "别名$index"
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Text(
                text = "漫画推荐",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            ComicGrid(
                comics = mockComics.take(5),
                onComicClick = {},
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "最新上架",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            ComicGrid(
                comics = mockComics.reversed(),
                onComicClick = {},
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}