package com.rchdliang.copymanga_neo.data.download

import com.rchdliang.copymanga_neo.domain.model.ChapterInfo

enum class ChapterDownloadStatus {
    NOT_DOWNLOADED,
    WAITING,
    DOWNLOADING,
    DOWNLOADED,
    ERROR
}

data class ChapterDownloadItem(
    val chapter: ChapterInfo,
    val status: ChapterDownloadStatus = ChapterDownloadStatus.NOT_DOWNLOADED,
    val progress: Float = 0f,
    val errorMessage: String? = null
)

data class ComicDownloadTask(
    val pathWord: String,
    val name: String,
    val cover: String?,
    val chapters: List<ChapterDownloadItem>,
    val authorNames: String,
    val statusDisplay: String,
    val lastUpdate: String?,
    val brief: String?,
    val taskId: String = java.util.UUID.randomUUID().toString()
) {
    val totalChapters: Int get() = chapters.size
    val completedChapters: Int get() = chapters.count { it.status == ChapterDownloadStatus.DOWNLOADED }
    val overallProgress: Float
        get() = if (totalChapters == 0) 0f
        else completedChapters.toFloat() / totalChapters

    val currentChapterDisplayName: String?
        get() {
            val downloading =
                chapters.firstOrNull { it.status == ChapterDownloadStatus.DOWNLOADING }
            if (downloading != null) return downloading.chapter.name
            val waiting = chapters.firstOrNull { it.status == ChapterDownloadStatus.WAITING }
            return waiting?.chapter?.name
        }

    val errorCount: Int get() = chapters.count { it.status == ChapterDownloadStatus.ERROR }
}

data class DownloadProgressInfo(
    val comicPathWord: String,
    val name: String,
    val cover: String?,
    val completedChapters: Int,
    val totalChapters: Int,
    val overallProgress: Float,
    val currentChapterIndex: Int,
    val currentChapterName: String?,
    val currentChapterProgress: Float,
    val status: DownloadTaskStatus,
    /** 当前状态描述文案 */
    val statusText: String = "等待中",
    /** 错误信息（出错或风控暂停时显示） */
    val errorMessage: String? = null,
    /** 当前章节已下载图片数 */
    val downloadedImages: Int = 0,
    /** 当前章节总图片数 */
    val totalImages: Int = 0,
    /** 下载速度，单位 bytes/s */
    val speedBytesPerSecond: Long = 0L
)

enum class DownloadTaskStatus {
    IDLE,
    WAITING,
    REQUESTING_PAGE,
    DOWNLOADING,
    PAUSED,
    PAUSED_BY_RISK,
    COMPLETED,
    ERROR
}
