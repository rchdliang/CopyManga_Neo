package com.rchdliang.copymanga_neo.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.readerTapGestures(
    isRtl: Boolean,
    isContinuous: Boolean,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onShowMenu: () -> Unit,
    onLongPress: () -> Unit
): Modifier = this.pointerInput(isRtl, isContinuous) {
    detectTapGestures(
        onTap = { offset ->
            val tapWidth = size.width.toFloat()
            val tapHeight = size.height.toFloat()
            val edgeRatio = 0.15f

            when {
                offset.y > tapHeight * (1f - edgeRatio) -> onForward()
                offset.x < tapWidth * edgeRatio -> if (isRtl) onForward() else onBackward()
                offset.x > tapWidth * (1f - edgeRatio) -> if (isRtl) onBackward() else onForward()
                else -> onShowMenu()
            }
        },
        onLongPress = { onLongPress() }
    )
}
