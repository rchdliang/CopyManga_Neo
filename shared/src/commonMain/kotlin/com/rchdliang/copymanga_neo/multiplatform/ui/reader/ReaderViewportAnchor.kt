package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import com.rchdliang.copymanga_neo.domain.reading.ReadingPositionPolicy

internal data class ReaderViewportSample(val key: String, val offset: Int, val extent: Int)
internal data class ReaderViewportCorrection(val key: String, val fraction: Double)

/** Remembers the pre-reflow measurement, independently of progress reporting's mutable anchor. */
internal class ReaderViewportAnchor {
    private var width = 0
    private var sample: ReaderViewportSample? = null

    fun invalidate() { width = 0; sample = null }

    fun observe(viewportWidth: Int, current: ReaderViewportSample?, eligible: Boolean): ReaderViewportCorrection? {
        if (!eligible || viewportWidth <= 0) { invalidate(); return null }
        val old = sample
        val changedWidth = width > 0 && width != viewportWidth
        width = viewportWidth
        sample = current?.takeIf { it.extent > 0 && it.offset >= 0 }
        return if (changedWidth && old != null)
            ReaderViewportCorrection(old.key, ReadingPositionPolicy.fraction(old.offset, old.extent)) else null
    }
}

internal fun readerViewportResizeAllowed(
    desktopContinuous: Boolean,
    progressAllowed: Boolean,
    scrolling: Boolean,
    pendingJump: Boolean,
    interactionBusy: Boolean
): Boolean = desktopContinuous && progressAllowed && !scrolling && !pendingJump && !interactionBusy
