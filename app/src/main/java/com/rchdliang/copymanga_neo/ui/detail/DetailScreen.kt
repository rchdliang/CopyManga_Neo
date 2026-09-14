package com.rchdliang.copymanga_neo.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import com.rchdliang.copymanga_neo.data.download.ChapterDownloadStatus
import com.rchdliang.copymanga_neo.data.remote.util.toCompactLocale
import com.rchdliang.copymanga_neo.domain.model.Author
import com.rchdliang.copymanga_neo.domain.model.BrowseInfo
import com.rchdliang.copymanga_neo.domain.model.ChapterGroup
import com.rchdliang.copymanga_neo.domain.model.ChapterInfo
import com.rchdliang.copymanga_neo.domain.model.ComicInfo
import com.rchdliang.copymanga_neo.domain.model.DisplayValue
import com.rchdliang.copymanga_neo.domain.model.LastChapter
import com.rchdliang.copymanga_neo.domain.model.Theme
import com.rchdliang.copymanga_neo.domain.model.blocks
import com.rchdliang.copymanga_neo.ui.components.ChapterItem
import com.rchdliang.copymanga_neo.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.ui.components.comicContainerSharedBounds
import com.rchdliang.copymanga_neo.ui.components.comicCoverSharedElement
import com.rchdliang.copymanga_neo.ui.components.currentComicTransitionData
import com.rchdliang.copymanga_neo.ui.components.isComicSharedTransitionActive
import com.rchdliang.copymanga_neo.ui.preview.previewImageModel
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DetailScreen(
    pathWord: String,
    viewModel: DetailViewModel = hiltViewModel(),
    onChapterClick: (String, String, String, String) -> Unit,
    onNavigateBack: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onThemeClick: (Theme) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pullToRefreshState = rememberPullToRefreshState()
    val toastHost = LocalToastHost.current
    var pendingTheme by remember(pathWord) { mutableStateOf<Theme?>(null) }
    val transitionData = currentComicTransitionData(pathWord)
    val sharedTransitionKey = transitionData?.transitionKey ?: pathWord
    val isSharedTransitionActive = isComicSharedTransitionActive(pathWord)
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val windowWidthDp = with(density) { containerSize.width.toDp() }
    val detailCoverWidth = if (windowWidthDp >= 840.dp) 180.dp else 144.dp
    var keepTransitionCover by remember(pathWord, transitionData) {
        mutableStateOf(transitionData != null)
    }

    LaunchedEffect(pathWord, transitionData) {
        if (transitionData != null) {
            keepTransitionCover = true
            delay(700)
            keepTransitionCover = false
        }
    }

    val isWaitingForDetail = uiState.comicDetail == null &&
            uiState.error == null &&
            uiState.riskControlReason == null
    val showTransitionCover = transitionData != null &&
            (keepTransitionCover || isSharedTransitionActive || isWaitingForDetail)

    LaunchedEffect(pathWord) {
        viewModel.loadDetail(pathWord)
    }

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
                    text = if (isBlocked) "是否取消屏蔽「${theme.name}」？首页将恢复显示包含此标签的漫画。"
                    else "是否将「${theme.name}」加入黑名单？首页将自动隐藏包含此标签的漫画。",
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

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .comicContainerSharedBounds(sharedTransitionKey)
                .matchParentSize()
        ) {}

        PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = { viewModel.refreshDetail() },
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
                .background(MaterialTheme.colorScheme.surface)
        ) {
            when {
            uiState.error != null && uiState.comicDetail == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
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
                        text = "加载失败：${uiState.error}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 32.dp),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = { viewModel.loadDetail(pathWord) },
                    ) { Text("重试") }
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
                            onChapterClick(
                                info.uuid,
                                uiState.comicDetail!!.pathWord,
                                info.name,
                                info.groupPathWord
                            )
                        }
                    }
                }
                // 计算点击"继续/开始阅读"后跳转的目标章节，用于按钮文案展示
                val startChapterInfo =
                    remember(uiState.chapters, uiState.browse, uiState.sortAscending) {
                        viewModel.getStartChapter()
                    }
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
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        }

        transitionData?.takeIf { showTransitionCover }?.let { coverData ->
            AsyncImage(
                model = previewImageModel(coverData.cover),
                contentDescription = coverData.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 48.dp)
                    .comicCoverSharedElement(sharedTransitionKey)
                    .width(detailCoverWidth)
                    .aspectRatio(3f / 4f)
                    .clip(MaterialTheme.shapes.medium)
            )
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DetailContent(
    comicDetail: ComicInfo,
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
    detailCoverWidth: Dp = 120.dp,
    transitionPathWord: String? = null,
    hideCoverDuringTransition: Boolean = false,
    modifier: Modifier
) {
    val currentPathWord = remember(comicDetail.pathWord) { comicDetail.pathWord }
    val currentOnChapterClick by rememberUpdatedState(onChapterClick)
    val currentOnChapterLongPress by rememberUpdatedState(onChapterLongPress)
    val currentOnChapterToggleSelect by rememberUpdatedState(onChapterToggleSelect)
    val coverTransitionModifier = if (
        transitionPathWord != null && !hideCoverDuringTransition
    ) {
        Modifier.comicCoverSharedElement(transitionPathWord)
    } else {
        Modifier
    }

    val gridState = rememberLazyGridState()
    var scrollToChapterIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(batchSelection, scrollToChapterIndex) {
        if (batchSelection != null && scrollToChapterIndex >= 0) {
            gridState.animateScrollToItem(scrollToChapterIndex)
            scrollToChapterIndex = -1
        }
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        val isInBatchMode = batchSelection != null
        BackHandler(enabled = isInBatchMode) {
            onExitBatchMode()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .scrollable(
                    state = rememberScrollableState { 0f },
                    orientation = Orientation.Vertical
                )
        ) {
            if (isInBatchMode) {
                val selectedCount = batchSelection.selectedChapterUuids.size
                SelectionTopRow(
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
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (chapters.isNotEmpty()) {
                        IconButton(onClick = onEnterManageMode) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "管理下载",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SubcomposeAsyncImage(
                    model = previewImageModel(comicDetail.cover),
                    contentDescription = comicDetail.name,
                    contentScale = ContentScale.Crop,
                    modifier = coverTransitionModifier
                        .width(detailCoverWidth)
                        .aspectRatio(3f / 4f)
                        .clip(MaterialTheme.shapes.medium)
                        .alpha(if (hideCoverDuringTransition) 0f else 1f)
                ) {
                    when (painter.state) {
                        is AsyncImagePainter.State.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                LoadingIndicator(
                                    modifier = Modifier
                                        .width(32.dp)
                                        .aspectRatio(1f),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        is AsyncImagePainter.State.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    imageVector = Icons.Default.BrokenImage,
                                    contentDescription = "加载失败",
                                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }

                        is AsyncImagePainter.State.Success -> {
                            Image(
                                painter = painter,
                                contentDescription = comicDetail.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        else -> Unit
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = comicDetail.name,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "作者",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        comicDetail.author?.let { authors ->
                            Text(
                                text = authors.joinToString { author -> author.name },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Pending,
                            contentDescription = "状态",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (comicDetail.status.value == 1) "已完结" else "连载中",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Update,
                            contentDescription = "最后更新",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = comicDetail.lastUpdate ?: "--",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocalFireDepartment,
                            contentDescription = "热度",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = comicDetail.popular?.toCompactLocale() ?: "--",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleShelf,
                    modifier = Modifier.weight(1f),
                    enabled = !isShelfLoading && isLoggedIn
                ) {
                    Text(
                        text = if (isInShelf) "已在书架" else "加入书架",
                        maxLines = 1
                    )
                }
                Button(
                    onClick = onStartReading,
                    modifier = Modifier.weight(1f),
                    enabled = chapters.isNotEmpty()
                ) {
                    val prefix = if (startChapterIsContinue) "继续阅读" else "开始阅读"
                    Text(
                        text = if (!startChapterName.isNullOrEmpty()) "$prefix：$startChapterName" else prefix,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            comicDetail.theme?.let { themes ->
                if (themes.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        themes.forEach { theme ->
                            val isBlocked = blockedThemes.blocks(theme)
                            Box(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.large)
                                    .background(
                                        if (isBlocked) MaterialTheme.colorScheme.errorContainer
                                        else MaterialTheme.colorScheme.secondaryContainer
                                    )
                                    .combinedClickable(
                                        role = Role.Button,
                                        onClickLabel = "查看${theme.name}漫画",
                                        onLongClickLabel = if (isBlocked) "取消屏蔽此标签" else "屏蔽此标签",
                                        onClick = { onThemeClick(theme) },
                                        onLongClick = { onThemeLongClick(theme) }
                                    )
                                    //.minimumInteractiveComponentSize()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = theme.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isBlocked) MaterialTheme.colorScheme.onErrorContainer
                                    else MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            comicDetail.brief?.let { brief ->
                var expanded by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { expanded = !expanded }
                        .animateContentSize()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = brief,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (groups.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
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
                                Text(text = group.name, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "章节列表",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (groups.isNotEmpty()) {
                    val currentGroup =
                        remember(
                            groups,
                            selectedGroup
                        ) { groups.find { it.pathWord == selectedGroup } }
                    if (currentGroup != null) {
                        var sortMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            Button(
                                onClick = { sortMenuExpanded = true },
                                modifier = Modifier.defaultMinSize(
                                    minWidth = 0.dp,
                                    minHeight = 0.dp
                                ),
                                shape = MaterialTheme.shapes.extraSmall,
                                colors = ButtonDefaults.buttonColors(Color.Transparent),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "共${currentGroup.total}话",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = Icons.Default.UnfoldMore,
                                    contentDescription = "排序菜单",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("正序") },
                                    onClick = {
                                        if (!sortAscending) onToggleSortOrder()
                                        sortMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("倒序") },
                                    onClick = {
                                        if (sortAscending) onToggleSortOrder()
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            val displayedChapters = remember(chapters, sortAscending) {
                if (sortAscending) chapters else chapters.reversed()
            }

            val bottomPadding = if (isInBatchMode) 80.dp else 16.dp

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 90.dp),
                state = gridState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 4.dp,
                    end = 16.dp,
                    bottom = bottomPadding
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(
                    count = displayedChapters.size,
                    key = { displayedChapters[it].uuid },
                    contentType = { "chapter" }
                ) { index ->
                    val chapter = displayedChapters[index]
                    val status = chapterDownloadStatuses[chapter.uuid]
                    val progress = chapterDownloadProgresses[chapter.uuid]
                    ChapterItem(
                        chapter = chapter,
                        isSelected = batchSelection?.selectedChapterUuids?.contains(chapter.uuid) == true,
                        isSelectionMode = isInBatchMode,
                        downloadStatus = status,
                        downloadProgress = progress,
                        onClick = {
                            if (isInBatchMode) {
                                currentOnChapterToggleSelect(chapter.uuid)
                            } else {
                                currentOnChapterClick(
                                    chapter.uuid,
                                    currentPathWord,
                                    chapter.name,
                                    chapter.groupPathWord ?: "default"
                                )
                            }
                        },
                        onLongPress = {
                            scrollToChapterIndex = index
                            currentOnChapterLongPress(chapter.uuid)
                        }
                    )
                }
            }
        }

        if (isInBatchMode) {
            val downloadedUuids = chapterDownloadStatuses
                .filter { it.value == ChapterDownloadStatus.DOWNLOADED }
                .keys
            val activeDownloadUuids = chapterDownloadStatuses
                .filter {
                    it.value == ChapterDownloadStatus.WAITING ||
                            it.value == ChapterDownloadStatus.DOWNLOADING
                }
                .keys
            val currentSelection = batchSelection.selectedChapterUuids
            val downloadCount = currentSelection.count { uuid ->
                val status = chapterDownloadStatuses[uuid]
                status != ChapterDownloadStatus.DOWNLOADED &&
                        status != ChapterDownloadStatus.WAITING &&
                        status != ChapterDownloadStatus.DOWNLOADING
            }
            val deleteCount = downloadedUuids.count { it !in currentSelection }
            val cancelCount = activeDownloadUuids.count { it !in currentSelection }
            ManageBottomBar(
                downloadCount = downloadCount,
                deleteCount = deleteCount,
                cancelCount = cancelCount,
                onApply = onConfirmBatchAction,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun SelectionTopRow(
    selectedCount: Int,
    canSelectBetween: Boolean,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onSelectToLatest: () -> Unit,
    onUnselectToLatest: () -> Unit,
    onSelectToFirst: () -> Unit,
    onUnselectToFirst: () -> Unit,
    onSelectBetween: () -> Unit,
    onUnselectBetween: () -> Unit,
    onClearSelection: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onExit) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "取消选择",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "已选 $selectedCount 章",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = "全选",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onInvertSelection) {
                Icon(
                    imageVector = Icons.Default.Deselect,
                    contentDescription = "反选",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                DropdownMenuPopup(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuGroup(
                        shapes = MenuDefaults.groupShape(0, 2)
                    ) {
                        MenuDefaults.DropdownMenuGroupLabel {
                            Text(
                                "选中",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("选中本章到最新") },
                            onClick = {
                                onSelectToLatest()
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("选中第一章到本章") },
                            onClick = {
                                onSelectToFirst()
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("选中之间") },
                            onClick = {
                                onSelectBetween()
                                menuExpanded = false
                            },
                            enabled = canSelectBetween
                        )
                    }
                    DropdownMenuGroup(
                        shapes = MenuDefaults.groupShape(1, 2)
                    ) {
                        MenuDefaults.DropdownMenuGroupLabel {
                            Text(
                                "取消选中",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("取消选中本章到最新") },
                            onClick = {
                                onUnselectToLatest()
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("取消选中第一章到本章") },
                            onClick = {
                                onUnselectToFirst()
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("取消选中之间") },
                            onClick = {
                                onUnselectBetween()
                                menuExpanded = false
                            },
                            enabled = canSelectBetween
                        )
                    }
                    DropdownMenuGroup(
                        shapes = MenuDefaults.groupShape(2, 2)
                    ) {
                        DropdownMenuItem(
                            text = { Text("清空选择") },
                            onClick = {
                                onClearSelection()
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun ManageBottomBar(
    downloadCount: Int,
    deleteCount: Int,
    cancelCount: Int,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasChanges = downloadCount > 0 || deleteCount > 0 || cancelCount > 0
    val parts = buildList {
        if (downloadCount > 0) add("下载 $downloadCount 章")
        if (cancelCount > 0) add("取消下载 $cancelCount 章")
        if (deleteCount > 0) add("删除本地 $deleteCount 章")
    }
    val label = if (parts.isEmpty()) "没有变更" else parts.joinToString(", ")
    BottomAppBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        actions = {
            Button(
                onClick = onApply,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                enabled = hasChanges,
                colors = if (deleteCount > 0) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Icon(
                    imageVector = when {
                        deleteCount > 0 -> Icons.Default.Delete
                        cancelCount > 0 && downloadCount == 0 -> Icons.Default.Close
                        else -> Icons.Default.Downloading
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
        }
    )
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
