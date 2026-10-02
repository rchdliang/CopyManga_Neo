package com.rchdliang.copymanga_neo.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.readerTapGestures(
    isRtl: Boolean,
    isContinuous: Boolean,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onShowMenu: () -> Unit,
    onLongPress: (Offset) -> Unit,
    onBottomAreaTap: () -> Unit,
    onDoubleTap: (Offset) -> Unit,
    allowTap: () -> Boolean
): Modifier = composed {
    val currentForward by rememberUpdatedState(onForward)
    val currentBackward by rememberUpdatedState(onBackward)
    val currentMenu by rememberUpdatedState(onShowMenu)
    val currentLongPress by rememberUpdatedState(onLongPress)
    val currentBottomTap by rememberUpdatedState(onBottomAreaTap)
    val currentDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentAllowTap by rememberUpdatedState(allowTap)
    pointerInput(isRtl, isContinuous) {
        detectTapGestures(
            onTap = { offset ->
                if (!currentAllowTap()) return@detectTapGestures
                val tapWidth = size.width.toFloat()
                val tapHeight = size.height.toFloat()
                val edgeRatio = 0.15f
                // 底部区域占比 40%：从最底部往上的 40% 都响应底栏切换
                val bottomAreaRatio = 0.4f

                when {
                    // 左右翻页优先级高于底栏：点击右下角等角落仍触发翻页
                    offset.x < tapWidth * edgeRatio -> if (isRtl) currentForward() else currentBackward()
                    offset.x > tapWidth * (1f - edgeRatio) -> if (isRtl) currentBackward() else currentForward()
                    // 下方区域点击（底部 40%，排除左右边缘后）：切换底栏（进度条）
                    offset.y > tapHeight * (1f - bottomAreaRatio) -> currentBottomTap()
                    else -> currentMenu()
                }
            },
            onDoubleTap = { offset -> if (currentAllowTap()) currentDoubleTap(offset) },
            onLongPress = { offset -> if (currentAllowTap()) currentLongPress(offset) }
        )
    }
}
