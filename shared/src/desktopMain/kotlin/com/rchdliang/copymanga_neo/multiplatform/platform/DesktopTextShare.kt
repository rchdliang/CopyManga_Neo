package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.domain.model.UserActionCancelled
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** The Swing modal loop can outlive its caller. Recheck ownership before publishing text. */
internal suspend fun performDesktopTextShare(
    mailAvailable: Boolean,
    choose: () -> Int,
    copy: () -> Unit,
    mail: () -> Unit,
) {
    currentCoroutineContext().ensureActive()
    val selected = choose()
    currentCoroutineContext().ensureActive()
    when (selected) {
        0 -> copy()
        1 -> if (mailAvailable) mail() else throw UserActionCancelled()
        else -> throw UserActionCancelled()
    }
}
