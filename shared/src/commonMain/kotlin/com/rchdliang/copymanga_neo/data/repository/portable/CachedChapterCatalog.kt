package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.domain.model.ChapterCatalog

/** 共用目录与 API 元数据分别计时；网页刷新不能把精简目录标记成完整 API 目录。 */
internal data class CachedChapterCatalog(
    val result: Result<ChapterCatalog>,
    val expiresAt: Long,
    val apiMetadataExpiresAt: Long? = null,
) {
    fun canReuse(now: Long, requiresApiMetadata: Boolean): Boolean =
        expiresAt > now && (!requiresApiMetadata || result.isFailure ||
            apiMetadataExpiresAt?.let { it > now } == true)

    companion object {
        fun success(
            fresh: ChapterCatalog,
            fromApi: Boolean,
            expiresAt: Long,
            previous: CachedChapterCatalog?,
        ): CachedChapterCatalog {
            // API 是完整字段来源，显式返回的 null 也应覆盖旧值，不反向借用网页摘要。
            if (fromApi) return CachedChapterCatalog(Result.success(fresh), expiresAt, expiresAt)
            val old = previous?.result?.getOrNull()
                ?: return CachedChapterCatalog(Result.success(fresh), expiresAt)

            // 只有同一组、同一作品、同一章节顺序未变化时，旧的服务端 ordered 才仍然适用。
            val sameLayout = fresh.chaptersByGroup.mapValues { (_, chapters) ->
                chapters.map { it.comicPathWord to it.uuid }
            } == old.chaptersByGroup.mapValues { (_, chapters) ->
                chapters.map { it.comicPathWord to it.uuid }
            }
            var compatible = sameLayout
            val merged = fresh.copy(chaptersByGroup = fresh.chaptersByGroup.mapValues { (group, chapters) ->
                val oldChapters = old.chaptersByGroup[group].orEmpty().associateBy { it.uuid }
                chapters.map { chapter ->
                    val prior = oldChapters[chapter.uuid]?.takeIf {
                        it.comicPathWord == chapter.comicPathWord &&
                            (chapter.comicId.isBlank() || it.comicId == chapter.comicId) &&
                            (chapter.groupId == null || it.groupId == chapter.groupId)
                    }
                    if (prior == null) {
                        compatible = false
                        chapter
                    } else chapter.copy(
                        size = chapter.size ?: prior.size,
                        news = chapter.news ?: prior.news,
                        datetimeCreated = chapter.datetimeCreated ?: prior.datetimeCreated,
                        comicId = chapter.comicId.ifBlank { prior.comicId },
                        groupId = chapter.groupId ?: prior.groupId,
                        ordered = if (sameLayout && previous.apiMetadataExpiresAt != null)
                            prior.ordered else chapter.ordered,
                    )
                    // 名称、type、index、count、上下章和章节增删均以新目录为准。
                }
            })
            return CachedChapterCatalog(Result.success(merged), expiresAt,
                previous.apiMetadataExpiresAt.takeIf { compatible })
        }
    }
}
