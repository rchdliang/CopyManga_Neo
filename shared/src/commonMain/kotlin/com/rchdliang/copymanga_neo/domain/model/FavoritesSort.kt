package com.rchdliang.copymanga_neo.domain.model

/** 云端书架排序；保留完整参数以兼容已保存的排序偏好。 */
enum class FavoritesSort(val ordering: String, val label: String) {
    ADDED("-datetime_modifier", "添加时间"),
    UPDATED("-datetime_updated", "更新时间"),
    READ("-datetime_browse", "阅读时间"),
    ADDED_ASC("datetime_modifier", "添加时间"),
    UPDATED_ASC("datetime_updated", "更新时间"),
    READ_ASC("datetime_browse", "阅读时间");

    val descending: Boolean get() = ordering.startsWith('-')

    fun withDescending(descending: Boolean): FavoritesSort =
        fromOrdering((if (descending) "-" else "") + ordering.removePrefix("-"))

    companion object {
        fun fromOrdering(value: String): FavoritesSort = entries.firstOrNull { it.ordering == value } ?: ADDED
    }
}
