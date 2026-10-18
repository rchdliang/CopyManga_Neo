package com.rchdliang.copymanga_neo.multiplatform

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** 只有 Service 成功调用 startForeground 后，任务才允许进行 I/O。 */
class ForegroundServiceGate(
    private val start: () -> Unit,
    private val timeoutMillis: Long = 10_000
) {
    private val lock = Any()
    private var ready = CompletableDeferred<Unit>()
    private var owner: Any? = null
    suspend fun awaitReady() {
        val expected = synchronized(lock) { ready }
        start()
        check(withTimeoutOrNull(timeoutMillis) { expected.await(); true } == true) { "后台服务启动超时，请返回应用重试" }
    }

    fun started(service: Any) = synchronized(lock) { owner = service; ready.complete(Unit); Unit }
    fun stopped(service: Any) = synchronized(lock) {
        if (owner !== service) return@synchronized
        owner = null
        if (!ready.isCompleted) ready.completeExceptionally(IllegalStateException("后台服务未能启动"))
        ready = CompletableDeferred()
    }

    fun failed() = synchronized(lock) {
        if (owner == null) {
            ready.completeExceptionally(IllegalStateException("后台服务未能启动"))
            ready = CompletableDeferred()
        }
    }
}
