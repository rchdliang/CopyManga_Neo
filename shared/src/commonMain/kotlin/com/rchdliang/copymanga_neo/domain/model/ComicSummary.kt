package com.rchdliang.copymanga_neo.domain.model

/**
 * 漫画简要信息（用于首页、搜索、推荐列表）
 */
@kotlinx.serialization.Serializable
data class ComicSummary(
    val name: String,
    val pathWord: String,
    val cover: String?,
    val author: List<Author>?,
    val popular: Int?,
    val females: List<Character>?,
    val males: List<Character>?,
    val theme: List<Theme>?,
    val authorAlias: String?,
    val lastUpdate: String? = null
)

/**
 * 作者信息
 */
@kotlinx.serialization.Serializable
data class Author(
    val name: String,
    val pathWord: String?
)

/**
 * 主题/题材信息
 */
@kotlinx.serialization.Serializable
data class Theme(
    val name: String,
    val pathWord: String?
)

/**
 * 角色信息
 */
@kotlinx.serialization.Serializable
data class Character(
    val name: String,
    val pathWord: String?
)
