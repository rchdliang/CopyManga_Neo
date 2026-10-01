package com.rchdliang.copymanga_neo.ui.explore

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.rchdliang.copymanga_neo.domain.model.ComicFilterOption
import com.rchdliang.copymanga_neo.domain.model.ComicTopic
import com.rchdliang.copymanga_neo.ui.components.ComicCard
import com.rchdliang.copymanga_neo.ui.components.comicGridColumns
import kotlinx.coroutines.flow.distinctUntilChanged

/** 排行、更新、专题和筛选共用的漫画发现页面。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicExploreScreen(
    onComicClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ComicExploreViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val back = {
        if (state.topic != null && !viewModel.openedFromTopic) viewModel.closeTopic()
        else onNavigateBack()
    }
    BackHandler(enabled = state.topic != null, onBack = back)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.topic?.title ?: state.section.title,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val topicsOnly = state.topic == null && state.section == ExploreSection.TOPICS
            val columns = if (topicsOnly) ((maxWidth - 32.dp) / 360.dp).toInt().coerceAtLeast(1)
            else comicGridColumns(maxWidth - 32.dp)
            val gridState = rememberLazyGridState()
            LaunchedEffect(
                state.section, state.topic?.pathWord, state.theme, state.top,
                state.ordering, state.rankAudience, state.rankPeriod
            ) {
                gridState.scrollToItem(0)
            }
            LaunchedEffect(
                state.nextOffset,
                state.isLoading,
                state.isLoadingMore,
                state.error,
                columns
            ) {
                if (state.nextOffset != null && !state.isLoading && !state.isLoadingMore && state.error == null) {
                    snapshotFlow {
                        val layout = gridState.layoutInfo
                        layout.totalItemsCount > 0 && (layout.visibleItemsInfo.lastOrNull()?.index
                            ?: -1) >=
                                layout.totalItemsCount - columns * 2 - 1
                    }.distinctUntilChanged()
                        .collect { nearEnd -> if (nearEnd) viewModel.loadMore() }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.topic?.fullResolutionCover?.takeIf { it.isNotBlank() }?.let { cover ->
                    item(key = "topic_cover", span = { GridItemSpan(maxLineSpan) }) {
                        AsyncImage(
                            model = cover,
                            contentDescription = state.topic?.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1920f / 412f)
                        )
                    }
                }
                if (state.topic == null && state.section == ExploreSection.RANKS) {
                    item(key = "ranks", span = { GridItemSpan(maxLineSpan) }) {
                        RankChoices(
                            state,
                            viewModel
                        )
                    }
                }
                if (state.topic == null && state.section == ExploreSection.FILTER) {
                    item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                        FilterChoices(
                            state,
                            viewModel
                        )
                    }
                }
                state.topic?.brief?.takeIf { it.isNotBlank() }?.let { brief ->
                    item(key = "brief", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            brief, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (state.isLoading) {
                    item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                if (state.error != null && state.comics.isEmpty() && state.topics.isEmpty()) {
                    item(
                        key = "error",
                        span = { GridItemSpan(maxLineSpan) }) {
                        ErrorMessage(
                            state.error.orEmpty(),
                            viewModel::refresh
                        )
                    }
                }
                if (!state.isLoading && state.error == null && state.comics.isEmpty() && state.topics.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "暂无内容", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        )
                    }
                }
                if (state.topic == null && state.section == ExploreSection.TOPICS) {
                    items(state.topics, key = { "topic:${it.pathWord}" }) { topic ->
                        TopicCard(topic = topic, onClick = { viewModel.openTopic(topic) })
                    }
                } else {
                    itemsIndexed(
                        state.comics,
                        key = { _, comic -> "comic:${comic.pathWord}" }) { index, comic ->
                        ComicCard(
                            name = comic.name,
                            cover = comic.cover,
                            subtitle = comic.author?.firstOrNull()?.name,
                            statusText = if (state.section == ExploreSection.RANKS && state.topic == null)
                                "第 ${index + 1} 位" else null,
                            sharedTransitionKey = "explore-${state.section.route}:${comic.pathWord}",
                            comicPathWord = comic.pathWord,
                            onClick = { onComicClick(comic.pathWord) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (state.nextOffset != null || state.isLoadingMore || state.error != null &&
                    (state.comics.isNotEmpty() || state.topics.isNotEmpty())
                ) {
                    item(key = "pagination", span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (state.isLoadingMore) CircularProgressIndicator(
                                modifier = Modifier.size(
                                    28.dp
                                )
                            )
                            else TextButton(
                                onClick = viewModel::loadMore,
                                enabled = state.nextOffset != null
                            ) {
                                Text(
                                    if (state.error != null) "重试加载" else "加载更多",
                                    color = MaterialTheme.colorScheme.primary
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
private fun RankChoices(state: ComicExploreUiState, viewModel: ComicExploreViewModel) {
    Column {
        OptionRow(
            "受众", listOf(ComicFilterOption("男榜", "male"), ComicFilterOption("女榜", "female")),
            state.rankAudience, viewModel::selectRankAudience
        )
        val periods = listOf(
            ComicFilterOption("日榜", "day"), ComicFilterOption("周榜", "week"),
            ComicFilterOption("月榜", "month"), ComicFilterOption("总榜", "total")
        )
        OptionRow("时间", periods, state.rankPeriod, viewModel::selectRankPeriod)
    }
}

@Composable
private fun FilterChoices(state: ComicExploreUiState, viewModel: ComicExploreViewModel) {
    Column {
        state.filterError?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        val options = state.filters
        OptionRow(
            "类型", listOf(ComicFilterOption("全部", "")) + options?.tops.orEmpty(),
            state.top.orEmpty(), { viewModel.selectTop(it.ifEmpty { null }) })
        OptionRow(
            "题材", listOf(ComicFilterOption("全部", "")) + options?.themes.orEmpty(),
            state.theme.orEmpty(), { viewModel.selectTheme(it.ifEmpty { null }) })
        OptionRow(
            "排序",
            options?.orderings.orEmpty()
                .ifEmpty { listOf(ComicFilterOption("最近更新", "-datetime_updated")) },
            state.ordering,
            viewModel::selectOrdering
        )
    }
}

@Composable
private fun OptionRow(
    title: String,
    options: List<ComicFilterOption>,
    selected: String,
    onSelect: (String) -> Unit
) {
    if (options.isEmpty()) return
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options, key = { it.pathWord }) { option ->
            FilterChip(
                selected = selected == option.pathWord,
                onClick = { onSelect(option.pathWord) },
                label = { Text(option.name, color = MaterialTheme.colorScheme.onSurface) }
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun TopicCard(topic: ComicTopic, onClick: () -> Unit) {
    com.rchdliang.copymanga_neo.ui.components.TopicCard(topic, onClick, Modifier.fillMaxWidth())
}

@Composable
private fun ErrorMessage(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
        TextButton(onClick = onRetry) { Text("重试", color = MaterialTheme.colorScheme.primary) }
    }
}
