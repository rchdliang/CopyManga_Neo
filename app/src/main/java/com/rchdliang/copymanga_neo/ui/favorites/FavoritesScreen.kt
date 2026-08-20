package com.rchdliang.copymanga_neo.ui.favorites

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.model.Author
import com.rchdliang.copymanga_neo.domain.model.BrowseInfo
import com.rchdliang.copymanga_neo.domain.model.FavoriteComic
import com.rchdliang.copymanga_neo.domain.model.FavoriteItem
import com.rchdliang.copymanga_neo.ui.components.ComicCard
import com.rchdliang.copymanga_neo.ui.components.LocalToastHost
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val toastHost = LocalToastHost.current

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            toastHost.showToast(it)
            viewModel.clearToastMessage()
        }
    }

    FavoritesContent(
        uiState = uiState,
        onComicClick = onComicClick,
        onNavigateToProfile = onNavigateToProfile,
        onRefresh = viewModel::loadFavorites,
        onExitSelectionMode = viewModel::exitSelectionMode,
        onSelectAll = viewModel::selectAll,
        onInvertSelection = viewModel::invertSelection,
        onToggleSelection = viewModel::toggleSelection,
        onEnterSelectionMode = viewModel::enterSelectionMode,
        onRemoveSelected = viewModel::removeSelected
    )
}

/**
 * 收藏页纯 UI 内容，Preview 可覆盖登录、空态、列表和多选状态。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FavoritesContent(
    uiState: FavoritesUiState,
    onComicClick: (String) -> Unit,
    onNavigateToProfile: () -> Unit,
    onRefresh: () -> Unit,
    onExitSelectionMode: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onToggleSelection: (Int) -> Unit,
    onEnterSelectionMode: (Int) -> Unit,
    onRemoveSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pullToRefreshState = rememberPullToRefreshState()
    BackHandler(enabled = uiState.isSelectionMode, onBack = onExitSelectionMode)

    // 选择模式下底部留出 BottomAppBar 的空间
    val bottomPadding = if (uiState.isSelectionMode) 80.dp else 0.dp

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
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
                NotLoggedInContent(onNavigateToProfile = onNavigateToProfile)
            }

            uiState.error != null && uiState.favorites.isEmpty() -> {
                ErrorContent(
                    error = uiState.error.orEmpty(),
                    onRetry = onRefresh
                )
            }

            uiState.favorites.isEmpty() -> {
                Text(
                    text = "暂无收藏，快去添加喜欢的漫画吧！",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(120.dp),
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 8.dp,
                            bottom = bottomPadding
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 选择模式顶栏
                        if (uiState.isSelectionMode) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "selection_bar") {
                                SelectionTopBar(
                                    selectedCount = uiState.selectedIds.size,
                                    onClose = onExitSelectionMode,
                                    onSelectAll = onSelectAll,
                                    onInvert = onInvertSelection
                                )
                            }
                        }

                        items(
                            items = uiState.favorites,
                            key = { it.comic.pathWord }
                        ) { favorite ->
                            val isSelected = favorite.uuid in uiState.selectedIds
                            val comic = favorite.comic
                            val (statusText, statusColor) = rememberReadingStatus(comic)
                            ComicCard(
                                name = comic.name,
                                cover = comic.cover,
                                subtitle = comic.author?.firstOrNull()?.name,
                                statusText = statusText,
                                statusColor = statusColor,
                                isSelected = isSelected,
                                onClick = {
                                    if (uiState.isSelectionMode) {
                                        onToggleSelection(favorite.uuid)
                                    } else {
                                        onComicClick(comic.pathWord)
                                    }
                                },
                                onLongClick = { onEnterSelectionMode(favorite.uuid) }
                            )
                        }
                    }

                    // 删除中遮罩
                    if (uiState.isDeleting) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }

                    // 选择模式底栏
                    if (uiState.isSelectionMode) {
                        SelectionBottomBar(
                            selectedCount = uiState.selectedIds.size,
                            onDelete = onRemoveSelected,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotLoggedInContent(onNavigateToProfile: () -> Unit) {
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

@Composable
private fun ErrorContent(error: String, onRetry: () -> Unit) {
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
            text = error,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onInvert: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .statusBarsPadding()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                Icons.Default.Close,
                "取消选择",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            "已选 $selectedCount 部",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSelectAll) {
            Icon(
                Icons.Default.SelectAll,
                "全选",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = onInvert) {
            Icon(
                Icons.Default.Deselect,
                "反选",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SelectionBottomBar(
    selectedCount: Int,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    BottomAppBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        actions = {
            Button(
                onClick = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("移除 $selectedCount 部")
            }
        }
    )
}

/**
 * 计算漫画的阅读状态文本与颜色
 * @return Pair(状态文本, 颜色)
 */
@Composable
private fun rememberReadingStatus(comic: FavoriteComic): Pair<String, Color> {
    return when {
        comic.browse == null || comic.browse.chapterUuid.isNullOrEmpty() -> {
            "还未读过" to MaterialTheme.colorScheme.primary
        }

        comic.browse.chapterUuid == comic.lastChapterId -> {
            "上次读到：${comic.browse.chapterName ?: ""}" to MaterialTheme.colorScheme.onSurfaceVariant
        }

        else -> {
            "有新内容：${comic.lastChapterName}" to MaterialTheme.colorScheme.primary
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
            FavoriteItem(
                uuid = 1,
                name = null,
                bFolder = false,
                folderId = null,
                comic = FavoriteComic(
                    uuid = "fav-uuid-1",
                    bDisplay = true,
                    name = "示例漫画：冒险者的旅途",
                    pathWord = "adventure-journey",
                    author = listOf(Author("山田太郎", "yamada-taro")),
                    theme = emptyList(),
                    cover = "https://example.com/cover.jpg",
                    status = 0,
                    popular = 10000,
                    datetimeUpdated = "2025-03-15",
                    lastChapterId = "ch-128",
                    lastChapterName = "第 128 话",
                    browse = BrowseInfo(
                        comicUuid = "fav-uuid-1",
                        pathWord = "adventure-journey",
                        chapterUuid = "ch-120",
                        chapterName = "第 120 话"
                    )
                )
            )
        }

        val (statusText, statusColor) = rememberReadingStatus(mockFavorite.comic)
        ComicCard(
            name = mockFavorite.comic.name,
            cover = mockFavorite.comic.cover,
            subtitle = mockFavorite.comic.author?.firstOrNull()?.name,
            statusText = statusText,
            statusColor = statusColor,
            isSelected = false,
            onClick = {}
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true, showSystemUi = true, name = "书架 - 完整列表")
@Composable
private fun FavoriteGridPreview() {
    CopyManga_NeoTheme {
        val mockFavorites = remember {
            (1..6).map { index ->
                FavoriteItem(
                    uuid = index,
                    name = null,
                    bFolder = false,
                    folderId = null,
                    comic = FavoriteComic(
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
                        author = listOf(Author("作者$index", "author-$index")),
                        theme = emptyList(),
                        cover = "https://example.com/cover$index.jpg",
                        status = index % 2,
                        popular = 1000 * index,
                        datetimeUpdated = "2025-03-${10 + index}",
                        lastChapterId = "ch-$index",
                        lastChapterName = "第 ${100 + index} 话",
                        browse = BrowseInfo(
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

        FavoritesContent(
            uiState = FavoritesUiState(
                favorites = mockFavorites,
                isLoggedIn = true
            ),
            onComicClick = {},
            onNavigateToProfile = {},
            onRefresh = {},
            onExitSelectionMode = {},
            onSelectAll = {},
            onInvertSelection = {},
            onToggleSelection = {},
            onEnterSelectionMode = {},
            onRemoveSelected = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "书架 - 未登录")
@Composable
private fun FavoritesNotLoggedInPreview() {
    CopyManga_NeoTheme {
        FavoritesContent(
            uiState = FavoritesUiState(isLoggedIn = false),
            onComicClick = {},
            onNavigateToProfile = {},
            onRefresh = {},
            onExitSelectionMode = {},
            onSelectAll = {},
            onInvertSelection = {},
            onToggleSelection = {},
            onEnterSelectionMode = {},
            onRemoveSelected = {}
        )
    }
}

// endregion
