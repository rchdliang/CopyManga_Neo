package com.rchdliang.copymanga_neo.data.local

import com.rchdliang.copymanga_neo.domain.model.ReadingProgress
import com.rchdliang.copymanga_neo.domain.model.ReadingProgressSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** 验证仓库实际使用的加载流，包括失败、重试与账号隔离。 */
class ReadingProgressLoaderTest {
    private val record = ReadingProgress("c", "章节", "default", 10, false)

    @Test
    fun malformedProgressJsonFinishesWithVisibleError() = runBlocking {
        val snapshots = readingProgressLoadFlow(
            "guest",
            1,
            flowOf("broken")
        ) { _, _ -> throw IllegalArgumentException("invalid JSON") }.toList()
        assertFalse(snapshots.first().isReady)
        assertTrue(snapshots.last().isReady); assertNotNull(snapshots.last().error)
    }

    @Test
    fun currentProgressLoadsForRequestedOwner() = runBlocking {
        val finished = readingProgressLoadFlow("user:a", 2, flowOf("original")) { _, owner ->
            assertEquals("user:a", owner)
            mapOf("book" to record)
        }.last()
        assertTrue(finished.isReady); assertNull(finished.error); assertEquals(
        record,
        finished.records["book"]
    )
    }

    @Test
    fun retryStartsAnotherAttemptAndClearsRecoveredFailure() = runBlocking {
        val failed = readingProgressLoadFlow(
            "guest",
            1,
            flowOf("original")
        ) { _, _ -> throw IOException() }.last()
        val recovered = readingProgressLoadFlow(
            "guest",
            2,
            flowOf("original")
        ) { _, _ -> mapOf("book" to record) }.last()
        assertNotNull(failed.error); assertNull(recovered.error); assertEquals(
        2L,
        recovered.loadVersion
    )
    }

    @Test
    fun datastoreReadFailureFinishesInsteadOfLeavingLoading() = runBlocking {
        val source = flow<String> { throw IOException("unreadable") }
        val finished = readingProgressLoadFlow("guest", 1, source) { _, _ -> emptyMap() }.last()
        assertTrue(finished.isReady); assertNotNull(finished.error)
    }

    @Test
    fun failedReadySnapshotEndsReaderAndBackupWait() = runBlocking {
        val state = MutableStateFlow(
            ReadingProgressSnapshot(
                "guest",
                isReady = true,
                error = "read failed",
                loadVersion = 3
            )
        )
        assertEquals(state.value, awaitReadingProgress(state, "guest", 3, 10))
    }

    @Test
    fun switchedOwnerOrStalledLoadHasBoundedVisibleTimeout() = runBlocking {
        val state = MutableStateFlow(ReadingProgressSnapshot("user:b"))
        val result = awaitReadingProgress(state, "guest", 4, 5)
        assertTrue(result.isReady); assertNotNull(result.error); assertEquals(
        "user:b",
        state.value.owner
    )
    }

    @Test
    fun cancellationIsNotReportedAsReadFailure() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                readingProgressLoadFlow(
                    "guest",
                    1,
                    flowOf("original")
                ) { _, _ -> throw CancellationException("owner changed") }.toList()
            }
        }
    }
}
