package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Only the reader whose route owns the current gesture moves. Its last pose survives the pop. */
internal class ReaderBackMotion(
    private val scope: CoroutineScope,
    private val currentReaderEntry: () -> String?,
) {
    var entryId by mutableStateOf<String?>(null)
        private set
    var signedProgress by mutableFloatStateOf(0f)
        private set
    var alpha by mutableFloatStateOf(1f)
        private set
    private var returnJob: Job? = null
    private var pendingCancel: (() -> Unit)? = null
    private var fadeJob: Job? = null
    private var pendingCommit: ((Boolean) -> Unit)? = null
    val isCommitting: Boolean get() = pendingCommit != null

    fun start(routeId: String?, event: UiBackEvent): Boolean {
        if (routeId == null || routeId != currentReaderEntry()) return false
        entryId = routeId
        alpha = 1f
        update(event)
        return true
    }

    fun update(event: UiBackEvent) {
        val fraction = event.progress.coerceIn(0f, 1f)
        signedProgress = if (event.fromLeft) fraction else -fraction
    }

    fun returnToReader(onReturned: () -> Unit) {
        pendingCancel = onReturned
        val duration = (100 * abs(signedProgress)).roundToInt()
        if (duration == 0) {
            finishReturn()
        } else {
            returnJob = scope.launch {
                animate(signedProgress, 0f, animationSpec = tween(duration, easing = LinearOutSlowInEasing)) { value, _ ->
                    signedProgress = value
                }
                finishReturn()
            }
        }
    }

    fun finishReturn() {
        val onReturned = pendingCancel ?: return
        pendingCancel = null
        returnJob?.cancel()
        returnJob = null
        entryId = null
        signedProgress = 0f
        onReturned()
    }

    fun fadeOnCommit(finish: (Boolean) -> Unit) {
        if (isCommitting) return
        pendingCommit = finish
        // Apply the first fade step directly to the existing layer while the destination underneath
        // is mounted. Popping here would put NavHost recomposition on the first visible frame.
        alpha = .84f
        fadeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            animate(alpha, 0f, animationSpec = tween(64, easing = LinearEasing)) { value, _ -> alpha = value }
            val commit = pendingCommit
            pendingCommit = null
            fadeJob = null
            commit?.invoke(true)
        }
    }

    fun forget(routeId: String?) {
        if (entryId != routeId) return
        finishReturn()
        val commit = pendingCommit
        pendingCommit = null
        fadeJob?.cancel()
        fadeJob = null
        entryId = null
        signedProgress = 0f
        alpha = 1f
        commit?.invoke(false)
    }
}

internal fun Modifier.readerBackTransform(motion: ReaderBackMotion): Modifier = graphicsLayer {
    val progress = motion.signedProgress
    alpha = motion.alpha
    scaleX = 1f - 0.1f * abs(progress)
    scaleY = scaleX
    translationX = minOf(size.width * 0.035f, 16.dp.toPx()) * progress
}
