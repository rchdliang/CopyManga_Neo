package com.rchdliang.copymanga_neo.ui.detail

import com.rchdliang.copymanga_neo.ui.components.metadataText

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.model.Author
import com.rchdliang.copymanga_neo.domain.model.BrowseInfo
import com.rchdliang.copymanga_neo.domain.model.ChapterDownloadStatus
import com.rchdliang.copymanga_neo.domain.model.ChapterGroup
import com.rchdliang.copymanga_neo.domain.model.ChapterInfo
import com.rchdliang.copymanga_neo.domain.model.ComicInfo
import com.rchdliang.copymanga_neo.domain.model.CommentKind
import com.rchdliang.copymanga_neo.domain.model.CommentPolicy
import com.rchdliang.copymanga_neo.domain.model.CommentTarget
import com.rchdliang.copymanga_neo.domain.model.DisplayValue
import com.rchdliang.copymanga_neo.domain.model.LastChapter
import com.rchdliang.copymanga_neo.domain.model.ShareTarget
import com.rchdliang.copymanga_neo.domain.model.Theme
import com.rchdliang.copymanga_neo.domain.model.blocks
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
import com.rchdliang.copymanga_neo.ui.components.ChapterItem
import com.rchdliang.copymanga_neo.ui.components.DownloadManageBottomBar
import com.rchdliang.copymanga_neo.ui.components.DownloadSelectionTopBar
import com.rchdliang.copymanga_neo.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.ui.components.bookDetailCoverWidth
import com.rchdliang.copymanga_neo.ui.components.comicCoverSharedElement
import com.rchdliang.copymanga_neo.ui.components.currentComicTransitionData
import com.rchdliang.copymanga_neo.ui.components.isComicSharedTransitionActive
import com.rchdliang.copymanga_neo.ui.preview.PreviewAwareAsyncImage
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme
import com.rchdliang.copymanga_neo.ui.util.toCompactLocale
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DetailScreen(
    pathWord: String,
    viewModel: DetailViewModel = hiltViewModel(),
    onChapterClick: (String, String, String, String) -> Unit,
    onStartChapterClick: (String, String, String, String, Int) -> Unit,
    onNavigateBack: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onThemeClick: (Theme) -> Unit = {},
    onAuthorClick: (Author) -> Unit = {},
    onOriginalWorkClick: (com.rchdliang.copymanga_neo.domain.model.ComicOriginalWork) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val detailGridState = rememberLazyGridState()
    val pullToRefreshState = rememberPullToRefreshState()
    val toastHost = LocalToastHost.current
    var pendingTheme by remember(pathWord) { mutableStateOf<Theme?>(null) }
    var showComments by remember(pathWord) { mutableStateOf(false) }
    if (showComments) uiState.comicDetail?.let { comic ->
        CommentsDialog(
            target = CommentTarget(CommentKind.COMIC, comic.uuid),
            title = comic.name,
            postingAllowed = CommentPolicy.postingAllowed(
                CommentKind.COMIC,
                comic.closeComment,
                comic.closeRoast
            ),
            onDismiss = { showComments = false },
            onLogin = onNavigateToProfile
        )
    }
    val transitionData = currentComicTransitionData(pathWord)
    val sharedTransitionKey = transitionData?.transitionKey ?: pathWord
    val isSharedTransitionActive = isComicSharedTransitionActive(pathWord)
    val detailCoverWidth = bookDetailCoverWidth()
    var keepTransitionCover by remember(pathWord, transitionData) {
        mutableStateOf(transitionData != null)
    }

    LaunchedEffect(pathWord, transitionData) {
        if (transitionData != null) {
            keepTransitionCover = true
            delay(700.milliseconds)
            keepTransitionCover = false
        }
    }

    val isWaitingForDetail = uiState.comicDetail == null &&
            uiState.error == null &&
            uiState.riskControlReason == null
    val headerVisible by remember(detailGridState) {
        derivedStateOf {
            !detailGridState.canScrollBackward || detailGridState.layoutInfo.visibleItemsInfo.any { it.key == "comic_header" }
        }
    }
    val showTransitionCover = transitionData != null &&
            (isWaitingForDetail || ((keepTransitionCover || isSharedTransitionActive) && headerVisible))

    LaunchedEffect(pathWord) {
        viewModel.loadDetail(pathWord)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onScreenResumed() }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { message ->
            toastHost.showToast(message)
            viewModel.clearToastMessage()
        }
    }

    pendingTheme?.let { theme ->
        val isBlocked = uiState.blockedThemes.blocks(theme)
        AlertDialog(
            onDismissRequest = { pendingTheme = null },
            title = {
                Text(
                    text = if (isBlocked) "移出标签黑名单" else "加入标签黑名单",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = if (isBlocked) "各漫画列表将恢复显示含「${metadataText(theme.name)}」的封面。"
                    else "各漫画列表将模糊含「${metadataText(theme.name)}」的封面，标题和条目保留。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setThemeBlocked(theme, !isBlocked)
                    pendingTheme = null
                }) {
                    Text(
                        text = if (isBlocked) "取消屏蔽" else "加入黑名单",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTheme = null }) {
                    Text("取消", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    BookDetailSurface(sharedTransitionKey, Modifier.fillMaxSize()) {
        BookDetailRefresh(uiState.isLoading, onRefresh = { viewModel.refreshDetail() }) {
            when {
                uiState.error != null && uiState.comicDetail == null -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                        contentAlignment = Alignment.Center
                    ) {
                        BookDetailStatus(
                            false,
                            "加载失败：${uiState.error}",
                            onRetry = { viewModel.loadDetail(pathWord) })
                    }
                }

                uiState.riskControlReason != null && uiState.comicDetail == null -> {
                    val reason = uiState.riskControlReason.orEmpty()
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = "风控",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "触发风控",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }

                uiState.comicDetail != null -> {
                    val stableOnGroupSelect =
                        remember { { group: String -> viewModel.selectGroup(group) } }
                    val stableOnToggleSortOrder =
                        remember { { viewModel.toggleSortOrder() } }
                    val stableOnToggleShelf = remember(uiState.isLoggedIn) {
                        {
                            if (uiState.isLoggedIn) {
                                viewModel.toggleShelf()
                            } else {
                                onNavigateToProfile()
                            }
                        }
                    }
                    val stableOnStartReading = remember {
                        {
                            val info = viewModel.getStartChapter()
                            if (info != null) {
                                onStartChapterClick(
                                    info.uuid,
                                    uiState.comicDetail!!.pathWord,
                                    info.name,
                                    info.groupPathWord,
                                    info.initialPage
                                )
                            }
                        }
                    }
                    // 计算点击"继续/开始阅读"后跳转的目标章节，用于按钮文案展示
                    val startChapterInfo = remember(uiState) { resolveStartChapter(uiState) }
                    val onChapterToggleSelect = remember {
                        { uuid: String -> viewModel.toggleChapterSelection(uuid) }
                    }
                    val onEnterManageMode = remember {
                        { viewModel.enterManageMode() }
                    }
                    val onConfirmBatchAction = remember {
                        { viewModel.confirmBatchAction() }
                    }
                    DetailContent(
                        comicDetail = uiState.comicDetail!!,
                        onComments = { showComments = true },
                        onAuthorClick = onAuthorClick,
                        onOriginalWorkClick = onOriginalWorkClick,
                        blockedThemes = uiState.blockedThemes,
                        onThemeClick = { theme ->
                            if (theme.pathWord.isNullOrBlank()) {
                                toastHost.showToast("此标签缺少标识，暂时无法跳转")
                            } else {
                                onThemeClick(theme)
                            }
                        },
                        onThemeLongClick = { theme ->
                            if (uiState.isBlacklistReady && !uiState.isSavingBlacklist) {
                                pendingTheme = theme
                            } else {
                                toastHost.showToast("标签黑名单尚未就绪，请稍后重试")
                            }
                        },
                        chapters = uiState.chapters,
                        groups = uiState.groups,
                        selectedGroup = uiState.selectedGroup,
                        sortAscending = uiState.sortAscending,
                        isLoggedIn = uiState.isLoggedIn,
                        isInShelf = uiState.isInShelf,
                        isShelfLoading = uiState.isShelfLoading,
                        browse = uiState.browse,
                        completedChapters = uiState.completedChapters,
                        startChapterName = startChapterInfo?.name,
                        startChapterIsContinue = startChapterInfo?.isContinue ?: false,
                        batchSelection = uiState.batchSelection,
                        chapterDownloadStatuses = uiState.chapterDownloadStatuses,
                        chapterDownloadProgresses = uiState.chapterDownloadProgresses,
                        onChapterClick = onChapterClick,
                        onChapterLongPress = { uuid ->
                            viewModel.enterManageMode(uuid)
                        },
                        onChapterToggleSelect = onChapterToggleSelect,
                        onGroupSelect = stableOnGroupSelect,
                        onToggleSortOrder = stableOnToggleSortOrder,
                        onToggleShelf = stableOnToggleShelf,
                        onStartReading = stableOnStartReading,
                        onNavigateBack = onNavigateBack,
                        onExitBatchMode = { viewModel.exitBatchMode() },
                        onSelectAll = { viewModel.selectAll() },
                        onInvertSelection = { viewModel.invertSelection() },
                        onSelectToLatest = { viewModel.selectToLatest() },
                        onUnselectToLatest = { viewModel.unselectToLatest() },
                        onSelectToFirst = { viewModel.selectToFirst() },
                        onUnselectToFirst = { viewModel.unselectToFirst() },
                        onSelectBetween = { viewModel.selectBetween() },
                        onUnselectBetween = { viewModel.unselectBetween() },
                        canSelectBetween = { viewModel.canSelectBetween() },
                        onClearSelection = { viewModel.clearSelection() },
                        onEnterManageMode = onEnterManageMode,
                        onConfirmBatchAction = onConfirmBatchAction,
                        detailCoverWidth = detailCoverWidth,
                        transitionPathWord = sharedTransitionKey,
                        hideCoverDuringTransition = showTransitionCover,
                        gridState = detailGridState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        transitionData?.takeIf { showTransitionCover }?.let { coverData ->
            // 共享封面跟随头部的滚动位置，不能在滚动后仍固定绘制在页面顶端。
            Box(
                Modifier
                    .matchParentSize()
                    .statusBarsPadding()
                    .padding(top = 48.dp)
                    .clipToBounds()
            ) {
                com.rchdliang.copymanga_neo.ui.components.ComicCoverImage(
                    path = coverData.pathWord, model = coverData.cover,
                    contentDescription = coverData.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .offset {
                            val header =
                                detailGridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "comic_header" }
                            IntOffset(0, header?.offset?.y ?: 0)
                        }
                        .padding(start = 16.dp)
                        .comicCoverSharedElement(sharedTransitionKey)
                        .width(detailCoverWidth)
                        .aspectRatio(3f / 4f)
                        .clip(MaterialTheme.shapes.medium)
                )
            }
        }

        transitionData?.takeIf { isWaitingForDetail }?.let { loadingData ->
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(
                        start = detailCoverWidth + 32.dp,
                        top = 52.dp,
                        end = 16.dp
                    )
            ) {
                Text(
                    text = loadingData.name,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )
                loadingData.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "正在加载详情",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DetailContent(
    comicDetail: ComicInfo,
    onComments: () -> Unit = {},
    onAuthorClick: (Author) -> Unit = {},
    onOriginalWorkClick: (com.rchdliang.copymanga_neo.domain.model.ComicOriginalWork) -> Unit = {},
    blockedThemes: List<Theme> = emptyList(),
    onThemeClick: (Theme) -> Unit = {},
    onThemeLongClick: (Theme) -> Unit = {},
    chapters: ImmutableList<ChapterInfo>,
    groups: ImmutableList<ChapterGroup>,
    selectedGroup: String,
    sortAscending: Boolean,
    isLoggedIn: Boolean,
    isInShelf: Boolean,
    isShelfLoading: Boolean,
    browse: BrowseInfo?,
    startChapterName: String? = null,
    startChapterIsContinue: Boolean = false,
    batchSelection: ChapterBatchSelectionState?,
    chapterDownloadStatuses: Map<String, ChapterDownloadStatus>,
    chapterDownloadProgresses: Map<String, Float>,
    onChapterClick: (String, String, String, String) -> Unit,
    onChapterLongPress: (String) -> Unit,
    onChapterToggleSelect: (String) -> Unit,
    onGroupSelect: (String) -> Unit,
    onToggleSortOrder: () -> Unit,
    onToggleShelf: () -> Unit,
    onStartReading: () -> Unit,
    onNavigateBack: () -> Unit = {},
    onExitBatchMode: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onSelectToLatest: () -> Unit,
    onUnselectToLatest: () -> Unit,
    onSelectToFirst: () -> Unit,
    onUnselectToFirst: () -> Unit,
    onSelectBetween: () -> Unit,
    onUnselectBetween: () -> Unit,
    canSelectBetween: () -> Boolean,
    onClearSelection: () -> Unit,
    onEnterManageMode: () -> Unit,
    onConfirmBatchAction: () -> Unit,
    detailCoverWidth: Dp = bookDetailCoverWidth(),
    transitionPathWord: String? = null,
    hideCoverDuringTransition: Boolean = false,
    modifier: Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    completedChapters: Set<String> = emptySet()
) {

    val currentPathWord = comicDetail.pathWord
    val currentOnChapterClick by rememberUpdatedState(onChapterClick)
    val currentOnChapterLongPress by rememberUpdatedState(onChapterLongPress)
    val currentOnChapterToggleSelect by rememberUpdatedState(onChapterToggleSelect)
    val coverTransitionModifier = if (transitionPathWord != null && !hideCoverDuringTransition) {
        Modifier.comicCoverSharedElement(transitionPathWord)
    } else Modifier
    val isInBatchMode = batchSelection != null
    val managedChapterUuids = batchSelection?.managedChapters(chapters)?.keys.orEmpty()
    val density = LocalDensity.current
    var bottomBarHeight by remember { mutableIntStateOf(0) }
    val displayedChapters = remember(chapters, sortAscending) {
        if (sortAscending) chapters else chapters.reversed()
    }
    val hasReadingTarget = !startChapterName.isNullOrBlank()
    val readingAction = if (startChapterIsContinue) "继续阅读" else "开始阅读"
    val readingLabel = if (hasReadingTarget) "$readingAction：$startChapterName" else readingAction

    BackHandler(enabled = isInBatchMode, onBack = onExitBatchMode)
    BookDetailLayout(
        bookKey = currentPathWord, title = comicDetail.name, onBack = onNavigateBack,
        readingLabel = readingLabel, readingTargetName = startChapterName, onRead = onStartReading,
        modifier = modifier, gridState = gridState, headerKey = "comic_header",
        topBar = if (isInBatchMode) {
            {
                val selectedCount =
                    managedChapterUuids.count { it in batchSelection.selectedChapterUuids }
                DownloadSelectionTopBar(
                    selectedCount = selectedCount,
                    canSelectBetween = canSelectBetween(),
                    onExit = onExitBatchMode,
                    onSelectAll = onSelectAll,
                    onInvertSelection = onInvertSelection,
                    onSelectToLatest = onSelectToLatest,
                    onUnselectToLatest = onUnselectToLatest,
                    onSelectToFirst = onSelectToFirst,
                    onUnselectToFirst = onUnselectToFirst,
                    onSelectBetween = onSelectBetween,
                    onUnselectBetween = onUnselectBetween,
                    onClearSelection = onClearSelection
                )
            }
        } else null,
        actions = {
            BookDetailToolbar(
                ShareTarget.comic(comicDetail.pathWord, comicDetail.name),
                onComments, commentsEnabled = comicDetail.uuid.isNotBlank(),
                onDownload = onEnterManageMode.takeIf { chapters.isNotEmpty() }
            )
        },
        bottomPaddingOverride = if (isInBatchMode) with(density) { bottomBarHeight.toDp() } + 16.dp else null,
        bottomBar = if (isInBatchMode) {
            {
                val changes = chapterDownloadChanges(
                    managedChapterUuids,
                    batchSelection.selectedChapterUuids,
                    chapterDownloadStatuses
                )
                DownloadManageBottomBar(
                    changes.download.size,
                    changes.delete.size,
                    changes.cancel.size,
                    onConfirmBatchAction,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { bottomBarHeight = it.height })
            }
        } else null,
        header = { titleModifier ->
            BookDetailHeader(
                title = comicDetail.name,
                comicPathWord = comicDetail.pathWord,
                cover = comicDetail.cover,
                authors = comicDetail.author.orEmpty().map {
                    BookDetailAuthor(it.name, it.pathWord?.takeIf(String::isNotBlank) ?: it.name)
                },
                status = if (comicDetail.status.value == 1) "已完结" else "连载中",
                originalWorks = comicDetail.parodies.orEmpty(),
                onOriginalWork = onOriginalWorkClick,
                updated = comicDetail.lastUpdate ?: "--",
                popularity = comicDetail.popular?.toCompactLocale() ?: "--",
                coverWidth = detailCoverWidth,
                coverModifier = coverTransitionModifier.alpha(if (hideCoverDuringTransition) 0f else 1f),
                titleModifier = titleModifier,
                onAuthor = { path ->
                    comicDetail.author.orEmpty().firstOrNull {
                        (it.pathWord?.takeIf(String::isNotBlank) ?: it.name) == path
                    }?.let(onAuthorClick)
                }
            )
            BookDetailActions(
                readingLabel,
                hasReadingTarget,
                onStartReading,
                isInShelf,
                !isShelfLoading && isLoggedIn,
                onToggleShelf
            )
            val themes = comicDetail.theme.orEmpty()
            BookDetailTags(
                themes.mapIndexed { index, theme ->
                    BookDetailTag(
                        theme.name,
                        index.toString(),
                        blockedThemes.blocks(theme)
                    )
                },
                onClick = { tag -> onThemeClick(themes[tag.key.toInt()]) },
                onLongClick = { tag -> onThemeLongClick(themes[tag.key.toInt()]) }
            )
            comicDetail.brief?.let { BookDetailBrief(comicDetail.pathWord, it) }
            if (groups.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 0.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                ) {
                    groups.forEachIndexed { index, group ->
                        key(group.pathWord) {
                            ToggleButton(
                                checked = selectedGroup == group.pathWord,
                                onCheckedChange = { if (it) onGroupSelect(group.pathWord) },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    groups.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                                modifier = Modifier.semantics { role = Role.RadioButton }
                            ) {
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = LocalContentColor.current
                                )
                            }
                        }
                    }
                }
            }
        }
    ) {
        item(
            key = "chapter_controls",
            span = { GridItemSpan(maxLineSpan) },
            contentType = "chapter_controls"
        ) {
            val total = groups.firstOrNull { it.pathWord == selectedGroup }?.total ?: chapters.size
            BookDetailCatalogueHeader("章节列表", "共 $total 话", sortAscending, onToggleSortOrder)
        }
        items(
            count = displayedChapters.size,
            key = { displayedChapters[it].uuid },
            contentType = { "chapter" }) { index ->
            val chapter = displayedChapters[index]
            ChapterItem(
                chapter = chapter,
                isReadCompleted = chapter.uuid in completedChapters,
                isSelected = batchSelection?.selectedChapterUuids?.contains(chapter.uuid) == true,
                isSelectionMode = isInBatchMode,
                downloadStatus = chapterDownloadStatuses[chapter.uuid],
                downloadProgress = chapterDownloadProgresses[chapter.uuid],
                onClick = {
                    if (isInBatchMode) currentOnChapterToggleSelect(chapter.uuid)
                    else currentOnChapterClick(
                        chapter.uuid, currentPathWord, chapter.name,
                        chapter.groupPathWord ?: "default"
                    )
                },
                onLongPress = { currentOnChapterLongPress(chapter.uuid) }
            )
        }
    }
}


// region Previews
@PreviewScreenSizes
@Composable
private fun DetailContentPreview() {
    CopyManga_NeoTheme {
        val mockDetail = remember {
            ComicInfo(
                uuid = "preview-uuid",
                b404 = false,
                bHidden = false,
                ban = 0,
                name = "示例漫画：冒险者的奇幻旅途",
                comicAlias = "Adventure Journey",
                pathWord = "preview-comic",
                closeComment = false,
                closeRoast = false,
                freeType = DisplayValue("免费", 0),
                restrict = DisplayValue("一般向", 0),
                reclass = DisplayValue("热血", 1),
                females = listOf("女主A", "女主B"),
                males = listOf("男主C"),
                clubs = listOf("汉化社"),
                region = DisplayValue("日本", 1),
                status = DisplayValue("连载中", 0),
                author = listOf(
                    Author("山田太郎", "yamada-taro"),
                    Author("作画助手", "sakuga")
                ),
                theme = listOf(
                    Theme("冒险", "adventure"),
                    Theme("奇幻", "fantasy"),
                    Theme("热血", "hot-blood")
                ),
                parodies = null,
                brief = "这是一个关于少年冒险者在大陆上探索未知世界的故事。",
                lastUpdate = "2025-03-15",
                cover = "https://example.com/cover_detail.jpg",
                lastChapter = LastChapter("last-ch-id", "第 128 话"),
                popular = 10000,
                channel = null
            )
        }

        val mockChapters = remember {
            persistentListOf(
                ChapterInfo(
                    1,
                    "ch-uuid-1",
                    25,
                    1,
                    1024,
                    "第 128 话",
                    "comic-id",
                    "preview-comic",
                    "g1",
                    "group-alpha",
                    0,
                    null,
                    "2025-03-15",
                    "prev-ch-127",
                    "next-ch-129"
                ),
                ChapterInfo(
                    2,
                    "ch-uuid-2",
                    25,
                    2,
                    2048,
                    "第 127 话",
                    "comic-id",
                    "preview-comic",
                    "g1",
                    "group-alpha",
                    0,
                    null,
                    "2025-03-08",
                    "prev-ch-126",
                    "ch-uuid-1"
                ),
                ChapterInfo(
                    3,
                    "ch-uuid-3",
                    25,
                    3,
                    1536,
                    "第 126 话",
                    "comic-id",
                    "preview-comic",
                    "g1",
                    "group-alpha",
                    0,
                    null,
                    "2025-03-01",
                    "prev-ch-125",
                    "ch-uuid-2"
                )
            )
        }

        val mockGroups = remember {
            persistentListOf(
                ChapterGroup("group-alpha", 128, "Alpha 汉化组"),
                ChapterGroup("group-beta", 85, "Beta 汉化组")
            )
        }

        var selectedGroup by remember { mutableStateOf("group-alpha") }
        var sortAscending by remember { mutableStateOf(true) }

        DetailContent(
            comicDetail = mockDetail,
            chapters = mockChapters,
            groups = mockGroups,
            selectedGroup = selectedGroup,
            sortAscending = sortAscending,
            isLoggedIn = true,
            isInShelf = false,
            isShelfLoading = false,
            browse = null,
            startChapterName = mockChapters.firstOrNull()?.name,
            startChapterIsContinue = false,
            batchSelection = null,
            chapterDownloadStatuses = emptyMap(),
            chapterDownloadProgresses = emptyMap(),
            onChapterClick = { _, _, _, _ -> },
            onChapterLongPress = {},
            onChapterToggleSelect = {},
            onGroupSelect = { selectedGroup = it },
            onToggleSortOrder = { sortAscending = !sortAscending },
            onToggleShelf = {},
            onStartReading = {},
            onNavigateBack = {},
            onExitBatchMode = {},
            onSelectAll = {},
            onInvertSelection = {},
            onSelectToLatest = {},
            onUnselectToLatest = {},
            onSelectToFirst = {},
            onUnselectToFirst = {},
            onSelectBetween = {},
            onUnselectBetween = {},
            canSelectBetween = { false },
            onClearSelection = {},
            onEnterManageMode = {},
            onConfirmBatchAction = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}
// endregion
