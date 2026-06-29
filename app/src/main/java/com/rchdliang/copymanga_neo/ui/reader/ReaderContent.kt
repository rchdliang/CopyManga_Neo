package com.rchdliang.copymanga_neo.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ReaderContent(
    effectiveImages: List<String>,
    isContinuous: Boolean,
    isRtl: Boolean,
    contentScale: ContentScale,
    pagerState: PagerState,
    listState: LazyListState,
    continuousNestedScrollConnection: NestedScrollConnection,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onShowMenu: () -> Unit,
    onLongPress: () -> Unit,
    onBottomAreaTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier
        .fillMaxSize()
        .background(Color.Black)) {
        if (effectiveImages.isEmpty()) {
            Text("暂无图片", color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else if (isContinuous) {
            // 连续模式：缩放整个 LazyColumn，而非单个图片
            val continuousZoomState = remember { ReaderZoomState() }
            var continuousContainerSize by remember { mutableStateOf(IntSize.Zero) }
            val continuousContainerSizeProvider = remember { { continuousContainerSize } }

            // 放大时由 NestedScrollConnection 处理单指拖动：
            // 图片能移动时消费滚动量（阻止 LazyColumn 滚动），到边界后不消费（让 LazyColumn 自动滚动）
            val continuousZoomConnection = remember(continuousZoomState) {
                object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        if (continuousZoomState.scale <= 1f) return Offset.Zero
                        if (source != NestedScrollSource.UserInput) return Offset.Zero

                        val scale = continuousZoomState.scale
                        val containerSize = continuousContainerSize
                        val maxX = containerSize.width * (scale - 1f) / 2f
                        val maxY = containerSize.height * (scale - 1f) / 2f

                        // available 是 LazyColumn 坐标系（被 graphicsLayer 缩放过），
                        // 乘以 scale 转换为屏幕坐标系使图片跟手
                        val newOffsetX =
                            (continuousZoomState.offset.x + available.x * scale).coerceIn(
                                -maxX,
                                maxX
                            )
                        val newOffsetY =
                            (continuousZoomState.offset.y + available.y * scale).coerceIn(
                                -maxY,
                                maxY
                            )

                        val offsetChangeX = newOffsetX - continuousZoomState.offset.x
                        val offsetChangeY = newOffsetY - continuousZoomState.offset.y

                        continuousZoomState.offset = Offset(newOffsetX, newOffsetY)

                        // 消费量回到 LazyColumn 坐标系
                        return Offset(offsetChangeX / scale, offsetChangeY / scale)
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { continuousContainerSize = it }
                    .graphicsLayer {
                        scaleX = continuousZoomState.scale
                        scaleY = continuousZoomState.scale
                        translationX = continuousZoomState.offset.x
                        translationY = continuousZoomState.offset.y
                    }
                    .readerImageZoomable(
                        state = continuousZoomState,
                        containerSizeProvider = continuousContainerSizeProvider,
                        isContinuous = true,
                        isRtl = false,
                        minScale = 1f,
                        maxScale = 5f
                    )
                    .nestedScroll(continuousZoomConnection)
                    .nestedScroll(continuousNestedScrollConnection)
                    .readerTapGestures(
                        isRtl = false,
                        isContinuous = true,
                        onForward = onForward,
                        onBackward = onBackward,
                        onShowMenu = onShowMenu,
                        onLongPress = onLongPress,
                        onBottomAreaTap = onBottomAreaTap
                    )
            ) {
                itemsIndexed(
                    items = effectiveImages,
                    key = { _, path -> path }
                ) { index, path ->
                    ReaderImageItem(
                        pageNumber = index,
                        path = path,
                        contentScale = contentScale,
                        isContinuous = true,
                        isActivePage = index == listState.firstVisibleItemIndex,
                        enableZoom = false,
                        isRtl = false,
                        onTurnPage = null
                    )
                }
            }
        } else {
            val density = LocalDensity.current
            val thresholdPx = remember(density) { with(density) { 60.dp.toPx() } }
            val coroutineScope = rememberCoroutineScope()

            val pagerOverscrollConnection = remember(
                coroutineScope, thresholdPx, isRtl, onPrevChapter, onNextChapter
            ) {
                PagerChapterOverscroll(
                    coroutineScope = coroutineScope,
                    thresholdPx = thresholdPx,
                    isRtl = isRtl,
                    onPrevChapter = onPrevChapter,
                    onNextChapter = onNextChapter
                )
            }

            HorizontalPager(
                state = pagerState,
                reverseLayout = isRtl,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(pagerOverscrollConnection)
                    .readerTapGestures(
                        isRtl = isRtl,
                        isContinuous = false,
                        onForward = onForward,
                        onBackward = onBackward,
                        onShowMenu = onShowMenu,
                        onLongPress = onLongPress,
                        onBottomAreaTap = onBottomAreaTap
                    ),
                beyondViewportPageCount = 1
            ) { page ->
                ReaderImageItem(
                    pageNumber = page,
                    path = effectiveImages[page],
                    contentScale = contentScale,
                    isContinuous = false,
                    isActivePage = page == pagerState.currentPage,
                    enableZoom = true,
                    isRtl = isRtl,
                    onTurnPage = { isForward -> if (isForward) onForward() else onBackward() }
                )
            }
        }
    }
}

/**
 * 阅读器图片项 — 连续模式与分页模式共用的图片渲染组件
 * 提取自原 LazyColumn 和 HorizontalPager 中重复的 SubcomposeAsyncImage 逻辑
 */
@Composable
private fun ReaderImageItem(
    pageNumber: Int,
    path: String,
    contentScale: ContentScale,
    isContinuous: Boolean,
    isActivePage: Boolean,
    enableZoom: Boolean,
    isRtl: Boolean = false,
    onTurnPage: ((isForward: Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    var retryKey by rememberSaveable(path) { mutableIntStateOf(0) }
    val request = remember(path, retryKey) {
        val data: Any = if (path.startsWith("http")) path else File(path)
        ImageRequest.Builder(context)
            .data(data)
            .setParameter("retry_hash", retryKey)
            .build()
    }

    val zoomState = remember(path) { ReaderZoomState() }

    // 离开当前页时重置缩放，回到默认显示
    LaunchedEffect(isActivePage) {
        if (!isActivePage) {
            zoomState.reset()
        }
    }

    val imageModifier = if (isContinuous) Modifier.fillMaxWidth() else Modifier.fillMaxSize()
    val placeholderModifier = if (isContinuous) {
        Modifier
            .fillMaxWidth()
            .height(300.dp)
    } else {
        Modifier.fillMaxSize()
    }

    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        modifier = imageModifier,
        contentScale = contentScale
    ) {
        when (painter.state) {
            is AsyncImagePainter.State.Loading -> {
                Box(
                    modifier = placeholderModifier,
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = (pageNumber + 1).toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        CircularWavyProgressIndicator(color = Color.White)
                    }
                }
            }

            is AsyncImagePainter.State.Error -> {
                val error = painter.state as AsyncImagePainter.State.Error
                val message = error.result.throwable.message
                    ?: error.result.throwable.javaClass.simpleName
                    ?: "未知错误"
                Box(
                    modifier = placeholderModifier.clickable { retryKey++ },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "加载失败:$message",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            is AsyncImagePainter.State.Success -> {
                val currentPainter = painter
                if (enableZoom) {
                    // 分页模式：单个图片缩放
                    var containerSize by remember { mutableStateOf(IntSize.Zero) }
                    val containerSizeProvider = remember { { containerSize } }
                    val density = LocalDensity.current
                    val turnPageThresholdPx = remember(density) {
                        with(density) { 60.dp.toPx() }
                    }
                    val currentOnTurnPage = rememberUpdatedState(onTurnPage)
                    val stableOnTurnPage = remember {
                        { isForward: Boolean -> currentOnTurnPage.value?.invoke(isForward) }
                    }

                    Box(
                        modifier = Modifier
                            .onSizeChanged { containerSize = it }
                            .then(
                                Modifier.readerImageZoomable(
                                    state = zoomState,
                                    containerSizeProvider = containerSizeProvider,
                                    isContinuous = isContinuous,
                                    isRtl = isRtl,
                                    minScale = 1f,
                                    maxScale = 5f,
                                    onTurnPage = stableOnTurnPage as ((isForward: Boolean) -> Unit)?,
                                    turnPageThresholdPx = turnPageThresholdPx
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = currentPainter,
                            contentDescription = null,
                            modifier = imageModifier
                                .graphicsLayer {
                                    scaleX = zoomState.scale
                                    scaleY = zoomState.scale
                                    translationX = zoomState.offset.x
                                    translationY = zoomState.offset.y
                                },
                            contentScale = contentScale
                        )
                    }
                } else {
                    // 连续模式：缩放在 LazyColumn 层面，单个图片直接显示
                    Image(
                        painter = currentPainter,
                        contentDescription = null,
                        modifier = imageModifier,
                        contentScale = contentScale
                    )
                }
            }

            else -> Unit
        }
    }
}

private class PagerChapterOverscroll(
    private val coroutineScope: CoroutineScope,
    private val thresholdPx: Float,
    private val isRtl: Boolean,
    private val onPrevChapter: () -> Unit,
    private val onNextChapter: () -> Unit
) : NestedScrollConnection {
    private var totalOverscroll = 0f

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        return Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        // RTL (reverseLayout) 模式下 pager 内部坐标被翻转，需要对 available.x 取反
        val effectiveDelta = if (isRtl) -available.x else available.x
        totalOverscroll += effectiveDelta
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val overscroll = totalOverscroll
        totalOverscroll = 0f
        if (overscroll > thresholdPx) {
            coroutineScope.launch { onPrevChapter() }
        } else if (overscroll < -thresholdPx) {
            coroutineScope.launch { onNextChapter() }
        }
        return Velocity.Zero
    }
}

@Composable
fun ReaderTopBar(
    timeText: String,
    chapterName: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = timeText,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = chapterName,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ReaderPageIndicator(
    pageText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = pageText,
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
            .statusBarsPadding()
            .displayCutoutPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    )
}

/**
 * 阅读器底栏 — 覆盖在图片显示区域上方（不挤压图片区域）
 * 内容：【当前页码】+【可拖动进度条】+【全部页数】
 * 进度条拖动可实时跳转阅读位置
 */
@Composable
fun ReaderBottomBar(
    currentPage: Int,
    totalPages: Int,
    sliderValue: Float,
    onSliderValueChange: (Float) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.7f))
            .navigationBarsPadding()
            .displayCutoutPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = (currentPage + 1).toString(),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.width(12.dp))
        Slider(
            value = sliderValue,
            valueRange = 0f..maxOf((totalPages - 1).toFloat(), 1f),
            onValueChange = onSliderValueChange,
            onValueChangeFinished = onSliderValueChangeFinished,
            enabled = totalPages > 1,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = totalPages.toString(),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
