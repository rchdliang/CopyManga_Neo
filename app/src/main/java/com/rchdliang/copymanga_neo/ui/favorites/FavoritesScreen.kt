package com.rchdliang.copymanga_neo.ui.favorites

import androidx.compose.runtime.*
import com.rchdliang.copymanga_neo.ui.components.ConfirmRemovalDialog
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.rchdliang.copymanga_neo.ui.components.GridLoadMoreEffect
import com.rchdliang.copymanga_neo.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.ui.components.NovelTransitionCard
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme

/**
 * 收藏夹屏幕
 */
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel = hiltViewModel(),
    onComicClick: (String) -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onDiscover: () -> Unit = {},
    onNovelClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmRemoval by remember(uiState.selectedIds) { mutableStateOf(false) }
    if (confirmRemoval) ConfirmRemovalDialog(
        "移出书架？", "将从当前书架移除 ${uiState.selectedIds.size} 部作品；此操作不会删除下载文件。",
        onConfirm = { confirmRemoval = false; viewModel.removeSelected() },
        onDismiss = { confirmRemoval = false }, confirmLabel = "移出书架"
    )
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
        onDiscover = onDiscover,
        onRefresh = viewModel::refreshSelectedShelf,
        onKindSelected = viewModel::selectKind,
        onNovelClick = onNovelClick,
        onMoreNovels = viewModel::loadMoreNovels,
        onRetryMoreNovels = viewModel::retryMoreNovels,
        onExitSelectionMode = viewModel::exitSelectionMode,
        onSelectAll = viewModel::selectAll,
        onInvertSelection = viewModel::invertSelection,
        onToggleSelection = viewModel::toggleSelection,
        onEnterSelectionMode = viewModel::enterSelectionMode,
        onRemoveSelected = { confirmRemoval = true }
    )
}

/**
 * 收藏页纯 UI 内容，Preview 可覆盖登录、空态、列表和多选状态。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
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
    modifier: Modifier = Modifier,
    onKindSelected: (FavoritesKind) -> Unit = {},
    onDiscover: () -> Unit = {},
    onNovelClick: (String) -> Unit = {},
    onMoreNovels: () -> Unit = {},
    onRetryMoreNovels: () -> Unit = {}
) {
    com.rchdliang.copymanga_neo.ui.components.ComicPreloadEffect(uiState.favorites.map { it.comic.pathWord })
    val pullToRefreshState = rememberPullToRefreshState()
    BackHandler(enabled = uiState.isSelectionMode, onBack = onExitSelectionMode)
    val showingNovels = uiState.selectedKind == FavoritesKind.NOVEL
    val isLoading = if (showingNovels) uiState.novelsLoading else uiState.isLoading
    val error = if (showingNovels) uiState.novelsError else uiState.error
    val isEmpty = if (showingNovels) uiState.novels.isEmpty() else uiState.favorites.isEmpty()
    val comicGrid = rememberLazyGridState()
    val novelGrid = rememberLazyGridState()
    GridLoadMoreEffect(
        novelGrid,
        showingNovels && uiState.isLoggedIn && uiState.novelsHaveMore &&
                !uiState.novelsLoading && !uiState.novelsLoadingMore && !uiState.isDeleting &&
                uiState.novelsError == null && uiState.novelLoadMoreError == null,
        uiState.novels.size, onMoreNovels
    )

    // 选择模式下底部留出 BottomAppBar 的空间
    val bottomPadding = if (uiState.isSelectionMode) 80.dp else 0.dp

    Column(modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = uiState.selectedKind.ordinal) {
            FavoritesKind.entries.forEach { kind ->
                val selected = uiState.selectedKind == kind
                Tab(
                    selected = selected,
                    enabled = !uiState.isDeleting,
                    onClick = { onKindSelected(kind) },
                    text = {
                        Text(
                            kind.label,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = pullToRefreshState,
            contentAlignment = Alignment.TopCenter,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = isLoading,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            when {
                !uiState.isLoggedIn -> {
                    NotLoggedInContent(onNavigateToProfile = onNavigateToProfile)
                }

                error != null && isEmpty -> {
                    ErrorContent(
                        error = error,
                        onRetry = onRefresh
                    )
                }

                isEmpty && isLoading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                isEmpty -> {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("还没有收藏", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onDiscover) { Text("去发现作品") }
                    }
                }

                else -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            state = if (showingNovels) novelGrid else comicGrid,
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
                            if (error != null) item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = "refresh_error"
                            ) {
                                TextButton(onClick = onRefresh) {
                                    Text(
                                        "$error，点击重试",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
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

                            if (showingNovels) {
                                items(uiState.novels, key = { "novel:${it.collectId}" }) { entry ->
                                    val book = entry.book
                                    val readingStatus = uiState.novelReadingStatuses[book.path]
                                        ?: NovelShelfReadingStatus()
                                    NovelTransitionCard(
                                        book = book, source = "bookshelf",
                                        statusText = readingStatus.text,
                                        statusColor = if (readingStatus.highlighted)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        isSelected = entry.collectId in uiState.selectedIds,
                                        selectionMode = uiState.isSelectionMode,
                                        onClick = {
                                            if (uiState.isSelectionMode) onToggleSelection(entry.collectId)
                                            else onNovelClick(book.path)
                                        },
                                        onLongClick = { onEnterSelectionMode(entry.collectId) }
                                    )
                                }
                                if (uiState.novelsLoadingMore || uiState.novelLoadMoreError != null) {
                                    item(
                                        span = { GridItemSpan(maxLineSpan) },
                                        key = "novel_pagination"
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (uiState.novelsLoadingMore) CircularProgressIndicator(
                                                Modifier.size(28.dp)
                                            )
                                            else TextButton(onClick = onRetryMoreNovels) {
                                                Text(
                                                    "加载更多失败，点击重试",
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            } else items(
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
                                    sharedTransitionKey = "bookshelf:${comic.pathWord}",
                                    comicPathWord = comic.pathWord,
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
            text = "登录后查看书架",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "漫画与轻小说收藏将在这里显示",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onNavigateToProfile) {
            Text("前往登录", color = MaterialTheme.colorScheme.onSecondaryContainer)
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
        FilledTonalButton(onClick = onRetry) {
            Text(
                "重试",
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
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
                Text("移除 $selectedCount 部", color = MaterialTheme.colorScheme.onError)
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
        comic.browse == null || comic.browse.chapterUuid.isNullOrBlank() -> {
            "还未读过" to MaterialTheme.colorScheme.primary
        }

        comic.hasUnreadUpdate -> {
            "有新内容：${comic.lastChapterName}" to MaterialTheme.colorScheme.primary
        }

        else -> {
            "上次读到：${comic.browse.chapterName ?: ""}" to MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
}

// region Previews

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
