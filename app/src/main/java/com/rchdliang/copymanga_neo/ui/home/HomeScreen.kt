package com.rchdliang.copymanga_neo.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.model.Author
import com.rchdliang.copymanga_neo.domain.model.ComicSummary
import com.rchdliang.copymanga_neo.domain.model.ComicTopic
import com.rchdliang.copymanga_neo.domain.repository.RiskControlNotice
import com.rchdliang.copymanga_neo.ui.components.ComicCard
import com.rchdliang.copymanga_neo.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.ui.components.TopicCard
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.floor

/**
 * 首页屏幕
 */

@ExperimentalMaterial3ExpressiveApi
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onComicClick: (String) -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToExplore: (String) -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToTopic: (String, String) -> Unit = { _, _ -> },
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
        onNavigateToExplore = onNavigateToExplore,
        onNavigateToTopic = onNavigateToTopic,
        onLoadMoreTopics = viewModel::loadMoreTopics,
        onRefresh = viewModel::loadComics,
        onViewportChanged = viewModel::onShelfViewportChanged,
        onLoadMore = viewModel::loadMore,
        onRankAudienceSelected = viewModel::selectRankAudience,
        onRankPeriodSelected = viewModel::selectRankPeriod,
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
    modifier: Modifier = Modifier,
    onNavigateToExplore: (String) -> Unit = {},
    onRefresh: () -> Unit,
    onNavigateToTopic: (String, String) -> Unit = { _, _ -> },
    onLoadMoreTopics: () -> Unit = {},
    onViewportChanged: (HomeShelf, Int) -> Unit = { _, _ -> },
    onLoadMore: (HomeShelf, Boolean) -> Unit = { _, _ -> },
    onRankAudienceSelected: (String) -> Unit = {},
    onRankPeriodSelected: (String) -> Unit = {},
    onRetryAfterRisk: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {}
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
                            Text(
                                "查看本地下载",
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        TextButton(onClick = onRetryAfterRisk) {
                            Text("重试", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                (uiState.error != null && uiState.recComicList.isEmpty() &&
                        uiState.hotComics.isEmpty() && uiState.newComics.isEmpty() && uiState.finishComics.isEmpty()) -> {
                    val errorMsg = uiState.error
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
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        if (uiState.recComicList.isNotEmpty() || HomeShelf.RECOMMENDED in uiState.paging) item(
                            key = "recommended"
                        ) {
                            HomeComicSection(
                                "漫画推荐",
                                uiState.recComicList,
                                "home-recommended",
                                onComicClick,
                                onMore = { onNavigateToExplore("recommended") },
                                shelf = HomeShelf.RECOMMENDED,
                                paging = uiState.paging[HomeShelf.RECOMMENDED],
                                filterReady = uiState.filterReady,
                                onViewportChanged = onViewportChanged,
                                onLoadMore = onLoadMore
                            )
                        }
                        if (uiState.hotComics.isNotEmpty()) item(key = "hot") {
                            HomeComicSection(
                                "热门更新",
                                uiState.hotComics,
                                "home-hot",
                                onComicClick
                            )
                        }
                        if (uiState.newComics.isNotEmpty() || HomeShelf.NEWEST in uiState.paging) item(
                            key = "newest"
                        ) {
                            HomeComicSection(
                                "最新上架",
                                uiState.newComics,
                                "home-new",
                                onComicClick,
                                onMore = { onNavigateToExplore("newest") },
                                shelf = HomeShelf.NEWEST,
                                paging = uiState.paging[HomeShelf.NEWEST],
                                filterReady = uiState.filterReady,
                                onViewportChanged = onViewportChanged,
                                onLoadMore = onLoadMore
                            )
                        }
                        if (uiState.rankComics.isNotEmpty() || HomeShelf.RANKS in uiState.paging) item(
                            key = "ranks"
                        ) {
                            HomeComicSection(
                                "排行榜",
                                uiState.rankComics,
                                "home-ranks",
                                onComicClick,
                                onMore = { onNavigateToExplore("ranks?audience=${uiState.rankAudience}&period=${uiState.rankPeriod}") },
                                shelf = HomeShelf.RANKS,
                                paging = uiState.paging[HomeShelf.RANKS],
                                filterReady = uiState.filterReady,
                                onViewportChanged = onViewportChanged,
                                onLoadMore = onLoadMore,
                                ranked = true,
                                rankPositions = uiState.rankPositions,
                                rankAudience = uiState.rankAudience,
                                rankPeriod = uiState.rankPeriod,
                                onRankAudienceSelected = onRankAudienceSelected,
                                onRankPeriodSelected = onRankPeriodSelected
                            )
                        }
                        if (uiState.topics.isNotEmpty()) item(key = "topics") {
                            HomeTopicsSection(
                                topics = uiState.topics,
                                paging = uiState.topicPaging,
                                onLoadMore = onLoadMoreTopics,
                                onTopicClick = { onNavigateToTopic(it.pathWord, it.title) },
                                onMore = { onNavigateToExplore("topics") })
                        }
                        if (uiState.finishComics.isNotEmpty() || HomeShelf.FINISHED in uiState.paging) item(
                            key = "finished"
                        ) {
                            HomeComicSection(
                                "完结漫画",
                                uiState.finishComics,
                                "home-finished",
                                onComicClick,
                                onMore = { onNavigateToExplore("finished") },
                                shelf = HomeShelf.FINISHED,
                                paging = uiState.paging[HomeShelf.FINISHED],
                                filterReady = uiState.filterReady,
                                onViewportChanged = onViewportChanged,
                                onLoadMore = onLoadMore
                            )
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
                text = "搜索 & 探索",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/** 分区容器将标题、跳转入口和首页预览统一包裹。 */
@Composable
private fun HomeComicSection(
    title: String,
    comics: List<ComicSummary>,
    transitionGroupKey: String,
    onComicClick: (String) -> Unit,
    onMore: (() -> Unit)? = null,
    shelf: HomeShelf? = null,
    paging: HomeShelfPaging? = null,
    filterReady: Boolean = true,
    onViewportChanged: (HomeShelf, Int) -> Unit = { _, _ -> },
    onLoadMore: (HomeShelf, Boolean) -> Unit = { _, _ -> },
    ranked: Boolean = false,
    rankPositions: Map<String, Int> = emptyMap(),
    rankAudience: String = "male",
    rankPeriod: String = "day",
    onRankAudienceSelected: (String) -> Unit = {},
    onRankPeriodSelected: (String) -> Unit = {}
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            HomeSectionHeader(
                title,
                onMore,
                if (ranked) rankAudience else null,
                onRankAudienceSelected
            )
            if (ranked) {
                HomeRankChoices(
                    rankPeriod,
                    onRankPeriodSelected
                )
            }
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val visibleCards = floor(((maxWidth + 8.dp) / 128.dp).toDouble())
                    .toInt().coerceAtLeast(3)
                val cardWidth = (maxWidth - 8.dp * (visibleCards - 1)) / visibleCards
                val listState = rememberLazyListState()
                LaunchedEffect(shelf, visibleCards) {
                    if (shelf != null) onViewportChanged(shelf, visibleCards)
                }
                LaunchedEffect(
                    shelf, visibleCards, comics.size, filterReady,
                    paging?.nextOffset, paging?.isLoading, paging?.error, paging?.requiredCount
                ) {
                    if (shelf != null && filterReady && paging?.nextOffset != null &&
                        !paging.isLoading && paging.error == null &&
                        comics.size < maxOf(visibleCards + 1, paging.requiredCount)
                    ) {
                        onLoadMore(shelf, true)
                    }
                }
                LaunchedEffect(rankAudience, rankPeriod) {
                    if (ranked) listState.scrollToItem(0)
                }
                LaunchedEffect(
                    shelf,
                    comics.size,
                    filterReady,
                    paging?.nextOffset,
                    paging?.isLoading,
                    paging?.error
                ) {
                    if (shelf != null && filterReady && paging?.nextOffset != null && !paging.isLoading && paging.error == null) {
                        snapshotFlow {
                            listState.firstVisibleItemIndex > 0 &&
                                    (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                                        ?: -1) >= comics.lastIndex - 1
                        }.distinctUntilChanged().collect { nearEnd ->
                            if (nearEnd) onLoadMore(shelf, false)
                        }
                    }
                }
                ComicGrid(
                    comics = comics,
                    transitionGroupKey = transitionGroupKey,
                    onComicClick = onComicClick,
                    ranked = ranked,
                    rankPositions = rankPositions,
                    cardWidth = cardWidth,
                    placeholderCount = if (paging?.nextOffset != null || paging?.isLoading == true)
                        (visibleCards - comics.size).coerceAtLeast(0) else 0,
                    state = listState,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (paging?.error != null) {
                TextButton(onClick = { if (shelf != null) onLoadMore(shelf, false) }) {
                    Text("加载更多失败，点击重试", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    onMore: (() -> Unit)?,
    rankAudience: String? = null,
    onRankAudienceSelected: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onMore != null, role = Role.Button) {
                onMore?.invoke()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (rankAudience != null) {
                val nextAudience = if (rankAudience == "male") "female" else "male"
                Text(
                    if (rankAudience == "male") "男榜" else "女榜",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(
                            role = Role.Button,
                            onClickLabel = if (nextAudience == "female") "切换至女榜" else "切换至男榜"
                        ) {
                            onRankAudienceSelected(nextAudience)
                        }
                        .padding(vertical = 14.dp, horizontal = 2.dp)
                )
            }
        }
        if (onMore != null) {
            Text(
                "查看更多  ›", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun HomeRankChoices(
    period: String,
    onPeriodSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val periods = listOf(
            "day" to "日榜", "week" to "周榜", "month" to "月榜", "total" to "总榜"
        )
        periods.forEach { (value, label) ->
            FilterChip(
                selected = period == value, onClick = { onPeriodSelected(value) },
                label = { Text(label, color = MaterialTheme.colorScheme.onSurface) })
        }
    }
}

/** 专题横向列表按窗口宽度展示，到达末尾时分页加载。 */
@Composable
private fun HomeTopicsSection(
    topics: List<ComicTopic>,
    paging: HomeShelfPaging,
    onLoadMore: () -> Unit,
    onTopicClick: (ComicTopic) -> Unit,
    onMore: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            HomeSectionHeader("漫画专题", onMore)
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val visibleCards = ((maxWidth + 12.dp) / 280.dp).toInt().coerceAtLeast(1)
                val cardWidth = if (visibleCards == 1 && topics.size > 1)
                    (maxWidth * 0.9f).coerceAtMost(360.dp)
                else (maxWidth - 12.dp * (visibleCards - 1)) / visibleCards
                val listState = rememberLazyListState()
                LaunchedEffect(
                    topics.size,
                    visibleCards,
                    paging.nextOffset,
                    paging.isLoading,
                    paging.error
                ) {
                    if (paging.nextOffset != null && !paging.isLoading && paging.error == null) {
                        snapshotFlow {
                            val layout = listState.layoutInfo
                            layout.visibleItemsInfo.isNotEmpty() &&
                                    (topics.size <= visibleCards || listState.firstVisibleItemIndex > 0) &&
                                    (layout.visibleItemsInfo.lastOrNull()?.index
                                        ?: -1) >= topics.lastIndex - 1
                        }.distinctUntilChanged().collect { nearEnd -> if (nearEnd) onLoadMore() }
                    }
                }
                LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(topics, key = { it.pathWord }) { topic ->
                        TopicCard(
                            topic,
                            onClick = { onTopicClick(topic) },
                            modifier = Modifier.width(cardWidth)
                        )
                    }
                    if (paging.isLoading || paging.error != null) item(key = "topic-pagination") {
                        Column(
                            Modifier
                                .width(128.dp)
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (paging.isLoading) CircularProgressIndicator(Modifier.size(28.dp))
                            else TextButton(onClick = onLoadMore) {
                                Text("加载失败，重试", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComicGrid(
    comics: List<ComicSummary>,
    transitionGroupKey: String,
    onComicClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    ranked: Boolean = false,
    rankPositions: Map<String, Int> = emptyMap(),
    cardWidth: Dp = 120.dp,
    placeholderCount: Int = 0,
    state: LazyListState = rememberLazyListState()
) {
    LazyRow(
        contentPadding = PaddingValues(0.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        state = state,
        modifier = modifier
    ) {
        itemsIndexed(
            items = comics,
            key = { _, comic -> comic.pathWord },
            contentType = { _, _ -> "comic_card" }
        ) { index, comic ->
            ComicCard(
                name = comic.name,
                cover = comic.cover,
                subtitle = comic.author?.firstOrNull()?.name,
                statusText = if (ranked) "第 ${rankPositions[comic.pathWord] ?: (index + 1)} 位" else null,
                sharedTransitionKey = "$transitionGroupKey:${comic.pathWord}",
                comicPathWord = comic.pathWord,
                onClick = { onComicClick(comic.pathWord) },
                modifier = Modifier.width(cardWidth)
            )
        }
        items(placeholderCount, key = { "placeholder-$it" }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .width(cardWidth)
                    .aspectRatio(3f / 4f)
            ) {}
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
            finishComics = comics.takeLast(5),
            rankComics = comics,
            topics = listOf(
                ComicTopic("preview-summer", "夏番漫畫2026 必看熱門漫畫推薦第41期", null, null),
                ComicTopic(
                    "preview-awards",
                    "这本漫画真厉害2026获奖作品 必看热门漫画推荐第40期",
                    null,
                    null
                ),
                ComicTopic(
                    "preview-animation",
                    "最期待2026動畫化漫畫 必看热门漫画推荐第39期",
                    null,
                    null
                ),
                ComicTopic("preview-spring", "春番漫畫2026 必看熱門漫畫推薦第40期", null, null)
            )
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

/** 独立预览首页中部，直接展示排行与专题，不需要从推荐区滚动。 */
@Preview(name = "首页中部 - 手机", showBackground = true, widthDp = 411, heightDp = 600)
@Preview(
    name = "首页中部 - 手机深色",
    showBackground = true,
    widthDp = 411,
    heightDp = 600,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Preview(name = "首页中部 - 横屏平板", showBackground = true, widthDp = 1200, heightDp = 600)
@Preview(
    name = "首页中部 - 横屏平板深色",
    showBackground = true,
    widthDp = 1200,
    heightDp = 600,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun HomeRanksAndTopicsPreview() {
    val state = rememberHomePreviewState()
    CopyManga_NeoTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    HomeComicSection(
                        title = "排行榜",
                        comics = state.rankComics,
                        transitionGroupKey = "preview-ranks",
                        onComicClick = {},
                        onMore = {},
                        ranked = true,
                        rankAudience = "male",
                        rankPeriod = "week"
                    )
                }
                item {
                    HomeTopicsSection(
                        topics = state.topics,
                        paging = HomeShelfPaging(),
                        onLoadMore = {},
                        onTopicClick = {},
                        onMore = {}
                    )
                }
            }
        }
    }
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
