package com.rchdliang.copymanga_neo.data.download

import com.rchdliang.copymanga_neo.domain.model.BookDownloadTask
import com.rchdliang.copymanga_neo.domain.model.ChapterDownloadStatus
import com.rchdliang.copymanga_neo.domain.model.DownloadProgressInfo
import com.rchdliang.copymanga_neo.domain.model.DownloadTaskStatus

/** 章节退回 WAITING 不代表图片丢失；同一任务暂停/等待时保留有效部分进度。 */
internal fun downloadOverallProgress(
    task: BookDownloadTask,
    previous: DownloadProgressInfo?,
    status: DownloadTaskStatus,
    currentChapterProgress: Float
): Float {
    if (status in setOf(DownloadTaskStatus.WAITING_FOR_WIFI, DownloadTaskStatus.PAUSED, DownloadTaskStatus.PAUSED_BY_RISK) &&
        previous?.taskId == task.taskId && previous.totalChapters == task.totalChapters &&
        previous.completedChapters == task.completedChapters
    ) return previous.overallProgress
    if (task.totalChapters <= 0) return 0f
    val partial = if (task.items.any { it.status == ChapterDownloadStatus.DOWNLOADING }) currentChapterProgress else 0f
    return ((task.completedChapters + partial) / task.totalChapters).coerceIn(0f, 1f)
}
