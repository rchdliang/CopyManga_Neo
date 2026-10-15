package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** 搜索与小说列表共用接近底部加载；错误或请求中由调用方关闭触发。 */
@Composable
internal fun GridLoadMoreEffect(
    grid: LazyGridState, enabled: Boolean, itemCount: Int, onMore: () -> Unit
) {
    val currentOnMore by rememberUpdatedState(onMore)
    ActiveRouteLaunchedEffect(grid, enabled, itemCount) {
        if (enabled) snapshotFlow {
            val layout = grid.layoutInfo
            isGridNearEnd(layout.totalItemsCount, layout.visibleItemsInfo.lastOrNull()?.index ?: -1)
        }.distinctUntilChanged().collect { nearEnd -> if (nearEnd) currentOnMore() }
    }
}

internal fun isGridNearEnd(totalItems: Int, lastVisible: Int): Boolean =
    totalItems > 0 && lastVisible >= 0 && lastVisible >= totalItems - 3
