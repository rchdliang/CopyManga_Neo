package com.rchdliang.copymanga_neo.domain.model

@kotlinx.serialization.Serializable
enum class DownloadKind { COMIC, NOVEL }

fun comicDownloadKey(path: String) = "comic:${path.length}:$path"
/** 下载内容按作品共享；mode/owner 仅保留调用方的请求来源信息。 */
fun novelDownloadKey(mode: RequestMode, owner: String, path: String) =
    "novel:${path.length}:$path"

@kotlinx.serialization.Serializable
enum class ChapterDownloadStatus { NOT_DOWNLOADED, WAITING, DOWNLOADING, DOWNLOADED, ERROR }

/** 调度只关心可下载单元；漫画章节和小说分卷各自保留业务模型。 */
@kotlinx.serialization.Serializable
sealed interface BookDownloadItem {
    val id: String
    val name: String
    val status: ChapterDownloadStatus
    val progress: Float
    val errorMessage: String?
    fun withState(
        status: ChapterDownloadStatus,
        progress: Float = 0f,
        errorMessage: String? = null
    ): BookDownloadItem
}

@kotlinx.serialization.Serializable
data class ChapterDownloadItem(
    val chapter: ChapterInfo,
    override val status: ChapterDownloadStatus = ChapterDownloadStatus.NOT_DOWNLOADED,
    override val progress: Float = 0f,
    override val errorMessage: String? = null
) : BookDownloadItem {
    override val id get() = chapter.uuid
    override val name get() = chapter.name
    override fun withState(status: ChapterDownloadStatus, progress: Float, errorMessage: String?) =
        copy(status = status, progress = progress, errorMessage = errorMessage)
}

@kotlinx.serialization.Serializable
data class NovelDownloadItem(
    val volume: NovelVolume,
    override val status: ChapterDownloadStatus = ChapterDownloadStatus.WAITING,
    override val progress: Float = 0f,
    override val errorMessage: String? = null
) : BookDownloadItem {
    override val id get() = volume.id
    override val name get() = volume.name
    override fun withState(status: ChapterDownloadStatus, progress: Float, errorMessage: String?) =
        copy(status = status, progress = progress, errorMessage = errorMessage)
}

@kotlinx.serialization.Serializable
sealed interface BookDownloadTask {
    val taskKey: String
    val kind: DownloadKind
    val pathWord: String
    val name: String
    val cover: String?
    val items: List<BookDownloadItem>
    val taskId: String
    fun withItems(items: List<BookDownloadItem>): BookDownloadTask
    val totalChapters: Int get() = items.size
    val completedChapters: Int get() = items.count { it.status == ChapterDownloadStatus.DOWNLOADED }
    val overallProgress: Float get() = if (items.isEmpty()) 0f else completedChapters.toFloat() / items.size
    val currentChapterDisplayName: String?
        get() =
            items.firstOrNull { it.status == ChapterDownloadStatus.DOWNLOADING }?.name
                ?: items.firstOrNull { it.status == ChapterDownloadStatus.WAITING }?.name
    val errorCount: Int get() = items.count { it.status == ChapterDownloadStatus.ERROR }
}

/** 同一持久任务追加单元时保留已有完成结果，只重试本次明确选中的失败项。 */
fun BookDownloadTask.mergeRequest(request: BookDownloadTask): BookDownloadTask {
    require(taskKey == request.taskKey && kind == request.kind) { "不能合并不同类型或作品的下载任务" }
    val requested = request.items.map { it.id }.toSet()
    val known = items.map { it.id }.toSet()
    return withItems(items.map {
        if (it.id in requested && it.status == ChapterDownloadStatus.ERROR)
            it.withState(ChapterDownloadStatus.WAITING) else it
    } + request.items.filterNot { it.id in known }.distinctBy { it.id })
}

fun NovelDownloadTask.matches(session: LocalReadingSession?): Boolean =
    session?.owner != null

@kotlinx.serialization.Serializable
data class ComicDownloadTask(
    override val pathWord: String,
    override val name: String,
    override val cover: String?,
    val chapters: List<ChapterDownloadItem>,
    val authorNames: String,
    val statusDisplay: String,
    val lastUpdate: String?,
    val brief: String?,
    override val taskId: String = kotlin.uuid.Uuid.random().toString()
) : BookDownloadTask {
    override val taskKey get() = comicDownloadKey(pathWord)
    override val kind get() = DownloadKind.COMIC
    override val items get() = chapters
    override fun withItems(items: List<BookDownloadItem>) =
        copy(chapters = items.map { it as ChapterDownloadItem })
}

@kotlinx.serialization.Serializable
data class NovelDownloadTask(
    val mode: RequestMode,
    val owner: String,
    val book: NovelBook,
    val volumes: List<NovelDownloadItem>,
    override val taskId: String = kotlin.uuid.Uuid.random().toString()
) : BookDownloadTask {
    override val taskKey get() = novelDownloadKey(mode, owner, book.path)
    override val kind get() = DownloadKind.NOVEL
    override val pathWord get() = book.path
    override val name get() = book.name
    override val cover get() = book.cover
    override val items get() = volumes
    override fun withItems(items: List<BookDownloadItem>) =
        copy(volumes = items.map { it as NovelDownloadItem })
}

@kotlinx.serialization.Serializable
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
    val statusText: String = "等待中",
    val errorMessage: String? = null,
    val downloadedImages: Int = 0,
    val totalImages: Int = 0,
    val speedBytesPerSecond: Long = 0L,
    val taskId: String = "",
    val kind: DownloadKind = DownloadKind.COMIC,
    val taskKey: String = comicDownloadKey(comicPathWord),
    val owner: String? = null,
    val mode: RequestMode? = null
)

@kotlinx.serialization.Serializable
enum class DownloadTaskStatus {
    IDLE, WAITING, WAITING_FOR_WIFI, REQUESTING_PAGE, DOWNLOADING,
    PAUSED, PAUSED_BY_RISK, COMPLETED, ERROR
}
