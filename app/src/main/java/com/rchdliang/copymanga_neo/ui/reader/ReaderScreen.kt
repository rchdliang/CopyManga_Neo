package com.rchdliang.copymanga_neo.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.ui.components.LocalToastHost
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds


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

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)) {
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
                pendingInitialPage = pendingInitialPage
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
    onResetPreferences: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val locale = LocalLocale.current.platformLocale
    val toastHost = LocalToastHost.current
    var previousPageInOriginal by rememberSaveable { mutableIntStateOf(0) }

    // 章节切换时显示带撤回按钮的 Snackbar
    val onPrevChapterWithSnackbar: () -> Unit =
        remember(onPrevChapter, onNextChapter, hasPrevChapter) {
            {
                if (hasPrevChapter) {
                    val savedPage = previousPageInOriginal
                    pendingInitialPage.value = 0
                    onPrevChapter()
                    toastHost.showSnackbar("已切换到上一章", "撤回") {
                        pendingInitialPage.value = savedPage
                        onNextChapter()
                    }
                } else {
                    toastHost.showToast("已是第一章")
                }
            }
        }
    val onNextChapterWithSnackbar: () -> Unit =
        remember(onNextChapter, onPrevChapter, hasNextChapter) {
            {
                if (hasNextChapter) {
                    val savedPage = previousPageInOriginal
                    pendingInitialPage.value = 0
                    onNextChapter()
                    toastHost.showSnackbar("已切换到下一章", "撤回") {
                        pendingInitialPage.value = savedPage
                        onPrevChapter()
                    }
                } else {
                    toastHost.showToast("已是最后一章")
                }
            }
        }

    val dir = remember(readingDirection) {
        ReadingDirection.entries.find { it.name == readingDirection }
            ?: ReadingDirection.RIGHT_TO_LEFT
    }
    val scale = remember(pageScale) {
        PageScale.entries.find { it.name == pageScale } ?: PageScale.FIT_SCREEN
    }
    val orient = remember(screenOrientation) {
        ScreenOrientationSetting.fromName(screenOrientation)
    }

    var prevDir by remember { mutableStateOf(dir) }
    var dirStable by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showImageMenu by remember { mutableStateOf(false) }
    // 底栏（进度条）显示状态与拖动状态
    var showBottomBar by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    var sliderDragging by remember { mutableStateOf(false) }

    if (dir != prevDir) {
        dirStable = false
        prevDir = dir
    }

    val isContinuous = dir == ReadingDirection.TOP_TO_BOTTOM
    val contentScale = scale.toContentScale()

    val initialPage = previousPageInOriginal.coerceIn(0, (images.size - 1).coerceAtLeast(0))

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { images.size.coerceAtLeast(1) }
    )
    val listState = rememberLazyListState()
    val continuousVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    val currentPageIndex = if (isContinuous) continuousVisibleIndex else pagerState.currentPage
    if (dirStable) {
        previousPageInOriginal = currentPageIndex
    }

    // 章节切换或方向切换时重置滚动位置
    LaunchedEffect(dir, images) {
        if (images.isNotEmpty()) {
            val target = pendingInitialPage.value?.coerceIn(0, images.size - 1)
                ?: previousPageInOriginal.coerceIn(0, images.size - 1)
            if (isContinuous) listState.scrollToItem(target) else pagerState.scrollToPage(target)
            dirStable = true
            pendingInitialPage.value = null
        }
    }

    // 非拖动状态下，进度条跟随当前页码同步
    LaunchedEffect(currentPageIndex) {
        if (!sliderDragging) {
            sliderValue = currentPageIndex.toFloat()
        }
    }

    // 底栏显示后 5s 自动收回；拖动进度条会重置计时
    LaunchedEffect(showBottomBar, sliderValue) {
        if (showBottomBar) {
            delay(5000.milliseconds)
            showBottomBar = false
        }
    }

    // 对话框打开时拦截返回键，避免直接退出阅读器
    BackHandler(enabled = showSettingsMenu || showImageMenu) {
        showSettingsMenu = false
        showImageMenu = false
    }

    val displayedPageNumber = currentPageIndex + 1

    // 翻页 — reverseLayout 下 LTR/RTL 方向统一
    fun animateToPagerPage(target: Int) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(
                target.coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0))
            )
        }
    }

    fun animateToListIndex(target: Int) {
        coroutineScope.launch {
            listState.animateScrollToItem(target.coerceIn(0, (images.size - 1).coerceAtLeast(0)))
        }
    }

    // 进度条拖动时即时跳页（无动画，跟随拖动实时跳转）
    fun scrollToPageInstant(target: Int) {
        coroutineScope.launch {
            val safe = target.coerceIn(0, (images.size - 1).coerceAtLeast(0))
            if (isContinuous) listState.scrollToItem(safe) else pagerState.scrollToPage(safe)
        }
    }

    val goForward: () -> Unit = {
        when {
            isContinuous -> {
                if (currentPageIndex >= images.size - 1 ||
                    (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                        ?: -1) >= images.size - 1
                ) {
                    onNextChapterWithSnackbar()
                } else {
                    animateToListIndex(currentPageIndex + 1)
                }
            }

            else -> {
                if (pagerState.currentPage >= pagerState.pageCount - 1) {
                    onNextChapterWithSnackbar()
                } else {
                    animateToPagerPage(pagerState.currentPage + 1)
                }
            }
        }
    }

    val goBackward: () -> Unit = {
        when {
            isContinuous -> {
                if (currentPageIndex <= 0) {
                    onPrevChapterWithSnackbar()
                } else {
                    animateToListIndex(currentPageIndex - 1)
                }
            }

            else -> {
                if (pagerState.currentPage <= 0) {
                    onPrevChapterWithSnackbar()
                } else {
                    animateToPagerPage(pagerState.currentPage - 1)
                }
            }
        }
    }

    // 连续模式跨章节滑动
    val density = LocalDensity.current
    val threshold = remember(density) { with(density) { 60.dp.toPx() } }
    val nestedScrollConnection = rememberContinuousOverscroll(
        coroutineScope = coroutineScope,
        threshold = threshold,
        onPrevChapter = onPrevChapterWithSnackbar,
        onNextChapter = onNextChapterWithSnackbar
    )

    val timeText = java.text.SimpleDateFormat("HH:mm", locale).format(java.util.Date())

    // 全屏布局
    Box(modifier = modifier
        .fillMaxSize()
        .background(Color.Black)) {
        ReaderContent(
            effectiveImages = images,
            isContinuous = isContinuous,
            isRtl = dir == ReadingDirection.RIGHT_TO_LEFT,
            contentScale = contentScale,
            pagerState = pagerState,
            listState = listState,
            continuousNestedScrollConnection = nestedScrollConnection,
            onForward = goForward,
            onBackward = goBackward,
            onPrevChapter = onPrevChapterWithSnackbar,
            onNextChapter = onNextChapterWithSnackbar,
            onShowMenu = { showSettingsMenu = true },
            onLongPress = { showImageMenu = true },
            onBottomAreaTap = { showBottomBar = !showBottomBar }
        )

        ReaderTopBar(
            timeText = timeText,
            chapterName = chapterName,
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopCenter)
        )

        if (images.isNotEmpty()) {
            ReaderPageIndicator(
                pageText = "$displayedPageNumber/${images.size}",
                onClick = { showSettingsMenu = true },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // 底栏（进度条）——覆盖在图片上方，不挤压图片显示区域；带滑入/滑出动画
        AnimatedVisibility(
            visible = showBottomBar && images.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderBottomBar(
                currentPage = currentPageIndex,
                totalPages = images.size,
                sliderValue = sliderValue,
                onSliderValueChange = { value ->
                    sliderValue = value
                    sliderDragging = true
                    scrollToPageInstant(value.toInt())
                },
                onSliderValueChangeFinished = {
                    sliderDragging = false
                    // 拖动结束后吸附到实际页码位置
                    sliderValue = sliderValue.toInt().toFloat()
                }
            )
        }
    }

    if (showSettingsMenu) {
        ReaderSettingsDialog(
            readingDirection = dir,
            pageScale = scale,
            screenOrientation = orient,
            onReadingDirectionChange = { onReadingDirectionChange(it.name) },
            onPageScaleChange = { onPageScaleChange(it.name) },
            onScreenOrientationChange = { onScreenOrientationChange(it.name) },
            onResetPreferences = onResetPreferences,
            onDismiss = { showSettingsMenu = false }
        )
    }

    if (showImageMenu) {
        ReaderImageActionDialog(
            title = "$chapterName - 第${displayedPageNumber}页",
            currentImageUrl = images.getOrNull(currentPageIndex),
            onRefresh = { showImageMenu = false; onRefresh() },
            onDismiss = { showImageMenu = false }
        )
    }
}

// ── 连续模式跨章节滑动 NestedScrollConnection ──────────────────────

@Composable
private fun rememberContinuousOverscroll(
    coroutineScope: CoroutineScope,
    threshold: Float,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit
): NestedScrollConnection {
    val continuousOverscroll = remember { mutableFloatStateOf(0f) }
    val latestOnPrevChapter by rememberUpdatedState(onPrevChapter)
    val latestOnNextChapter by rememberUpdatedState(onNextChapter)

    return remember(coroutineScope, threshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                continuousOverscroll.floatValue += available.y
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val v = continuousOverscroll.floatValue
                if (v > threshold) {
                    coroutineScope.launch { latestOnPrevChapter() }
                } else if (v < -threshold) {
                    coroutineScope.launch { latestOnNextChapter() }
                }
                continuousOverscroll.floatValue = 0f
                return Velocity.Zero
            }
        }
    }
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
        Box(Modifier
            .fillMaxSize()
            .background(Color.Black)) {
            CircularWavyProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "阅读器 - 加载失败")
@Composable
private fun ReaderErrorPreview() {
    CopyManga_NeoTheme {
        Box(Modifier
            .fillMaxSize()
            .background(Color.Black)) {
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
        Box(Modifier
            .fillMaxSize()
            .background(Color.Black)) {
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
