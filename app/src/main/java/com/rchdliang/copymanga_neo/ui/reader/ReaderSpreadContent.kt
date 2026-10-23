package com.rchdliang.copymanga_neo.ui.reader

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import coil.ImageLoader
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

) {
    val scope = rememberCoroutineScope()
    val zooms = remember { mutableMapOf<String, ReaderZoomState>() }
    fun groupKey(group: ReaderSpread) = group.indices.joinToString("|") { pages[it].key }
    val current = spreads.getOrNull(pager.currentPage) ?: return
    val activeKey = groupKey(current)
    val zoom = zooms.getOrPut(activeKey) { ReaderZoomState() }
    SideEffect { onZoomAvailable(zoom) }
    LaunchedEffect(activeKey) { zoom.reset() }
    DisposableEffect(zooms) { onDispose { zooms.values.forEach { it.stopFling() } } }
    val retained = spreads.filterIndexed { index, _ -> kotlin.math.abs(index - pager.currentPage) <= 1 }.map(::groupKey).toSet()
    SideEffect { zooms.keys.toList().filter { it !in retained }.forEach { zooms.remove(it)?.stopFling() } }
    val imageActive = pages[current.indices.first()].source != null
    // Explicit LTR layout prevents the host's locale from reversing the already ordered leaves.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        HorizontalPager(state = pager, reverseLayout = rtl, beyondViewportPageCount = 1,
            flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = if (tools.pageAnimation) tween(180) else snap()),
            key = { groupKey(spreads[it]) }, userScrollEnabled = !zoom.zoomed,
            modifier = Modifier.fillMaxSize()
                .readerSpreadWheel(zoom, imageActive, onInteraction, onForward, onBackward, rtl)
        ) { index ->
            val spread = spreads[index]
            val first = pages[spread.indices.first()]
            if (first.source == null) {
                if (index == pager.currentPage) commentContent(first)
            } else {
                val state = zooms.getOrPut(groupKey(spread)) { ReaderZoomState() }
                Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged(state::updateViewport)
                    .readerImageZoomable(state, scope, false, 5f, isContentScrolling = { pager.isScrollInProgress })
                    .readerTapGestures(rtl, false,
                        onForward = { if (state.zoomed) onShowMenu() else onForward() },
                        onBackward = { if (state.zoomed) onShowMenu() else onBackward() },
                        onShowMenu = onShowMenu, onBottomAreaTap = onShowMenu,
                        onDoubleTap = { state.toggleZoom(it, 5f) }, allowTap = state::allowTap,
                        onLongPress = { point ->
                            val center = state.viewportCenter()
                            val scene = (point - center - state.offset) / state.scale + center
                            val visual = spread.visualIndices(rtl)
                            val leaf = if (visual.size == 2 && scene.x >= center.x) visual[1] else visual[0]
                            onLongPress(pages[leaf])
                        })) {
                    Row(Modifier.fillMaxSize().graphicsLayer {
                        scaleX = state.scale; scaleY = state.scale
                        translationX = state.offset.x; translationY = state.offset.y
                    }) {
                        spread.visualIndices(rtl).forEach { rawIndex ->
                            val leaf = pages[rawIndex]
                            key(leaf.key) {
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    ReaderImageItem(leaf.page, leaf.source!!,
                                        if (spread.indices.size == 2) ContentScale.Fit else contentScale,
                                        false, index == pager.currentPage, false,
                                        zoomState = state, imageLoader = imageLoader, tools = tools,
                                        onReady = { onReady(leaf.key) },
                                        onDimensions = { width, height -> onDimensions(leaf.key, width, height) },

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
