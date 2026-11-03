package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import com.rchdliang.copymanga_neo.multiplatform.ui.components.mouseDragScroll
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import coil3.ImageLoader
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings

/** One zoom viewport for both pages: the gutter and focal point move with the spread. */
@Composable
internal fun ReaderSpreadContent(
    pages: List<ReaderPageEntry>, spreads: List<ReaderSpread>, pager: PagerState,
    rtl: Boolean, contentScale: ContentScale, imageLoader: ImageLoader, tools: ReaderToolsSettings,
    onForward: () -> Unit, onBackward: () -> Unit, onShowMenu: () -> Unit,
    onLongPress: (ReaderPageEntry) -> Unit, onInteraction: () -> Unit,
    onReady: (String) -> Unit, onDimensions: (String, Float, Float) -> Unit,
    onZoomAvailable: (ReaderZoomState) -> Unit,
    commentContent: @Composable (ReaderPageEntry) -> Unit,
    imageQuality: ReaderImageQuality? = null,
    pageShapes: Map<String, Boolean>? = null,
    onSpreadDisplayReady: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val zooms = remember { mutableMapOf<String, ReaderZoomState>() }
    fun groupKey(group: ReaderSpread) = group.key(pages)
    if (spreads.isEmpty()) return
    // 裁剪旧章节后 pager 的下标要到下一次测量才更新；此时必须保留 Pager，
    // 让它按稳定 key 迁移已有页面，不能因旧下标越界而移除整棵图片视图。
    val laidOutKey by remember(pager) {
        derivedStateOf {
            pager.layoutInfo.visiblePagesInfo.firstOrNull { it.index == pager.currentPage }?.key
        }
    }
    val activeIndex = spreads.indexOfFirst { groupKey(it) == laidOutKey }
        .takeIf { it >= 0 } ?: pager.currentPage.coerceIn(spreads.indices)
    val current = spreads[activeIndex]
    val activeKey = groupKey(current)
    val zoom = zooms.getOrPut(activeKey) { ReaderZoomState() }
    SideEffect { onZoomAvailable(zoom) }
    LaunchedEffect(activeKey) { zoom.reset() }
    DisposableEffect(zooms) { onDispose { zooms.values.forEach { it.stopFling() } } }
    val retained = spreads.filterIndexed { index, _ -> kotlin.math.abs(index - activeIndex) <= 1 }.map(::groupKey).toSet()
    SideEffect { zooms.keys.toList().filter { it !in retained }.forEach { zooms.remove(it)?.stopFling() } }
    val imageActive = pages[current.indices.first()].source != null
    val fling = PagerDefaults.flingBehavior(pager, snapAnimationSpec = if (tools.pageAnimation) tween(180) else snap())
    val commentPager = remember(pager, fling, scope) { ReaderCommentPager(pager, fling, scope) }
    val scrollingEnabled = !zoom.zoomed && !commentPager.blocked
    // Explicit LTR layout prevents the host's locale from reversing the already ordered leaves.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, LocalReaderCommentPager provides commentPager) {
        HorizontalPager(state = pager, reverseLayout = rtl, beyondViewportPageCount = 1,
            flingBehavior = fling,
            key = { groupKey(spreads[it]) }, userScrollEnabled = scrollingEnabled,
            modifier = Modifier.fillMaxSize()
                .mouseDragScroll(pager, Orientation.Horizontal, enabled = scrollingEnabled, reverseLayout = rtl,
                    flingBehavior = fling)
                .readerSpreadWheel(zoom, imageActive, onInteraction, onForward, onBackward, rtl)
        ) { index ->
            val spread = spreads[index]
            val first = pages[spread.indices.first()]
            if (first.source == null) {
                // 越过半页时 currentPage 已改变，但拖动中的评论仍然可见，不能提前卸载。
                val visible by remember(pager, index) {
                    derivedStateOf {
                        val layout = pager.layoutInfo
                        layout.visiblePagesInfo.any {
                            it.index == index && it.offset < layout.viewportEndOffset &&
                                it.offset + layout.pageSize > layout.viewportStartOffset
                        }
                    }
                }
                if (index == activeIndex || (pager.isScrollInProgress && (visible || index == pager.settledPage))) {
                    commentContent(first)
                }
                if (index == activeIndex) SideEffect { onSpreadDisplayReady(activeKey) }
            } else {
                val preview = LocalInspectionMode.current
                var failedLeaves by remember { mutableStateOf(emptySet<String>()) }
                // 先让候选页解码，等宽高确认后再绘制双页，避免把横图短暂画进半屏。
                val pairingReady = preview || pageShapes == null || spread.indices.size == 1 ||
                    spread.indices.all {
                        val key = pages[it].key
                        pageShapes[key] == false || (key in failedLeaves && pageShapes[key] == null)
                    }
                if (index == activeIndex && pairingReady) SideEffect { onSpreadDisplayReady(activeKey) }
                val state = zooms.getOrPut(groupKey(spread)) { ReaderZoomState() }
                Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged(state::updateViewport)
                    .readerImageZoomable(state, scope, false, READER_MAX_SCALE, isContentScrolling = { pager.isScrollInProgress })
                    .readerTapGestures(rtl, false,
                        onForward = { if (state.zoomed) onShowMenu() else onForward() },
                        onBackward = { if (state.zoomed) onShowMenu() else onBackward() },
                        onShowMenu = onShowMenu, onBottomAreaTap = onShowMenu,
                        onDoubleTap = { state.toggleZoom(it, READER_MAX_SCALE) }, allowTap = state::allowTap,
                        onLongPress = { point ->
                            val center = state.viewportCenter()
                            val scene = (point - center - state.offset) / state.scale + center
                            val visual = spread.visualIndices(rtl)
                            val leaf = if (visual.size == 2 && scene.x >= center.x) visual[1] else visual[0]
                            onLongPress(pages[leaf])
                        })) {
                    Row(Modifier.fillMaxSize().drawWithContent { if (pairingReady) drawContent() }.graphicsLayer {
                        scaleX = state.scale; scaleY = state.scale
                        translationX = state.offset.x; translationY = state.offset.y
                    }) {
                        spread.visualIndices(rtl).forEachIndexed { visualIndex, rawIndex ->
                            val leaf = pages[rawIndex]
                            key(leaf.key) {
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    ReaderImageItem(leaf.page, leaf.source!!,
                                        if (spread.indices.size == 2) ContentScale.Fit else contentScale,
                                        false, index == activeIndex, false,
                                        zoomState = state, imageLoader = imageLoader, tools = tools,
                                        imageAlignment = readerSpreadImageAlignment(spread.indices.size, visualIndex),
                                        onReady = { onReady(leaf.key) },
                                        onDimensions = { width, height -> onDimensions(leaf.key, width, height) },
                                        onImageError = { failedLeaves = failedLeaves + leaf.key },
                                        imageQuality = imageQuality,
                                        enhancementPageKey = leaf.key,
                                        paired = spread.indices.size == 2,
                                    )
                                }
                            }
                        }
                    }
                    if (!pairingReady) {
                        CircularWavyProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                    }
                }
            }
        }
    }
}
