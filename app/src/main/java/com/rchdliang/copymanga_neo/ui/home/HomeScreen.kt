package com.rchdliang.copymanga_neo.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
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
            uiState.isLoading && uiState.comicList.isEmpty() -> {
//                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                LoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }

            (uiState.error != null && uiState.comicList.isEmpty()) -> {
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
        columns = GridCells.Adaptive(160.dp),
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
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(160.dp)
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

@PreviewScreenSizes
@Composable
private fun ComicGridPreview() {
    ComicGrid(
        comics = sampleComics,
        onComicClick = {}
    )
}
