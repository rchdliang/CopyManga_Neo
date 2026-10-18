package com.rchdliang.copymanga_neo.data.download.portable

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadBandwidthLimiterTest {
    @Test fun concurrentImagesShareOneBudgetWithoutIdleCredit() = runTest {
        val limiter = DownloadBandwidthLimiter(testScheduler.timeSource)
        limiter.limitKiB = 1
        listOf(async { limiter.consume(1024) }, async { limiter.consume(1024) }).awaitAll()
        // delay 按整毫秒调度，允许两次消费累计 2ms 的取整误差。
        assertTrue(currentTime in 2000L..2002L)
        advanceTimeBy(10_000)
        val resumedAt = currentTime
        limiter.consume(1024)
        assertTrue(currentTime - resumedAt in 999L..1002L)
    }
    @Test fun changingLimitAndCancellingWakeTheWaiters() = runTest {
        val limiter = DownloadBandwidthLimiter(testScheduler.timeSource)
        limiter.limitKiB = 1
        val first = async { limiter.consume(8192) }
        runCurrent(); advanceTimeBy(100)
        limiter.limitKiB = 0
        first.await()
        assertTrue(currentTime <= 200)
        limiter.limitKiB = 1
        val cancelled = async { limiter.consume(8192) }
        runCurrent(); cancelled.cancelAndJoin()
        limiter.limitKiB = 0
        limiter.consume(8192)
        assertTrue(cancelled.isCancelled)
    }
}
