package com.rchdliang.copymanga_neo.ui.novel

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.model.ChapterDownloadStatus
import com.rchdliang.copymanga_neo.domain.model.ComicFilterOption
import com.rchdliang.copymanga_neo.domain.model.CommentKind
import com.rchdliang.copymanga_neo.domain.model.CommentTarget
import com.rchdliang.copymanga_neo.domain.model.DownloadTaskStatus
import com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord
import com.rchdliang.copymanga_neo.domain.model.NovelBook
import com.rchdliang.copymanga_neo.domain.model.NovelTag
import com.rchdliang.copymanga_neo.domain.model.NovelVolume
import com.rchdliang.copymanga_neo.domain.model.ShareTarget
import com.rchdliang.copymanga_neo.domain.model.displayBookTitle
import com.rchdliang.copymanga_neo.ui.comments.CommentsDialog
import com.rchdliang.copymanga_neo.ui.components.BookDetailActions
import com.rchdliang.copymanga_neo.ui.components.BookDetailAuthor
import com.rchdliang.copymanga_neo.ui.components.BookDetailBrief
import com.rchdliang.copymanga_neo.ui.components.BookDetailCatalogueHeader
import com.rchdliang.copymanga_neo.ui.components.BookDetailHeader
import com.rchdliang.copymanga_neo.ui.components.BookDetailLayout
import com.rchdliang.copymanga_neo.ui.components.BookDetailRefresh
import com.rchdliang.copymanga_neo.ui.components.BookDetailStatus
import com.rchdliang.copymanga_neo.ui.components.BookDetailSurface
import com.rchdliang.copymanga_neo.ui.components.BookDetailTag
import com.rchdliang.copymanga_neo.ui.components.BookDetailTags
import com.rchdliang.copymanga_neo.ui.components.BookDetailToolbar
import com.rchdliang.copymanga_neo.ui.components.ChapterTitleItem
import com.rchdliang.copymanga_neo.ui.components.GridLoadMoreEffect
import com.rchdliang.copymanga_neo.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.ui.components.NovelAnimatedContent
import com.rchdliang.copymanga_neo.ui.components.NovelSharedTransitionProvider
import com.rchdliang.copymanga_neo.ui.components.NovelTransitionCard
import com.rchdliang.copymanga_neo.ui.components.comicCoverSharedElement
import com.rchdliang.copymanga_neo.ui.components.comicGridColumns
import com.rchdliang.copymanga_neo.ui.components.currentComicTransitionData
import com.rchdliang.copymanga_neo.ui.components.isComicSharedTransitionActive
import com.rchdliang.copymanga_neo.ui.components.novelTransitionPath
import com.rchdliang.copymanga_neo.ui.downloads.DownloadingCard
import com.rchdliang.copymanga_neo.ui.search.AllTagsScreen
import com.rchdliang.copymanga_neo.ui.search.DiscoveryOrdering
import com.rchdliang.copymanga_neo.ui.search.SearchInput
import com.rchdliang.copymanga_neo.ui.search.SearchOptionRow
import com.rchdliang.copymanga_neo.ui.search.SearchToolButton
import com.rchdliang.copymanga_neo.ui.util.toCompactLocale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelScreen(
    onBack: () -> Unit,
    initialResume: LocalReadingRecord? = null,
    initialPath: String? = null,
    viewModel: NovelViewModel = hiltViewModel()
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
    var directDetailEntry by rememberSaveable(initialPath) { mutableStateOf(initialPath != null) }
    LaunchedEffect(state.view) {
        if (state.view == NovelView.BROWSE) directDetailEntry = false
    }
    fun back() {
        if (directDetailEntry && state.view == NovelView.DETAIL) onBack()
        else if (!viewModel.back()) onBack()
    }
    BackHandler {
        if (showTags) showTags = false else back()
    }
    DisposableEffect(viewModel) { onDispose { viewModel.flushPosition() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.flushPosition() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshShelfState() }
    LaunchedEffect(initialResume, initialPath) {
        viewModel.openInitialBook(initialPath, initialResume)
    }
    LaunchedEffect(state.notice) { state.notice?.let { toast.showToast(it); viewModel.clearNotice() } }
    if (showTags) {
        AllTagsScreen(
            tags = state.tags.map { ComicFilterOption(it.name, it.path) },
            selectedPath = state.theme.takeIf { it.isNotBlank() },
            onSelect = { tag ->
                showTags = false; viewModel.filter(
                "",
                "",
                state.ordering,
                tag?.pathWord.orEmpty()
            )
            },
            onBack = { showTags = false }
        )
        return
    }
    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding), contentAlignment = Alignment.TopCenter
        ) {
            Column(
                Modifier
                    .then(if (state.view == NovelView.DETAIL) Modifier else Modifier.widthIn(max = 1040.dp))
                    .then(
                        if (state.view == NovelView.BROWSE) Modifier
                            .statusBarsPadding()
                            .navigationBarsPadding() else Modifier
                    )
                    .fillMaxSize()
            ) {
                if (state.busy && state.view != NovelView.DETAIL) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { error ->
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
                            ) { Text("重试", color = MaterialTheme.colorScheme.onErrorContainer) }
                        }
                    }
                }
                NovelAnimatedContent(
                    state,
                    Modifier.fillMaxSize(),
                    contentKey = { it.view }) { state ->
                    when (state.view) {
                        NovelView.BROWSE -> NovelDiscovery(
                            state,
                            onFilter = { query, type, ordering, theme ->
                                viewModel.filter(
                                    query,
                                    type,
                                    ordering,
                                    theme
                                )
                            },
                            onOpen = viewModel::openBook, onMore = viewModel::loadMore,
                            onRetry = viewModel::retryPage,
                            onTags = { showTags = true }, onBack = ::back,
                            onSort = viewModel::sortDiscovery
                        )

                        NovelView.DETAIL -> NovelDetailContent(
                            state, local.records,
                            onContinue = viewModel::continueBook,
                            onAuthor = { viewModel.filter("", "", state.ordering, "", it) },
                            onTheme = viewModel::selectTheme,
                            onAddToShelf = viewModel::toggleShelf,
                            onDownload = viewModel::downloadVolumes,
                            onCancelDownload = viewModel::cancelDownloads,
                            onPauseDownload = viewModel::pauseDownloads,
                            onResumeDownload = viewModel::resumeDownloads,
                            onDeleteDownloads = viewModel::removeDownloadedVolumes,
                            onVolume = { id -> viewModel.openVolume(state.detailPath, id) },
                            onRetryInfo = viewModel::retryBookInfo,
                            onRetryVolumes = viewModel::retryVolumes,
                            onBack = ::back, onRefresh = viewModel::retry
                        )

                        NovelView.READER -> if (state.reader != null) NovelReader(
                            state,
                            viewModel,
                            ::back
                        ) else {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.MenuBook,
                                    null,
                                    Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    if (state.busy) "正在打开本卷…" else "本卷暂时无法打开",
                                    Modifier.padding(top = 16.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "已缓存的正文会优先从本机读取。",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = ::back) {
                                    Text(
                                        "返回目录",
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
}

/** 复用漫画搜索框、封面卡及题材页面，请求模式由全局设置决定。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NovelDiscovery(
    state: NovelUiState,
    onFilter: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onOpen: (String) -> Unit = {}, onMore: () -> Unit = {},
    onTags: () -> Unit = {}, onBack: () -> Unit = {}, onRetry: () -> Unit = {},
    onSort: (String) -> Unit = {}
) {
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
    Column(Modifier.fillMaxSize()) {
        SearchInput(
            query, { query = it }, submit,
            { query = ""; onFilter("", "", state.ordering, "") }, onBack, placeholder = "搜索轻小说"
        )
        BoxWithConstraints(Modifier.weight(1f)) {
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
                        if (state.query.isNotBlank()) {
                            val types = listOf(
                                ComicFilterOption("书名", "name"),
                                ComicFilterOption("作者", "author")
                            )
                            SearchOptionRow(
                                "搜索",
                                types,
                                types.firstOrNull { it.pathWord == state.searchType },
                                onSelected = {
                                    onFilter(
                                        query.trim(),
                                        it?.pathWord.orEmpty(),
                                        state.ordering,
                                        ""
                                    )
                                },
                                allLabel = "综合"
                            )
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (state.query.isNotBlank()) "搜索结果" else if (state.author.isNotBlank()) "作者作品" else "发现",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (state.query.isBlank()) DiscoveryOrdering(
                                state.ordering,
                                onChange = onSort,
                                allowAscending = false
                            )
                            SearchToolButton(
                                if (compactLayout) "切换为列表布局" else "切换为紧凑布局",
                                if (compactLayout) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView
                            ) { compactLayout = !compactLayout }
                        }
                        if (state.query.isBlank()) {
                            val themes = state.tags.map { ComicFilterOption(it.name, it.path) }
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
    onContinue: () -> Unit = {}, onAuthor: (String) -> Unit = {},
    onTheme: (NovelTag) -> Unit = {}, onVolume: (String) -> Unit = {},
    onAddToShelf: () -> Unit = {}, onDownload: (Set<String>) -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onPauseDownload: () -> Unit = {},
    onResumeDownload: () -> Unit = {},
    onDeleteDownloads: (Set<String>) -> Unit = {},
    onRetryInfo: () -> Unit = {}, onRetryVolumes: () -> Unit = {},
    onBack: () -> Unit = {}, onRefresh: () -> Unit = {}
) {
    val book = state.book
    val path = book?.path ?: state.detailPath
    val grid = rememberLazyGridState()
    var showComments by rememberSaveable(path) { mutableStateOf(false) }
    var showDownloads by rememberSaveable(path) { mutableStateOf(false) }
    var ascending by rememberSaveable(path) { mutableStateOf(true) }
    val volumes = remember(
        state.volumes,
        ascending
    ) { if (ascending) state.volumes else state.volumes.reversed() }
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
            postingAllowed = false,
            onDismiss = { showComments = false }
        )
    }
    if (showDownloads) {
        NovelDownloadDialog(
            state,
            onDownload,
            onPauseDownload,
            onResumeDownload,
            onCancelDownload,
            onDeleteDownloads
        ) { showDownloads = false }
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
                onRefresh = { if (!state.busy) onRefresh() }) {
                BookDetailLayout(
                    bookKey = path, title = displayBookTitle(path, headerBook?.name),
                    onBack = onBack, readingLabel = readingLabel,
                    readingTargetName = target?.name?.takeIf { book != null }, onRead = onContinue,
                    modifier = Modifier.fillMaxSize(), gridState = grid,
                    actions = {
                        BookDetailToolbar(
                            ShareTarget.novel(path, displayBookTitle(path, headerBook?.name)),
                            onComments = { showComments = true },
                            commentsEnabled = !book?.uuid.isNullOrBlank(),
                            onDownload = {
                                showDownloads = true
                            }.takeIf { state.volumes.isNotEmpty() }
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
                            onAuthor = onAuthor
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
                                if (state.shelfLoading) "处理中…" else if (state.isInShelf) "取消收藏" else "加入书架",
                                state.isLoggedIn && !state.shelfLoading && book.uuid.isNotBlank(),
                                onAddToShelf
                            )
                            state.bookError?.let { error ->
                                Text(
                                    error, color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (state.downloading) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text(
                                    state.downloadProgress.orEmpty(),
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
                                android.text.Html.fromHtml(
                                    book.brief,
                                    android.text.Html.FROM_HTML_MODE_LEGACY
                                ).toString()
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
                            downloadStatus = if (state.cachedVolumes.any { it.volume.id == volume.id && it.offlineReady })
                                ChapterDownloadStatus.DOWNLOADED
                            else state.downloadTask?.volumes?.firstOrNull { it.volume.id == volume.id }?.status,
                            downloadProgress = state.downloadTask?.volumes?.firstOrNull { it.volume.id == volume.id }?.progress
                                ?: 0f,
                            onClick = { onVolume(volume.id) }, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/** 分卷选择、任务控制与下载页共用同一队列和进度卡。 */
@Composable
private fun NovelDownloadDialog(
    state: NovelUiState,
    onDownload: (Set<String>) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember(state.detailPath) { mutableStateOf(emptySet<String>()) }
    val downloaded = state.cachedVolumes.filter { it.offlineReady }.map { it.volume.id }.toSet()
    val cached = state.cachedVolumes.map { it.volume.id }.toSet()
    val toDownload = selected - downloaded
    val toDelete = selected.intersect(cached)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分卷下载", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.downloadInfo?.takeIf { it.status != DownloadTaskStatus.COMPLETED }
                    ?.let { info ->
                        val paused =
                            info.status == DownloadTaskStatus.PAUSED || info.status == DownloadTaskStatus.PAUSED_BY_RISK
                        DownloadingCard(
                            info.name, info.cover, info, paused,
                            onPauseResume = { if (paused) onResume() else onPause() },
                            onCancel = onCancel, onRetry = onResume, unitLabel = "卷"
                        )
                    }
                Text(
                    "已完整下载 ${downloaded.size}/${state.volumes.size} 卷；离开详情后下载仍会继续。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        selected = if (selected.size == state.volumes.size) emptySet()
                        else state.volumes.map { it.id }.toSet()
                    }) {
                        Text(
                            if (selected.size == state.volumes.size) "取消全选" else "全选",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (toDelete.isNotEmpty()) TextButton(onClick = {
                        onDelete(toDelete); selected = emptySet()
                    }) {
                        Text("删除所选下载", color = MaterialTheme.colorScheme.error)
                    }
                }
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(state.volumes, key = { it.id }) { volume ->
                        val checked = volume.id in selected
                        val item =
                            state.downloadTask?.volumes?.firstOrNull { it.volume.id == volume.id }
                        val local = state.cachedVolumes.firstOrNull { it.volume.id == volume.id }
                        val label = when {
                            local?.offlineReady == true -> "已下载"
                            item?.status == ChapterDownloadStatus.ERROR -> item.errorMessage
                                ?: "下载失败"

                            item?.status == ChapterDownloadStatus.DOWNLOADING -> "下载中"
                            item?.status == ChapterDownloadStatus.WAITING -> "等待下载"
                            local != null -> "正文已保存 · 插图 ${local.cachedImages}/${local.totalImages}"
                            else -> "未下载"
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected =
                                        if (checked) selected - volume.id else selected + volume.id
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked, onCheckedChange = null)
                            Column(Modifier.weight(1f)) {
                                Text(volume.name, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    label, style = MaterialTheme.typography.labelSmall,
                                    color = if (item?.status == ChapterDownloadStatus.ERROR) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onDownload(toDownload); selected = emptySet() },
                enabled = toDownload.isNotEmpty()
            ) {
                Text("下载（${toDownload.size}）", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "关闭",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}

@Preview(name = "小说发现 · 手机", showBackground = true, widthDp = 412, heightDp = 900)
@Preview(
    name = "小说发现 · 平板深色",
    showBackground = true,
    widthDp = 840,
    heightDp = 900,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun NovelDiscoveryPreview() {
    com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme(dynamicColor = false) {
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
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun NovelDetailPreview() {
    com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme(dynamicColor = false) {
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
    com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme {
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
    com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme {
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
    com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme {
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
    com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme { NovelDiscovery(NovelUiState(busy = true)) }
}

@Preview(name = "轻小说 · 空状态", showBackground = true, widthDp = 412)
@Composable
private fun NovelEmptyPreview() {
    com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme { NovelDiscovery(NovelUiState()) }
}
