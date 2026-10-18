package com.rchdliang.copymanga_neo.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rchdliang.copymanga_neo.R
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import com.rchdliang.copymanga_neo.ui.preview.PreviewImage
import kotlinx.coroutines.delay
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/** 图片页与章末评论页共享稳定索引，评论页独立处理触摸和滚动。 */
@Composable
fun ReaderContent(
    pages: List<ReaderPageEntry>,
    isContinuous: Boolean,
    isRtl: Boolean,
    contentScale: ContentScale,
    pagerState: PagerState,
    listState: LazyListState,
    continuousNestedScrollConnection: NestedScrollConnection,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onShowMenu: () -> Unit,
    onImageLongPress: (ReaderPageEntry) -> Unit,
    onBottomAreaTap: () -> Unit,
    imageLoader: ImageLoader,
    tools: ReaderToolsSettings,
    onUserInteraction: () -> Unit,
    commentContent: @Composable (ReaderPageEntry) -> Unit,
    modifier: Modifier = Modifier,
    onImageReady: (String) -> Unit = {},
    trackedPageKeys: Set<String> = pages.map { it.key }.toSet()
) {
    val zoomScope = rememberCoroutineScope()
    val currentInteraction by rememberUpdatedState(onUserInteraction)
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    currentInteraction()
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                    } while (event.changes.any { it.pressed })
                }
            }) {
        if (pages.isEmpty()) {
            Text("暂无图片", color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else if (isContinuous) {
            val zoom = remember { ReaderZoomState() }
            var height by remember { mutableIntStateOf(0) }
            val visibleIndex by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex } }
            DisposableEffect(zoom) { onDispose { zoom.stopFling() } }
            LaunchedEffect(pages.getOrNull(visibleIndex)?.key) {
                if (pages.getOrNull(visibleIndex)?.source == null) zoom.reset()
            }
            fun touchedPage(y: Float): ReaderPageEntry? {
                val center = height / 2f
                val sceneY = (y - center - zoom.offset.y) / zoom.scale + center
                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                    sceneY >= it.offset && sceneY < it.offset + it.size
                }
                return item?.let { pages.getOrNull(it.index) }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .onSizeChanged {
                        height = it.height; zoom.updateViewport(it)
                    }
                    .readerImageZoomable(
                        zoom, zoomScope, true, 3f,
                        isContentScrolling = { listState.isScrollInProgress },
                        enabledAt = { touchedPage(it.y)?.source != null })
                    .readerTapGestures(
                        isRtl = false,
                        isContinuous = true,
                        onForward = onForward,
                        onBackward = onBackward,
                        onShowMenu = onShowMenu,
                        onLongPress = { position ->
                            touchedPage(position.y)?.takeIf { it.source != null }
                                ?.let(onImageLongPress)
                        },
                        onBottomAreaTap = onBottomAreaTap,
                        onDoubleTap = {
                            if (touchedPage(it.y)?.source != null) zoom.toggleZoom(
                                it,
                                3f
                            )
                        },
                        allowTap = zoom::allowTap
                    )
            ) {
                LazyColumn(
                    state = listState, modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom.scale; scaleY = zoom.scale
                            translationX = zoom.offset.x; translationY = zoom.offset.y
                        }
                        .nestedScroll(continuousNestedScrollConnection)
                ) {
                    itemsIndexed(pages, key = { _, page -> page.key }) { index, page ->
                        if (page.source == null) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillParentMaxHeight()
                            ) { commentContent(page) }
                        } else ReaderImageItem(
                            page.page, page.source, contentScale, true,
                            index == visibleIndex, false, zoomState = zoom,
                            imageLoader = imageLoader,
                            tools = tools,
                            onReady = { onImageReady(page.key) },
                            trackReadiness = page.key in trackedPageKeys
                        )
                    }
                }
            }
        } else {
            val zoomStates = remember { mutableMapOf<String, ReaderZoomState>() }
            val activeKey by remember(pagerState) {
                derivedStateOf { pagerState.layoutInfo.visiblePagesInfo.firstOrNull { it.index == pagerState.currentPage }?.key }
            }
            val visibleKeys by remember(pagerState) {
                derivedStateOf { pagerState.layoutInfo.visiblePagesInfo.mapNotNull { it.key as? String }.toSet() }
            }
            val active = ReaderWindowPolicy.pageAt(pages, pagerState.currentPage, activeKey)
                ?: pages.first()
            val zoom = zoomStates.getOrPut(active.key) { ReaderZoomState() }
            val retainedKeys = ReaderWindowPolicy.pageKeys(pages, pages.indexOf(active), visibleKeys)
            androidx.compose.runtime.SideEffect {
                zoomStates.keys.toList().filter { it !in retainedKeys }.forEach { zoomStates.remove(it)?.stopFling() }
            }
            DisposableEffect(zoomStates) { onDispose { zoomStates.values.forEach { it.stopFling() } } }
            val gestureModifier =
                if (active.source == null) Modifier else Modifier.readerTapGestures(
                    isRtl, false, onForward, onBackward, onShowMenu,
                    onLongPress = { onImageLongPress(active) },
                    onBottomAreaTap = onBottomAreaTap,
                    onDoubleTap = { zoom.toggleZoom(it, 5f) },
                    allowTap = zoom::allowTap
                )
            HorizontalPager(
                state = pagerState, reverseLayout = isRtl, userScrollEnabled = !zoom.zoomed,
                key = { pages[it].key }, modifier = Modifier
                    .fillMaxSize()
                    .then(gestureModifier),
                beyondViewportPageCount = 1
            ) { index ->
                val page = pages[index]
                if (page.source == null) {
                    if (index == pagerState.currentPage) commentContent(page)
                } else ReaderImageItem(
                    page.page, page.source, contentScale, false,
                    index == pagerState.currentPage, true,
                    zoomState = zoomStates.getOrPut(page.key) { ReaderZoomState() },
                    isContentScrolling = { pagerState.isScrollInProgress },
                    imageLoader = imageLoader, tools = tools, onReady = { onImageReady(page.key) },
                    trackReadiness = page.key in trackedPageKeys
                )
            }
        }
    }
}

@Composable
private fun ReaderImageItem(
    pageNumber: Int,
    path: String,
    contentScale: ContentScale,
    isContinuous: Boolean,
    isActivePage: Boolean,
    enableZoom: Boolean,
    zoomState: ReaderZoomState = remember(path) { ReaderZoomState() },
    isContentScrolling: () -> Boolean = { false },
    imageLoader: ImageLoader,
    tools: ReaderToolsSettings,
    onReady: () -> Unit = {},
    trackReadiness: Boolean = true
) {
    if (LocalInspectionMode.current) {
        PreviewImage(
            contentDescription = null,
            drawable = R.drawable.preview_reader_page,
            contentScale = contentScale,
            modifier = if (isContinuous) Modifier.fillMaxWidth() else Modifier.fillMaxSize()
        )
        return
    }
    val context = LocalContext.current
    var retryKey by rememberSaveable(path) { mutableIntStateOf(0) }
    val data: Any = if (path.startsWith("http")) path else File(path)
    var automaticRetries by remember(path) { mutableIntStateOf(0) }
    val request = remember(data, retryKey) {
        ImageRequest.Builder(context)
            .data(data)
            .setParameter("retry_hash", retryKey)
            .diskCachePolicy(if (retryKey > 0) CachePolicy.WRITE_ONLY else CachePolicy.ENABLED)
            .build()
    }

    val zoomScope = rememberCoroutineScope()

    // 离开当前页时重置缩放，回到默认显示
    LaunchedEffect(zoomState, isActivePage, enableZoom) {
        if (enableZoom && !isActivePage) {
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
        imageLoader = imageLoader,
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
                LaunchedEffect(path, retryKey, isActivePage) {
                    val riskControlled = generateSequence(error.result.throwable) { it.cause }
                        .any { it is com.rchdliang.copymanga_neo.domain.repository.RiskControlException }
                    if (isActivePage && !riskControlled && automaticRetries < tools.imageRetries) {
                        delay((700L * (automaticRetries + 1)).milliseconds)
                        automaticRetries++
                        retryKey++
                    }
                }
                Box(
                    modifier = placeholderModifier.clickable { automaticRetries = 0; retryKey++ },
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
                LaunchedEffect(
                    path,
                        retryKey,
                        trackReadiness
                    ) {
                        if (trackReadiness) {
                            androidx.compose.runtime.withFrameNanos { }
                            onReady()
                        }
                    }
                val currentPainter = painter
                if (enableZoom) {
                    // 分页模式：单个图片缩放
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .onSizeChanged(zoomState::updateViewport)
                            .then(
                                Modifier.readerImageZoomable(
                                    state = zoomState,
                                    scope = zoomScope,
                                    isContinuous = isContinuous,
                                    maxScale = 5f,
                                    isContentScrolling = isContentScrolling
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
