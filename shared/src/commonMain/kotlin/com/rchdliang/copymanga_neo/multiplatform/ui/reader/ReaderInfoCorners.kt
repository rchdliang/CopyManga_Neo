package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.ui.layout.getDisplayCutoutBounds
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor

/** 同一行的左、右、居中三组信息共享可用宽度。 */
@Composable
internal fun ReaderInfoCorners(
    modifier: Modifier, top: Boolean, measureLeftFirst: Boolean, bothVisible: Boolean, centerHasNeighbors: Boolean,
    content: @Composable () -> Unit
) {
    Layout(content, modifier) { children, constraints ->
        val width = constraints.maxWidth
        val spacing = if (bothVisible) 12.dp.roundToPx() else 0
        val firstIndex = if (measureLeftFirst) 0 else 1
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        // 先量不含章节名的角落，让长标题只占用剩余宽度。
        val center = children[2].measure(loose.copy(maxWidth = if (centerHasNeighbors) width / 3 else width))
        val centered = center.width > 0
        val sideLimit = if (centered) ((width - center.width) / 2 - 12.dp.roundToPx()).coerceAtLeast(0) else width
        val first = children[firstIndex].measure(loose.copy(maxWidth = sideLimit))
        val secondLimit = if (centered) sideLimit else (width - first.width - spacing).coerceAtLeast(0)
        val second = children[1 - firstIndex].measure(loose.copy(maxWidth = secondLimit))
        val left = if (measureLeftFirst) first else second
        val right = if (measureLeftFirst) second else first
        val centerX = (width - center.width) / 2
        val height = maxOf(left.height, center.height, right.height)
        layout(width, height) {
            if (!top) {
                left.place(0, height - left.height)
                right.place(width - right.width, height - right.height)
                center.place(centerX, height - center.height)
                return@layout
            }
            val gap = 6.dp.toPx()
            val holes = getDisplayCutoutBounds().mapNotNull { rulers ->
                val rect = Rect(rulers.left.current(Float.NaN), rulers.top.current(Float.NaN),
                    rulers.right.current(Float.NaN), rulers.bottom.current(Float.NaN))
                if (rect.isFinite && !rect.isEmpty) rect.inflate(gap) else null
            }
            if (holes.isEmpty()) {
                // 系统仅提供安全边距时，保留边距避让。
                val safe = WindowInsetsRulers.DisplayCutout.current
                val xLeft = ceil(safe.left.current(-gap) + gap).toInt().coerceAtLeast(0)
                val xEnd = floor(safe.right.current(width + gap) - gap).toInt().coerceAtMost(width)
                val y = ceil(safe.top.current(-gap) + gap).toInt().coerceAtLeast(0)
                center.place(centerX.coerceIn(xLeft, (xEnd - center.width).coerceAtLeast(xLeft)), y)
                left.place(xLeft, y)
                right.place(xEnd - right.width, y)
                return@layout
            }

            // 居中信息遇到摄像头时下移，其他两组仍可使用两侧空间。
            var centerBox = Rect(centerX.toFloat(), 0f, (centerX + center.width).toFloat(), center.height.toFloat())
            for (hole in holes.sortedBy { it.top }) {
                if (centerBox.overlaps(hole)) centerBox = centerBox.translate(0f, ceil(hole.bottom) - centerBox.top)
            }
            center.place(centerX, centerBox.top.toInt())
            val obstacles = if (centered) holes + centerBox.inflate(gap) else holes
            var leftBox = left.findCornerSpace(width, true, obstacles)
            var rightBox = right.findCornerSpace(width, false, obstacles)
            if (bothVisible && leftBox.inflate(spacing.toFloat()).overlaps(rightBox)) {
                // 优先保留原本离自己角落更近的一组，再为另一组重新选位。
                if (leftBox.top < rightBox.top || (leftBox.top == rightBox.top && leftBox.left <= width - rightBox.right)) {
                    rightBox = right.findCornerSpace(width, false, obstacles + leftBox.inflate(spacing.toFloat()))
                } else {
                    leftBox = left.findCornerSpace(width, true, obstacles + rightBox.inflate(spacing.toFloat()))
                }
            }
            left.place(leftBox.left.toInt(), leftBox.top.toInt())
            right.place(rightBox.left.toInt(), rightBox.top.toInt())
        }
    }
}

private fun Placeable.findCornerSpace(rowWidth: Int, fromLeft: Boolean, obstacles: List<Rect>): Rect {
    val lastX = rowWidth - width
    val home = if (fromLeft) 0 else lastX
    fun bounds(x: Int, y: Int) = Rect(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat())
    val original = bounds(home, 0)
    if (obstacles.none(original::overlaps)) return original

    // 同行空位的起点必在某个障碍边缘，按距离本侧屏幕边缘由近到远尝试。
    val candidates = if (fromLeft) obstacles.map { ceil(it.right).toInt() }.sorted()
        else obstacles.map { floor(it.left).toInt() - width }.sortedDescending()
    for (x in candidates) {
        if (x !in 0..lastX) continue
        val candidate = bounds(x, 0)
        if (obstacles.none(candidate::overlaps)) return candidate
    }

    var below = original
    for (obstacle in obstacles.sortedBy { it.top }) {
        if (below.overlaps(obstacle)) below = bounds(home, ceil(obstacle.bottom).toInt())
    }
    return below
}
