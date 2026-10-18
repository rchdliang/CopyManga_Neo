package com.rchdliang.copymanga_neo.data.download.portable

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil
import kotlin.time.TimeSource

/** 漫画图片共享总预算；不积累空闲额度，等待期间响应动态限速和取消。 */
internal class DownloadBandwidthLimiter(private val clock: TimeSource = TimeSource.Monotonic) {
    private val setting = MutableStateFlow(0)
    var limitKiB: Int
        get() = setting.value
        set(value) {
            setting.value = value.coerceAtLeast(0)
        }
    private val mutex = Mutex()
    private var credit = 0.0
    private var creditedRate = 0L
    suspend fun consume(bytes: Int) {
        currentCoroutineContext().ensureActive()
        if (bytes <= 0) return
        mutex.withLock {
            if (limitKiB.toLong() * 1024L != creditedRate) credit = 0.0
            var remaining = bytes - credit
            credit = 0.0
            while (remaining > 0) {
                currentCoroutineContext().ensureActive()
                val rate = limitKiB.toLong() * 1024L
                if (rate <= 0) {
                    creditedRate = 0; return
                }
                val waitMillis = ceil(remaining * 1000.0 / rate).toLong().coerceIn(1L, 100L)
                val started = clock.markNow()
                delay(waitMillis)
                remaining -= started.elapsedNow().inWholeNanoseconds.coerceAtLeast(0L) / 1_000_000_000.0 * rate
                creditedRate = rate
            }
            credit = (-remaining).coerceIn(0.0, maxOf(8192.0, creditedRate / 10.0))
        }
    }
}
