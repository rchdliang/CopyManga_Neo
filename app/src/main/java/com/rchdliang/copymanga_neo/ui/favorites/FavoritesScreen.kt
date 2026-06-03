package com.rchdliang.copymanga_neo.ui.favorites

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.rchdliang.copymanga_neo.data.remote.dto.AuthorDto
import com.rchdliang.copymanga_neo.data.remote.dto.BrowseDto
import com.rchdliang.copymanga_neo.data.remote.dto.FavComicDto
import com.rchdliang.copymanga_neo.data.remote.dto.FavoritesDto
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme

/**
 * 收藏夹屏幕
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel = hiltViewModel(),
    onComicClick: (String) -> Unit,
    onNavigateToProfile: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = { viewModel.loadFavorites() },
        modifier = Modifier.fillMaxSize(),
        state = pullToRefreshState,
        contentAlignment = Alignment.TopCenter,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullToRefreshState,
                isRefreshing = uiState.isLoading,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        when {
            !uiState.isLoggedIn -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Login,
                        contentDescription = "未登录",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "请先登录以查看书架",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "登录后即可收藏和管理漫画",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = onNavigateToProfile) {
                        Text("前往登录")
                    }
                }
            }

            uiState.error != null && uiState.favorites.isEmpty() -> {
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
                        onClick = { viewModel.loadFavorites() },
                    ) { Text("重试") }
                }
            }

            uiState.favorites.isEmpty() -> {
                Text(
                    text = "暂无收藏，快去添加喜欢的漫画吧！",
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.favorites) { favorite ->
                        FavoriteCard(
                            favorite = favorite,
                            onClick = { onComicClick(favorite.favComic.pathWord) }
                        )
                    }
                }
            }
        }
    }
}

// region Previews

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true, showSystemUi = false, name = "收藏 - 收藏卡片")
@Composable
private fun FavoriteCardPreview() {
    CopyManga_NeoTheme {
        val mockFavorite = remember {
            FavoritesDto(
                uuid = 1,
                name = null,
                bFolder = false,
                folderId = null,
                favComic = FavComicDto(
                    uuid = "fav-uuid-1",
                    bDisplay = true,
                    name = "示例漫画：冒险者的旅途",
                    pathWord = "adventure-journey",
                    author = listOf(AuthorDto("山田太郎", "yamada-taro")),
                    theme = emptyList(),
                    cover = "https://example.com/cover.jpg",
                    status = 0,
                    popular = 10000,
                    datetimeUpdated = "2025-03-15",
                    lastChapterId = "ch-128",
                    lastChapterName = "第 128 话",
                    browse = BrowseDto(
                        comicUuid = "fav-uuid-1",
                        pathWord = "adventure-journey",
                        chapterUuid = "ch-120",
                        chapterName = "第 120 话"
                    )
                )
            )
        }

        FavoriteCard(
            favorite = mockFavorite,
            onClick = {}
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true, showSystemUi = true, name = "收藏 - 多卡片网格")
@Composable
private fun FavoriteGridPreview() {
    CopyManga_NeoTheme {
        val mockFavorites = remember {
            (1..6).map { index ->
                FavoritesDto(
                    uuid = index,
                    name = null,
                    bFolder = false,
                    folderId = null,
                    favComic = FavComicDto(
                        uuid = "fav-uuid-$index",
                        bDisplay = true,
                        name = when (index) {
                            1 -> "冒险者的奇幻旅途"
                            2 -> "都市灵异事件簿"
                            3 -> "剑与魔法的学院"
                            4 -> "星际迷航记"
                            5 -> "美食大冒险"
                            else -> "异世界悠闲生活"
                        },
                        pathWord = "comic-$index",
                        author = listOf(AuthorDto("作者$index", "author-$index")),
                        theme = emptyList(),
                        cover = "https://example.com/cover$index.jpg",
                        status = index % 2,
                        popular = 1000 * index,
                        datetimeUpdated = "2025-03-${10 + index}",
                        lastChapterId = "ch-$index",
                        lastChapterName = "第 ${100 + index} 话",
                        browse = BrowseDto(
                            comicUuid = "fav-uuid-$index",
                            pathWord = "comic-$index",
                            chapterUuid = when (index % 3) {
                                0 -> "ch-$index"
                                1 -> "ch-${index - 1}"
                                else -> null
                            },
                            chapterName = when (index % 3) {
                                0 -> "第 ${90 + index} 话"
                                1 -> "第 ${80 + index} 话"
                                else -> null
                            }
                        )
                    )
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(mockFavorites) { favorite ->
                FavoriteCard(
                    favorite = favorite,
                    onClick = {}
                )
            }
        }
    }
}

// endregion

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FavoriteCard(
    favorite: FavoritesDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val comic = favorite.favComic
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.height(200.dp)
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
//                    Spacer(Modifier.height(4.dp))
                    comic.author?.firstOrNull()?.let { author ->
                        Text(
                            text = author.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    when {
                        comic.browse == null || comic.browse.chapterUuid.isNullOrEmpty() -> {
                            Text(
                                text = "还未读过",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        comic.browse?.chapterUuid == comic.lastChapterId -> {
                            Text(
                                text = "上次读到：${comic.browse?.chapterName ?: ""}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        else -> {
                            Text(
                                text = "有新内容：${comic.lastChapterName}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}