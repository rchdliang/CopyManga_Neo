package com.rchdliang.copymanga_neo.ui.search

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rchdliang.copymanga_neo.data.remote.dto.AuthorDto
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDto
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme

/**
 * 搜索屏幕
 */
@Composable
fun SearchScreen(
    onComicClick: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<ComicDto>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
//            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 搜索框
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("搜索漫画...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // 搜索按钮
        Button(
            onClick = {
                isSearching = true
                errorMessage = null
                // TODO: 实现搜索逻辑
                isSearching = false
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("搜索")
        }

        // 搜索结果显示
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isSearching -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                errorMessage != null -> {
                    Text(
                        text = "搜索失败：$errorMessage",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                searchResults.isNotEmpty() -> {
                    SearchResultsGrid(
                        comics = searchResults,
                        onComicClick = onComicClick
                    )
                }

                searchQuery.isNotEmpty() -> {
                    Text(
                        text = "输入关键词后点击搜索",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsGrid(
    comics: List<ComicDto>,
    onComicClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items(comics) { comic ->
            SearchComicCard(
                comic = comic,
                onClick = { onComicClick(comic.pathWord) }
            )
        }
    }
}

@Composable
private fun SearchComicCard(
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
    }
}

// region Previews

@Preview(showBackground = true, showSystemUi = true, name = "搜索 - 完整界面")
@Composable
private fun SearchScreenPreview() {
    CopyManga_NeoTheme {
        SearchScreen(
            onComicClick = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "搜索 - 搜索结果网格")
@Composable
private fun SearchResultsGridPreview() {
    CopyManga_NeoTheme {
        val mockComics = remember {
            (1..8).map { index ->
                ComicDto(
                    name = when (index) {
                        1 -> "冒险者的旅途"
                        2 -> "都市灵异事件簿"
                        3 -> "剑与魔法的学院"
                        4 -> "星际迷航记"
                        5 -> "美食大冒险"
                        6 -> "异世界悠闲生活"
                        7 -> "暗影猎手"
                        else -> "机械纪元"
                    },
                    pathWord = "search-comic-$index",
                    cover = "https://example.com/cover$index.jpg",
                    author = listOf(AuthorDto("作者$index", "author-$index")),
                    females = emptyList(),
                    males = emptyList(),
                    theme = emptyList(),
                    popular = 1000 * index,
                    authorAlias = null
                )
            }
        }

        SearchResultsGrid(
            comics = mockComics,
            onComicClick = {}
        )
    }
}

// endregion