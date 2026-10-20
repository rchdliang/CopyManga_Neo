/*
 * Context-menu provider and outside-gesture lifecycle adapted from Kixyu Book ReaderSelectionToolbar.kt.
 * Copyright (c) 2026 kkyu9527. MIT License; see assets/licenses/kixyubook-MIT.txt.
 * https://github.com/kkyu9527/kixyubook
 */
package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive
import com.rchdliang.copymanga_neo.multiplatform.ui.components.ActiveRouteLaunchedEffect
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.rchdliang.copymanga_neo.domain.model.NovelTextSelection
import com.rchdliang.copymanga_neo.domain.reading.NovelTextDocument
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

/** 文字菜单、选区和取消操作由一个状态持有者协调，滚动与分页共用。 */
@Stable
internal class NovelReaderTextMenuState : TextContextMenuProvider {
    var request by mutableStateOf<NovelTextMenuRequest?>(null)
        private set
    var selection by mutableStateOf<SelectionState?>(null)
        private set
    val visible get() = request != null
    val active get() = selection != null || visible

    fun activate(value: SelectionState) {
        if (selection !== value) {
            selection?.clear()
            selection = value
        }
    }

    fun release(value: SelectionState) {
        if (selection === value) dismiss()
    }

    fun dismiss() {
        request?.close()
        request = null
        val previous = selection
        selection = null
        previous?.clear()
    }

    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        request?.close()
        val next = NovelTextMenuRequest(dataProvider)
        request = next
        try {
            next.awaitClose()
        } finally {
            if (request === next) request = null
        }
    }
}

internal class NovelTextMenuRequest(val dataProvider: TextContextMenuDataProvider) :
    TextContextMenuSession {
    private val closed = CompletableDeferred<Unit>()
    override fun close() {
        closed.complete(Unit)
    }

    suspend fun awaitClose() {
        closed.await()
    }
}

private val LocalNovelReaderTextMenu = staticCompositionLocalOf<NovelReaderTextMenuState?> { null }

/** 保留 Compose 的选字、手柄和复制行为，只替换菜单呈现与取消规则。 */
@Composable
internal fun NovelReaderTextMenuHost(
    state: NovelReaderTextMenuState,
    dismissKey: Any,
    document: NovelTextDocument,
    onAnnotate: (NovelTextSelection) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var menuBounds by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(dismissKey) { state.dismiss() }
    DisposableEffect(state) { onDispose { state.dismiss() } }
    BackHandler(enabled = state.active) { state.dismiss() }
    CompositionLocalProvider(
        LocalNovelReaderTextMenu provides state,
        LocalTextContextMenuToolbarProvider provides state,
        LocalTextContextMenuDropdownProvider provides state
    ) {
        Box(
            modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates = it }
                .pointerInput(state) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        if (!state.visible || menuBounds?.contains(down.position) == true) return@awaitEachGesture
                        // 第一次外部手势只取消选字；等到抬手再清除，防止同次手势落到正文点击逻辑。
                        down.consume()
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        state.dismiss()
                    }
                },
            propagateMinConstraints = true
        ) {
            content()
            val anchor = coordinates
            val request = state.request
            if (LocalRouteActive.current && anchor?.isAttached == true && request != null) {
                NovelReaderSelectionMenu(
                    state,
                    request,
                    anchor,
                    document,
                    onAnnotate,
                    onSearch
                ) { menuBounds = it }
            }
        }
    }
}

/** 当前选区属于哪个容器由实际选中文字决定；离开页、卷或模式时清理，避免残留菜单。 */
@Composable
internal fun ObserveNovelReaderSelection(selection: SelectionState, onSelected: () -> Unit) {
    val menu = LocalNovelReaderTextMenu.current
    val selected by rememberUpdatedState(onSelected)
    ActiveRouteLaunchedEffect(menu, selection) {
        snapshotFlow { selection.selectedTexts.isNotEmpty() }.distinctUntilChanged()
            .collect { active ->
                if (active) {
                    menu?.activate(selection)
                    selected()
                } else menu?.release(selection)
            }
    }
    DisposableEffect(menu, selection) { onDispose { menu?.release(selection) } }
}

@Composable
private fun NovelReaderSelectionMenu(
    state: NovelReaderTextMenuState, request: NovelTextMenuRequest,
    coordinates: LayoutCoordinates, document: NovelTextDocument,
    onAnnotate: (NovelTextSelection) -> Unit, onSearch: (String) -> Unit,
    onBounds: (Rect?) -> Unit
) {
    val data by remember(request) { derivedStateOf(request.dataProvider::data) }
    val clipboard = LocalClipboardManager.current
    val selectedParts = state.selection?.selectedTexts.orEmpty()
    val canCopy = data.components.any { it.key == TextContextMenuKeys.CopyKey } &&
        selectedParts.any { it.isNotEmpty() }
    val bounds = request.dataProvider.contentBounds(coordinates)
    DisposableEffect(request) { onDispose { onBounds(null) } }

    val selected = novelSelectedSource(state.selection?.selectedTexts.orEmpty(), document)
    val query =
        selected?.text ?: state.selection?.selectedTexts?.joinToString("\n") { it.text }.orEmpty()

    Layout(
        content = {
            NovelReaderTextMenuSurface(
                canCopy = canCopy,
                canAnnotate = selected != null,
                canSearch = query.isNotBlank(),
                onCopy = {
                    clipboard.setText(novelClipboardText(selectedParts))
                    state.dismiss()
                },
                onAnnotate = { selected?.let { state.dismiss(); onAnnotate(it) } },
                onSearch = { state.dismiss(); onSearch(query) },
                modifier = Modifier.onGloballyPositioned { onBounds(it.boundsInParent()) }
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
    ) { measurables, constraints ->
        val gap = 12.dp.roundToPx()
        val placeable = measurables.single().measure(
            constraints.copy(
                minWidth = 0, minHeight = 0,
                maxWidth = (constraints.maxWidth - gap * 2).coerceAtLeast(0),
                maxHeight = (constraints.maxHeight - gap * 2).coerceAtLeast(0)
            )
        )
        val position = novelTextMenuPosition(
            bounds,
            IntSize(constraints.maxWidth, constraints.maxHeight),
            IntSize(placeable.width, placeable.height),
            gap
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(
                position.x,
                position.y
            )
        }
    }
}

@Composable
internal fun NovelReaderTextMenuSurface(
    canCopy: Boolean, canAnnotate: Boolean, canSearch: Boolean,
    onCopy: () -> Unit, onAnnotate: () -> Unit, onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier.semantics { paneTitle = "文字操作" },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp, shadowElevation = 3.dp
    ) {
        Row(Modifier.padding(2.dp)) {
            listOf(
                Triple("复制", Icons.Outlined.ContentCopy, canCopy to onCopy),
                Triple("标注", Icons.Outlined.FormatColorFill, canAnnotate to onAnnotate),
                Triple("搜索", Icons.Outlined.Search, canSearch to onSearch)
            ).forEach { (label, icon, action) ->
                TextButton(
                    onClick = action.second, enabled = action.first,
                    modifier = Modifier
                        // Selection toolbar actions must keep focus in the selected text.
                        // Taking focus clears the selection and removes this button before release.
                        .focusProperties { canFocus = false }
                        .width(56.dp)
                        .heightIn(min = 52.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(icon, null, Modifier.size(20.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalContentColor.current
                        )
                    }
                }
            }
        }
    }
}

/** 菜单优先放在选区上方，顶部空间不足时放到下方；手机和平板都限制在视口内部。 */
internal fun novelTextMenuPosition(
    bounds: Rect,
    viewport: IntSize,
    menu: IntSize,
    gap: Int
): IntOffset {
    val maxX = (viewport.width - menu.width - gap).coerceAtLeast(0)
    val maxY = (viewport.height - menu.height - gap).coerceAtLeast(0)
    val minX = gap.coerceAtMost(maxX)
    val minY = gap.coerceAtMost(maxY)
    val x = (bounds.center.x - menu.width / 2f).roundToInt().coerceIn(minX, maxX)
    val above = bounds.top.roundToInt() - menu.height - gap
    val below = bounds.bottom.roundToInt() + gap
    val y = when {
        above >= minY -> above
        below <= maxY -> below
        else -> minY
    }.coerceIn(minY, maxY)
    return IntOffset(x, y)
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "小说长按菜单 · 手机",
    showBackground = true,
    widthDp = 412,
    heightDp = 160
)
@androidx.compose.ui.tooling.preview.Preview(
    name = "小说长按菜单 · 平板",
    showBackground = true,
    widthDp = 600,
    heightDp = 160
)
@androidx.compose.ui.tooling.preview.Preview(
    name = "小说长按菜单 · 深色", showBackground = true, widthDp = 412, heightDp = 160,
    uiMode = 0x20 // Android-compatible preview NIGHT_YES metadata, without an Android dependency.
)
@Composable
private fun NovelReaderTextMenuPreview() {
    com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                NovelReaderTextMenuSurface(true, true, true, {}, {}, {})
            }
        }
    }
}

/** Matches Compose SelectionManager.getSelectedText, retaining spans and inter-selectable newlines. */
internal fun novelClipboardText(parts: List<AnnotatedString>): AnnotatedString {
    val text = AnnotatedString.Builder()
    parts.forEachIndexed { index, part ->
        if (index > 0) text.append('\n')
        text.append(part)
    }
    return text.toAnnotatedString()
}
