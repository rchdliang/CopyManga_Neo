package com.rchdliang.copymanga_neo.domain.model

enum class ComicExportFormat(val label: String, val extension: String, val mimeType: String) {
    ZIP("ZIP 压缩包", "zip", "application/zip"),
    CBZ("CBZ 漫画文件（每章一个）", "cbz", "application/vnd.comicbook+zip"),
    IMAGES("原图文件夹", "", "vnd.android.document/directory")
}

data class ComicExportRequest(val comics: List<String>, val chapters: Set<String>? = null)
data class ComicExportPage(val source: String, val size: Long, val extension: String)
data class ComicExportChapter(
    val uuid: String, val name: String, val index: Int, val group: String,
    val pages: List<ComicExportPage>
)

data class ComicExportComic(
    val pathWord: String, val name: String, val author: String,
    val chapters: List<ComicExportChapter>
)

data class ComicExportPlan(
    val comics: List<ComicExportComic>,
    val skipped: List<String> = emptyList()
) {
    val chapterCount get() = comics.sumOf { it.chapters.size }
    val pageCount get() = comics.sumOf { comic -> comic.chapters.sumOf { it.pages.size } }
    val bytes get() = comics.sumOf { comic -> comic.chapters.sumOf { chapter -> chapter.pages.sumOf { it.size } } }
}

enum class ComicExportStatus { IDLE, RUNNING, COMPLETED, CANCELLED, INTERRUPTED }
data class ComicExportOutput(
    val id: String,
    val uri: String,
    val name: String,
    val mimeType: String
)

data class ComicExportFailure(val id: String, val name: String, val message: String)
data class ComicExportDraft(
    val plan: ComicExportPlan,
    val outputs: List<ComicExportOutput> = emptyList()
)

/** 系统保存位置选择器所需的信息，不暴露归档内部的输出单元。 */
data class ComicExportLocation(val directory: Boolean, val name: String, val mimeType: String)
data class ComicExportState(
    val taskId: String? = null,
    val status: ComicExportStatus = ComicExportStatus.IDLE,
    val plan: ComicExportPlan? = null,
    val format: ComicExportFormat = ComicExportFormat.ZIP,
    val destination: String = "",
    val directory: Boolean = false,
    val current: String = "",
    val processedBytes: Long = 0,
    val totalBytes: Long = 0,
    val outputs: List<ComicExportOutput> = emptyList(),
    val failures: List<ComicExportFailure> = emptyList(),
    val activeUri: String? = null,
    val cleanupWarning: String? = null
) {
    val running get() = status == ComicExportStatus.RUNNING
    val progress
        get() = if (totalBytes > 0) (processedBytes.toDouble() / totalBytes).toFloat()
            .coerceIn(0f, 1f) else 0f
}
