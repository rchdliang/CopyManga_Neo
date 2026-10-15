package com.rchdliang.copymanga_neo.multiplatform.app

import kotlinx.coroutines.*

/** Call from outside the graph lifetime, after disposing UI and flushing reading records. */
internal suspend fun drainGraphLifetime(lifetime: Job, closeResources: () -> Unit) {
    withContext(NonCancellable) {
        lifetime.cancelAndJoin()
        closeResources()
    }
}
