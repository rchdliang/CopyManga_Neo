package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import com.rchdliang.copymanga_neo.multiplatform.ui.components.RouteImageScope
import com.rchdliang.copymanga_neo.multiplatform.ui.components.routeImageModel
import com.rchdliang.copymanga_neo.domain.reading.ReaderSrSession
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.pager.PagerDefaults
import com.rchdliang.copymanga_neo.multiplatform.ui.components.mouseDragScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.rchdliang.copymanga_neo.multiplatform.resources.Res
import com.rchdliang.copymanga_neo.multiplatform.resources.preview_reader_page
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import com.rchdliang.copymanga_neo.multiplatform.ui.preview.PreviewImage
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** 图片页与章末评论页共享稳定索引，评论页独立处理触摸和滚动。 */
@Composable
internal fun ReaderContent(
    pages: List<ReaderPageEntry>,
    spreads: List<ReaderSpread>,
    onDimensions: (String, Float, Float) -> Unit,
    onZoomAvailable: (ReaderZoomState) -> Unit,
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
    trackedPageKeys: Set<String> = pages.map { it.key }.toSet(),
    imageQuality: ReaderImageQuality? = null,
    srSession: ReaderSrSession? = null,
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
                    .readerWheelZoom(
                        zoom, imageQuality != null, 3f, onUserInteraction,
                        enabledAt = { touchedPage(it.y)?.source != null }
                    )
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
                        .mouseDragScroll(listState, Orientation.Vertical)
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
                            imageQuality = imageQuality,
                            srSession = srSession, srPageKey = page.key,
                            tools = tools,
                            onReady = { onImageReady(page.key) },
                            trackReadiness = page.key in trackedPageKeys
                        )
                    }
                }
            }
        } else {
            ReaderSpreadContent(pages, spreads, pagerState, isRtl, contentScale, imageLoader, tools,
                onForward, onBackward, onShowMenu, onImageLongPress, onUserInteraction,
                onImageReady, onDimensions, onZoomAvailable, commentContent, imageQuality, srSession)
        }
    }
}

@Composable
internal fun ReaderImageItem(
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
    imageQuality: ReaderImageQuality? = null,
    onReady: () -> Unit = {},
    onDimensions: (Float, Float) -> Unit = { _, _ -> },
    trackReadiness: Boolean = true,
    srSession: ReaderSrSession? = null,
    srPageKey: String = path,
) {
    if (LocalInspectionMode.current) {
        PreviewImage(
            contentDescription = null,
            drawable = Res.drawable.preview_reader_page,
            contentScale = contentScale,
            modifier = if (isContinuous) Modifier.fillMaxWidth() else Modifier.fillMaxSize()
        )
        return
    }
    val context = LocalPlatformContext.current
    var retryKey by rememberSaveable(path) { mutableIntStateOf(0) }
    val data: Any = readerImageData(path)
    var automaticRetries by remember(path) { mutableIntStateOf(0) }
    var lastSuccess by remember(data, retryKey) { mutableStateOf<AsyncImagePainter.State.Success?>(null) }

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

    ReaderImageRequestLayout(
        imageQuality,
        if (imageQuality != null) zoomState.scale else 1f,
        imageModifier
    ) { decodeOptions ->
        val request = remember(data, retryKey, decodeOptions) {
            ImageRequest.Builder(context)
                .data(data)
                .memoryCacheKeyExtra("retry_hash", retryKey.toString())
                .diskCachePolicy(if (retryKey > 0) CachePolicy.WRITE_ONLY else CachePolicy.ENABLED)
                .apply { decodeOptions?.applyTo(this) }
                .build()
        }
        RouteImageScope {
            SubcomposeAsyncImage(
                model = routeImageModel(request),
                imageLoader = imageLoader,
                contentDescription = null,
                modifier = imageModifier,
                contentScale = contentScale,
                filterQuality = if (imageQuality != null) FilterQuality.High else FilterQuality.Low,
                onState = { if (imageQuality != null && it is AsyncImagePainter.State.Success) lastSuccess = it },
            ) {
                val imageState by painter.state.collectAsState()
                // 升级解码或窗口变化时保留旧图；失败也不打断已经可以阅读的页面。
                val displayState = if (imageQuality != null && imageState !is AsyncImagePainter.State.Success)
                    lastSuccess ?: imageState else imageState
                when (val result = displayState) {
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
                        val error = result
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
                                "图片加载失败，点按重试",
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
                        val intrinsic = result.painter.intrinsicSize
                        LaunchedEffect(path, intrinsic) { onDimensions(intrinsic.width, intrinsic.height) }
                        val currentPainter = readerSrPainter(srSession, srPageKey, retryKey,
                            if (imageQuality != null) result.painter else painter)
                        val sharpness = if (isActivePage) LocalReaderSharpness.current else 0f
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
                                        }
                                        .readerSharpen(sharpness),
                                    contentScale = contentScale
                                )
                            }
                        } else {
                            // 连续模式：缩放在 LazyColumn 层面，单个图片直接显示
                            Image(
                                painter = currentPainter,
                                contentDescription = null,
                                modifier = imageModifier.readerSharpen(sharpness),
                                contentScale = contentScale
                            )
                        }
                    }

                    else -> Unit
                }
            }
        }
    }
}
