package com.rchdliang.copymanga_neo.domain.model

/** 展示字段长期保留；25 小时只决定动态详情何时允许再次补全。 */
data class ComicCardMetadata(
    val pathWord: String,
    val themes: List<Theme>?,
    val brief: String?,
    val popular: Int?,
    val lastUpdate: String?,
    val fetchedAt: Long
) {
    fun isFresh(now: Long): Boolean = now - fetchedAt in 0 until CACHE_MILLIS

    companion object {
        const val CACHE_MILLIS = 25L * 60 * 60 * 1000
    }
}

enum class ComicCoverVisibility { PENDING, VISIBLE, BLURRED }

/** 未确认的题材不放行；详情确认的空题材是有效结果。 */
fun comicCoverVisibility(
    ready: Boolean, blocked: List<Theme>, metadata: ComicCardMetadata?
): ComicCoverVisibility = when {
    !ready -> ComicCoverVisibility.PENDING
    blocked.isEmpty() -> ComicCoverVisibility.VISIBLE
    metadata?.themes == null -> ComicCoverVisibility.PENDING
    metadata.themes.any { blocked.blocks(it) } -> ComicCoverVisibility.BLURRED
    else -> ComicCoverVisibility.VISIBLE
}
