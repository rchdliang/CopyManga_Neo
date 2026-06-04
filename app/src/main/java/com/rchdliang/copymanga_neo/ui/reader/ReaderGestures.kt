package com.rchdliang.copymanga_neo.ui.reader

import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val TAG = "ReaderGesture"

fun Modifier.readerOverscroll(
    pagerState: PagerState,
    thresholdPx: Float,
    coroutineScope: CoroutineScope,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit
): Modifier = this.pointerInput(pagerState) {
    Log.i(
        TAG,
        "readerOverscroll installed, thresholdPx=$thresholdPx pageCount=${pagerState.pageCount}"
    )
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        Log.i(TAG, "down, currentPage=${pagerState.currentPage}/${pagerState.pageCount}")
        var overscrollAmount = 0f
        var eventCount = 0

        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val delta = event.calculatePan().x
            val pressed = event.changes.any { it.pressed }

            val atStart = pagerState.currentPage == 0
            val atEnd = pagerState.currentPage == pagerState.pageCount - 1

            if (atStart && (delta > 0 || overscrollAmount > 0)) {
                overscrollAmount += delta
                overscrollAmount = overscrollAmount.coerceAtLeast(0f)
            } else if (atEnd && (delta < 0 || overscrollAmount < 0)) {
                overscrollAmount += delta
                overscrollAmount = overscrollAmount.coerceAtMost(0f)
            } else {
                if (overscrollAmount != 0f) Log.i(
                    TAG,
                    "  overscroll reset: amount was $overscrollAmount, atStart=$atStart atEnd=$atEnd delta=$delta"
                )
                overscrollAmount = 0f
            }

            eventCount++
            if (eventCount % 5 == 0 || abs(delta) > 1f) {
                Log.i(
                    TAG,
                    "  event #$eventCount delta=$delta overscroll=$overscrollAmount atStart=$atStart atEnd=$atEnd pressed=$pressed"
                )
            }
        } while (pressed)

        Log.i(TAG, "up: overscroll=$overscrollAmount threshold=$thresholdPx events=$eventCount")
        if (overscrollAmount > thresholdPx) {
            Log.i(TAG, "  --> TRIGGER prevChapter")
            coroutineScope.launch { onPrevChapter() }
        } else if (overscrollAmount < -thresholdPx) {
            Log.i(TAG, "  --> TRIGGER nextChapter")
            coroutineScope.launch { onNextChapter() }
        } else {
            Log.i(TAG, "  --> no trigger (below threshold)")
        }
    }
}

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
