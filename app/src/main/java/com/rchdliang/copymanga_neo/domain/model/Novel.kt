package com.rchdliang.copymanga_neo.domain.model

import com.rchdliang.copymanga_neo.domain.reading.NovelTextDocument

data class NovelTag(val name: String, val path: String, val count: Int? = null)
data class NovelBook(
    val path: String,
    val name: String,
    val cover: String,
    val authors: List<NovelTag>,
    val themes: List<NovelTag> = emptyList(),
    val brief: String = "",
    val status: String = "",
    val updated: String = "",
    val uuid: String = "",
    val popular: Int? = null
)

/** 分卷摘要不得冲掉同一作品已获取的详情；不同作品之间不能借用元数据。 */
fun NovelBook.withFallback(fallback: NovelBook?): NovelBook {
    if (fallback == null || fallback.path != path) return copy(name = safeBookTitle(path, name))
    return copy(
        name = safeBookTitle(path, name, fallback.name),
        cover = cover.ifBlank { fallback.cover },
        authors = authors.ifEmpty { fallback.authors },
        themes = themes.ifEmpty { fallback.themes },
        brief = brief.ifBlank { fallback.brief },
        status = status.ifBlank { fallback.status },
        updated = updated.ifBlank { fallback.updated },
        uuid = uuid.ifBlank { fallback.uuid },
        popular = popular ?: fallback.popular
    )
}

data class NovelPage(val books: List<NovelBook>, val total: Int, val offset: Int, val limit: Int) {
    val nextOffset get() = offset + books.size
    val hasMore get() = books.isNotEmpty() && nextOffset < total
}

/** 收藏记录 ID 与作品 UUID 不同，批量取消收藏必须使用前者。 */
data class NovelShelfEntry(val collectId: Int, val book: NovelBook)

data class NovelShelfPage(
    val entries: List<NovelShelfEntry>,
    val total: Int,
    val offset: Int,
    val limit: Int
) {
    val nextOffset get() = offset + entries.size
    val hasMore get() = entries.isNotEmpty() && nextOffset < total
}

data class NovelVolume(
    val id: String,
    val name: String,
    val index: Int,
    val previous: String?,
    val next: String?
)

data class NovelSection(
    val key: String,
    val name: String,
    val type: Int,
    val start: Int,
    val end: Int,
    val image: String?
)

data class NovelReadingVolume(
    val book: NovelBook,
    val volume: NovelVolume,
    val sections: List<NovelSection>,
    val document: NovelTextDocument,
    val cachedImages: Map<String, String>,
    val offlineReady: Boolean
)
