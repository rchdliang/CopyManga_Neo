package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Measure the real controls (including enlarged labels) before spending space on decoration.
 * The Material rail's fixed header gap otherwise puts destinations outside a short window.
 * Keep one bounded scroll viewport for both header and destinations; never shrink touch targets.
 */
@Composable
internal fun AdaptiveNavigationRailColumn(
    expanded: Boolean,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.widthIn(max = 220.dp).fillMaxWidth().fillMaxHeight()) {
        val viewportHeight = with(LocalDensity.current) { maxHeight.roundToPx() }
        Layout(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            content = {
                Box { header() }
                content()
            },
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val children = measurables.mapIndexed { index, measurable ->
                measurable.measure(
                    constraints.copy(
                        minWidth = 0,
                        minHeight = if (index == 0) 0 else (if (expanded) 48.dp else 64.dp).roundToPx(),
                        maxHeight = Constraints.Infinity,
                    )
                )
            }
            val itemGaps = (children.size - 2).coerceAtLeast(0)
            val minimumPadding = 8.dp.roundToPx()
            val topExtra = 52.dp.roundToPx()
            val headerExtra = 32.dp.roundToPx()
            val bottomExtra = 36.dp.roundToPx()
            val gapExtra = if (expanded) 0 else 4.dp.roundToPx()
            val controlsHeight = children.sumOf { it.height }
            val extraBudget = (viewportHeight - controlsHeight - minimumPadding * 3).coerceAtLeast(0)
            val desiredExtra = topExtra + headerExtra + bottomExtra + gapExtra * itemGaps
            val fraction = (extraBudget.toFloat() / desiredExtra.coerceAtLeast(1)).coerceIn(0f, 1f)
            val top = minimumPadding + (topExtra * fraction).roundToInt()
            val headerGap = minimumPadding + (headerExtra * fraction).roundToInt()
            val bottom = minimumPadding + (bottomExtra * fraction).roundToInt()
            val itemGap = (gapExtra * fraction).roundToInt()
            val height = (top + controlsHeight + headerGap + bottom + itemGap * itemGaps)
                .coerceAtLeast(viewportHeight)
            layout(width, height) {
                var y = top
                children.forEachIndexed { index, child ->
                    child.placeRelative(if (index == 0) 0 else (width - child.width) / 2, y)
                    y += child.height + if (index == 0) headerGap else itemGap
                }
            }
        }
    }
}
