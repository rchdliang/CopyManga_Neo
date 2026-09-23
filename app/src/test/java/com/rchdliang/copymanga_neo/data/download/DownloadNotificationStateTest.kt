package com.rchdliang.copymanga_neo.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNotificationStateTest {
    private fun progress(path: String, status: DownloadTaskStatus, progress: Float = 0.5f) =
        DownloadProgressInfo(path, path, null, 1, 2, progress, 2, "第二话", 0.5f, status)

    @Test
    fun pausedQueueReleasesServiceOnlyAfterCurrentIoFinishes() {
        val items = listOf(progress("paused", DownloadTaskStatus.PAUSED))
        assertTrue(DownloadNotificationState(items, "paused").isRunning)
        assertFalse(DownloadNotificationState(items, null).isRunning)
        assertTrue(DownloadNotificationState(items, null, pendingCommands = 1).isRunning)
    }

    @Test
    fun waitingWorkKeepsServiceRunningAndCompletedHistoryDoesNotReplaceActiveProgress() {
        val state = DownloadNotificationState(
            listOf(
                progress("old", DownloadTaskStatus.COMPLETED, 1f),
                progress("next", DownloadTaskStatus.WAITING, 0f),
                progress("active", DownloadTaskStatus.DOWNLOADING, 0.75f)
            ), "active"
        )
        assertTrue(state.isRunning)
        assertEquals("active", state.primary?.comicPathWord)
        assertEquals(750, state.progress)
        assertEquals(2, state.unfinished.size)
    }

    @Test
    fun completedAndFailedQueuesDoNotKeepTheServiceAlive() {
        assertFalse(DownloadNotificationState(emptyList(), null).isRunning)
        assertFalse(
            DownloadNotificationState(
                listOf(
                    progress(
                        "done",
                        DownloadTaskStatus.COMPLETED
                    )
                ), null
            ).isRunning
        )
        assertFalse(
            DownloadNotificationState(
                listOf(progress("failed", DownloadTaskStatus.ERROR)),
                null
            ).isRunning
        )
        assertFalse(
            DownloadNotificationState(
                listOf(
                    progress(
                        "risk",
                        DownloadTaskStatus.PAUSED_BY_RISK
                    )
                ), null
            ).isRunning
        )
    }
}
