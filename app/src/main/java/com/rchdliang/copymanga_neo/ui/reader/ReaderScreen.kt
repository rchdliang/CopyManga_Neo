package com.rchdliang.copymanga_neo.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import coil.request.ImageRequest
import com.rchdliang.copymanga_neo.di.ReaderImageLoaderEntryPoint
import com.rchdliang.copymanga_neo.domain.model.CommentKind
import com.rchdliang.copymanga_neo.domain.model.CommentTarget
import com.rchdliang.copymanga_neo.domain.model.MangaComment
import com.rchdliang.copymanga_neo.domain.model.ReaderBookmark
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import com.rchdliang.copymanga_neo.domain.model.ShareTarget
import com.rchdliang.copymanga_neo.ui.comments.CommentsDialog
import com.rchdliang.copymanga_neo.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
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
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentChapter = uiState as? ReaderUiState.Success
    val tools by viewModel.toolsSettings.collectAsStateWithLifecycle()
    val readerBookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val commentsAllowed by viewModel.commentsAllowed.collectAsStateWithLifecycle()
    val closedChapters by viewModel.closedChapters.collectAsStateWithLifecycle()
    val appending by viewModel.appending.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    var commentChapter by remember(comicPathWord) { mutableStateOf<String?>(null) }
    var commentReport by remember(comicPathWord) { mutableStateOf<MangaComment?>(null) }
    var commentRefreshVersion by remember(comicPathWord) { mutableIntStateOf(0) }
    if (commentChapter != null && currentChapter != null && commentsAllowed && commentChapter !in closedChapters) {
        CommentsDialog(
            target = CommentTarget(CommentKind.CHAPTER, commentChapter.orEmpty()),
            title = currentChapter.chapters.firstOrNull { it.uuid == commentChapter }?.content?.chapterName
                ?: chapterName,
            comicPathWord = comicPathWord,
            onDismiss = { commentChapter = null; commentReport = null; commentRefreshVersion++ },
            initialReport = commentReport
        )
    }
    val preferences by viewModel.readerPreferences.collectAsStateWithLifecycle()
    val readingDirectionPref = preferences?.readingDirection ?: "RIGHT_TO_LEFT"
    val pageScalePref = preferences?.pageScale ?: "FIT_SCREEN"
    val screenOrientationPref = preferences?.screenOrientation ?: "SYSTEM_DEFAULT"
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val toastHost = LocalToastHost.current
    val context = LocalContext.current
    val activity = context.findActivity()
    val view = LocalView.current

    val screenOrient = remember(screenOrientationPref) {
        ScreenOrientationSetting.fromName(screenOrientationPref)
    }
    LaunchedEffect(activity, screenOrient, preferences != null) {
        if (preferences != null) activity?.requestedOrientation = screenOrient.activityOrientation
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.let {
                if (!it.isChangingConfigurations) {
                    it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val window = activity?.window ?: return@DisposableEffect onDispose { }
        val insetsController = WindowCompat.getInsetsController(window, view)
        insetsController.hide(WindowInsetsCompat.Type.statusBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { insetsController.show(WindowInsetsCompat.Type.statusBars()) }
    }

    LaunchedEffect(comicPathWord, groupPathWord) {
        viewModel.openReader(comicPathWord, chapterPathWord, groupPathWord)
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            toastHost.showToast(it)
            viewModel.clearToastMessage()
        }
    }

    // 章节切换时的目标页码：null=保持当前页（方向切换/刷新），非null=跳转到指定页（切章/撤回）
    val pendingInitialPage = remember { mutableStateOf<Int?>(null) }

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
                images = state.chapters.flatMap { it.content.imageUrls },
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
                onComments = { commentReport = null; commentChapter = it },
                onReport = { uuid, comment -> commentChapter = uuid; commentReport = comment },
                pendingInitialPage = pendingInitialPage,
                sessionChapters = if (tools.continuousChapters) state.chapters else state.chapters.filter { it.uuid == state.chapterUuid },
                tools = tools, onToolsChange = viewModel::updateTools,
                commentsAllowed = commentsAllowed, closedChapters = closedChapters,
                commentRefreshVersion = commentRefreshVersion,
                onCloseComments = viewModel::closeChapterComments,
                onActivateChapter = viewModel::activateChapter,
                onAppendNext = { viewModel.appendNextChapter(it) }, appending = appending,
                bookmarks = readerBookmarks.filter { it.comicPathWord == comicPathWord },
                onToggleBookmark = viewModel::toggleBookmark,
                onBookmarkSelected = viewModel::jumpToBookmark,
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
    onReport: (String, MangaComment) -> Unit = { _, _ -> },
    sessionChapters: List<ReaderSessionChapter> = emptyList(),
    tools: ReaderToolsSettings = ReaderToolsSettings(),
    onToolsChange: ((ReaderToolsSettings) -> ReaderToolsSettings) -> Unit = {},
    commentsAllowed: Boolean = false,
    commentRefreshVersion: Int = 0,
    closedChapters: Set<String> = emptySet(),
    onCloseComments: (String) -> Unit = {},
    onActivateChapter: (String) -> Unit = {},
    onAppendNext: (Boolean) -> Unit = {},
    appending: Boolean = false,
    bookmarks: List<ReaderBookmark> = emptyList(),
    onToggleBookmark: (String, Int) -> Unit = { _, _ -> },
    onBookmarkSelected: (ReaderBookmark) -> Unit = {},
    onBack: () -> Unit = {},
    catalog: ReaderCatalogUiState = ReaderCatalogUiState(),
    onLoadCatalog: () -> Unit = {},
    onSelectCatalog: (String) -> Unit = {},
    shareComicPathWord: String? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    val preview = LocalInspectionMode.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
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
    val initial = (pendingInitialPage.value ?: 0).coerceIn(0, pages.lastIndex.coerceAtLeast(0))
    val pager =
        rememberPagerState(initialPage = initial, pageCount = { pages.size.coerceAtLeast(1) })
    val list = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val visibleIndex by remember(
        isContinuous,
        list,
        pager
    ) { derivedStateOf { if (isContinuous) list.firstVisibleItemIndex else pager.currentPage } }
    val page = pages.getOrNull(visibleIndex)
    // 底栏和章末评论共用同一章节的加载状态，角标取 API 总数，避免重复请求。
    val chapterCommentsViewModel = if (!preview && page != null) {
        hiltViewModel<ReaderChapterCommentsViewModel>(key = "reader-comments:${page.chapterUuid}")
    } else null
    val chapterCommentsState =
        chapterCommentsViewModel?.uiState?.collectAsStateWithLifecycle()?.value
    var anchor by rememberSaveable { mutableStateOf<String?>(null) }
    var anchorChapter by rememberSaveable { mutableStateOf<String?>(null) }
    var anchorPage by rememberSaveable { mutableIntStateOf(0) }
    var anchorScrollOffset by rememberSaveable { mutableIntStateOf(0) }
    var previousContinuous by remember { mutableStateOf(isContinuous) }
    var showControls by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showCatalog by remember { mutableStateOf(false) }
    var showImageMenu by rememberSaveable { mutableStateOf(false) }
    var selectedSource by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTitle by rememberSaveable { mutableStateOf("") }
    var automatic by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var sliderPreview by remember { mutableFloatStateOf(0f) }
    var pendingChapter by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(showControls, page?.chapterUuid, commentsAllowed, closedChapters) {
        val current = page ?: return@LaunchedEffect
        if (showControls && commentsAllowed && current.chapterUuid !in closedChapters) {
            chapterCommentsViewModel?.load(current.chapterUuid)
        }
    }
    LaunchedEffect(page?.chapterUuid, chapterCommentsState?.closed) {
        if (chapterCommentsState?.closed == true) page?.let { onCloseComments(it.chapterUuid) }
    }
    LaunchedEffect(commentRefreshVersion) {
        val current = page ?: return@LaunchedEffect
        if (commentRefreshVersion > 0 && commentsAllowed && current.chapterUuid !in closedChapters) {
            chapterCommentsViewModel?.load(current.chapterUuid, refresh = true)
        }
    }
    val currentAppending by rememberUpdatedState(appending)
    val loader = remember(context, tools.imageTimeoutSeconds, preview) {
        if (preview) context.imageLoader else EntryPointAccessors.fromApplication(
            context.applicationContext, ReaderImageLoaderEntryPoint::class.java
        ).readerImageLoaders().get(tools.imageTimeoutSeconds)
    }

    // 追加章节、移除评论页或改变阅读方向时保留当前页的稳定标识。
    LaunchedEffect(pages, isContinuous) {
        if (pages.isEmpty()) return@LaunchedEffect
        val target = resolveReaderPageIndex(pages, anchor, anchorChapter, anchorPage, initial)
        val offset =
            if (isContinuous && previousContinuous) anchorScrollOffset else 0
        previousContinuous = isContinuous
        if (isContinuous) list.scrollToItem(
            target.coerceIn(pages.indices),
            offset
        ) else pager.scrollToPage(
            target.coerceIn(pages.indices)
        )
        pendingInitialPage.value = null
        // 先恢复位置再观察可见页，避免移除评论页时把旧索引误认作下一章。
        snapshotFlow {
            visibleIndex to if (isContinuous) list.firstVisibleItemScrollOffset else 0
        }.collect { (index, scrollOffset) ->
            pages.getOrNull(index)?.let {
                anchor = it.key
                anchorChapter = it.chapterUuid
                anchorPage = it.page
                anchorScrollOffset = scrollOffset
                onActivateChapter(it.chapterUuid)
                if (!dragging) sliderPreview = it.page.toFloat()
                if (it.source == null) automatic = false
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
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) automatic =
                false
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(automatic) {
        val previous = view.keepScreenOn
        if (automatic) view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    LaunchedEffect(showSettings, showImageMenu, showBookmarks, showCatalog) {
        if (showSettings || showImageMenu || showBookmarks || showCatalog) automatic = false
        else focus.requestFocus()
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
    LaunchedEffect(
        page?.key,
        chapters.lastOrNull()?.uuid,
        chapters.lastOrNull()?.content?.nextUuid,
        tools.continuousChapters,
        tools.preloadPages
    ) {
        val current = page ?: return@LaunchedEffect
        if (tools.continuousChapters && current.chapterUuid == chapters.lastOrNull()?.uuid &&
            current.page >= current.imageCount - maxOf(tools.preloadPages, 1)
        ) onAppendNext(false)
    }
    DisposableEffect(page?.key, pages, loader, tools.preloadPages) {
        val jobs =
            if (preview) emptyList() else pages.drop(visibleIndex + 1).filter { it.source != null }
                .take(tools.preloadPages).map { next ->
                    loader.enqueue(
                        ImageRequest.Builder(context)
                            .data(next.source?.let {
                                if (it.startsWith("http")) it else java.io.File(
                                    it
                                )
                            })
                            .size(512).build()
                    )
                }
        onDispose { jobs.forEach { it.dispose() } }
    }

    // 每次只运行一条自动阅读协程；触摸、菜单、拖进度条及评论页立即暂停。
    LaunchedEffect(automatic, isContinuous, pages, tools.autoIntervalSeconds) {
        if (!automatic) return@LaunchedEffect
        while (automatic) {
            delay(tools.autoIntervalSeconds.seconds)
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
            .onPreviewKeyEvent { event ->
                if (!tools.volumeKeys || (event.key != Key.VolumeUp && event.key != Key.VolumeDown)) false
                else {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
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
            tools = tools,
            onUserInteraction = { automatic = false; pendingChapter = null },
            commentContent = { comment ->
                ReaderChapterComments(
                    comment,
                    onOpen = { automatic = false; onComments(comment.chapterUuid) },
                    onClosed = { onCloseComments(comment.chapterUuid) },
                    onShowControls = { showControls = !showControls },
                    onReport = { automatic = false; onReport(comment.chapterUuid, it) },
                    modifier = Modifier.fillMaxSize()
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
                    else onToggleBookmark(page.chapterUuid, page.page)
                }, onSettings = { showSettings = true },
                shareTarget = page?.let { current ->
                    shareComicPathWord?.let { comic ->
                        ShareTarget.chapter(comic, current.chapterUuid, current.chapterName)
                    }
                })
        }
        if (!showControls && page != null) Text(
            if (page.source == null) "章节评论" else "${page.page + 1}/${page.imageCount}",
            color = Color.White, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(12.dp)
        )
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
        onRefresh = { showImageMenu = false; onRefresh() }, onDismiss = { showImageMenu = false })
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview(showBackground = true, showSystemUi = true, name = "阅读器 - 加载中")
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

@Preview(showBackground = true, showSystemUi = true, name = "阅读器 - 加载失败")
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

@Preview(showBackground = true, showSystemUi = true, name = "阅读器 - 风控提示")
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

@Preview(showBackground = true, showSystemUi = true, name = "阅读器 - 阅读内容")
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
