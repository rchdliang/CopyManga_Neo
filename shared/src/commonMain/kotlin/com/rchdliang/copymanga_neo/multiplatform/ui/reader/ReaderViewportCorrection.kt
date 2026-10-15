package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rchdliang.copymanga_neo.domain.reading.ReadingPositionPolicy
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Separate from initial/jump restoration: an old correction cannot clear a newer owner's state. */
internal class ReaderViewportCorrectionOwner {
    private var generation = 0L
    private var activeGeneration by mutableStateOf<Long?>(null)
    val active: Boolean get() = activeGeneration != null
    fun begin(): Long = (++generation).also { activeGeneration = it }
    fun invalidate() { generation++; activeGeneration = null }
    fun owns(token: Long): Boolean = activeGeneration == token
    fun finish(token: Long) { if (owns(token)) activeGeneration = null }
}

internal data class ReaderViewportMeasurement(val width: Int, val extent: Int)

/** Every suspended layout/scroll boundary can be superseded; never commit an obsolete anchor. */
internal suspend fun correctReaderViewport(
    owner: ReaderViewportCorrectionOwner,
    fraction: Double,
    eligible: () -> Boolean,
    ensureTargetVisible: suspend () -> Unit,
    measure: () -> ReaderViewportMeasurement?,
    scroll: suspend (Int) -> Unit,
    commit: () -> Unit
): Boolean {
    val token = owner.begin()
    fun allowed() = owner.owns(token) && eligible()
    try {
        if (!allowed()) return false
        ensureTargetVisible()
        currentCoroutineContext().ensureActive()
        if (!allowed()) return false
        while (allowed()) {
            // scrollToItem forces layout. If no ready measurement exists, abort rather than wait
            // indefinitely for an offscreen item, paused lifecycle, or superseding navigation.
            val before = measure()?.takeIf { it.width > 0 && it.extent > 0 } ?: return false
            scroll(ReadingPositionPolicy.offset(fraction, before.extent))
            currentCoroutineContext().ensureActive()
            if (!allowed()) return false
            val after = measure() ?: return false
            if (after.width != before.width) continue
            commit()
            return true
        }
        return false
    } finally { owner.finish(token) }
}
