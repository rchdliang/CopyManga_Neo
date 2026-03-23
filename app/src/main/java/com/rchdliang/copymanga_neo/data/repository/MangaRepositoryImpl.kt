package com.rchdliang.copymanga_neo.data.repository

import com.rchdliang.copymanga_neo.data.remote.api.MangaApiService
import com.rchdliang.copymanga_neo.data.remote.dto.ChapterDto
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDetailDto
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDto
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 漫画数据仓库实现
 */
@Singleton
class MangaRepositoryImpl @Inject constructor(
    private val apiService: MangaApiService
) : MangaRepository {
    
    override suspend fun getHomeComics(page: Int, limit: Int): Result<List<ComicDto>> {
        return try {
            val response = apiService.getHomeComics(page, limit)
            if (response.isSuccessful && response.body()?.code == 200) {
                Result.success(response.body()?.results?.list ?: emptyList())
            } else {
                Result.failure(Exception(response.body()?.message ?: "获取漫画列表失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getComicDetail(pathWord: String): Result<ComicDetailDto> {
        return try {
            val response = apiService.getComicDetail(pathWord)
            if (response.isSuccessful && response.body()?.code == 200) {
                response.body()?.results?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("漫画详情为空"))
            } else {
                Result.failure(Exception(response.body()?.message ?: "获取漫画详情失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getChapters(pathWord: String): Result<List<ChapterDto>> {
        return try {
            val response = apiService.getChapters(pathWord)
            if (response.isSuccessful && response.body()?.code == 200) {
                Result.success(response.body()?.results?.list ?: emptyList())
            } else {
                Result.failure(Exception(response.body()?.message ?: "获取章节列表失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun searchComics(query: String, page: Int): Result<List<ComicDto>> {
        return try {
            val response = apiService.searchComics(query, page)
            if (response.isSuccessful && response.body()?.code == 200) {
                Result.success(response.body()?.results?.list ?: emptyList())
            } else {
                Result.failure(Exception(response.body()?.message ?: "搜索失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}