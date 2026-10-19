package com.rchdliang.copymanga_neo.data.remote

import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import com.rchdliang.copymanga_neo.domain.model.ThemeLoadingPolicy
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.milliseconds

/** 标签补全共用的并发与起始间隔；锁只保护调度状态，不覆盖网络等待。 */
internal class ThemeRequestScheduler(
    private val settings: Flow<NetworkSettings>,
    private val currentSettings: () -> NetworkSettings,
    private val nowMillis: () -> Long = defaultClock()
) {
    private class KeyLock(val mutex: Mutex = Mutex(), var users: Int = 0)

    private val mutex = Mutex()
    private val keys = mutableMapOf<String, KeyLock>()
    private val queue = ArrayDeque<Any>()
    private val changed = MutableStateFlow(0L)
    private var active = 0
    private var lastStarted: Long? = null

    /** 同一漫画只允许一个刷新者；后来的调用在锁内重新检查缓存和退避时间。 */
    suspend fun <T> withKey(key: String, block: suspend () -> T): T {
        val entry = mutex.withLock { keys.getOrPut(key) { KeyLock() }.also { it.users++ } }
        try {
            return entry.mutex.withLock { block() }
        } finally {
            withContext(NonCancellable) {
                mutex.withLock { if (--entry.users == 0) keys.remove(key) }
            }
        }
    }

    suspend fun <T> request(priority: Boolean = false, block: suspend () -> T): T {
        val ticket = Any()
        var acquired = false
        mutex.withLock {
            if (priority) queue.addFirst(ticket) else queue.addLast(ticket)
            changed.update { it + 1 }
        }
        try {
            while (!acquired) {
                currentCoroutineContext().ensureActive()
                val revision = changed.value
                val config = currentSettings()
                val limits = limits(config)
                val wait = mutex.withLock {
                    if (queue.firstOrNull() !== ticket || active >= limits.first) -1L
                    else {
                        val remaining =
                            lastStarted?.let { (it + limits.second - nowMillis()).coerceAtLeast(0) }
                                ?: 0L
                        if (remaining == 0L) {
                            queue.removeFirst()
                            active++
                            lastStarted = nowMillis()
                            acquired = true
                            changed.update { it + 1 }
                        }
                        remaining
                    }
                }
                if (!acquired) {
                    suspend fun awaitChange() {
                        combine(changed, settings) { version, value -> version to limits(value) }
                            .first { (version, value) -> version != revision || value != limits }
                    }
                    if (wait < 0) awaitChange()
                    else withTimeoutOrNull(wait.coerceAtLeast(1).milliseconds) { awaitChange() }
                }
            }
            currentCoroutineContext().ensureActive()
            return block()
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    if (acquired) active-- else queue.remove(ticket)
                    changed.update { it + 1 }
                }
            }
        }
    }

    private fun limits(value: NetworkSettings) =
        value.themeFetchConcurrency.coerceIn(1, ThemeLoadingPolicy.MAX_CONCURRENCY) to
                value.themeFetchIntervalMillis.coerceIn(0, ThemeLoadingPolicy.MAX_INTERVAL_MILLIS)

    companion object {
        private fun defaultClock(): () -> Long {
            val origin = TimeSource.Monotonic.markNow()
            return { origin.elapsedNow().inWholeMilliseconds }
        }
    }
}
