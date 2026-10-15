package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive
import com.rchdliang.copymanga_neo.multiplatform.ui.components.ActiveRouteReportingEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.NovelAnnotation
import com.rchdliang.copymanga_neo.domain.model.NovelReaderPreferences
import com.rchdliang.copymanga_neo.domain.model.NovelReadingVolume
import com.rchdliang.copymanga_neo.domain.model.NovelTextRange
import com.rchdliang.copymanga_neo.domain.reading.NovelReaderDocument
import com.rchdliang.copymanga_neo.domain.reading.NovelReaderEntry
import com.rchdliang.copymanga_neo.domain.reading.ReadingPositionPolicy
import com.rchdliang.copymanga_neo.domain.reading.novelPageFor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

internal data class NovelReaderLocation(
    val line: Int = 0, val character: Int = 0, val fraction: Double = 0.0,
    val section: String = "", val completed: Boolean = false
)

@Composable
internal fun NovelReaderViewport(
    volume: NovelReadingVolume,
    document: NovelReaderDocument,
    preferences: NovelReaderPreferences,
    location: NovelReaderLocation,
    navigation: Int,
    onLocation: (NovelReaderLocation) -> Unit,
    onPageLabel: (String?) -> Unit,
    onImage: (String) -> Unit,
    onToggleControls: () -> Unit,
    onDismissControls: () -> Unit,
    controlsVisible: Boolean,
    modifier: Modifier = Modifier,
    annotations: List<NovelAnnotation> = emptyList(),
    searchRange: NovelTextRange? = null,
    keyboardEnabled: Boolean = false,
    keyboardBlocked: Boolean = false
) {
    // 配色、常亮和模式不改变文字测量，避免这些变化清空布局并丢失当前位置。
    val layoutPreferences =
        preferences.copy(mode = "SCROLL", columns = "SINGLE", theme = "SYSTEM", keepScreenOn = true)
    val dismiss by rememberUpdatedState(onDismissControls)
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available != Offset.Zero) dismiss()
                return Offset.Zero
            }
        }
    }
    BoxWithConstraints(
        modifier
            .nestedScroll(scrollConnection)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(
                        if (controlsVisible) "隐藏阅读菜单" else "显示阅读菜单"
                    ) { onToggleControls(); true })
            }) {
        if (preferences.mode == "SCROLL") {
            NovelScrollViewport(
                volume, document, layoutPreferences, location, navigation, maxWidth.value,
                onLocation, onImage, onToggleControls, onDismissControls, annotations, searchRange, keyboardEnabled, keyboardBlocked
            )
            SideEffect { onPageLabel(null) }
        } else {
            val twoPages = preferences.columns != "SINGLE" && maxWidth >= 600.dp &&
                    (preferences.columns == "DOUBLE" || maxWidth >= 840.dp && maxWidth > maxHeight)
            NovelPagedViewport(
                volume,
                document,
                layoutPreferences,
                location,
                navigation,
                maxWidth.value,
                maxHeight.value,
                if (twoPages) 2 else 1,
                onLocation,
                onPageLabel,
                onImage,
                onToggleControls,
                onDismissControls,
                annotations,
                searchRange,
                keyboardEnabled,
                keyboardBlocked
            )
        }
    }
}

@Composable
private fun NovelScrollViewport(
    volume: NovelReadingVolume,
    document: NovelReaderDocument,
    preferences: NovelReaderPreferences,
    location: NovelReaderLocation,
    navigation: Int,
    width: Float,
    onLocation: (NovelReaderLocation) -> Unit,
    onImage: (String) -> Unit,
    onToggleControls: () -> Unit,
    onDismissControls: () -> Unit,
    annotations: List<NovelAnnotation>,
    searchRange: NovelTextRange?,
    keyboardEnabled: Boolean,
    keyboardBlocked: Boolean
) {
    val list = rememberLazyListState()
    val density = LocalDensity.current
    val layouts = remember(
        document,
        preferences,
        width,
        density
    ) { mutableStateMapOf<Int, TextLayoutResult>() }
    // 字号变化不一定改变插图高度，保留测量结果，避免等待不会再次触发的回调。
    val heights = remember(document) { mutableStateMapOf<Int, Int>() }
    var applied by remember(layouts) { mutableIntStateOf(-1) }
    val latestOnLocation by rememberUpdatedState(onLocation)
    LaunchedEffect(document, layouts, navigation) {
        val imageItem = document.imageItem(location.section)
        val target = imageItem ?: document.textItem(location.line)
        list.scrollToItem(target)
        if (imageItem != null) {
            val height = snapshotFlow { heights[target] ?: 0 }.first { it > 0 }
            list.scrollToItem(target, ReadingPositionPolicy.offset(location.fraction, height))
        } else {
            val line = document.source.clampLine(location.line)
            val layout =
                snapshotFlow { layouts[line] }.first { it != null } ?: return@LaunchedEffect
            val character = document.source.clampCharacter(line, location.character)
            val wrapped =
                layout.getLineForOffset(character.coerceAtMost(layout.layoutInput.text.length))
            val offset = layout.getLineTop(wrapped).toInt() + ReadingPositionPolicy.offset(
                location.fraction,
                (layout.getLineBottom(wrapped) - layout.getLineTop(wrapped)).toInt()
            )
            list.scrollToItem(target, offset)
        }
        applied = navigation
    }
    ActiveRouteReportingEffect(document, layouts, navigation) {
        snapshotFlow {
            if (applied != navigation) return@snapshotFlow null
            val index = list.firstVisibleItemIndex
            val entry = document.entries.getOrNull(index) ?: return@snapshotFlow null
            val offset = list.firstVisibleItemScrollOffset
            val completed = !list.canScrollForward && !list.isScrollInProgress &&
                    list.layoutInfo.visibleItemsInfo.lastOrNull()?.index == document.entries.lastIndex
            when (entry) {
                is NovelReaderEntry.Image -> {
                    val height = heights[index] ?: return@snapshotFlow null
                    NovelReaderLocation(
                        document.source.clampLine(entry.line), 0,
                        ReadingPositionPolicy.fraction(offset, height), entry.section.key, completed
                    )
                }

                is NovelReaderEntry.Text -> {
                    val layout = layouts[entry.line] ?: return@snapshotFlow null
                    val safeOffset = offset.coerceAtMost((layout.size.height - 1).coerceAtLeast(0))
                    val wrapped = layout.getLineForVerticalPosition(safeOffset.toFloat())
                    NovelReaderLocation(
                        entry.line,
                        document.source.clampCharacter(entry.line, layout.getLineStart(wrapped)),
                        ReadingPositionPolicy.fraction(
                            (safeOffset - layout.getLineTop(wrapped)).toInt(),
                            (layout.getLineBottom(wrapped) - layout.getLineTop(wrapped)).toInt()
                        ),
                        document.sectionAt(entry.line, volume.sections), completed
                    )
                }
            }
        }.distinctUntilChanged().collect { it?.let(latestOnLocation) }
    }
    val selection = rememberSelectionState()
    val keyboardFocus = remember { FocusRequester() }
    val keyboardScope = rememberCoroutineScope()
    var keyboardJob by remember { mutableStateOf<Job?>(null) }
    val hasSelection = selection.selectedTexts.isNotEmpty()
    val keyboardUnavailable = !LocalRouteActive.current || keyboardBlocked || hasSelection || applied != navigation
    LaunchedEffect(keyboardEnabled, keyboardUnavailable) {
        if (keyboardUnavailable) keyboardJob?.cancel()
        else if (keyboardEnabled) keyboardFocus.requestFocus()
    }
    ObserveNovelReaderSelection(selection, onDismissControls)
    SelectionContainer(
        state = selection,
        modifier = Modifier.novelReaderGestures(
            { selection.selectedTexts.isNotEmpty() }, onToggleControls, onDismissControls
        )
    ) {
        LazyColumn(
            Modifier.fillMaxSize()
                .focusRequester(keyboardFocus)
                .readerKeyboardNavigation(keyboardEnabled, ReadingDirection.TOP_TO_BOTTOM, keyboardUnavailable) { command ->
                    onDismissControls()
                    if (keyboardJob?.isActive != true) keyboardJob = keyboardScope.launch {
                        val viewport = (list.layoutInfo.viewportEndOffset - list.layoutInfo.viewportStartOffset).coerceAtLeast(0)
                        val distance = if (command.movement == ReaderKeyboardMovement.SMALL_SCROLL) with(density) { 48.dp.toPx() }
                            else viewport * 0.9f
                        list.animateScrollBy(if (command.forward) distance else -distance)
                    }
                }.focusable(enabled = keyboardEnabled), state = list,
            contentPadding = PaddingValues(horizontal = preferences.margin.dp, vertical = 12.dp)
        ) {
            itemsIndexed(document.entries, key = { _, entry -> entry.key }) { index, entry ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { heights[index] = it.height }
                        .padding(bottom = preferences.paragraphSpacing.dp)) {
                    when (entry) {
                        is NovelReaderEntry.Text -> NovelReaderText(
                            entry.text, entry.line, preferences = preferences,
                            annotations = annotations, searchRange = searchRange,
                            onTextLayout = { layouts[entry.line] = it }
                        )

                        is NovelReaderEntry.Image -> DisableSelection {
                            NovelReaderImage(
                                volume, entry.section,
                                Modifier
                                    .fillMaxWidth()
                                    .height(
                                        ((width - preferences.margin * 2) / .85f).coerceIn(
                                            1f,
                                            600f
                                        ).dp
                                    )
                                    .clickable { onImage(entry.section.key) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NovelPagedViewport(
    volume: NovelReadingVolume,
    document: NovelReaderDocument,
    preferences: NovelReaderPreferences,
    location: NovelReaderLocation,
    navigation: Int,
    widthDp: Float,
    heightDp: Float,
    columns: Int,
    onLocation: (NovelReaderLocation) -> Unit,
    onPageLabel: (String?) -> Unit,
    onImage: (String) -> Unit,
    onToggleControls: () -> Unit,
    onDismissControls: () -> Unit,
    annotations: List<NovelAnnotation>,
    searchRange: NovelTextRange?,
    keyboardEnabled: Boolean,
    keyboardBlocked: Boolean
) {
    val density = LocalDensity.current
    val gutter = if (columns == 2) 16f else 0f
    val leafWidth = (widthDp - gutter) / columns
    val contentWidthPx =
        with(density) { (leafWidth - preferences.margin * 2).coerceAtLeast(1f).dp.roundToPx() }
    val contentHeightPx = with(density) { (heightDp - 24f).coerceAtLeast(1f).dp.roundToPx() }
    val pagination = rememberNovelPagination(document, preferences, contentWidthPx, contentHeightPx)
    val pages = pagination.pages
    val ready = pagination.complete || pages.any { page ->
        page.blocks.any { block ->
            when (val entry = block.entry) {
                is NovelReaderEntry.Image -> entry.section.key == location.section
                is NovelReaderEntry.Text -> entry.line == location.line && location.character >= block.character &&
                        location.character < block.character + block.text.length.coerceAtLeast(1)
            }
        }
    }
    val pager =
        rememberPagerState(pageCount = { ((pages.size + columns - 1) / columns).coerceAtLeast(1) })
    var applied by remember(
        document,
        preferences,
        widthDp,
        heightDp,
        density,
        columns
    ) { mutableIntStateOf(-1) }
    val latestPages by rememberUpdatedState(pages)
    val latestComplete by rememberUpdatedState(pagination.complete)
    val latestOnLocation by rememberUpdatedState(onLocation)
    LaunchedEffect(navigation, ready, preferences, widthDp, heightDp, columns) {
        if (!ready || applied == navigation) return@LaunchedEffect
        val target =
            novelPageFor(pages, location.line, location.character, location.section) / columns
        pager.scrollToPage(target.coerceIn(0, (pager.pageCount - 1).coerceAtLeast(0)))
        applied = navigation
    }
    ActiveRouteReportingEffect(pager, navigation, preferences, columns) {
        var emitted: NovelReaderLocation? = null
        snapshotFlow {
            Triple(
                pager.settledPage to pager.isScrollInProgress,
                applied,
                latestPages.size to latestComplete
            )
        }.distinctUntilChanged().collect { (pagerPosition, restored, _) ->
            val (spread, scrolling) = pagerPosition
            if (scrolling || restored != navigation) return@collect
            val currentPages = latestPages
            val leaf = spread * columns
            val block = currentPages.getOrNull(leaf)?.blocks?.firstOrNull() ?: return@collect
            val completed = latestComplete && leaf + columns >= currentPages.size
            val anchor = when (val entry = block.entry) {
                is NovelReaderEntry.Text -> NovelReaderLocation(
                    entry.line, block.character, 0.0,
                    document.sectionAt(entry.line, volume.sections), completed
                )

                is NovelReaderEntry.Image -> NovelReaderLocation(
                    document.source.clampLine(entry.line), 0,
                    0.0, entry.section.key, completed
                )
            }
            if (emitted != anchor) {
                emitted = anchor
                latestOnLocation(anchor)
            }
        }
    }
    SideEffect {
        onPageLabel(
            if (!ready) "正在排版…" else
                "${pager.settledPage * columns + 1} / ${pages.size}${if (pagination.complete) " 页" else " 页…"}"
        )
    }
    val keyboardFocus = remember { FocusRequester() }
    val keyboardScope = rememberCoroutineScope()
    var keyboardJob by remember { mutableStateOf<Job?>(null) }
    val selectedColumns = remember(document) { mutableStateMapOf<String, Boolean>() }
    val keyboardUnavailable = !LocalRouteActive.current || keyboardBlocked || selectedColumns.values.any { it } || !ready || applied != navigation
    LaunchedEffect(keyboardEnabled, keyboardUnavailable) {
        if (keyboardUnavailable) keyboardJob?.cancel()
        else if (keyboardEnabled) keyboardFocus.requestFocus()
    }
    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
    } else {
        HorizontalPager(
            state = pager, modifier = Modifier.fillMaxSize()
                .focusRequester(keyboardFocus)
                .readerKeyboardNavigation(keyboardEnabled, ReadingDirection.LEFT_TO_RIGHT, keyboardUnavailable) { command ->
                    onDismissControls()
                    if (keyboardJob?.isActive != true) keyboardJob = keyboardScope.launch {
                        val target = (pager.settledPage + if (command.forward) 1 else -1).coerceIn(0, (pager.pageCount - 1).coerceAtLeast(0))
                        pager.animateScrollToPage(target)
                    }
                }.focusable(enabled = keyboardEnabled),
            key = { "novel-spread:$it" }) { spread ->
            Row(Modifier.fillMaxSize()) {
                repeat(columns) { column ->
                    if (column > 0) Spacer(Modifier.width(gutter.dp))
                    val page = pages.getOrNull(spread * columns + column)
                    val selection = rememberSelectionState()
                    val selectionKey = "$spread:$column"
                    val hasSelection = selection.selectedTexts.isNotEmpty()
                    SideEffect { selectedColumns[selectionKey] = hasSelection }
                    DisposableEffect(selectionKey) { onDispose { selectedColumns.remove(selectionKey) } }
                    ObserveNovelReaderSelection(selection, onDismissControls)
                    SelectionContainer(
                        state = selection,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .novelReaderGestures(
                                { selection.selectedTexts.isNotEmpty() },
                                onToggleControls,
                                onDismissControls
                            )
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = preferences.margin.dp, vertical = 12.dp)
                        ) {
                            page?.blocks?.forEach { block ->
                                when (val entry = block.entry) {
                                    is NovelReaderEntry.Text -> NovelReaderText(
                                        block.text, entry.line, block.character, preferences,
                                        annotations, searchRange
                                    )

                                    is NovelReaderEntry.Image -> DisableSelection {
                                        NovelReaderImage(
                                            volume, entry.section,
                                            Modifier
                                                .fillMaxWidth()
                                                .height(with(density) { block.heightPx.toDp() })
                                                .clickable { onImage(entry.section.key) })
                                    }
                                }
                                if (block.spacingPx > 0) Spacer(Modifier.height(with(density) { block.spacingPx.toDp() }))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 只观察未被插图等子组件处理的短按，不消费事件，让滚动、翻页和长按选字优先。 */
@Composable
private fun Modifier.novelReaderGestures(
    hasSelection: () -> Boolean,
    onToggleControls: () -> Unit,
    onDismissControls: () -> Unit
): Modifier {
    val selection by rememberUpdatedState(hasSelection)
    val toggle by rememberUpdatedState(onToggleControls)
    val dismiss by rememberUpdatedState(onDismissControls)
    return pointerInput(Unit) {
        awaitEachGesture {
            // Initial 阶段记录选区，避免“点击清除选区”同时唤出阅读菜单。
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var ignored = selection()
            var moved = false
            var releasedAt = down.uptimeMillis
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (event.changes.size != 1 || change == null) ignored = true
                if (change != null) {
                    releasedAt = change.uptimeMillis
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        if (!moved) dismiss()
                        moved = true
                    }
                }
                if (event.changes.any { it.isConsumed }) ignored = true
            } while (event.changes.any { it.pressed })
            if (!ignored && !moved && !selection() &&
                releasedAt - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
            ) {
                if (down.position.x in size.width * .33f..size.width * .67f) toggle()
                else dismiss()
            }
        }
    }
}
