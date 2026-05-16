package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.data.remote.dto.ChapterDto
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDetailResult
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDto
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
     * 获取章节列表
     */
    suspend fun getChapters(pathWord: String): Result<List<ChapterDto>>
    
    /**
     * 搜索漫画
     */
    suspend fun searchComics(query: String, page: Int): Result<List<ComicDto>>
}