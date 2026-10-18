package com.rchdliang.copymanga_neo.data.download

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil

/** 所有漫画图片共用一份带宽预算；只挂起协程，不阻塞线程或累积空闲额度。 */
internal class DownloadBandwidthLimiter {
    @Volatile
    var limitKiB: Int = 0

    private val mutex = Mutex()
    private var credit = 0.0
    private var creditedRate = 0L

    /** 每个读块按实际字节付费；等待期间最多每 100ms 读取一次新设置。 */
    suspend fun consume(bytes: Int) {
        currentCoroutineContext().ensureActive()
        if (bytes <= 0 || limitKiB == 0) return
        mutex.withLock {
            val initialRate = limitKiB.toLong() * 1024L
            if (initialRate != creditedRate) credit = 0.0
            var remaining = bytes - credit
            credit = 0.0
            while (remaining > 0) {
                currentCoroutineContext().ensureActive()
                val rate = limitKiB.toLong() * 1024L
                if (rate <= 0) return
                val waitMillis = ceil(remaining * 1000.0 / rate).toLong().coerceIn(1L, 100L)
                val started = System.nanoTime()
                delay(waitMillis)
                remaining -= (System.nanoTime() - started).coerceAtLeast(0L) / 1_000_000_000.0 * rate
                creditedRate = rate
            }
            // 保留 delay 毫秒取整产生的余量，避免高速设置被每块至少 1ms 限制。
            // 不按空闲时长累积，调度延迟的余量也最多保留 100ms 或一个读块。
            credit = (-remaining).coerceIn(0.0, maxOf(8192.0, creditedRate / 10.0))
        }
    }
}
