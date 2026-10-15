/*
 * Fast scroller adapted from Kixyu Book ReaderDirectory.kt.
 * Copyright (c) 2026 kkyu9527. MIT License; see assets/licenses/kixyubook-MIT.txt.
 * https://github.com/kkyu9527/kixyubook
 */
package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive
import com.rchdliang.copymanga_neo.multiplatform.ui.components.ActiveRouteLaunchedEffect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.rchdliang.copymanga_neo.multiplatform.ui.components.edgeToEdgeDialogProperties
import com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord
import com.rchdliang.copymanga_neo.domain.model.NovelAnnotation
import com.rchdliang.copymanga_neo.domain.model.NovelSection
import com.rchdliang.copymanga_neo.domain.model.NovelVolume
import com.rchdliang.copymanga_neo.domain.model.displayBookTitle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 手机使用底部圆角面板，大窗口使用左侧目录；面板始终覆盖正文，不触发阅读重排。 */
@Composable
internal fun NovelReaderDirectoryOverlay(
    state: NovelUiState,
    currentLine: Int,
    bookmarks: List<LocalReadingRecord>,
    showBookmarks: Boolean,
    onSection: (NovelSection) -> Unit,
    onVolume: (String) -> Unit,
    onReload: () -> Unit,
    onBookmark: (LocalReadingRecord) -> Unit,
    onDeleteBookmark: (LocalReadingRecord) -> Unit,
    onDismiss: () -> Unit,
    annotations: List<NovelAnnotation> = emptyList(),
    onAnnotation: (NovelAnnotation) -> Unit = {},
    onEditAnnotation: (NovelAnnotation) -> Unit = {},
    onDeleteAnnotation: (NovelAnnotation) -> Unit = {}
) {
    if (LocalRouteActive.current) Dialog(
        onDismissRequest = onDismiss,
        properties = edgeToEdgeDialogProperties(dismissOnClickOutside = true)
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.statusBars.union(WindowInsets.displayCutout)
                )
        ) {
            val expanded = maxWidth >= 840.dp
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = .32f))
                    .clickable(onClick = onDismiss)
            )
            Surface(
                modifier = Modifier
                    .align(if (expanded) Alignment.CenterStart else Alignment.BottomCenter)
                    .padding(12.dp)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .height(if (expanded) maxHeight - 24.dp else maxHeight * .84f),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                NovelReaderDirectory(
                    state, currentLine, bookmarks, showBookmarks, onSection, onVolume, onReload,
                    onBookmark, onDeleteBookmark, onDismiss,
                    annotations = annotations, onAnnotation = onAnnotation,
                    onEditAnnotation = onEditAnnotation, onDeleteAnnotation = onDeleteAnnotation
                )
            }
        }
    }
}

private sealed interface NovelDirectoryRow {
    val key: String

    data class Volume(val volume: NovelVolume) : NovelDirectoryRow {
        override val key = "volume:${volume.id}"
    }

    data object Opening : NovelDirectoryRow {
        override val key = "opening"
    }

    data class Chapter(val section: NovelSection, val index: Int) : NovelDirectoryRow {
        override val key = "chapter:${section.key}"
    }
}

/** 分卷与章节合并展示；只有当前卷已获取正文，因此其他分卷直接打开，不伪造章节目录。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NovelReaderDirectory(
    state: NovelUiState,
    currentLine: Int,
    bookmarks: List<LocalReadingRecord>,
    showBookmarks: Boolean = false,
    onSection: (NovelSection) -> Unit,
    onVolume: (String) -> Unit,
    onReload: () -> Unit,
    onBookmark: (LocalReadingRecord) -> Unit,
    onDeleteBookmark: (LocalReadingRecord) -> Unit,
    onDismiss: () -> Unit,
    annotations: List<NovelAnnotation> = emptyList(),
    onAnnotation: (NovelAnnotation) -> Unit = {},
    onEditAnnotation: (NovelAnnotation) -> Unit = {},
    onDeleteAnnotation: (NovelAnnotation) -> Unit = {}
) {
    val reading = state.reader ?: return
    val volume = reading.volume
    val sections = remember(reading.sections, reading.document) {
        reading.sections.filter { it.type == 1 && it.start in reading.document.lines.indices }
            .distinctBy { it.key }.sortedBy { it.start }
    }
    val volumes = remember(state.volumes, volume) {
        (state.volumes + volume).distinctBy { it.id }.sortedBy { it.index }
    }
    val currentChapter = sections.indexOfLast { it.start <= currentLine }
    var tab by rememberSaveable { mutableIntStateOf(if (showBookmarks) 1 else 0) }
    var expanded by rememberSaveable(volume.id) { mutableStateOf(true) }
    val rows = remember(volumes, volume.id, sections, expanded) {
        buildList<NovelDirectoryRow> {
            volumes.forEach { item ->
                add(NovelDirectoryRow.Volume(item))
                if (item.id == volume.id && expanded) {
                    add(NovelDirectoryRow.Opening)
                    sections.forEachIndexed { index, section ->
                        add(
                            NovelDirectoryRow.Chapter(
                                section,
                                index
                            )
                        )
                    }
                }
            }
        }
    }

    fun selectedIndex(items: List<NovelDirectoryRow>): Int = items.indexOfFirst {
        if (currentChapter < 0) it == NovelDirectoryRow.Opening
        else it is NovelDirectoryRow.Chapter && it.index == currentChapter
    }.takeIf { it >= 0 } ?: items.indexOfFirst {
        it is NovelDirectoryRow.Volume && it.volume.id == volume.id
    }.coerceAtLeast(0)

    val list = rememberLazyListState(
        initialFirstVisibleItemIndex = (selectedIndex(rows) - 2).coerceAtLeast(0)
    )
    val scope = rememberCoroutineScope()
    val latestRows by rememberUpdatedState(rows)
    fun locate() {
        expanded = true
        scope.launch {
            val ready = snapshotFlow { latestRows }.first { items ->
                items.any { it == NovelDirectoryRow.Opening }
            }
            val target = (selectedIndex(ready) - 2).coerceAtLeast(0)
            snapshotFlow { list.layoutInfo.totalItemsCount }.first { it == ready.size }
            list.animateScrollToItem(target)
        }
    }
    ActiveRouteLaunchedEffect(tab) { if (tab == 0) onReload() }
    // 分卷目录异步补齐时定位一次；手动滚动、收起当前卷、修改书签不重置滚动位置。
    LaunchedEffect(volumes, tab) {
        if (tab == 0 && expanded) {
            snapshotFlow { list.layoutInfo.totalItemsCount }.first { it == rows.size }
            list.scrollToItem((selectedIndex(rows) - 2).coerceAtLeast(0))
        }
    }

    val bookmarkedVolumes = remember(bookmarks) { bookmarks.mapTo(hashSetOf()) { it.chapter } }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "目录",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    displayBookTitle(reading.book.path, reading.book.name),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "关闭目录") }
        }
        SingleChoiceSegmentedButtonRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            listOf(
                "章节" to Icons.AutoMirrored.Outlined.FormatListBulleted,
                "书签 · ${bookmarks.size}" to Icons.Outlined.Bookmarks,
                "标注 · ${annotations.size}" to Icons.Outlined.FormatColorFill
            )
                .forEachIndexed { index, (title, icon) ->
                    SegmentedButton(
                        selected = tab == index, onClick = { tab = index },
                        shape = SegmentedButtonDefaults.itemShape(index, 3),
                        icon = { Icon(icon, null, Modifier.size(18.dp)) }
                    ) { Text(title, color = LocalContentColor.current) }
                }
        }
        if (tab == 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${volumes.size} 卷 · 本卷 ${sections.size} 章",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = ::locate) {
                    Icon(Icons.Outlined.MyLocation, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("当前章节", color = MaterialTheme.colorScheme.primary)
                }
            }
            if (state.volumesLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.volumesError?.let { error ->
                Row(
                    Modifier.padding(start = 20.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        error,
                        Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = onReload) {
                        Text(
                            "重试",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val fastScroll = rows.size >= 30
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(end = if (fastScroll) 32.dp else 0.dp),
                    state = list, contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    rows.forEach { row ->
                        if (row is NovelDirectoryRow.Volume) stickyHeader(key = row.key) {
                            val current = row.volume.id == volume.id
                            NovelDirectoryVolume(
                                row.volume, current, expanded, sections.size,
                                row.volume.id in bookmarkedVolumes,
                                onExpand = { expanded = !expanded },
                                onOpen = {
                                    if (current) onSection(NovelSection("", "卷首", 1, 0, 0, null))
                                    else onVolume(row.volume.id)
                                }
                            )
                        } else item(key = row.key) {
                            val section = (row as? NovelDirectoryRow.Chapter)?.section
                            val selected =
                                if (row is NovelDirectoryRow.Chapter) row.index == currentChapter else currentChapter < 0
                            val hasBookmark = bookmarks.any { mark ->
                                mark.chapter == volume.id && if (row is NovelDirectoryRow.Chapter) {
                                    mark.line >= row.section.start &&
                                            mark.line < (sections.getOrNull(row.index + 1)?.start
                                        ?: reading.document.lines.size)
                                } else mark.line < (sections.firstOrNull()?.start
                                    ?: reading.document.lines.size)
                            }
                            ListItem(
                                headlineContent = {
                                    Text(section?.name?.trim()?.ifBlank { "未命名章节" }
                                        ?: "卷首 / 完整正文",
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
                                },
                                leadingContent = {
                                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                        if (selected) Icon(
                                            Icons.Outlined.PlayArrow,
                                            "正在阅读",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        else Text(
                                            if (row is NovelDirectoryRow.Chapter) "${row.index + 1}" else "·",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                },
                                trailingContent = {
                                    if (hasBookmark) Icon(
                                        Icons.Filled.Bookmark,
                                        "有书签",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                modifier = Modifier
                                    .padding(
                                        start = 28.dp,
                                        end = 12.dp,
                                        top = 2.dp,
                                        bottom = 2.dp
                                    )
                                    .clip(MaterialTheme.shapes.large)
                                    .clickable(role = Role.Button) {
                                        onSection(
                                            section ?: NovelSection(
                                                "",
                                                "卷首",
                                                1,
                                                0,
                                                0,
                                                null
                                            )
                                        )
                                    },
                                colors = ListItemDefaults.colors(
                                    containerColor =
                                        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            )
                        }
                    }
                }
                if (fastScroll) NovelDirectoryFastScroller(
                    rows.size, list,
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 8.dp)
                )
            }
        } else if (tab == 1) NovelReaderBookmarks(bookmarks, reading, onBookmark, onDeleteBookmark)
        else NovelReaderAnnotations(annotations, onAnnotation, onEditAnnotation, onDeleteAnnotation)
    }
}

@Composable
private fun NovelDirectoryVolume(
    volume: NovelVolume, current: Boolean, expanded: Boolean,
    chapterCount: Int, bookmarked: Boolean, onExpand: () -> Unit, onOpen: () -> Unit
) {
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (current) IconButton(onClick = onExpand) {
                Icon(
                    if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                    if (expanded) "收起本卷章节" else "展开本卷章节"
                )
            } else Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Outlined.MenuBook,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .clickable(role = Role.Button, onClick = onOpen)
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    volume.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (current) "正在阅读 · ${chapterCount} 章" else "点击打开分卷",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (bookmarked) Icon(
                Icons.Filled.Bookmark,
                "本卷有书签",
                Modifier
                    .padding(horizontal = 12.dp)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            else Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
private fun NovelDirectoryFastScroller(
    count: Int,
    list: LazyListState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val thumbPx = with(LocalDensity.current) { 48.dp.toPx() }
    var trackPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    val listFraction by remember(list, count) {
        derivedStateOf { list.firstVisibleItemIndex.toFloat() / (count - 1).coerceAtLeast(1) }
    }

    fun scroll(value: Float) {
        dragFraction = value.coerceIn(0f, 1f)
        scrollJob?.cancel()
        scrollJob = scope.launch { list.scrollToItem(((count - 1) * dragFraction).roundToInt()) }
    }

    val travel = (trackPx - thumbPx).coerceAtLeast(0f)
    Box(
        modifier
            .width(32.dp)
            .onSizeChanged { trackPx = it.height.toFloat() }
            .pointerInput(count, thumbPx) {
                detectTapGestures { point ->
                    scroll(
                        (point.y - thumbPx / 2) / (size.height - thumbPx).coerceAtLeast(
                            1f
                        )
                    )
                }
            }
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .width(2.dp)
                .background(
                    MaterialTheme.colorScheme.outlineVariant,
                    MaterialTheme.shapes.extraLarge
                )
        )
        Surface(
            Modifier
                .align(Alignment.TopCenter)
                .offset {
                    IntOffset(
                        0,
                        (travel * if (dragging) dragFraction else listFraction).roundToInt()
                    )
                }
                .size(28.dp, 48.dp)
                .draggable(
                    state = rememberDraggableState { delta ->
                        scroll(
                            dragFraction + delta / travel.coerceAtLeast(
                                1f
                            )
                        )
                    },
                    orientation = Orientation.Vertical,
                    onDragStarted = { dragging = true; dragFraction = listFraction },
                    onDragStopped = { dragging = false }
                ),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                Icons.Outlined.DragHandle, "快速滚动目录", Modifier.padding(4.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Preview(name = "小说目录 · 手机", showBackground = true, widthDp = 412, heightDp = 720)
@Preview(name = "小说目录 · 平板侧栏", showBackground = true, widthDp = 480, heightDp = 760)
@Preview(
    name = "小说目录 · 深色",
    showBackground = true,
    widthDp = 412,
    heightDp = 720,
    uiMode = 0x20 // Android-compatible preview NIGHT_YES metadata, without an Android dependency.
)
@Composable
private fun NovelReaderDirectoryPreview() {
    val volume = previewNovelVolume()
    com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            NovelReaderDirectory(
                NovelUiState(
                    reader = volume, volumes = listOf(
                        volume.volume.copy(id = "previous", name = "序卷", index = -1),
                        volume.volume,
                        volume.volume.copy(id = "next", name = "第二卷 · 新的城市", index = 1)
                    )
                ),
                currentLine = 17,
                bookmarks = listOf(
                    LocalReadingRecord(
                        "preview",
                        "novel",
                        volume.book.path,
                        volume.volume.id,
                        volume.volume.name,
                        line = 17
                    )
                ),
                onSection = {},
                onVolume = {},
                onReload = {},
                onBookmark = {},
                onDeleteBookmark = {},
                onDismiss = {}
            )
        }
    }
}
