package com.rchdliang.copymanga_neo.ui.search

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.model.Author
import com.rchdliang.copymanga_neo.domain.model.ComicFilterDefaults
import com.rchdliang.copymanga_neo.domain.model.ComicFilterOption
import com.rchdliang.copymanga_neo.domain.model.ComicSummary
import com.rchdliang.copymanga_neo.domain.model.QueryType
import com.rchdliang.copymanga_neo.domain.model.ShareTarget
import com.rchdliang.copymanga_neo.ui.components.ComicCard
import com.rchdliang.copymanga_neo.ui.components.GridLoadMoreEffect
import com.rchdliang.copymanga_neo.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.ui.components.ShareAction
import com.rchdliang.copymanga_neo.ui.components.comicContainerSharedBounds
import com.rchdliang.copymanga_neo.ui.components.comicCoverSharedElement
import com.rchdliang.copymanga_neo.ui.components.comicGridColumns
import com.rchdliang.copymanga_neo.ui.components.rememberComicSharedTransitionClick
import com.rchdliang.copymanga_neo.ui.preview.PreviewAwareAsyncImage
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme
import com.rchdliang.copymanga_neo.ui.util.toCompactLocale

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    onComicClick: (String) -> Unit,
    onNavigateToExplore: (String?, String?) -> Unit = { _, _ -> },
    onNavigateBack: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast = LocalToastHost.current
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { toast.showToast(it); viewModel.clearToastMessage() }
    }
    SearchScreen(
        state, viewModel::updateQuery, viewModel::search, viewModel::clearSearch,
        viewModel::updateQueryType, onComicClick,
        onLoadMore = viewModel::loadMore,
        onDiscoveryThemeSelected = viewModel::selectDiscoveryTheme,
        onDiscoveryTopSelected = viewModel::selectDiscoveryTop,
        onNavigateBack = onNavigateBack,
        onRefresh = viewModel::refresh,
        onLayoutToggle = viewModel::toggleLayout,
        onOrderingSelected = viewModel::selectOrdering
    )
}

/** 一个搜索入口：空关键词显示发现条件，有关键词显示搜索结果。 */
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onQueryTypeChange: (QueryType?) -> Unit,
    onComicClick: (String) -> Unit,
    onLoadMore: () -> Unit = {},
    onDiscoveryThemeSelected: (ComicFilterOption?) -> Unit = {},
    onDiscoveryTopSelected: (ComicFilterOption?) -> Unit = {},
    onNavigateToExplore: (String?, String?) -> Unit = { _, _ -> },
    onNavigateBack: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onLayoutToggle: () -> Unit = {},
    onOrderingSelected: (String) -> Unit = {},
    pullToRefreshState: PullToRefreshState = rememberPullToRefreshState()
) {
    var allTags by rememberSaveable { mutableStateOf(false) }
    if (allTags) {
        AllTagsScreen(
            uiState.discoveryOptions?.themes.orEmpty(),
            uiState.selectedDiscoveryTheme?.pathWord,
            onSelect = { onDiscoveryThemeSelected(it); allTags = false },
            onBack = { allTags = false }
        )
        return
    }
    val searchMode = uiState.isKeywordSearch
    val share = if (searchMode) {
        uiState.submittedQuery.takeIf { it.isNotBlank() }?.let {
            ShareTarget.search(it, uiState.submittedQueryType)
        }
    } else ShareTarget.comics(
        uiState.selectedDiscoveryTheme?.name ?: "发现漫画",
        uiState.selectedDiscoveryTheme?.pathWord,
        uiState.selectedDiscoveryTop?.pathWord,
        uiState.ordering
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TopAppBar(
                    modifier = Modifier.widthIn(max = 1040.dp),
                    title = {
                        Text(
                            "搜索",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    actions = { share?.let { ShareAction(it) } }
                )
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                Modifier
                    .widthIn(max = 1040.dp)
                    .fillMaxSize()
            ) {
                SearchInput(uiState.query, onQueryChange, onSearch, onClear, onNavigateBack)
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing, onRefresh = onRefresh,
                    state = pullToRefreshState, modifier = Modifier.weight(1f)
                ) {
                    SearchResultsContent(
                        uiState,
                        onQueryTypeChange,
                        onDiscoveryThemeSelected,
                        onDiscoveryTopSelected,
                        onComicClick,
                        onLoadMore,
                        onRefresh,
                        { allTags = true },
                        onLayoutToggle,
                        onOrderingSelected
                    )
                }
            }
        }
    }
}

/** 恢复原来的 Material SearchBar，搜索命令只放在输入框内部。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchInput(
    query: String, onChange: (String) -> Unit, onSearch: (String) -> Unit,
    onClear: () -> Unit, onBack: () -> Unit, placeholder: String = "搜索漫画"
) {
    val text = rememberTextFieldState(query)
    val state = rememberSearchBarState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(query) {
        if (text.text.toString() != query) text.setTextAndPlaceCursorAtEnd(query)
    }
    LaunchedEffect(text.text.toString()) { onChange(text.text.toString()) }
    val submit = {
        onSearch(text.text.toString()); keyboard?.hide(); focus.clearFocus()
    }
    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                textFieldState = text, searchBarState = state, onSearch = { submit() },
                placeholder = {
                    Text(
                        placeholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                trailingIcon = {
                    if (text.text.isNotEmpty()) Row {
                        IconButton(onClick = submit) {
                            Icon(
                                Icons.Default.Search,
                                "搜索",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = onClear) {
                            Icon(
                                Icons.Default.Clear,
                                "清除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        },
        expanded = false, onExpandedChange = {},
        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
    ) {}
}

@Composable
private fun SearchCategorySelector(selected: QueryType?, onSelected: (QueryType?) -> Unit) {
    val choices = listOf(
        "全部" to null,
        "名称" to QueryType.NAME,
        "作者" to QueryType.AUTHOR,
        "汉化组" to QueryType.LOCAL
    )
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        choices.forEachIndexed { index, (label, type) ->
            ToggleButton(
                checked = selected == type,
                onCheckedChange = { if (it) onSelected(type) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.semantics { role = Role.RadioButton }
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelLarge,
                    color = androidx.compose.material3.LocalContentColor.current
                )
            }
        }
    }
}

@Composable
internal fun SearchOptionRow(
    title: String, options: List<ComicFilterOption>, selected: ComicFilterOption?,
    onSelected: (ComicFilterOption?) -> Unit, onAllTags: (() -> Unit)? = null,
    promotedPath: String? = null,
    allLabel: String = "全部"
) {
    val list = rememberLazyListState()
    // 仅详情页传入的题材置顶；页内选择不改变标签顺序或滚动位置。
    val orderedOptions = remember(options, promotedPath) {
        options.filter { it.pathWord == promotedPath } +
                options.filterNot { it.pathWord == promotedPath }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge
        )
        Box(Modifier.weight(1f)) {
            LazyRow(
                state = list,
                contentPadding = PaddingValues(end = if (onAllTags != null) 48.dp else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "all") {
                    FilterChip(
                        selected = selected == null, onClick = { onSelected(null) },
                        label = {
                            Text(
                                allLabel,
                                color = if (selected == null) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                        })
                }
                items(orderedOptions, key = { it.pathWord }) { option ->
                    val checked = selected?.pathWord == option.pathWord
                    FilterChip(
                        selected = checked,
                        enabled = option.enabled,
                        onClick = { onSelected(option) },
                        label = {
                            Text(
                                option.label, color = when {
                                    !option.enabled -> MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = 0.38f
                                    )

                                    checked -> MaterialTheme.colorScheme.onSecondaryContainer
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        })
                }
            }
            onAllTags?.let { open ->
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(64.dp)
                        .height(48.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                                )
                            )
                        ), contentAlignment = Alignment.CenterEnd
                ) {
                    IconButton(onClick = open) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight, "全部题材",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DiscoveryOrdering(
    ordering: String,
    onChange: (String) -> Unit,
    allowAscending: Boolean = true
) {
    val field = ordering.removePrefix("-")
    val descending = ordering.startsWith("-")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("更新时间" to "datetime_updated", "热度" to "popular").forEach { (name, key) ->
            val selected = field == key
            FilterChip(
                selected = selected,
                onClick = {
                    onChange(if (allowAscending && selected && descending) key else "-$key")
                },
                trailingIcon = if (selected) {
                    {
                        Icon(
                            if (descending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            contentDescription = if (!allowAscending) "按降序排列"
                            else if (descending) "降序，点击切换为升序" else "升序，点击切换为降序",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                } else null,
                label = {
                    Text(
                        name,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchToolButton(title: String, icon: ImageVector, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        state = rememberTooltipState(),
        tooltip = {
            PlainTooltip {
                Text(
                    title,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, title, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchResultsContent(
    state: SearchUiState,
    onType: (QueryType?) -> Unit,
    onTheme: (ComicFilterOption?) -> Unit,
    onTop: (ComicFilterOption?) -> Unit,
    onComic: (String) -> Unit,
    onMore: () -> Unit,
    onRetry: () -> Unit,
    onAllTags: () -> Unit,
    onLayoutToggle: () -> Unit,
    onOrdering: (String) -> Unit
) {
    val grid = rememberLazyGridState()
    LaunchedEffect(
        state.submittedQuery, state.submittedQueryType,
        state.selectedDiscoveryTheme?.pathWord, state.selectedDiscoveryTop?.pathWord, state.ordering
    ) {
        grid.scrollToItem(0)
    }
    GridLoadMoreEffect(
        grid,
        state.hasMore && !state.isLoading && !state.isLoadingMore &&
                state.loadMoreError == null && state.error == null,
        state.searchResults.size, onMore
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (state.compactLayout) GridCells.Fixed(comicGridColumns(maxWidth - 32.dp))
        else GridCells.Adaptive(360.dp)
        LazyVerticalGrid(
            columns = columns, state = grid, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchCategorySelector(state.queryType, onType)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (state.isKeywordSearch) "搜索结果" else "发现",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.headlineSmall
                            )
                            state.total?.let {
                                Text(
                                    "共 $it 部漫画",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        if (!state.isKeywordSearch) DiscoveryOrdering(state.ordering, onOrdering)
                        SearchToolButton(
                            if (state.compactLayout) "切换为列表布局" else "切换为紧凑布局",
                            if (state.compactLayout) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            onLayoutToggle
                        )
                    }
                    if (!state.isKeywordSearch) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SearchOptionRow(
                                "题材",
                                state.discoveryOptions?.themes.orEmpty(),
                                state.selectedDiscoveryTheme,
                                onTheme,
                                onAllTags,
                                promotedPath = state.promotedDiscoveryThemePath
                            )
                            SearchOptionRow(
                                "类型",
                                state.discoveryOptions?.tops.orEmpty(),
                                state.selectedDiscoveryTop,
                                onTop
                            )
                        }
                        state.discoveryOptionsError?.let {
                            Text(
                                "标签更新失败，保留已有标签",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            items(state.searchResults, key = { it.pathWord }) { comic ->
                if (state.compactLayout) ComicCard(
                    name = comic.name,
                    cover = comic.cover,
                    subtitle = comic.author?.firstOrNull()?.name,
                    comicPathWord = comic.pathWord,
                    sharedTransitionKey = "search:${comic.pathWord}",
                    onClick = { onComic(comic.pathWord) }
                ) else SearchComicCard(comic, onClick = { onComic(comic.pathWord) })
            }
            item(key = "status", span = { GridItemSpan(maxLineSpan) }) {
                val error = state.loadMoreError ?: state.error
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when {
                        state.isLoading && state.searchResults.isEmpty() -> {
                            Spacer(Modifier.height(32.dp)); LoadingIndicator()
                        }

                        state.isLoadingMore -> LoadingIndicator()
                        error != null -> {
                            Icon(
                                Icons.Default.Warning,
                                null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                error,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            FilledTonalButton(onClick = if (state.loadMoreError != null) onMore else onRetry) {
                                Text("重试", color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }

                        state.searchResults.isEmpty() -> {
                            Spacer(Modifier.height(32.dp))
                            Icon(
                                Icons.Default.Search,
                                null,
                                Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "没有找到相关漫画",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        state.hasMore -> TextButton(onClick = onMore) {
                            Text(
                                "加载更多",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        !state.isLoading -> Text(
                            "已显示全部结果", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchComicCard(comic: ComicSummary, onClick: () -> Unit) {
    SearchBookRow(
        name = comic.name, cover = comic.cover,
        authors = comic.author?.joinToString { it.name }.orEmpty(),
        transitionKey = "search:${comic.pathWord}", pathWord = comic.pathWord,
        onClick = onClick,
        popularity = comic.popular?.toCompactLocale() ?: "--"
    )
}

/** 漫画和小说搜索共用横向封面行；没有热度数据时省略该行。 */
@Composable
internal fun SearchBookRow(
    name: String, cover: String?, authors: String,
    transitionKey: String, pathWord: String, onClick: () -> Unit,
    popularity: String? = null
) {
    val click = rememberComicSharedTransitionClick(
        transitionKey, pathWord, cover, name, authors, onClick
    )
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = click)
    ) {
        Surface(
            Modifier
                .comicContainerSharedBounds(transitionKey)
                .matchParentSize(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {}
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PreviewAwareAsyncImage(
                model = cover, contentDescription = name,
                modifier = Modifier
                    .width(84.dp)
                    .aspectRatio(3f / 4f)
                    .comicCoverSharedElement(transitionKey)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentScale = ContentScale.Crop
            )
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 112.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    name, color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (authors.isNotBlank()) Text(
                    authors,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.weight(1f))
                if (popularity != null) Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.LocalFireDepartment, null, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        popularity,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

private fun previewSearchState(discovery: Boolean = false): SearchUiState = SearchUiState(
    total = if (discovery) 43826 else 126,
    query = if (discovery) "" else "大小姐",
    submittedQuery = if (discovery) "" else "大小姐",
    discoveryOptions = ComicFilterDefaults.options,
    searchResults = (1..6).map { index ->
        ComicSummary(
            name = if (index == 1) "大小姐情窦未开" else "漫长旅途中的故事 第 $index 卷",
            pathWord = "search-preview-$index", cover = "preview",
            author = listOf(Author("作者 $index", null)), popular = 12345 * index,
            females = null, males = null, theme = null, authorAlias = null
        )
    }
)

@Preview(showBackground = true, showSystemUi = true, widthDp = 360, heightDp = 800, name = "手机")
@Preview(showBackground = true, showSystemUi = true, widthDp = 1000, heightDp = 800, name = "平板")
@Target(AnnotationTarget.FUNCTION)
private annotation class SearchDevicePreviews

/** 静态数据复用真实组件，布局和排序可在交互预览中切换。 */
@Composable
private fun SearchPreview(state: SearchUiState) {
    var current by remember { mutableStateOf(state) }
    CopyManga_NeoTheme {
        SearchScreen(
            uiState = current,
            onQueryChange = { current = current.copy(query = it) },
            onSearch = { current = current.copy(query = it, submittedQuery = it.trim()) },
            onClear = { current = current.copy(query = "", submittedQuery = "") },
            onQueryTypeChange = { current = current.copy(queryType = it) },
            onComicClick = {},
            onDiscoveryThemeSelected = {
                current = current.copy(query = "", submittedQuery = "", selectedDiscoveryTheme = it)
            },
            onDiscoveryTopSelected = { current = current.copy(selectedDiscoveryTop = it) },
            onLayoutToggle = { current = current.copy(compactLayout = !current.compactLayout) },
            onOrderingSelected = { current = current.copy(ordering = it) }
        )
    }
}

@SearchDevicePreviews
@Preview(
    showBackground = true, showSystemUi = true, widthDp = 1000, heightDp = 800,
    uiMode = Configuration.UI_MODE_NIGHT_YES, name = "平板深色"
)
@Composable
private fun DiscoveryListPreview() = SearchPreview(previewSearchState(discovery = true))

@SearchDevicePreviews
@Composable
private fun KeywordResultsPreview() = SearchPreview(previewSearchState())

@SearchDevicePreviews
@Composable
private fun CompactResultsPreview() = SearchPreview(previewSearchState().copy(compactLayout = true))

@SearchDevicePreviews
@Composable
private fun SearchLoadingPreview() = SearchPreview(
    SearchUiState(
        query = "大小姐", submittedQuery = "大小姐",
        discoveryOptions = ComicFilterDefaults.options, isLoading = true
    )
)

@SearchDevicePreviews
@Composable
private fun SearchErrorPreview() = SearchPreview(
    SearchUiState(
        query = "大小姐", submittedQuery = "大小姐",
        discoveryOptions = ComicFilterDefaults.options, error = "暂时无法连接，请稍后重试"
    )
)
