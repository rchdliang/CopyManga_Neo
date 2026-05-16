package com.rchdliang.copymanga_neo.ui.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import com.rchdliang.copymanga_neo.data.remote.dto.ChapterDto
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDetailDto

/**
 * 漫画详情屏幕
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        when {
            uiState.isLoading && uiState.comicDetail == null -> {
                LoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }

            uiState.error != null && uiState.comicDetail == null -> {
                Text(
                    text = "加载失败：${uiState.error}",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.error
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DetailContent(
    comicDetail: ComicDetailDto,
    chapters: List<ChapterDto>,
    onChapterClick: (String, String, String) -> Unit,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier
) {
    Column(
        modifier = modifier
    ) {
        // 上半部分：漫画封面和信息
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 漫画封面（左侧）
            SubcomposeAsyncImage(
                model = comicDetail.cover,
                contentDescription = comicDetail.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(160.dp)
                    .aspectRatio(3f / 4f)
                    .clip(MaterialTheme.shapes.extraExtraLarge)
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator(
                                modifier = Modifier
                                    .width(32.dp)
                                    .aspectRatio(1f),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    is AsyncImagePainter.State.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                imageVector = Icons.Default.BrokenImage,
                                contentDescription = "加载失败",
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                                modifier = modifier.padding(16.dp)
                            )
                        }
                    }

                    is AsyncImagePainter.State.Success -> {
                        Image(
                            painter = painter,
                            contentDescription = comicDetail.name,
                            contentScale = ContentScale.Crop,
                            modifier = modifier
                        )
                    }

                    else -> Unit
                }
            }

            // 漫画信息（右侧）
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 漫画名
                Text(
                    text = comicDetail.name,
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )

                // 作者名
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Author",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    comicDetail.author?.let { authors ->
                        Text(
                            text = authors.joinToString { author -> author.name },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = "Description",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    comicDetail.brief?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 10,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

            }
        }

        // 章节列表标题
        Text(
            text = "章节列表",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // 下半部分：章节网格
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(chapters) { chapter ->
                ChapterItem(
                    chapter = chapter,
                    onClick = { onChapterClick(chapter.uuid, comicDetail.pathWord, chapter.name) }
                )
            }
        }
    }
}

@Composable
private fun ChapterItem(
    chapter: ChapterDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
//        colors = ButtonDefaults.buttonColors(
//            containerColor = ,
//            disabledContainerColor = ,
//            contentColor = ,
//            disabledContentColor = ,
//        )
    ) {
        Text(
            text = chapter.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
