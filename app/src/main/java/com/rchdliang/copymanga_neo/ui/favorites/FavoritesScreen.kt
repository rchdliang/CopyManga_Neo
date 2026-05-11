package com.rchdliang.copymanga_neo.ui.favorites

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDto

/**
 * 收藏夹屏幕
 */
@Composable
fun FavoritesScreen(
    onComicClick: (String) -> Unit
) {
    // TODO: 从 DataStore 加载收藏列表
    val favorites by remember { mutableStateOf(emptyList<ComicDto>()) }

    if (favorites.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(text = "暂无收藏，快去添加喜欢的漫画吧！")
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(favorites) { comic ->
                FavoriteCard(
                    comic = comic,
                    onClick = { onComicClick(comic.pathWord) }
                )
            }
        }
    }
}

@Composable
private fun FavoriteCard(
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
