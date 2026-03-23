package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.data.remote.dto.ChapterDto
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDetailDto
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDto

/**
 * 漫画数据仓库接口
 */
interface MangaRepository {
    /**
     * 获取首页漫画列表
     */
    suspend fun getHomeComics(page: Int, limit: Int): Result<List<ComicDto>>
    
    /**
     * 获取漫画详情
     */
    suspend fun getComicDetail(pathWord: String): Result<ComicDetailDto>
    
    /**
     * 获取章节列表
     */
    suspend fun getChapters(pathWord: String): Result<List<ChapterDto>>
    
    /**
     * 搜索漫画
     */
    suspend fun searchComics(query: String, page: Int): Result<List<ComicDto>>
}