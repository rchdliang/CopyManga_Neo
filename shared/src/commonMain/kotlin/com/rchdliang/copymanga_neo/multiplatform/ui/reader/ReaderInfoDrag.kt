package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import com.rchdliang.copymanga_neo.domain.model.ReaderInfoItem

@Composable
internal fun readerInfoDragModifier(
    item: ReaderInfoItem, freeArea: () -> LayoutCoordinates?, onSelect: () -> Unit,
    onDrop: (Float, Float) -> Unit
): Modifier {
    var label by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var translation by remember { mutableStateOf(Offset.Zero) }
    val select by rememberUpdatedState(onSelect)
    val drop by rememberUpdatedState(onDrop)
    return Modifier.onGloballyPositioned { label = it }.pointerInput(item) {
        var origin = Offset.Zero
        var travel: Offset? = null
        var delta = Offset.Zero
        detectDragGestures(
            onDragStart = {
                val area = freeArea()
                val source = label
                if (area?.isAttached == true && source?.isAttached == true) {
                    select()
                    origin = area.localPositionOf(source, Offset.Zero)
                    travel = Offset((area.size.width - source.size.width).coerceAtLeast(0).toFloat(),
                        (area.size.height - source.size.height).coerceAtLeast(0).toFloat())
                    delta = Offset.Zero
                }
            },
            onDragCancel = { translation = Offset.Zero; travel = null },
            onDragEnd = {
                travel?.let { range ->
                    val end = origin + translation
                    drop(if (range.x > 0f) end.x / range.x else 0.5f, if (range.y > 0f) end.y / range.y else 0.5f)
                }
                translation = Offset.Zero
                travel = null
            }
        ) { change, amount ->
            change.consume()
            travel?.let { range ->
                delta += amount
                val next = origin + delta
                translation = Offset(next.x.coerceIn(0f, range.x), next.y.coerceIn(0f, range.y)) - origin
            }
        }
    }.graphicsLayer {
        translationX = translation.x
        translationY = translation.y
    }.clickable(onClick = onSelect)
}
