package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.data.remote.dto.ChapterListResult
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDetailResult
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDto
import com.rchdliang.copymanga_neo.data.remote.dto.FavoritesDto
import com.rchdliang.copymanga_neo.data.remote.dto.TrialContentResult
import com.rchdliang.copymanga_neo.domain.model.ChapterContent
import com.rchdliang.copymanga_neo.domain.model.HomeComicsData

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
    suspend fun getComicDetail(pathWord: String): Result<ComicDetailResult>
    
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
    ): Result<ChapterListResult>

    /**
     * 获取漫画前五页（用于记录阅读进度）
     *
     */
    suspend fun trialChapterContent(
        comicPathWord: String,
        chapterUuid: String,
    ): Result<TrialContentResult>
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
    suspend fun searchComics(query: String, page: Int): Result<List<ComicDto>>

    /**
     * 获取书架漫画
     */
    suspend fun getFavoriteComics(offset: Int = 0, limit: Int = 21): Result<List<FavoritesDto>>
}
