package com.rchdliang.copymanga_neo.multiplatform.ui.components

/** Native hosts supply input; the shared dispatcher retains nested-handler ownership. */
class NativeBackBridge {
    private var dispatcher: UiBackDispatcher? = null
    private var gestureFromLeft = true
    val canHandleBack: Boolean get() = dispatcher?.hasEnabledHandlers == true
    val hasActiveGesture: Boolean get() = dispatcher?.isGestureInProgress == true

    internal fun connect(value: UiBackDispatcher) {
        disconnect()
        dispatcher = value
    }

    internal fun disconnect() {
        cancelGesture()
        dispatcher = null
    }

    fun beginGesture(progress: Float, fromLeft: Boolean = true): Boolean {
        if (!canHandleBack) return false
        gestureFromLeft = fromLeft
        dispatcher?.startGesture(UiBackEvent(normalizedBackProgress(progress), fromLeft = gestureFromLeft))
        return true
    }

    fun updateGesture(progress: Float) {
        dispatcher?.progressGesture(UiBackEvent(normalizedBackProgress(progress), fromLeft = gestureFromLeft))
    }

    fun finishGesture(progress: Float, velocity: Float) {
        if (!hasActiveGesture) return
        if (shouldCommitNativeBack(progress, velocity)) dispatcher?.commitGesture()
        else cancelGesture()
    }

    fun cancelGesture() { dispatcher?.cancelGesture() }

    fun performBack(): Boolean {
        cancelGesture()
        return dispatcher?.dispatchBack() == true
    }
}

internal fun normalizedBackProgress(progress: Float): Float =
    if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f

/** Velocity is measured in viewport widths per second; reversing the swipe cancels. */
internal fun shouldCommitNativeBack(progress: Float, velocity: Float): Boolean =
    velocity.isFinite() && velocity >= 0f &&
        (normalizedBackProgress(progress) >= .3f || (normalizedBackProgress(progress) > 0f && velocity >= .8f))
