package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 普通列表统一补齐鼠标拖动；触摸、滚轮、选字和子控件仍交给 Compose 原有处理。 */
@Composable
internal fun MouseDragLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (reverseLayout) Arrangement.Bottom else Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit
) = LazyColumn(
    modifier = modifier.mouseDragScroll(state, Orientation.Vertical, userScrollEnabled, reverseLayout, flingBehavior),
    state = state, contentPadding = contentPadding, reverseLayout = reverseLayout,
    verticalArrangement = verticalArrangement, horizontalAlignment = horizontalAlignment,
    flingBehavior = flingBehavior, userScrollEnabled = userScrollEnabled, content = content
)

/** 横向书单和筛选条与纵向列表采用相同的鼠标拖动约定。 */
@Composable
internal fun MouseDragLazyRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    horizontalArrangement: Arrangement.Horizontal = if (reverseLayout) Arrangement.End else Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit
) = LazyRow(
    modifier = modifier.mouseDragScroll(state, Orientation.Horizontal, userScrollEnabled, reverseLayout, flingBehavior),
    state = state, contentPadding = contentPadding, reverseLayout = reverseLayout,
    horizontalArrangement = horizontalArrangement, verticalAlignment = verticalAlignment,
    flingBehavior = flingBehavior, userScrollEnabled = userScrollEnabled, content = content
)

/** 详情目录、搜索结果和书架共用的网格滚动入口。 */
@Composable
internal fun MouseDragLazyVerticalGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (reverseLayout) Arrangement.Bottom else Arrangement.Top,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyGridScope.() -> Unit
) = LazyVerticalGrid(
    columns = columns,
    modifier = modifier.mouseDragScroll(state, Orientation.Vertical, userScrollEnabled, reverseLayout, flingBehavior),
    state = state, contentPadding = contentPadding, reverseLayout = reverseLayout,
    verticalArrangement = verticalArrangement, horizontalArrangement = horizontalArrangement,
    flingBehavior = flingBehavior, userScrollEnabled = userScrollEnabled, content = content
)

/** 表单、说明和弹层复用同一个 ScrollState，避免鼠标与触摸进度不一致。 */
internal fun Modifier.verticalScrollWithMouse(
    state: ScrollState,
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false
): Modifier = verticalScroll(state, enabled, flingBehavior, reverseScrolling)
    .mouseDragScroll(state, Orientation.Vertical, enabled, reverseScrolling, flingBehavior)

internal fun Modifier.horizontalScrollWithMouse(
    state: ScrollState,
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false
): Modifier = horizontalScroll(state, enabled, flingBehavior, reverseScrolling)
    .mouseDragScroll(state, Orientation.Horizontal, enabled, reverseScrolling, flingBehavior)
