package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import com.rchdliang.copymanga_neo.multiplatform.ui.components.ActiveRouteProgressFlushEffect
import com.rchdliang.copymanga_neo.multiplatform.ui.components.ActiveRouteLaunchedEffect
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.model.ComicFilterOption
import com.rchdliang.copymanga_neo.domain.model.CommentKind
import com.rchdliang.copymanga_neo.domain.model.CommentTarget
import com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord
import com.rchdliang.copymanga_neo.domain.model.NovelBook
import com.rchdliang.copymanga_neo.domain.model.NovelTag
import com.rchdliang.copymanga_neo.domain.model.NovelVolume
import com.rchdliang.copymanga_neo.domain.model.QueryType
import com.rchdliang.copymanga_neo.domain.model.ShareTarget
import com.rchdliang.copymanga_neo.domain.model.displayBookTitle
import com.rchdliang.copymanga_neo.multiplatform.ui.comments.CommentsViewModel
import com.rchdliang.copymanga_neo.multiplatform.ui.comments.CommentsDialog
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BookDetailActions
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BookDetailAuthor
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BookDetailBrief
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BookDetailCatalogueHeader
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BookDetailHeader
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BookDetailLayout
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BookDetailRefresh
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BookDetailStatus
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BookDetailSurface
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BookDetailTag
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BookDetailTags
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BookDetailToolbar
import com.rchdliang.copymanga_neo.multiplatform.ui.components.ChapterTitleItem
import com.rchdliang.copymanga_neo.multiplatform.ui.components.DownloadManageBottomBar
import com.rchdliang.copymanga_neo.multiplatform.ui.components.DownloadSelectionTopBar
import com.rchdliang.copymanga_neo.multiplatform.ui.components.GridLoadMoreEffect
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.multiplatform.ui.components.NovelAnimatedContent
import com.rchdliang.copymanga_neo.multiplatform.ui.components.NovelSharedTransitionProvider
import com.rchdliang.copymanga_neo.multiplatform.ui.components.NovelTransitionCard
import com.rchdliang.copymanga_neo.multiplatform.ui.components.comicCoverSharedElement
import com.rchdliang.copymanga_neo.multiplatform.ui.components.comicGridColumns
import com.rchdliang.copymanga_neo.multiplatform.ui.components.currentComicTransitionData
import com.rchdliang.copymanga_neo.multiplatform.ui.components.isComicSharedTransitionActive
import com.rchdliang.copymanga_neo.multiplatform.ui.components.novelTransitionPath
import com.rchdliang.copymanga_neo.multiplatform.ui.search.AllTagsScreen
import com.rchdliang.copymanga_neo.multiplatform.ui.search.SearchCategorySelector
import com.rchdliang.copymanga_neo.multiplatform.ui.search.SearchOptionRow
import com.rchdliang.copymanga_neo.multiplatform.ui.search.SearchPage
import com.rchdliang.copymanga_neo.multiplatform.ui.search.SearchResultsHeader
import com.rchdliang.copymanga_neo.multiplatform.ui.components.toCompactLocale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelScreen(
    onBack: () -> Unit,
    initialResume: LocalReadingRecord? = null,
    initialPath: String? = null,
    viewModel: NovelViewModel,
    commentsViewModel: CommentsViewModel
) {
    val currentState by viewModel.state.collectAsStateWithLifecycle()
    var awaitingInitialDetail by remember(initialPath, initialResume) {
        mutableStateOf(
            initialPath != null && initialResume == null &&
                    currentState.detailPath != initialPath && currentState.reader == null
        )
    }
    LaunchedEffect(currentState.view, currentState.detailPath) {
        if (currentState.view == NovelView.DETAIL && currentState.detailPath == initialPath) {
            awaitingInitialDetail = false
        }
    }
    // 首页卡片点击后立即放置目标封面，不等待登录状态及详情请求完成。
    val state = if (awaitingInitialDetail) currentState.copy(
        view = NovelView.DETAIL, detailPath = initialPath.orEmpty(), book = null,
        volumes = emptyList(), bookLoading = true, volumesLoading = true
    ) else currentState
    val local by viewModel.localData.collectAsStateWithLifecycle()
    val toast = LocalToastHost.current
    var showTags by rememberSaveable { mutableStateOf(false) }
    val discoveryStateHolder = rememberSaveableStateHolder()
    var directDetailEntry by rememberSaveable(initialPath) { mutableStateOf(initialPath != null) }
    LaunchedEffect(state.view, state.browseOriginPath) {
        if (state.view == NovelView.BROWSE && state.browseOriginPath == null) directDetailEntry =
            false
    }
    fun back() {
        if (directDetailEntry && state.view == NovelView.DETAIL && state.browseOriginPath == null) onBack()
        else if (!viewModel.back()) onBack()
    }
    val handlesLocalBack = showTags ||
        (!(directDetailEntry && state.view == NovelView.DETAIL && state.browseOriginPath == null) &&
            (state.view != NovelView.BROWSE || state.browseOriginPath != null))
    BackHandler(enabled = handlesLocalBack) {
        if (showTags) showTags = false else back()
    }
    ActiveRouteProgressFlushEffect(viewModel) { viewModel.flushPosition() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshShelfState() }
    ActiveRouteLaunchedEffect(initialResume, initialPath) {
        viewModel.openInitialBook(initialPath, initialResume)
    }
    ActiveRouteLaunchedEffect(state.notice) { state.notice?.let { toast.showToast(it); viewModel.clearNotice() } }
    if (showTags) {
        AllTagsScreen(
            tags = state.tags.map { ComicFilterOption(it.name, it.path, it.count) },
            selectedPath = state.theme.takeIf { it.isNotBlank() },
            onSelect = { tag ->
                showTags = false; viewModel.filter(
                "",
                "",
                state.ordering,
                tag?.pathWord.orEmpty()
            )
            },
            onBack = { showTags = false },
            bookLabel = "轻小说",
            isLoading = state.tagsLoading,
            error = state.tagsError,
            onRefresh = viewModel::refreshTags
        )
        return
    }
    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding), contentAlignment = Alignment.TopCenter
        ) {
            // 动画外层固定全宽，限宽和系统边距随各自场景保持，避免退出页面提前重排。
            NovelAnimatedContent(
                state,
                Modifier.fillMaxSize(),
                contentKey = { it.view }) { state ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (state.view == NovelView.BROWSE) Modifier
                                .statusBarsPadding()
                                .navigationBarsPadding() else Modifier
                        )
                ) {
                    if (state.busy && state.view == NovelView.BROWSE) LinearProgressIndicator(
                        Modifier.fillMaxWidth()
                    )
                    state.error?.takeIf { state.view != NovelView.READER }?.let { error ->
                        Surface(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                                TextButton(
                                    onClick = viewModel::retry,
                                    enabled = !state.busy
                                ) {
                                    Text(
                                        "重试",
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                    when (state.view) {
                        NovelView.BROWSE -> discoveryStateHolder.SaveableStateProvider("novel-discovery") {
                            NovelDiscovery(
                                state,
                                onFilter = { query, type, ordering, theme ->
                                    viewModel.filter(
                                        query,
                                        type,
                                        ordering,
                                        theme
                                    )
                                },
                                onOpen = viewModel::openBook,
                                onMore = viewModel::loadMore,
                                onRetry = viewModel::retryPage,
                                onTags = { showTags = true; viewModel.refreshTags() },
                                onBack = ::back,
                                onSort = viewModel::sortDiscovery,
                                onRefresh = viewModel::refresh,
                                onRefreshTags = viewModel::refreshTags
                            )
                        }

                        NovelView.DETAIL -> NovelDetailContent(
                            state, local.records,
                            commentsViewModel = commentsViewModel,
                            onContinue = viewModel::continueBook,
                            onAuthor = viewModel::selectAuthor,
                            onTheme = viewModel::selectTheme,
                            onAddToShelf = viewModel::toggleShelf,
                            onEnterDownloadMode = viewModel::enterDownloadManagement,
                            onExitDownloadMode = viewModel::exitDownloadManagement,
                            onToggleVolumeSelection = viewModel::toggleVolumeSelection,
                            onSelectAllVolumes = viewModel::selectAllVolumes,
                            onInvertVolumeSelection = viewModel::invertVolumeSelection,
                            onClearVolumeSelection = viewModel::clearVolumeSelection,
                            onSelectVolumeRange = viewModel::selectVolumeRange,
                            onApplyVolumeChanges = viewModel::applyVolumeDownloadChanges,
                            onVolume = { id -> viewModel.openVolume(state.detailPath, id) },
                            onRetryInfo = viewModel::retryBookInfo,
                            onRetryVolumes = viewModel::retryVolumes,
                            onBack = ::back, onRefresh = viewModel::retry
                        )

                        NovelView.READER -> NovelReader(state, viewModel, ::back)
                    }
                }
            }
        }
    }
}

/** 复用漫画搜索框、封面卡及题材页面，请求模式由全局设置决定。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun NovelDiscovery(
    state: NovelUiState,
    onFilter: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onOpen: (String) -> Unit = {}, onMore: () -> Unit = {},
    onTags: () -> Unit = {}, onBack: () -> Unit = {}, onRetry: () -> Unit = {},
    onSort: (String) -> Unit = {}, onRefresh: () -> Unit = {},
    onRefreshTags: () -> Unit = {}
) {
    val resultMode = state.query.isNotBlank()
    val queryType = QueryType.entries.firstOrNull { it.value == state.searchType }
    var query by rememberSaveable(state.query) { mutableStateOf(state.query) }
    var compactLayout by rememberSaveable { mutableStateOf(false) }
    val grid = rememberLazyGridState()
    var scrollRevision by rememberSaveable { mutableIntStateOf(state.browseRevision) }
    LaunchedEffect(state.browseRevision) {
        if (scrollRevision != state.browseRevision) {
            grid.scrollToItem(0)
            scrollRevision = state.browseRevision
        }
    }
    GridLoadMoreEffect(
        grid, state.hasMore && state.books.isNotEmpty() && !state.busy &&
                !state.loadingMore && state.error == null && state.loadMoreError == null,
        state.books.size, onMore
    )
    val submit: (String) -> Unit = { onFilter(it.trim(), state.searchType, state.ordering, "") }
    val share = if (resultMode) ShareTarget.novelSearch(state.query, queryType) else null
    SearchPage(
        query = query, onQueryChange = { query = it }, onSearch = submit,
        onClear = { query = ""; onFilter("", "", state.ordering, "") },
        onBack = onBack, shareTarget = share, placeholder = "搜索轻小说",
        isRefreshing = state.busy && state.books.isNotEmpty(), onRefresh = onRefresh,
        applySystemInsets = false
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = grid,
                columns = if (compactLayout) GridCells.Fixed(comicGridColumns(maxWidth - 32.dp))
                else GridCells.Adaptive(360.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (resultMode) {
                            SearchCategorySelector(
                                queryType,
                                onSelected = {
                                    onFilter(
                                        query.trim(),
                                        it?.value.orEmpty(),
                                        state.ordering,
                                        ""
                                    )
                                },
                                queryTypes = listOf(QueryType.NAME, QueryType.AUTHOR)
                            )
                        }
                        SearchResultsHeader(
                            resultMode, state.total, "轻小说", compactLayout,
                            onLayoutToggle = { compactLayout = !compactLayout },
                            ordering = state.ordering, onOrdering = onSort, allowAscending = false
                        )
                        if (!resultMode) {
                            val themes =
                                state.tags.map { ComicFilterOption(it.name, it.path, it.count) }
                            SearchOptionRow(
                                "题材", themes, themes.firstOrNull { it.pathWord == state.theme },
                                onSelected = {
                                    onFilter(
                                        "",
                                        "",
                                        state.ordering,
                                        it?.pathWord.orEmpty()
                                    )
                                },
                                onAllTags = onTags,
                                promotedPath = state.promotedTheme?.path
                            )
                            state.tagsError?.let {
                                TextButton(onClick = onRefreshTags) {
                                    Text(
                                        "题材数量更新失败，点击重试",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.books.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (state.busy) LoadingIndicator()
                        Text(
                            if (state.busy) "正在加载轻小说…" else if (state.error != null) "作品暂时无法加载" else "没有找到作品",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(state.books, key = { it.path }) { book ->
                    NovelTransitionCard(
                        book = book, source = "browse", compact = compactLayout,
                        onClick = { onOpen(book.path) })
                }
                if (state.books.isNotEmpty()) item(
                    key = "page_status",
                    span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when {
                            state.loadingMore -> LoadingIndicator()
                            state.loadMoreError != null -> {
                                Text(
                                    state.loadMoreError,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FilledTonalButton(onClick = onRetry) {
                                    Text("重试", color = LocalContentColor.current)
                                }
                            }

                            !state.hasMore -> Text(
                                "已加载全部作品",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelDetailContent(
    state: NovelUiState, records: List<LocalReadingRecord>,
    commentsViewModel: CommentsViewModel? = null,
    onContinue: () -> Unit = {}, onAuthor: (NovelTag) -> Unit = {},
    onTheme: (NovelTag) -> Unit = {}, onVolume: (String) -> Unit = {},
    onAddToShelf: () -> Unit = {},
    onEnterDownloadMode: (String?) -> Unit = {},
    onExitDownloadMode: () -> Unit = {},
    onToggleVolumeSelection: (String) -> Unit = {},
    onSelectAllVolumes: () -> Unit = {},
    onInvertVolumeSelection: () -> Unit = {},
    onClearVolumeSelection: () -> Unit = {},
    onSelectVolumeRange: (List<String>, Boolean, Boolean, Boolean) -> Unit = { _, _, _, _ -> },
    onApplyVolumeChanges: () -> Unit = {},
    onRetryInfo: () -> Unit = {}, onRetryVolumes: () -> Unit = {},
    onBack: () -> Unit = {}, onRefresh: () -> Unit = {}
) {
    val book = state.book
    val path = book?.path ?: state.detailPath
    val grid = rememberLazyGridState()
    var showComments by rememberSaveable(path) { mutableStateOf(false) }
    val isInBatchMode = state.selectedVolumeIds != null
    val density = LocalDensity.current
    var bottomBarHeight by remember(path) { mutableIntStateOf(0) }
    var ascending by rememberSaveable(path) { mutableStateOf(true) }
    val volumes = remember(
        state.volumes,
        ascending
    ) { if (ascending) state.volumes else state.volumes.reversed() }
    val displayedIds = volumes.map { it.id }
    val selectedIndices =
        displayedIds.indices.filter { displayedIds[it] in state.selectedVolumeIds.orEmpty() }
    val canSelectBetween = selectedIndices.size >= 2 &&
            selectedIndices.last() - selectedIndices.first() + 1 > selectedIndices.size
    val statuses = novelVolumeStatuses(state)
    val changes = novelDownloadChanges(state)
    BackHandler(enabled = isInBatchMode, onBack = onExitDownloadMode)
    val platform = novelReaderPlatform()
    val saved = records.filter { it.owner == state.owner && it.kind == "novel" && it.path == path }
        .maxByOrNull { it.updatedAt }
    val target = saved?.let { record -> state.volumes.firstOrNull { it.id == record.chapter } }
        ?: state.volumes.firstOrNull()
    val readingAction =
        if (saved?.chapter == target?.id && saved != null) "继续阅读" else "开始阅读"
    val readingLabel = target?.let { "$readingAction：${it.name}" } ?: readingAction
    if (showComments && book != null) {
        CommentsDialog(
            target = CommentTarget(CommentKind.NOVEL, book.uuid),
            title = book.name,
            postingAllowed = true,
            viewModel = requireNotNull(commentsViewModel) { "Provide CommentsViewModel to the novel route" },
            onDismiss = { showComments = false }
        )
    }
    NovelSharedTransitionProvider {
        val transitionPath = novelTransitionPath(path)
        val transition = currentComicTransitionData(transitionPath)
        val transitionKey = transition?.transitionKey ?: transitionPath
        val transitioning = isComicSharedTransitionActive(transitionPath)
        val headerBook = book ?: transition?.let {
            NovelBook(
                path, it.name, it.cover.orEmpty(),
                it.subtitle?.takeIf { subtitle -> subtitle.isNotBlank() }
                    ?.let { subtitle -> listOf(NovelTag(subtitle, "")) }.orEmpty()
            )
        }
        BookDetailSurface(transitionKey, Modifier.fillMaxSize()) {
            BookDetailRefresh(
                state.busy && book != null,
                onRefresh = { if (!state.busy && !state.applyingDownloads) onRefresh() }) {
                BookDetailLayout(
                    bookKey = path, title = displayBookTitle(path, headerBook?.name),
                    onBack = onBack, readingLabel = readingLabel,
                    readingTargetName = target?.name?.takeIf { book != null }, onRead = onContinue,
                    modifier = Modifier.fillMaxSize(), gridState = grid,
                    topBar = if (isInBatchMode) {
                        {
                            DownloadSelectionTopBar(
                                selectedCount = state.selectedVolumeIds.orEmpty()
                                    .intersect(state.managedVolumeIds).size,
                                canSelectBetween = canSelectBetween,
                                onExit = onExitDownloadMode,
                                onSelectAll = onSelectAllVolumes,
                                onInvertSelection = onInvertVolumeSelection,
                                onSelectToLatest = {
                                    onSelectVolumeRange(
                                        displayedIds,
                                        true,
                                        true,
                                        true
                                    )
                                },
                                onUnselectToLatest = {
                                    onSelectVolumeRange(
                                        displayedIds,
                                        true,
                                        true,
                                        false
                                    )
                                },
                                onSelectToFirst = {
                                    onSelectVolumeRange(
                                        displayedIds,
                                        false,
                                        false,
                                        true
                                    )
                                },
                                onUnselectToFirst = {
                                    onSelectVolumeRange(
                                        displayedIds,
                                        false,
                                        false,
                                        false
                                    )
                                },
                                onSelectBetween = {
                                    onSelectVolumeRange(
                                        displayedIds,
                                        true,
                                        false,
                                        true
                                    )
                                },
                                onUnselectBetween = {
                                    onSelectVolumeRange(
                                        displayedIds,
                                        true,
                                        false,
                                        false
                                    )
                                },
                                onClearSelection = onClearVolumeSelection,
                                unitLabel = "卷"
                            )
                        }
                    } else null,
                    bottomPaddingOverride = if (isInBatchMode)
                        with(density) { bottomBarHeight.toDp() } + 16.dp else null,
                    bottomBar = if (isInBatchMode) {
                        {
                            DownloadManageBottomBar(
                                changes.download.size, changes.delete.size, changes.cancel.size,
                                onApplyVolumeChanges,
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .onSizeChanged { bottomBarHeight = it.height },
                                unitLabel = "卷"
                            )
                        }
                    } else null,
                    actions = {
                        BookDetailToolbar(
                            ShareTarget.novel(path, displayBookTitle(path, headerBook?.name)),
                            onComments = { showComments = true },
                            commentsEnabled = !book?.uuid.isNullOrBlank(),
                            onDownload = {
                                onEnterDownloadMode(null)
                            }.takeIf { state.volumes.isNotEmpty() && book != null && !state.applyingDownloads }
                        )
                    },
                    header = { titleModifier ->
                        if (headerBook != null) BookDetailHeader(
                            title = displayBookTitle(path, headerBook.name),
                            cover = if (transitioning && transition != null) transition.cover else headerBook.cover,
                            coverModifier = Modifier.comicCoverSharedElement(transitionKey),
                            titleModifier = titleModifier,
                            authors = headerBook.authors.map { BookDetailAuthor(it.name, it.path) },
                            status = headerBook.status,
                            updated = headerBook.updated,
                            popularity = headerBook.popular?.toCompactLocale() ?: "--",
                            onAuthor = { authorPath ->
                                headerBook.authors.firstOrNull { it.path == authorPath }
                                    ?.let(onAuthor)
                            }
                        )
                        if (book == null) BookDetailStatus(
                            loading = state.bookLoading,
                            message = state.bookError
                                ?: if (state.bookLoading) "正在加载作品详情…" else "作品信息暂缺",
                            onRetry = onRetryInfo, retryEnabled = !state.busy
                        )
                        if (book != null) {
                            BookDetailActions(
                                readingLabel, target != null, onContinue,
                                state.isInShelf,
                                state.isLoggedIn && !state.shelfLoading && book.uuid.isNotBlank(),
                                onAddToShelf
                            )
                            state.bookError?.let { error ->
                                Text(
                                    error, color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (state.applyingDownloads) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text(
                                    "正在应用下载更改…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            BookDetailTags(
                                book.themes.map {
                                    BookDetailTag(
                                        it.name,
                                        it.path,
                                        enabled = it.path.isNotBlank()
                                    )
                                },
                                onClick = { onTheme(NovelTag(it.name, it.key)) })
                            val brief = remember(book.brief) {
                                platform?.plainTextFromHtml(book.brief) ?: book.brief
                            }
                            BookDetailBrief(book.path, brief)
                        }
                    }
                ) {
                    item(key = "volumes", span = { GridItemSpan(maxLineSpan) }) {
                        BookDetailCatalogueHeader(
                            "分卷列表",
                            "共 ${state.volumes.size} 卷",
                            ascending
                        ) { ascending = !ascending }
                    }
                    if ((state.volumesLoading && volumes.isEmpty()) || state.volumesError != null) {
                        item(key = "volumes_status", span = { GridItemSpan(maxLineSpan) }) {
                            BookDetailStatus(
                                state.volumesLoading, state.volumesError ?: "正在加载分卷目录…",
                                onRetryVolumes, retryEnabled = !state.busy
                            )
                        }
                    } else if (volumes.isEmpty()) {
                        item(key = "volumes_empty", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                "暂时没有可阅读的分卷",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(volumes, key = { it.id }, contentType = { "chapter" }) { volume ->
                        val completed = records.any {
                            it.owner == state.owner && it.kind == "novel" &&
                                    it.path == path && it.chapter == volume.id && it.completed
                        }
                        ChapterTitleItem(
                            name = volume.name, isReadCompleted = completed,
                            isSelected = volume.id in state.selectedVolumeIds.orEmpty(),
                            isSelectionMode = isInBatchMode,
                            downloadStatus = statuses[volume.id],
                            downloadProgress = state.downloadTask?.volumes?.firstOrNull { it.volume.id == volume.id }?.progress
                                ?: 0f,
                            onClick = {
                                if (isInBatchMode) onToggleVolumeSelection(volume.id) else onVolume(
                                    volume.id
                                )
                            },
                            onLongPress = { onEnterDownloadMode(volume.id) }.takeIf { book != null && !state.applyingDownloads },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}


@Preview(name = "小说发现 · 手机", showBackground = true, widthDp = 412, heightDp = 900)
@Preview(
    name = "小说发现 · 平板深色",
    showBackground = true,
    widthDp = 840,
    heightDp = 900,
    uiMode = 0x20 // Android-compatible preview NIGHT_YES metadata, without an Android dependency.
)
@Composable
private fun NovelDiscoveryPreview() {
    com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme(dynamicColor = false) {
        NovelDiscovery(
            NovelUiState(
                tags = listOf("冒险", "奇幻", "校园", "恋爱", "科幻", "推理", "日常", "异世界")
                    .mapIndexed { index, name -> NovelTag(name, "preview-tag-$index") },
                books = listOf(
                    NovelBook(
                        "a",
                        "狼与香辛料",
                        "preview://novel-cover",
                        listOf(NovelTag("支仓冻砂", "author")),
                        updated = "最近更新"
                    ),
                    NovelBook(
                        "b",
                        "奇诺之旅",
                        "preview://novel-cover",
                        listOf(NovelTag("时雨泽惠一", "author2")),
                        updated = "连载中"
                    )
                )
            )
        )
    }
}

@Preview(name = "小说详情", showBackground = true, widthDp = 412, heightDp = 860)
@Preview(
    name = "小说详情 · 平板深色", showBackground = true, widthDp = 840, heightDp = 900,
    uiMode = 0x20 // Android-compatible preview NIGHT_YES metadata, without an Android dependency.
)
@Composable
private fun NovelDetailPreview() {
    com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme(dynamicColor = false) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                NovelDetailContent(
                    NovelUiState(
                        book = NovelBook(
                            "a",
                            "狼与香辛料",
                            "preview://novel-cover",
                            listOf(NovelTag("支仓冻砂", "author")),
                            brief = "旅行商人与贤狼的旅途。示例简介用于检查单行截断与点击展开后的布局。",
                            status = "连载中",
                            updated = "2026-10-11"
                        ),
                        volumes = listOf(
                            NovelVolume("1", "第一卷 · 旅途的起点", 1, null, "2"),
                            NovelVolume("2", "第二卷 · 遥远的灯火", 2, "1", null)
                        )
                    ), emptyList()
                )
            }
        }
    }
}

@Preview(name = "小说详情 · 加载", showBackground = true, widthDp = 412, heightDp = 860)
@Composable
private fun NovelDetailLoadingPreview() {
    com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme {
        NovelDetailContent(
            NovelUiState(
                view = NovelView.DETAIL, busy = true,
                bookLoading = true, volumesLoading = true
            ), emptyList()
        )
    }
}

@Preview(
    name = "小说详情 · 信息缺失与目录失败",
    showBackground = true,
    widthDp = 412,
    heightDp = 860
)
@Composable
private fun NovelDetailMissingPreview() {
    com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme {
        NovelDetailContent(
            NovelUiState(
                view = NovelView.DETAIL,
                book = NovelBook("missing-pathword", "missing-pathword", "", emptyList()),
                volumesError = "分卷目录加载失败"
            ), emptyList()
        )
    }
}

@Preview(
    name = "小说详情 · 详情失败且目录可用",
    showBackground = true,
    widthDp = 412,
    heightDp = 860
)
@Composable
private fun NovelDetailPartialPreview() {
    com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme {
        NovelDetailContent(
            NovelUiState(
                view = NovelView.DETAIL, detailPath = "sample",
                bookError = "作品详情加载失败",
                volumes = listOf(NovelVolume("1", "第一卷", 1, null, null))
            ), emptyList()
        )
    }
}

@Preview(name = "轻小说 · 加载", showBackground = true, widthDp = 412)
@Composable
private fun NovelLoadingPreview() {
    com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme { NovelDiscovery(NovelUiState(busy = true)) }
}

@Preview(name = "轻小说 · 空状态", showBackground = true, widthDp = 412)
@Composable
private fun NovelEmptyPreview() {
    com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme { NovelDiscovery(NovelUiState()) }
}
