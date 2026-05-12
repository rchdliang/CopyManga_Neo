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
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.rchdliang.copymanga_neo.data.remote.dto.AuthorDto
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

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading && uiState.recComicList.isEmpty() -> {
//                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    // 推荐漫画
                    if (uiState.recComicList.isNotEmpty()) {
                        item {
                            Text(
                                text = "漫画推荐",
                                style = MaterialTheme.typography.titleLarge,
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
                                style = MaterialTheme.typography.titleLarge,
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
                                style = MaterialTheme.typography.titleLarge,
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
                                style = MaterialTheme.typography.titleLarge,
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
            AsyncImage(
                model = comic.cover,
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

// Preview data
private val sampleComics = listOf(
    ComicDto(
        name = "冰之公主 渴望在一隅暖陽中融化",
        pathWord = "bzgzkwzyynyzrh",
        cover = "https://sb.mangafunb.fun/b/bzgzkwzyynyzrh/cover/1774282897.jpeg.328x422.jpg",
        author = listOf(AuthorDto(name = "樹多村あかつき", pathWord = "sdcaktk")),
        popular = 82392,
        females = null,
        males = null,
        theme = null,
        authorAlias = "樹多村あかつき"
    ),
    ComicDto(
        name = "喜歡來者不拒的你",
        pathWord = "xihuanlaizhebujudeni",
        cover = "https://sx.mangafunb.fun/x/xihuanlaizhebujudeni/cover/1688534408.jpg.328x422.jpg",
        author = listOf(AuthorDto(name = "武田スーパー", pathWord = "wutiansuupaa")),
        popular = 5686932,
        females = null,
        males = null,
        theme = null,
        authorAlias = "武田スーパー"
    ),
    ComicDto(
        name = "半調子戀人",
        pathWord = "bandiaozilianrne",
        cover = "https://sb.mangafunb.fun/b/bandiaozilianrne/cover/1708480013.jpg.328x422.jpg",
        author = listOf(AuthorDto(name = "川田大智", pathWord = "ctdz")),
        popular = 414339,
        females = null,
        males = null,
        theme = null,
        authorAlias = "川田大智"
    )
)


@Preview
@Composable
private fun HomeScreenPreview() {
    Column {
        Text(
            text = "漫画推荐",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        ComicGrid(
            comics = sampleComics,
            onComicClick = {}
        )
    }
}