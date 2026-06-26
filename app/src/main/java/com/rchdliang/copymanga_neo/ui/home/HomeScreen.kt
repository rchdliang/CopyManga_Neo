package com.rchdliang.copymanga_neo.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.model.Author
import com.rchdliang.copymanga_neo.domain.model.ComicSummary
import com.rchdliang.copymanga_neo.ui.components.ComicCard
import com.rchdliang.copymanga_neo.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme

/**
 * 首页屏幕
 */

@ExperimentalMaterial3ExpressiveApi
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onComicClick: (String) -> Unit,
    onNavigateToSearch: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pullToRefreshState = rememberPullToRefreshState()
    val toastHost = LocalToastHost.current

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            toastHost.showToast(it)
            viewModel.clearToastMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchEntryBar(
            onClick = onNavigateToSearch,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

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
            modifier = Modifier.fillMaxSize()
        ) {
        when {
            (uiState.error != null && uiState.recComicList.isEmpty()) -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "错误",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = uiState.error!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = { viewModel.loadComics() },
                    ) { Text("重试") }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
//                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    // 推荐漫画
                    if (uiState.recComicList.isNotEmpty()) {
                        item(key = "rec_title", contentType = "section_title") {
                            Text(
                                text = "漫画推荐",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
                            )
                        }
                        item(key = "rec_grid", contentType = "comic_grid") {
                            ComicGrid(
                                comics = uiState.recComicList,
                                onComicClick = onComicClick,
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
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                        }
                        item(key = "hot_grid", contentType = "comic_grid") {
                            ComicGrid(
                                comics = uiState.hotComics,
                                onComicClick = onComicClick,
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
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                        }
                        item(key = "new_grid", contentType = "comic_grid") {
                            ComicGrid(
                                comics = uiState.newComics,
                                onComicClick = onComicClick,
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
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                        }
                        item(key = "finish_grid", contentType = "comic_grid") {
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
}

@Composable
private fun SearchEntryBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.height(48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "搜索漫画",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ComicGrid(
    comics: List<ComicSummary>,
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
                name = comic.name,
                cover = comic.cover,
                subtitle = comic.author?.firstOrNull()?.name,
                onClick = { onComicClick(comic.pathWord) }
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
                ComicSummary(
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
                    author = listOf(Author("作者$index", "author-$index")),
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
                color = MaterialTheme.colorScheme.onSurface,
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
                color = MaterialTheme.colorScheme.onSurface,
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