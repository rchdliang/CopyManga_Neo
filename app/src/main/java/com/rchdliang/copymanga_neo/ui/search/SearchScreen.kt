package com.rchdliang.copymanga_neo.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.rchdliang.copymanga_neo.data.remote.util.toCompactLocale
import com.rchdliang.copymanga_neo.domain.model.Author
import com.rchdliang.copymanga_neo.domain.model.ComicSummary
import com.rchdliang.copymanga_neo.domain.model.QueryType
import com.rchdliang.copymanga_neo.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.ui.preview.previewImageModel
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme

/**
 * 搜索屏幕
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    onComicClick: (String) -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pullToRefreshState = rememberPullToRefreshState()
    val toastHost = LocalToastHost.current

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            toastHost.showToast(it)
            viewModel.clearToastMessage()
        }
    }

    SearchScreen(
        uiState = uiState,
        onQueryChange = { viewModel.updateQuery(it) },
        onSearch = { viewModel.search(it) },
        onClear = { viewModel.clearSearch() },
        onLoadMore = { viewModel.loadMore() },
        onQueryTypeChange = { viewModel.updateQueryType(it) },
        onComicClick = onComicClick,
        onNavigateBack = onNavigateBack,
        onRefresh = { viewModel.search(viewModel.uiState.value.query) },
        pullToRefreshState = pullToRefreshState
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onLoadMore: () -> Unit = {},
    onQueryTypeChange: (QueryType?) -> Unit,
    onComicClick: (String) -> Unit,
    onNavigateBack: () -> Unit = {},
    onRefresh: () -> Unit = {},
    pullToRefreshState: PullToRefreshState = rememberPullToRefreshState()
) {
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }

    BackHandler(enabled = expanded) {
        expanded = false
    }

    LaunchedEffect(uiState.query) {
        if (textFieldState.text.toString() != uiState.query) {
            textFieldState.setTextAndPlaceCursorAtEnd(uiState.query)
        }
    }

    LaunchedEffect(textFieldState.text.toString()) {
        onQueryChange(textFieldState.text.toString())
    }

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = {
                onSearch(textFieldState.text.toString())
            },
            placeholder = {
                Text(modifier = Modifier.clearAndSetSemantics {}, text = "搜索漫画")
            },
            leadingIcon = {
                IconButton(onClick = {
                    if (expanded) {
                        expanded = false
                    } else {
                        onNavigateBack()
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            trailingIcon = {
                Row {
                    if (textFieldState.text.isNotEmpty()) {
                        IconButton(onClick = {
                            onSearch(textFieldState.text.toString())
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                        IconButton(onClick = {
                            textFieldState.setTextAndPlaceCursorAtEnd("")
                            onClear()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp)
    ) {
        SearchBar(
            inputField = inputField,
            expanded = expanded,
            onExpandedChange = { newExpanded ->
                expanded = newExpanded
            },
            modifier = Modifier.fillMaxWidth()
        ) {}

        SearchCategorySelector(
            selectedType = uiState.queryType,
            onTypeSelected = onQueryTypeChange,
            modifier = Modifier.fillMaxWidth()
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
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.large)
        ) {
            SearchScreenContent(
                uiState = uiState,
                onComicClick = onComicClick,
                onLoadMore = onLoadMore,
                onRetry = { onSearch(uiState.query) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchCategorySelector(
    selectedType: QueryType?,
    onTypeSelected: (QueryType?) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf(
        "全部" to null,
        "名称" to QueryType.NAME,
        "作者" to QueryType.AUTHOR,
        "汉化组" to QueryType.LOCAL
    )

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        categories.forEachIndexed { index, (label, queryType) ->
            ToggleButton(
                checked = selectedType == queryType,
                onCheckedChange = { if (it) onTypeSelected(queryType) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    categories.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.semantics { role = Role.RadioButton }
            ) {
                Text(text = label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchScreenContent(
    uiState: SearchUiState,
    onComicClick: (String) -> Unit,
    onLoadMore: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.error != null && uiState.searchResults.isEmpty() && !uiState.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize(),
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
                        text = "搜索失败：${uiState.error}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 32.dp),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = onRetry,
                    ) { Text("重试") }
                }
            }

            uiState.searchResults.isNotEmpty() -> {
                SearchResultsList(
                    comics = uiState.searchResults,
                    isLoadingMore = uiState.isLoadingMore,
                    onComicClick = onComicClick,
                    onLoadMore = onLoadMore
                )
            }

            uiState.query.isNotEmpty() && !uiState.isLoading -> {
                Text(
                    text = "没有找到相关结果",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchResultsList(
    comics: List<ComicSummary>,
    isLoadingMore: Boolean,
    onComicClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLoadMore: () -> Unit = {}
) {
    val gridState = rememberLazyGridState()

    val shouldLoadMore = remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            onLoadMore()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 360.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        state = gridState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceBright)
    ) {
        items(comics, key = { it.pathWord }) { comic ->
            SearchComicCard(
                comic = comic,
                onClick = { onComicClick(comic.pathWord) }
            )
        }
        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }
        }
    }
}

@Composable
private fun SearchComicCard(
    comic: ComicSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        onClick = onClick
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
//            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = previewImageModel(comic.cover),
                contentDescription = comic.name,
                modifier = Modifier.height(80.dp),
                contentScale = ContentScale.Fit,
            )
            Column(
                modifier = Modifier
                    .height(80.dp)
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = comic.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                comic.author?.let { authors ->
                    Text(
                        text = "作者: " + authors.joinToString { author -> author.name },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "热度: " + (comic.popular?.toCompactLocale() ?: "--"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
// region Previews

@Preview(showBackground = true, showSystemUi = true, name = "搜索 - 完整界面")
@Composable
private fun SearchScreenPreview() {
    CopyManga_NeoTheme {
        SearchScreen(
            uiState = SearchUiState(query = "测试"),
            onQueryChange = {},
            onSearch = {},
            onClear = {},
            onLoadMore = {},
            onQueryTypeChange = {},
            onComicClick = {},
            onNavigateBack = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "搜索 - 搜索结果列表")
@Composable
private fun SearchResultsListPreview() {
    CopyManga_NeoTheme {
        val mockComics = remember {
            (1..8).map { index ->
                ComicSummary(
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
                    author = listOf(Author("作者$index", "author-$index")),
                    females = emptyList(),
                    males = emptyList(),
                    theme = emptyList(),
                    popular = 1000 * index,
                    authorAlias = null
                )
            }
        }

        SearchResultsList(
            comics = mockComics,
            isLoadingMore = false,
            onComicClick = {},
            onLoadMore = {}
        )
    }
}