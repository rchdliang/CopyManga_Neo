package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.domain.model.*
import java.awt.Taskbar
import kotlin.test.*

class DesktopTaskbarProgressTest {
    private fun download(progress: Float = 0.5f, status: DownloadTaskStatus = DownloadTaskStatus.DOWNLOADING) = DownloadProgressInfo(
        comicPathWord = "book", name = "作品", cover = null, completedChapters = 1, totalChapters = 2,
        overallProgress = progress, currentChapterIndex = 1, currentChapterName = null,
        currentChapterProgress = 0f, status = status)

    @Test fun idleAndCompletedTransfersClearTheBar() {
        assertEquals(DesktopTaskbarPresentation(Taskbar.State.OFF), desktopTaskbarPresentation(emptyList(), ComicExportState()))
        assertEquals(Taskbar.State.OFF, desktopTaskbarPresentation(listOf(download(1f, DownloadTaskStatus.COMPLETED)),
            ComicExportState(status = ComicExportStatus.COMPLETED)).state)
    }

    @Test fun simultaneousDownloadsAndExportContributeToProgress() {
        val presentation = desktopTaskbarPresentation(listOf(download(0.25f), download(0.5f)),
            ComicExportState(status = ComicExportStatus.RUNNING, processedBytes = 75, totalBytes = 100))
        assertEquals(DesktopTaskbarPresentation(Taskbar.State.NORMAL, 50), presentation)
    }

    @Test fun preparingTransfersUseIndeterminateProgress() {
        assertEquals(Taskbar.State.INDETERMINATE, desktopTaskbarPresentation(
            listOf(download(status = DownloadTaskStatus.REQUESTING_PAGE).copy(totalChapters = 0)), ComicExportState()).state)
        assertEquals(Taskbar.State.INDETERMINATE, desktopTaskbarPresentation(emptyList(),
            ComicExportState(status = ComicExportStatus.RUNNING)).state)
    }

    @Test fun pauseErrorAndResumeChangeTheNativeState() {
        assertEquals(DesktopTaskbarPresentation(Taskbar.State.PAUSED, 50), desktopTaskbarPresentation(
            listOf(download(status = DownloadTaskStatus.PAUSED)), ComicExportState()))
        assertEquals(Taskbar.State.PAUSED, desktopTaskbarPresentation(
            listOf(download(status = DownloadTaskStatus.WAITING_FOR_WIFI)), ComicExportState()).state)
        assertEquals(Taskbar.State.ERROR, desktopTaskbarPresentation(
            listOf(download(status = DownloadTaskStatus.PAUSED_BY_RISK)), ComicExportState()).state)
        assertEquals(Taskbar.State.ERROR, desktopTaskbarPresentation(
            listOf(download(status = DownloadTaskStatus.ERROR)), ComicExportState()).state)
        assertEquals(Taskbar.State.NORMAL, desktopTaskbarPresentation(listOf(download()), ComicExportState()).state)
    }

    @Test fun invalidFractionsCannotEscapeTheNativeRange() {
        assertEquals(100, desktopTaskbarPresentation(listOf(download(2f)), ComicExportState()).percent)
        assertEquals(0, desktopTaskbarPresentation(listOf(download(Float.NaN)), ComicExportState()).percent)
    }
}
