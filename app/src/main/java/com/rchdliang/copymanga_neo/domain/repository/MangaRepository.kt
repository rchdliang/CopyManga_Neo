package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.ChapterContent
import com.rchdliang.copymanga_neo.domain.model.ChapterList
import com.rchdliang.copymanga_neo.domain.model.ComicDetail
import com.rchdliang.copymanga_neo.domain.model.ComicPage
import com.rchdliang.copymanga_neo.domain.model.ComicStatus
import com.rchdliang.copymanga_neo.domain.model.ComicSummary
import com.rchdliang.copymanga_neo.domain.model.FavoriteItem
import com.rchdliang.copymanga_neo.domain.model.HomeComicsData
import com.rchdliang.copymanga_neo.domain.model.QueryType
import com.rchdliang.copymanga_neo.domain.model.TrialContent

/**
 * 漫画数据仓库接口
 */
interface MangaRepository {
    /**
     * 获取首页漫画数据
     */
    suspend fun getHomeComics(): Result<HomeComicsData>
    
    /**
     * 获取漫画详情
     */
    suspend fun getComicDetail(pathWord: String): Result<ComicDetail>
    
    /**
     * 获取章节列表（单页）
     * @param pathWord 漫画路径标识
     * @param group 分组（不同汉化组）
     * @param offset 偏移量
     * @param limit 每页数量
     */
    suspend fun getChapters(
        pathWord: String,
        group: String = "default",
        offset: Int = 0,
        limit: Int = 20
    ): Result<ChapterList>

    /**
     * 获取漫画前五页（用于记录阅读进度）
     *
     */
    suspend fun trialChapterContent(
        comicPathWord: String,
        chapterUuid: String,
    ): Result<TrialContent>
    /**
     * 获取章节具体内容（图片列表）
     * @param group 汉化组 pathWord
     */
    suspend fun getChapterContent(
        comicPathWord: String,
        chapterUuid: String,
        group: String = "default"
    ): Result<ChapterContent>

    /**
     * 搜索漫画
     */
    suspend fun searchComics(
        query: String,
        page: Int,
        queryType: QueryType? = null
    ): Result<List<ComicSummary>>

    /**
     * 获取推荐漫画
     */
    suspend fun getRecComics(limit: Int = 10, offset: Int = 0): Result<List<ComicSummary>>

    /** 按题材标识筛选漫画。 */
    suspend fun getThemeComics(theme: String, offset: Int = 0): Result<ComicPage>

    /**
     * 获取书架漫画
     */
    suspend fun getFavoriteComics(offset: Int = 0, limit: Int = 21): Result<List<FavoriteItem>>

    /**
     * 查询漫画收藏状态和阅读进度
     */
    suspend fun queryComicStatus(pathWord: String): Result<ComicStatus>

    /**
     * 添加漫画到书架
     * @param comicId 漫画 String 类型 UUID
     */
    suspend fun addFavorite(comicId: String): Result<Unit>

    /**
     * 从书架移除漫画
     * @param collectId 收藏的 Int 类型 ID
     */
    suspend fun removeFavorite(collectId: Int): Result<Unit>

    /**
     * 从书架批量移除漫画
     * @param collectIds 要移除的收藏 Int 类型 ID 列表
     */
    suspend fun removeFavorites(collectIds: List<Int>): Result<Unit>
}
