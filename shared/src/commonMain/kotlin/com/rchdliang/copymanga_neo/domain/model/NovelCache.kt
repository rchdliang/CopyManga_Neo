package com.rchdliang.copymanga_neo.domain.model

@kotlinx.serialization.Serializable
enum class NovelCacheStage { TEXT, IMAGES }

/** 当前卷实际写入的进度；缓存中已存在的插图也计入完成数。 */
@kotlinx.serialization.Serializable
data class NovelCacheProgress(
    val stage: NovelCacheStage,
    val completedImages: Int = 0,
    val totalImages: Int = 0,
    val bytes: Long = 0L
)

/** 经过正文及插图摘要校验的本地卷，账号和请求模式是缓存身份的一部分。 */
@kotlinx.serialization.Serializable
data class NovelCachedVolume(
    val mode: RequestMode,
    val owner: String,
    val book: NovelBook,
    val volume: NovelVolume,
    val bytes: Long,
    val cachedImages: Int,
    val totalImages: Int,
    val lastAccessedAt: Long,
    /** Only an explicit download request makes a reading cache appear in Downloads. */
    val downloadRequested: Boolean = false
) {
    val visibleInDownloads: Boolean get() = downloadRequested
    val offlineReady: Boolean get() = cachedImages == totalImages
}

@kotlinx.serialization.Serializable
data class NovelCacheUsage(val bytes: Long, val volumes: Int)
