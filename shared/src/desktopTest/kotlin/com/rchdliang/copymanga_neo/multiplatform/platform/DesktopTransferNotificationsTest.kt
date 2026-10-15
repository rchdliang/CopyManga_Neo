package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.domain.model.*
import kotlin.test.*

class DesktopTransferNotificationsTest {
    private fun download(status: DownloadTaskStatus, id: String = "task") = DownloadProgressInfo(
        comicPathWord = "book", name = "作品", cover = null, completedChapters = 1, totalChapters = 2,
        overallProgress = 0.5f, currentChapterIndex = 1, currentChapterName = "第二章", currentChapterProgress = 0f,
        status = status, taskId = id, statusText = "原始进度说明")
    @Test fun completionAndErrorMessagesAreDeduplicatedByTaskIdentity() {
        val tracker = DesktopTransferNotificationTracker()
        val completed = download(DownloadTaskStatus.COMPLETED)
        assertEquals(1, tracker.events(listOf(completed), ComicExportState()).size)
        assertTrue(tracker.events(listOf(completed), ComicExportState()).isEmpty())
        assertEquals(1, tracker.events(listOf(completed.copy(taskId = "new-task")), ComicExportState()).size)
        val error = download(DownloadTaskStatus.PAUSED_BY_RISK).copy(errorMessage = "风控已暂停")
        assertTrue(tracker.events(listOf(error), ComicExportState()).single().error)
        assertTrue(tracker.events(listOf(error), ComicExportState()).isEmpty())
    }
    @Test fun startsDoNotRepeatForEachProgressUpdate() {
        val tracker = DesktopTransferNotificationTracker()
        val active = download(DownloadTaskStatus.DOWNLOADING)
        assertEquals(1, tracker.events(listOf(active), ComicExportState()).size)
        assertTrue(tracker.events(listOf(active.copy(overallProgress = 0.8f)), ComicExportState()).isEmpty())
    }
    @Test fun partialExportIsReportedAsPartialNotUnqualifiedSuccess() {
        val tracker = DesktopTransferNotificationTracker()
        val export = ComicExportState(taskId = "export", status = ComicExportStatus.COMPLETED,
            failures = listOf(ComicExportFailure("one", "第一章", "disk full")))
        val notice = tracker.events(emptyList(), export).single()
        assertTrue(notice.error)
        assertEquals("导出部分完成", notice.title)
        assertTrue(notice.message.contains("失败 1 项"))
        assertTrue(tracker.events(emptyList(), export).isEmpty())
    }
    @Test fun tooltipContainsRealDownloadAndExportProgress() {
        val text = desktopTransferPresentation(listOf(download(DownloadTaskStatus.DOWNLOADING)),
            ComicExportState(taskId = "export", status = ComicExportStatus.RUNNING, processedBytes = 1, totalBytes = 4, current = "第二章"))
        assertTrue(text.contains("作品 50%"))
        assertTrue(text.contains("导出 25%"))
    }
}
