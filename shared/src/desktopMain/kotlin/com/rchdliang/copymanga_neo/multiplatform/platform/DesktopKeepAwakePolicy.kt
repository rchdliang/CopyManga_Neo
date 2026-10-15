package com.rchdliang.copymanga_neo.multiplatform.platform

import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.awaitCancellation

internal fun desktopKeepAwakeAllowed(enabled: Boolean, state: Lifecycle.State): Boolean =
    enabled && state == Lifecycle.State.RESUMED

/** Cancellation from pause/route disposal releases the process lease even if acquisition just finished. */
internal suspend fun holdDesktopKeepAwakeLease(acquire: () -> Result<AutoCloseable>, warning: (String) -> Unit) {
    val lease = acquire().getOrElse { warning(it.message ?: "无法保持屏幕常亮"); return }
    try { awaitCancellation() }
    finally { runCatching { lease.close() }.onFailure { warning(it.message ?: "无法恢复休眠策略") } }
}
