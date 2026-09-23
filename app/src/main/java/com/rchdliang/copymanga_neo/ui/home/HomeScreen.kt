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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.model.Author
import com.rchdliang.copymanga_neo.domain.model.ComicSummary
import com.rchdliang.copymanga_neo.domain.repository.RiskControlNotice
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
    onNavigateToDownloads: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val toastHost = LocalToastHost.current

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            toastHost.showToast(it)
            viewModel.clearToastMessage()
        }
    }

    if (uiState.showRiskDialog) {
        HomeRiskControlDialog(
            reason = uiState.riskControl?.reason.orEmpty(),
            onDismiss = viewModel::dismissRiskDialog,
            onNavigateToDownloads = onNavigateToDownloads
        )
    }

    HomeContent(
        uiState = uiState,
        onComicClick = onComicClick,
        onNavigateToSearch = onNavigateToSearch,
        onRefresh = viewModel::loadComics,
        onRetryAfterRisk = viewModel::retryAfterRiskControl,
        onNavigateToDownloads = onNavigateToDownloads
    )
}

/** 与页面状态分离，运行时和 Preview 共用同一个风控弹窗。 */
@Composable
private fun HomeRiskControlDialog(
    reason: String,
    onDismiss: () -> Unit,
    onNavigateToDownloads: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("已触发风控", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Text(
                "当前ip被官方暂时限制访问，已停止网络请求。请等待限制解除后再试。\n如果近期频繁无故触发风控，请等待开发者更新。\n\n$reason",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了", color = MaterialTheme.colorScheme.primary)
            }
        },
//        dismissButton = {
//            TextButton(onClick = {
//                onDismiss()
//                onNavigateToDownloads()
//            }) { Text("查看本地下载", color = MaterialTheme.colorScheme.primary) }
//        }
    )
}

/**
 * 首页纯 UI 内容，便于 Preview 在不创建 Hilt ViewModel 的情况下展示完整页面。
 */
@ExperimentalMaterial3ExpressiveApi
@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onComicClick: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onRefresh: () -> Unit,
    onRetryAfterRisk: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pullToRefreshState = rememberPullToRefreshState()

    Column(modifier = modifier.fillMaxSize()) {
        SearchEntryBar(
            onClick = onNavigateToSearch,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = onRefresh,
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
            uiState.riskControl != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "当前网络已被风控", style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "已暂停网络请求，请更换代理节点或等待官方解除限制。\n本地下载仍可正常阅读。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    FilledTonalButton(onClick = onNavigateToDownloads) {
                        Text("查看本地下载", color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    TextButton(onClick = onRetryAfterRisk) {
                        Text("重试", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            (uiState.error != null && uiState.recComicList.isEmpty() &&
                    uiState.hotComics.isEmpty() && uiState.newComics.isEmpty() && uiState.finishComics.isEmpty()) -> {
                val errorMsg = uiState.error.orEmpty()
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
                        text = errorMsg,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = onRefresh,
                    ) { Text("重试", color = MaterialTheme.colorScheme.onSecondaryContainer) }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
//                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    uiState.filterWarning?.let { warning ->
                        item(key = "filter_warning") {
                            Surface(
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Info, null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        warning, style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
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
                                transitionGroupKey = "home-recommended",
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
                                transitionGroupKey = "home-hot",
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
                                transitionGroupKey = "home-new",
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
                                transitionGroupKey = "home-finished",
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
    transitionGroupKey: String,
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
                sharedTransitionKey = "$transitionGroupKey:${comic.pathWord}",
                comicPathWord = comic.pathWord,
                onClick = { onComicClick(comic.pathWord) },
                modifier = Modifier.width(120.dp)
            )
        }
    }
}

/** Preview 只传入静态状态，封面沿用 ComicCard 的本地预览资源。 */
@Composable
private fun rememberHomePreviewState(): HomeUiState {
    return remember {
        val comics = (1..8).map { index ->
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
                cover = null,
                author = listOf(Author("作者$index", "author-$index")),
                females = emptyList(),
                males = emptyList(),
                theme = emptyList(),
                popular = 1000 * index,
                authorAlias = "别名$index"
            )
        }
        HomeUiState(
            recComicList = comics.take(5),
            hotComics = comics.drop(2).take(5),
            newComics = comics.reversed().take(5),
            finishComics = comics.takeLast(5)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomePreviewContent(uiState: HomeUiState) {
    CopyManga_NeoTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HomeContent(
                uiState = uiState,
                onComicClick = {},
                onNavigateToSearch = {},
                onRefresh = {}
            )
            if (uiState.showRiskDialog) {
                HomeRiskControlDialog(
                    reason = uiState.riskControl?.reason.orEmpty(),
                    onDismiss = {},
                    onNavigateToDownloads = {}
                )
            }
        }
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    name = "首页 - 完整内容",
    widthDp = 411,
    heightDp = 891
)
@Composable
private fun HomeScreenPreview() {
    HomePreviewContent(rememberHomePreviewState())
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    name = "首页 - 加载中",
    widthDp = 411,
    heightDp = 891
)
@Composable
private fun HomeLoadingPreview() {
    HomePreviewContent(HomeUiState(isLoading = true))
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    name = "首页 - 加载失败",
    widthDp = 411,
    heightDp = 891
)
@Composable
private fun HomeErrorPreview() {
    HomePreviewContent(HomeUiState(error = "网络连接失败，请检查网络后重试"))
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    name = "首页 - 标签暂不可用",
    widthDp = 411,
    heightDp = 891
)

@Composable
private fun HomeTagWarningPreview() {
    HomePreviewContent(
        rememberHomePreviewState().copy(
            filterWarning = "部分漫画的标签暂时无法获取，已先展示漫画。标签加载完成后会自动应用黑名单。"
        )
    )
}

private fun homeRiskPreviewState(showDialog: Boolean = false) = HomeUiState(
    riskControl = RiskControlNotice(detectedAt = 1L, reason = "请求过于频繁，请稍后再试（code 210）"),
    showRiskDialog = showDialog
)

@Preview(
    showBackground = true,
    showSystemUi = true,
    name = "首页 - 风控暂停",
    widthDp = 411,
    heightDp = 891
)

@Composable
private fun HomeRiskControlPreview() {
    HomePreviewContent(homeRiskPreviewState())
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    name = "首页 - 风控弹窗",
    widthDp = 411,
    heightDp = 891
)

@Composable
private fun HomeRiskDialogPreview() {
    HomePreviewContent(homeRiskPreviewState(showDialog = true))
}
