package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import com.rchdliang.copymanga_neo.multiplatform.ui.components.routeProgressAllowed
import com.rchdliang.copymanga_neo.multiplatform.ui.components.ActiveRouteProgressFlushEffect
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive
import com.rchdliang.copymanga_neo.multiplatform.ui.components.ActiveRouteLaunchedEffect
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BackHandler
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalUiPlatform
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import coil3.compose.LocalPlatformContext
import coil3.SingletonImageLoader
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.request.ImageRequest
import com.rchdliang.copymanga_neo.domain.model.CommentKind
import com.rchdliang.copymanga_neo.domain.model.CommentTarget
import com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord
import com.rchdliang.copymanga_neo.domain.model.ReaderBookmark
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import com.rchdliang.copymanga_neo.domain.model.ShareTarget
import com.rchdliang.copymanga_neo.domain.reading.ReadingPositionPolicy
import com.rchdliang.copymanga_neo.multiplatform.ui.comments.CommentsDialog
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds


@Composable
fun ReaderScreen(
    chapterPathWord: String,
    comicPathWord: String,
    chapterName: String,
    groupPathWord: String = "default",
    onNavigateBack: () -> Unit,
    viewModel: ReaderViewModel,
    services: ReaderUiServices,
    initialPage: Int = -1,
    initialFraction: Double = 0.0
) {
    val committedActive by rememberUpdatedState(LocalRouteActive.current)
    val reportingLifecycle = LocalLifecycleOwner.current.lifecycle
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentChapter = uiState as? ReaderUiState.Success
    val tools by viewModel.toolsSettings.collectAsStateWithLifecycle()
    val readerBookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val commentsAllowed by viewModel.commentsAllowed.collectAsStateWithLifecycle()
    val closedChapters by viewModel.closedChapters.collectAsStateWithLifecycle()
    val appending by viewModel.appending.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    var commentChapter by remember(comicPathWord) { mutableStateOf<String?>(null) }
    var commentRefreshVersion by remember(comicPathWord) { mutableIntStateOf(0) }
    if (commentChapter != null && currentChapter != null && commentsAllowed && commentChapter !in closedChapters) {
        CommentsDialog(
            target = CommentTarget(CommentKind.CHAPTER, commentChapter.orEmpty()),
            title = currentChapter.chapters.firstOrNull { it.uuid == commentChapter }?.content?.chapterName
                ?: chapterName,
            comicPathWord = comicPathWord,
            viewModel = services.modalComments(),
            onDismiss = { commentChapter = null; commentRefreshVersion++ }
        )
    }
    val preferences by viewModel.readerPreferences.collectAsStateWithLifecycle()
    val readingDirectionPref = preferences?.readingDirection ?: "RIGHT_TO_LEFT"
    val pageScalePref = preferences?.pageScale ?: "FIT_SCREEN"
    val screenOrientationPref = preferences?.screenOrientation ?: "SYSTEM_DEFAULT"
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val storageError by viewModel.localReadingError.collectAsStateWithLifecycle()
    val toastHost = LocalToastHost.current
    val screenOrient = remember(screenOrientationPref) {
        ScreenOrientationSetting.fromName(screenOrientationPref)
    }
    if (LocalRouteActive.current) services.platform.ImmersiveEffect(screenOrient.takeIf { preferences != null })

    ActiveRouteLaunchedEffect(comicPathWord, chapterPathWord, groupPathWord) {
        viewModel.openReader(comicPathWord, chapterPathWord, groupPathWord)
    }

    // 章节切换时的目标页码：null=保持当前页（方向切换/刷新），非null=跳转到指定页（切章/撤回）
    val pendingInitialPage = remember { mutableStateOf<Int?>(initialPage.takeIf { it >= 0 }) }
    val pendingInitialFraction =
        remember { mutableStateOf<Double?>(initialFraction.takeIf { initialPage >= 0 }) }
    val pendingInitialChapter =
        remember { mutableStateOf<String?>(chapterPathWord.takeIf { initialPage >= 0 }) }


    ActiveRouteProgressFlushEffect(viewModel) { viewModel.flushPosition() }
    ActiveRouteLaunchedEffect(storageError) { storageError?.let { toastHost.showToast(it) } }
    ActiveRouteLaunchedEffect(toastMessage) {
        toastMessage?.let {
            if (it.startsWith("加载失败") || it.startsWith("无法读取")) {
                pendingInitialPage.value = null; pendingInitialFraction.value =
                    null; pendingInitialChapter.value = null
            }
            toastHost.showToast(it)
            viewModel.clearToastMessage()
        }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val state = uiState) {
            is ReaderUiState.Loading -> CircularWavyProgressIndicator(
                modifier = Modifier.align(Alignment.Center), color = Color.White
            )

            is ReaderUiState.Error -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        viewModel.refreshCurrentChapter()
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "加载失败：${state.message}", color = Color.White,
                        modifier = Modifier.padding(16.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "点击屏幕重试", color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            is ReaderUiState.RiskControl -> ReaderRiskControlContent(
                state.reason, modifier = Modifier.align(Alignment.Center)
            )

            is ReaderUiState.Success -> ReaderSuccessContent(
                services = services,
                externalModalVisible = commentChapter != null,
                images = state.content.imageUrls,
                chapterName = state.content.chapterName.ifEmpty { chapterName },
                readingDirection = readingDirectionPref,
                pageScale = pageScalePref,
                screenOrientation = screenOrientationPref,
                onPrevChapter = { viewModel.loadPrevChapter() },
                onNextChapter = { viewModel.loadNextChapter() },
                hasPrevChapter = state.content.prevUuid != null,
                hasNextChapter = state.content.nextUuid != null,
                onRefresh = { viewModel.refreshCurrentChapter() },
                onReadingDirectionChange = { viewModel.setReadingDirection(it) },
                onPageScaleChange = { viewModel.setPageScale(it) },
                onScreenOrientationChange = { viewModel.setScreenOrientation(it) },
                onResetPreferences = viewModel::resetComicPreferences,
                onComments = { commentChapter = it },
                pendingInitialPage = pendingInitialPage,
                pendingInitialFraction = pendingInitialFraction,
                pendingInitialChapter = pendingInitialChapter,
                activeChapterUuid = state.chapterUuid,
                sessionChapters = if (tools.continuousChapters) state.chapters else state.chapters.filter { it.uuid == state.chapterUuid },
                tools = tools, onToolsChange = viewModel::updateTools,
                commentsAllowed = commentsAllowed, closedChapters = closedChapters,
                commentRefreshVersion = commentRefreshVersion,
                onCloseComments = viewModel::closeChapterComments,
                onActivateChapter = { if (routeProgressAllowed(committedActive, reportingLifecycle.currentState)) viewModel.activateChapter(it) },
                onWindowSettled = { if (routeProgressAllowed(committedActive, reportingLifecycle.currentState)) viewModel.retainWindow(it) },
                onPrependPrevious = { viewModel.prependPreviousChapter(it) },
                savedPosition = viewModel.savedPosition(state.chapterUuid),
                onPosition = { chapter, page, fraction, complete ->
                    if (routeProgressAllowed(committedActive, reportingLifecycle.currentState)) viewModel.recordPosition(chapter, page, fraction, complete)
                },
                onCompleted = { if (routeProgressAllowed(committedActive, reportingLifecycle.currentState)) viewModel.completeChapter(it) },
                onAppendNext = { viewModel.appendNextChapter(it) }, appending = appending,
                bookmarks = readerBookmarks.filter { it.comicPathWord == comicPathWord },
                onToggleBookmark = viewModel::toggleBookmark,
                onBookmarkSelected = { mark ->
                    pendingInitialChapter.value = mark.chapterUuid
                    pendingInitialFraction.value = mark.fraction
                    viewModel.jumpToBookmark(mark)
                },
                onBack = onNavigateBack, catalog = catalog,
                onLoadCatalog = viewModel::loadCatalog,
                onSelectCatalog = viewModel::selectCatalogChapter,
                shareComicPathWord = comicPathWord
            )
        }
    }
}

// ── ReaderSuccessContent ──────────────────────────────────────────

@Composable
fun ReaderSuccessContent(
    images: List<String>,
    chapterName: String,
    readingDirection: String,
    pageScale: String,
    screenOrientation: String,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    hasPrevChapter: Boolean,
    hasNextChapter: Boolean,
    onRefresh: () -> Unit,
    onReadingDirectionChange: (String) -> Unit,
    onPageScaleChange: (String) -> Unit,
    onScreenOrientationChange: (String) -> Unit,
    pendingInitialPage: MutableState<Int?>,
    modifier: Modifier = Modifier,
    onResetPreferences: () -> Unit = {},
    onComments: (String) -> Unit = {},
    sessionChapters: List<ReaderSessionChapter> = emptyList(),
    tools: ReaderToolsSettings = ReaderToolsSettings(),
    onToolsChange: ((ReaderToolsSettings) -> ReaderToolsSettings) -> Unit = {},
    commentsAllowed: Boolean = false,
    commentRefreshVersion: Int = 0,
    closedChapters: Set<String> = emptySet(),
    onCloseComments: (String) -> Unit = {},
    onActivateChapter: (String) -> Unit = {},
    onWindowSettled: (String) -> Unit = {},
    onPrependPrevious: (Boolean) -> Unit = {},
    onAppendNext: (Boolean) -> Unit = {},
    appending: Boolean = false,
    bookmarks: List<ReaderBookmark> = emptyList(),
    onToggleBookmark: (String, Int, Double) -> Unit = { _, _, _ -> },
    onBookmarkSelected: (ReaderBookmark) -> Unit = {},
    onBack: () -> Unit = {},
    catalog: ReaderCatalogUiState = ReaderCatalogUiState(),
    onLoadCatalog: () -> Unit = {},
    onSelectCatalog: (String) -> Unit = {},
    shareComicPathWord: String? = null,
    savedPosition: LocalReadingRecord? = null,
    onPosition: (String, Int, Double, Boolean) -> Unit = { _, _, _, _ -> },
    onCompleted: (String) -> Unit = {},
    pendingInitialFraction: MutableState<Double?>? = null,
    pendingInitialChapter: MutableState<String?>? = null,
    activeChapterUuid: String? = null,
    services: ReaderUiServices? = null,
    externalModalVisible: Boolean = false
) {
    val context = LocalPlatformContext.current
    val owner = LocalLifecycleOwner.current
    val preview = LocalInspectionMode.current
    check(preview || services != null) { "Provide ReaderUiServices for the reader route." }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val keyboardStep = with(LocalDensity.current) { 48.dp.toPx() }
    var keyboardScrollJob by remember { mutableStateOf<Job?>(null) }
    val toast = LocalToastHost.current
    val dir = ReadingDirection.fromName(readingDirection)
    val isContinuous = dir == ReadingDirection.TOP_TO_BOTTOM
    val chapters = sessionChapters.ifEmpty {
        listOf(
            ReaderSessionChapter(
                "preview", "default",
                com.rchdliang.copymanga_neo.domain.model.ChapterContent(
                    images,
                    null,
                    null,
                    chapterName
                )
            )
        )
    }
    val pages = remember(chapters, tools.inlineComments, commentsAllowed, closedChapters) {
        chapters.flatMap { chapter ->
            buildList {
                chapter.content.imageUrls.forEachIndexed { index, source ->
                    add(
                        ReaderPageEntry(
                            "${chapter.uuid}:image:$index",
                            chapter.uuid,
                            chapter.content.chapterName.ifBlank { chapterName },
                            source,
                            index,
                            chapter.content.imageUrls.size
                        )
                    )
                }
                if (tools.inlineComments && commentsAllowed && chapter.uuid !in closedChapters && chapter.content.imageUrls.isNotEmpty())
                    add(
                        ReaderPageEntry(
                            "${chapter.uuid}:comments",
                            chapter.uuid,
                            chapter.content.chapterName,
                            null,
                            chapter.content.imageUrls.lastIndex,
                            chapter.content.imageUrls.size
                        )
                    )
            }
        }
    }
    val initial = (pendingInitialPage.value ?: savedPosition?.page ?: 0).coerceIn(
        0,
        pages.lastIndex.coerceAtLeast(0)
    )
    val pager =
        rememberPagerState(initialPage = initial, pageCount = { pages.size.coerceAtLeast(1) })
    val list = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val visibleIndex by remember(
        isContinuous,
        list,
        pager
    ) { derivedStateOf { if (isContinuous) list.firstVisibleItemIndex else pager.currentPage } }
    val visibleKey by remember(isContinuous, list, pager) {
        derivedStateOf {
            if (isContinuous) list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == list.firstVisibleItemIndex }?.key
            else pager.layoutInfo.visiblePagesInfo.firstOrNull { it.index == pager.currentPage }?.key
        }
    }
    val page = ReaderWindowPolicy.pageAt(pages, visibleIndex, visibleKey)
    // 底栏和章末评论共用同一章节的加载状态，角标取 API 总数，避免重复请求。
    val chapterCommentsViewModel = if (!preview && page != null) {
        services!!.chapterComments(page.chapterUuid)
    } else null
    val chapterCommentsState =
        chapterCommentsViewModel?.uiState?.collectAsStateWithLifecycle()?.value
    var anchor by rememberSaveable { mutableStateOf(savedPosition?.let { "${it.chapter}:image:${it.page}" }) }
    var anchorChapter by rememberSaveable { mutableStateOf(savedPosition?.chapter) }
    var anchorPage by rememberSaveable { mutableIntStateOf(savedPosition?.page ?: 0) }
    var anchorFraction by rememberSaveable { mutableStateOf(savedPosition?.fraction ?: 0.0) }
    val readyImages = remember { mutableStateMapOf<String, Boolean>() }
    val visibleKeys by remember(isContinuous, list, pager) {
        derivedStateOf {
            if (isContinuous) list.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }.toSet()
            else pager.layoutInfo.visiblePagesInfo.mapNotNull { it.key as? String }.toSet()
        }
    }
    val retainedPageKeys = ReaderWindowPolicy.pageKeys(pages, pages.indexOfFirst { it.key == page?.key }, visibleKeys)
    val latestRetainedPageKeys by rememberUpdatedState(retainedPageKeys)
    androidx.compose.runtime.SideEffect {
        readyImages.keys.toList().filter { it !in retainedPageKeys }.forEach { readyImages.remove(it) }
        services?.comments?.retain(chapters.map { it.uuid }.toSet())
    }
    var restoring by remember { mutableStateOf(false) }
    val viewportAnchor = remember { ReaderViewportAnchor() }
    val viewportCorrection = remember { ReaderViewportCorrectionOwner() }
    val currentContinuousMode by rememberUpdatedState(isContinuous)
    var showControls by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showCatalog by remember { mutableStateOf(false) }
    var showImageMenu by rememberSaveable { mutableStateOf(false) }
    var selectedSource by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTitle by rememberSaveable { mutableStateOf("") }
    val routeActive = LocalRouteActive.current
    val currentRouteActive by rememberUpdatedState(routeActive)
    val reportingState by owner.lifecycle.currentStateFlow.collectAsState()
    var automatic by remember { mutableStateOf(false) }
    LaunchedEffect(routeActive) {
        if (!routeActive) { viewportCorrection.invalidate(); viewportAnchor.invalidate(); automatic = false; keyboardScrollJob?.cancel() }
    }
    var dragging by remember { mutableStateOf(false) }
    var sliderPreview by remember { mutableFloatStateOf(0f) }
    var pendingChapter by remember { mutableStateOf<String?>(null) }
    ActiveRouteLaunchedEffect(showControls, page?.chapterUuid, commentsAllowed, closedChapters) {
        val current = page ?: return@ActiveRouteLaunchedEffect
        if (showControls && commentsAllowed && current.chapterUuid !in closedChapters) {
            chapterCommentsViewModel?.load(current.chapterUuid)
        }
    }
    ActiveRouteLaunchedEffect(page?.chapterUuid, chapterCommentsState?.closed) {
        if (chapterCommentsState?.closed == true) page?.let { onCloseComments(it.chapterUuid) }
    }
    ActiveRouteLaunchedEffect(commentRefreshVersion) {
        val current = page ?: return@ActiveRouteLaunchedEffect
        if (commentRefreshVersion > 0 && commentsAllowed && current.chapterUuid !in closedChapters) {
            chapterCommentsViewModel?.load(current.chapterUuid, refresh = true)
        }
    }
    val currentAppending by rememberUpdatedState(appending)
    val loader = remember(context, services, tools.imageTimeoutSeconds, preview) {
        if (preview) SingletonImageLoader.get(context) else services!!.imageLoader(tools.imageTimeoutSeconds)
    }
    val pressedVolumeKeys = remember { mutableSetOf<Key>() }


    // 追加章节、移除评论页或改变阅读方向时保留当前页的稳定标识。
    LaunchedEffect(
        pages, isContinuous, pendingInitialPage.value, pendingInitialChapter?.value,
        if (pendingInitialChapter?.value != null) activeChapterUuid else null
    ) {
        // Explicit navigation, chapter changes and mode changes supersede an old viewport anchor.
        viewportCorrection.invalidate()
        viewportAnchor.invalidate()
        if (pages.isEmpty()) return@LaunchedEffect
        val requestedChapter = pendingInitialChapter?.value
        if (requestedChapter != null && requestedChapter != activeChapterUuid) return@LaunchedEffect
        val explicit = pendingInitialPage.value
        if (explicit == null && (list.isScrollInProgress || pager.isScrollInProgress)) {
            snapshotFlow { !list.isScrollInProgress && !pager.isScrollInProgress }.first { it }
            val index = if (isContinuous) list.firstVisibleItemIndex else pager.settledPage
            val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            val key = if (isContinuous) item?.key else pager.layoutInfo.visiblePagesInfo.firstOrNull { it.index == index }?.key
            ReaderWindowPolicy.pageAt(pages, index, key)?.let {
                anchor = it.key; anchorChapter = it.chapterUuid; anchorPage = it.page
                anchorFraction = if (isContinuous) ReadingPositionPolicy.fraction(list.firstVisibleItemScrollOffset, item?.size ?: 0) else 0.0
            }
        }
        val target = if (explicit != null)
            resolveReaderPageIndex(
                pages,
                null,
                requestedChapter ?: activeChapterUuid ?: pages.first().chapterUuid,
                explicit,
                initial
            )
        else resolveReaderPageIndex(pages, anchor, anchorChapter, anchorPage, initial)
        val fraction =
            if (!isContinuous) 0.0 else if (explicit != null) pendingInitialFraction?.value
                ?: 0.0 else anchorFraction
        restoring = true
        if (isContinuous) {
            val retainedItem = list.layoutInfo.visibleItemsInfo.firstOrNull {
                it.key == pages[target].key && readyImages[pages[target].key] == true
            }
            val measuredSize = retainedItem?.size
            list.scrollToItem(target, if (measuredSize != null) ReadingPositionPolicy.offset(fraction, measuredSize) else 0)
            if (fraction > 0 && measuredSize == null) {
                val size = snapshotFlow {
                    if (!restoring) 0 else list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
                        ?.takeIf { readyImages[pages[target].key] == true }?.size
                }.first { it != null && it >= 0 } ?: 0
                if (restoring) list.scrollToItem(
                    target,
                    ReadingPositionPolicy.offset(fraction, size)
                )
            }
        } else pager.scrollToPage(target)
        val finalIndex = if (restoring) target else visibleIndex.coerceIn(pages.indices)
        val finalPage = pages[finalIndex]
        anchor = finalPage.key; anchorChapter = finalPage.chapterUuid; anchorPage = finalPage.page
        anchorFraction = if (restoring) fraction else if (isContinuous) {
            val size =
                list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == finalIndex }?.size ?: 0
            ReadingPositionPolicy.fraction(list.firstVisibleItemScrollOffset, size)
        } else 0.0
        restoring = false
        pendingInitialChapter?.value = null
        pendingInitialFraction?.value = null
        pendingInitialPage.value = null
        // Placeholder heights and pages seen only while swiping cannot complete a chapter.
        snapshotFlow {
            val index = if (isContinuous) list.firstVisibleItemIndex else pager.settledPage
            val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            Triple(
                index, if (isContinuous) list.firstVisibleItemScrollOffset else 0,
                listOf(
                    item?.size ?: 0, list.layoutInfo.viewportEndOffset,
                    if (list.isScrollInProgress || pager.isScrollInProgress) 1 else 0,
                    if (readyImages[pages.getOrNull(index)?.key] == true) 1 else 0,
                    list.layoutInfo.visibleItemsInfo.hashCode(),
                    if (routeProgressAllowed(currentRouteActive, reportingState)) 1 else 0,
                    list.layoutInfo.viewportSize.width
                )
            )
        }.collect { (index, scrollOffset, metrics) ->
            if (restoring || !routeProgressAllowed(currentRouteActive, reportingState)) {
                viewportAnchor.invalidate()
                return@collect
            }
            val measuredPage = pages.getOrNull(index)
            val measured = measuredPage?.takeIf { it.source != null && readyImages[it.key] == true }
                ?.let { ReaderViewportSample(it.key, scrollOffset, metrics[0]) }
            val correction = viewportAnchor.observe(metrics[6], measured,
                eligible = readerViewportResizeAllowed(
                    desktopContinuous = services?.platform is DesktopReaderKeyboard && isContinuous,
                    progressAllowed = routeProgressAllowed(currentRouteActive, reportingState),
                    scrolling = metrics[2] != 0,
                    pendingJump = pendingInitialPage.value != null || pendingInitialChapter?.value != null,
                    interactionBusy = dragging || automatic || keyboardScrollJob?.isActive == true))
            if (correction != null) {
                val target = pages.indexOfFirst { it.key == correction.key }
                if (target >= 0 && !list.isScrollInProgress) {
                    var correctionCommitted = false
                    try {
                        correctionCommitted = correctReaderViewport(
                            owner = viewportCorrection,
                            fraction = correction.fraction,
                            eligible = {
                                !restoring && currentContinuousMode &&
                                    routeProgressAllowed(currentRouteActive, reportingState) &&
                                    pendingInitialPage.value == null && pendingInitialChapter?.value == null &&
                                    !list.isScrollInProgress && !dragging && !automatic && keyboardScrollJob?.isActive != true
                            },
                            ensureTargetVisible = {
                                if (list.layoutInfo.visibleItemsInfo.none { it.index == target }) list.scrollToItem(target)
                            },
                            measure = {
                                list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
                                    ?.takeIf { readyImages[correction.key] == true }?.let {
                                        ReaderViewportMeasurement(list.layoutInfo.viewportSize.width, it.size)
                                    }
                            },
                            scroll = { offset -> list.scrollToItem(target, offset) },
                            commit = {
                                anchor = correction.key
                                anchorChapter = pages[target].chapterUuid
                                anchorPage = pages[target].page
                                anchorFraction = correction.fraction
                            })
                    } catch (cancel: CancellationException) {
                        // A higher-priority real scroll may interrupt correction without killing reporting.
                        currentCoroutineContext().ensureActive()
                    }
                    if (!correctionCommitted) {
                        if (!restoring && !viewportCorrection.active) viewportAnchor.invalidate()
                        return@collect
                    }
                    viewportAnchor.invalidate()
                    val settled = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == list.firstVisibleItemIndex }
                    val settledPage = pages.getOrNull(list.firstVisibleItemIndex)
                    viewportAnchor.observe(list.layoutInfo.viewportSize.width,
                        settledPage?.takeIf { it.source != null && readyImages[it.key] == true }?.let {
                            ReaderViewportSample(it.key, list.firstVisibleItemScrollOffset, settled?.size ?: 0)
                        }, !restoring && readerViewportResizeAllowed(
                            services?.platform is DesktopReaderKeyboard && currentContinuousMode,
                            routeProgressAllowed(currentRouteActive, reportingState), list.isScrollInProgress,
                            pendingInitialPage.value != null || pendingInitialChapter?.value != null,
                            dragging || automatic || keyboardScrollJob?.isActive == true))
                    // Never persist the transient old pixel offset against the new image extent.
                    return@collect
                }
            }
            pages.getOrNull(index)?.let {
                val sameImage = anchor == it.key
                anchor = it.key; anchorChapter = it.chapterUuid; anchorPage = it.page
                if (!sameImage && readyImages[it.key] != true) anchorFraction = 0.0
                if (!isContinuous || readyImages[it.key] == true)
                    anchorFraction = if (isContinuous) ReadingPositionPolicy.fraction(
                        scrollOffset,
                        metrics[0]
                    ) else 0.0
                if (it.source == null) anchorFraction = if (isContinuous) 1.0 else 0.0
                onActivateChapter(it.chapterUuid)
                if (!dragging) sliderPreview = it.page.toFloat()
                if (it.source == null) automatic = false
                val bottomVisible =
                    it.source == null || !isContinuous || list.layoutInfo.visibleItemsInfo.any { item ->
                        val entry = pages.getOrNull(item.index)
                        entry?.chapterUuid == it.chapterUuid && entry.page == it.imageCount - 1 &&
                                entry.source != null && readyImages[entry.key] == true && item.offset + item.size <= metrics[1]
                    }
                val complete = ReadingPositionPolicy.completed(
                    it.page, it.imageCount, metrics[2] == 0,
                    bottomVisible && (it.source == null || readyImages[it.key] == true),
                    continuous = isContinuous
                )
                onPosition(it.chapterUuid, it.page, anchorFraction, complete)
                if (isContinuous && metrics[2] == 0) list.layoutInfo.visibleItemsInfo.forEach { item ->
                    pages.getOrNull(item.index)?.takeIf { entry ->
                        entry.source != null &&
                                readyImages[entry.key] == true && entry.page == entry.imageCount - 1 &&
                                item.offset + item.size <= metrics[1]
                    }?.let { entry -> onCompleted(entry.chapterUuid) }
                }
                if (metrics[2] == 0) onWindowSettled(it.chapterUuid)
            }
        }
    }
    LaunchedEffect(showControls, dragging, sliderPreview) {
        if (showControls && !dragging) {
            delay(5.seconds); showControls = false
        }
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                automatic = false
                pressedVolumeKeys.clear()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val backGestureInProgress = LocalUiPlatform.current?.backDispatcher?.isGestureInProgress == true
    LaunchedEffect(backGestureInProgress) {
        // Back is a user interaction even when its preview keeps this route RESUMED.
        // Cancellation deliberately does not restart automatic reading or flush the route.
        if (backGestureInProgress) automatic = false
    }
    if (routeActive) services?.platform?.KeepScreenOnEffect(automatic)
    ActiveRouteLaunchedEffect(showSettings, showImageMenu, showBookmarks, showCatalog) {
        if (showSettings || showImageMenu || showBookmarks || showCatalog) automatic = false
        else focus.requestFocus()
    }

    ActiveRouteLaunchedEffect(externalModalVisible) {
        if (services?.platform is DesktopReaderKeyboard) {
            if (externalModalVisible) {
                automatic = false
                keyboardScrollJob?.cancel()
            } else if (!showSettings && !showImageMenu && !showBookmarks && !showCatalog) focus.requestFocus()
        }
    }

    fun jump(index: Int, animate: Boolean = false) {
        scope.launch {
            if (pages.isEmpty()) return@launch
            val safe = index.coerceIn(pages.indices)
            if (isContinuous) {
                if (animate) list.animateScrollToItem(safe) else list.scrollToItem(safe)
            } else {
                if (animate) pager.animateScrollToPage(safe) else pager.scrollToPage(safe)
            }
        }
    }

    fun nextChapter() {
        val current = chapters.firstOrNull { it.uuid == page?.chapterUuid }
        val next = current?.content?.nextUuid
        val target = pages.indexOfFirst { it.chapterUuid == next && it.source != null }
        if (target >= 0) jump(target)
        else if (next != null && tools.continuousChapters) {
            pendingChapter = next; onAppendNext(true)
        } else if (hasNextChapter) {
            pendingInitialPage.value = 0; onNextChapter()
        } else toast.showToast("已是最后一章")
    }

    fun previousChapter() {
        val current = chapters.firstOrNull { it.uuid == page?.chapterUuid }
        val previous = current?.content?.prevUuid
        val target = pages.indexOfFirst { it.chapterUuid == previous && it.source != null }
        if (target >= 0) jump(target)
        else if (hasPrevChapter) {
            pendingInitialPage.value = 0; onPrevChapter()
        } else toast.showToast("已是第一章")
    }

    fun forward() {
        if (visibleIndex < pages.lastIndex) jump(visibleIndex + 1, true) else nextChapter()
    }

    fun backward() {
        if (visibleIndex > 0) jump(visibleIndex - 1, true) else previousChapter()
    }
    LaunchedEffect(pages, pendingChapter) {
        val uuid = pendingChapter ?: return@LaunchedEffect
        val target = pages.indexOfFirst { it.chapterUuid == uuid && it.source != null }
        if (target >= 0) {
            pendingChapter = null; jump(target)
        }
    }
    ActiveRouteLaunchedEffect(
        page?.key,
        chapters.lastOrNull()?.uuid,
        chapters.lastOrNull()?.content?.nextUuid,
        chapters.size,
        tools.continuousChapters,
        tools.preloadPages
    ) {
        val current = page ?: return@ActiveRouteLaunchedEffect
        if (tools.continuousChapters && current.chapterUuid == chapters.lastOrNull()?.uuid &&
            current.page >= current.imageCount - maxOf(tools.preloadPages, 1)
        ) onAppendNext(false)
    }
    ActiveRouteLaunchedEffect(page?.key, chapters.firstOrNull()?.uuid, chapters.firstOrNull()?.content?.prevUuid, chapters.size, tools.continuousChapters, tools.preloadPages) {
        val current = page ?: return@ActiveRouteLaunchedEffect
        if (tools.continuousChapters && current.chapterUuid == chapters.firstOrNull()?.uuid &&
            current.source != null && current.page <= maxOf(tools.preloadPages, 1)) onPrependPrevious(false)
    }
    DisposableEffect(routeActive, page?.key, pages, loader, tools.preloadPages) {
        val jobs =
            if (preview || !routeActive) emptyList() else pages.asSequence().drop(visibleIndex + 1).filter { it.source != null }
                .take(tools.preloadPages).map { next ->
                    loader.enqueue(
                        ImageRequest.Builder(context)
                            .data(next.source?.let(::readerImageData))
                            .size(512).build()
                    )
                }.toList()
        onDispose { jobs.forEach { it.dispose() } }
    }

    // 每次只运行一条自动阅读协程；触摸、菜单、拖进度条及评论页立即暂停。
    ActiveRouteLaunchedEffect(automatic, isContinuous, pages, tools.autoIntervalSeconds) {
        if (!automatic) return@ActiveRouteLaunchedEffect
        while (automatic) {
            delay(tools.autoIntervalSeconds.seconds)
            if (!currentRouteActive) break
            val index = if (isContinuous) list.firstVisibleItemIndex else pager.currentPage
            val current = pages.getOrNull(index) ?: break
            if (current.source == null) {
                automatic = false; break
            }
            if (isContinuous) {
                val height = list.layoutInfo.viewportEndOffset - list.layoutInfo.viewportStartOffset
                val before = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
                list.animateScrollBy(height * 0.75f, tween(900, easing = LinearEasing))
                if (list.layoutInfo.visibleItemsInfo.any { pages.getOrNull(it.index)?.source == null }) {
                    automatic = false
                    break
                }
                if (before == (list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)) {
                    if (currentAppending) continue
                    automatic = false
                    if (hasNextChapter) nextChapter()
                }
            } else if (index < pages.lastIndex) pager.animateScrollToPage(index + 1)
            else {
                if (currentAppending) continue
                automatic = false
                if (hasNextChapter) nextChapter()
            }
        }
    }

    BackHandler(enabled = showControls && !showSettings && !showImageMenu && !showBookmarks && !showCatalog) {
        showControls = false
    }
    val nested = remember { object : NestedScrollConnection {} }
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .readerKeyboardNavigation(
                enabled = services?.platform is DesktopReaderKeyboard,
                direction = dir,
                blocked = !routeActive || externalModalVisible || showSettings || showImageMenu || showBookmarks || showCatalog || restoring || viewportCorrection.active
            ) { command ->
                showControls = false
                automatic = false
                if (command.movement == ReaderKeyboardMovement.PAGE) {
                    if (command.forward) forward() else backward()
                } else if (keyboardScrollJob?.isActive != true) {
                    if (command.forward && !list.canScrollForward) nextChapter()
                    else if (!command.forward && !list.canScrollBackward) previousChapter()
                    else keyboardScrollJob = scope.launch {
                        val viewport = (list.layoutInfo.viewportEndOffset - list.layoutInfo.viewportStartOffset).coerceAtLeast(0)
                        val distance = if (command.movement == ReaderKeyboardMovement.SMALL_SCROLL) keyboardStep else viewport * 0.9f
                        list.animateScrollBy(if (command.forward) distance else -distance)
                    }
                }
            }
            .onFocusChanged { if (!it.isFocused) pressedVolumeKeys.clear() }
            .onPreviewKeyEvent { event ->
                if (!routeActive || !tools.volumeKeys || (event.key != Key.VolumeUp && event.key != Key.VolumeDown)) false
                else {
                    if (event.type == KeyEventType.KeyUp) pressedVolumeKeys.remove(event.key)
                    if (event.type == KeyEventType.KeyDown && pressedVolumeKeys.add(event.key)) {
                        automatic = false
                        if (event.key == Key.VolumeDown) forward() else backward()
                    }
                    true
                }
            }
            .focusable()
    ) {
        ReaderContent(
            pages,
            isContinuous,
            dir == ReadingDirection.RIGHT_TO_LEFT,
            PageScale.fromName(pageScale).toContentScale(),
            pager,
            list,
            nested,
            ::forward,
            ::backward,
            onShowMenu = { showControls = !showControls },
            onImageLongPress = { selected ->
                automatic = false; selectedSource = selected.source
                selectedTitle =
                    "${selected.chapterName} - 第${selected.page + 1}页"; showImageMenu = true
            },
            onBottomAreaTap = { showControls = !showControls },
            imageLoader = loader,
            imageQuality = services?.platform?.imageQuality,
            tools = tools,
            onUserInteraction = { viewportCorrection.invalidate(); viewportAnchor.invalidate(); automatic = false; pendingChapter = null; restoring = false },
            onImageReady = { if (it in latestRetainedPageKeys) readyImages[it] = true },
            trackedPageKeys = retainedPageKeys,
            commentContent = { comment ->
                ReaderChapterComments(
                    comment,
                    comicPathWord = shareComicPathWord.orEmpty(),
                    onClosed = { onCloseComments(comment.chapterUuid) },
                    onShowControls = { showControls = !showControls },
                    refreshVersion = commentRefreshVersion,
                    modifier = Modifier.fillMaxSize(),
                    viewModel = services!!.chapterComments(comment.chapterUuid),
                    commentsViewModel = services.inlineComments(comment.chapterUuid)
                )
            })
        AnimatedVisibility(
            showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopBar(
                page?.chapterName ?: chapterName,
                page?.let { current -> bookmarks.any { it.chapterUuid == current.chapterUuid && it.page == current.page } }
                    ?: false,
                onBack = onBack, onBookmark = {
                    if (page?.source == null) toast.showToast("返回图片页后可添加书签")
                    else onToggleBookmark(page.chapterUuid, page.page, anchorFraction)
                }, onSettings = { showSettings = true },
                shareTarget = page?.let { current ->
                    shareComicPathWord?.let { comic ->
                        ShareTarget.chapter(comic, current.chapterUuid, current.chapterName)
                    }
                })
        }
        if (!showControls && page?.source != null) Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                page.chapterName,
                modifier = Modifier.weight(1f),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${page.page + 1}/${page.imageCount}",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        }
        AnimatedVisibility(
            showControls && page != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderBottomBar(
                page?.imageCount ?: 0, sliderPreview,
                onSliderValueChange = { automatic = false; dragging = true; sliderPreview = it },
                onSliderValueChangeFinished = {
                    dragging = false
                    val target =
                        pages.indexOfFirst { it.chapterUuid == page?.chapterUuid && it.source != null && it.page == sliderPreview.roundToInt() }
                    if (target >= 0) jump(target)
                },
                onPrevious = { automatic = false; previousChapter() },
                onNext = { automatic = false; nextChapter() },
                onCatalog = { automatic = false; showCatalog = true; onLoadCatalog() },
                onBookmarks = { automatic = false; showBookmarks = true },
                onComments = { automatic = false; page?.let { onComments(it.chapterUuid) } },
                commentsEnabled = commentsAllowed && page?.chapterUuid !in closedChapters,
                commentCount = chapterCommentsState?.page?.total,
                autoRunning = automatic,
                onAuto = {
                    if (page?.source == null) toast.showToast("返回图片页后可开始自动阅读")
                    else {
                        automatic = !automatic; showControls = false
                    }
                })
        }
        if (appending) Text(
            "正在加载下一章…", color = Color.White, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(12.dp)
        )
    }
    if (showSettings) ReaderSettingsDialog(
        dir,
        PageScale.fromName(pageScale),
        ScreenOrientationSetting.fromName(screenOrientation),
        onReadingDirectionChange = { onReadingDirectionChange(it.name) },
        onPageScaleChange = { onPageScaleChange(it.name) },
        onScreenOrientationChange = { onScreenOrientationChange(it.name) },
        onResetPreferences = onResetPreferences,
        onDismiss = { showSettings = false },
        leadingSettings = {
            ReaderSettingSwitch("显示章节评论区（全局）", tools.inlineComments) { enabled ->
                onToolsChange { it.copy(inlineComments = enabled) }
            }
        },
        extraSettings = { ReaderToolsOptions(tools, onToolsChange) })
    if (showBookmarks) ReaderBookmarksDialog(
        bookmarks,
        onSelect = {
            pendingInitialPage.value = it.page; showBookmarks = false; onBookmarkSelected(
            it
        )
        },
        onDismiss = { showBookmarks = false })
    if (showCatalog) ReaderCatalogDialog(
        catalog, page?.chapterUuid.orEmpty(),
        onSelect = { pendingInitialPage.value = 0; showCatalog = false; onSelectCatalog(it) },
        onRetry = onLoadCatalog, onDismiss = { showCatalog = false })
    if (showImageMenu) ReaderImageActionDialog(
        selectedTitle, selectedSource,
        onRefresh = { showImageMenu = false; onRefresh() }, onDismiss = { showImageMenu = false },
        viewModel = services!!.imageActions,
        platform = services.platform
    )
}

// ── RiskControl ────────────────────────────────────────────────────

@Composable
fun ReaderRiskControlContent(reason: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Block, "风控", tint = Color.White, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("触发风控", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            reason, color = Color.LightGray, style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

// ── Previews ──────────────────────────────────────────────────────

@Preview(showBackground = true, name = "阅读器 - 加载中")
@Composable
private fun ReaderLoadingPreview() {
    CopyManga_NeoTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            CircularWavyProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }
    }
}

@Preview(showBackground = true, name = "阅读器 - 加载失败")
@Composable
private fun ReaderErrorPreview() {
    CopyManga_NeoTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Text(
                "加载失败：网络连接超时",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            )
        }
    }
}

@Preview(showBackground = true, name = "阅读器 - 风控提示")
@Composable
private fun ReaderRiskControlPreview() {
    CopyManga_NeoTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            ReaderRiskControlContent("访问过于频繁，请稍后再试", Modifier.align(Alignment.Center))
        }
    }
}

@Preview(showBackground = true, name = "阅读器 - 阅读内容")
@Composable
private fun ReaderSuccessContentPreview() {
    CopyManga_NeoTheme {
        val mock =
            remember { (1..5).map { "https://devtool.tech/api/placeholder/500/700?bgColor=%23653356&fontSize=100" } }
        ReaderSuccessContent(
            images = mock,
            chapterName = "第 128 话",
            readingDirection = "RIGHT_TO_LEFT",
            pageScale = "FIT_SCREEN",
            screenOrientation = "SYSTEM_DEFAULT",
            onPrevChapter = {},
            onNextChapter = {},
            hasPrevChapter = true,
            hasNextChapter = true,
            onRefresh = {},
            onReadingDirectionChange = {},
            onPageScaleChange = {},
            onScreenOrientationChange = {},
            pendingInitialPage = remember { mutableStateOf(null) }
        )
    }
}
