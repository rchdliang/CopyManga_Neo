package com.rchdliang.copymanga_neo.data.repository

import com.rchdliang.copymanga_neo.data.remote.api.MangaApiService
import com.rchdliang.copymanga_neo.data.remote.dto.ChapterDto
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDetailResult
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDto
import com.rchdliang.copymanga_neo.domain.model.HomeComicsData
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

    override suspend fun getHomeComics(): Result<HomeComicsData> {
        return try {
            val response = apiService.getHomeComics()

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("MangaRepository", "Error response: $errorBody")
                return Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }

            val body = response.body()
            android.util.Log.d("MangaRepository", "Response body is null: ${body == null}")

            if (body == null) {
                return Result.failure(Exception("Response body is null"))
            }

            val recComics = body.results.recComics?.list?.map { it.comic } ?: emptyList()
            val hotComics = body.results.hotComics?.map { it.comic } ?: emptyList()
            val newComics = body.results.newComics?.map { it.comic } ?: emptyList()
            val finishComics = body.results.finishComics?.list ?: emptyList()

            android.util.Log.d(
                "MangaRepository",
                "Loaded ${recComics.size} recComics, ${hotComics.size} hotComics, ${newComics.size} newComics, ${finishComics.size} finishComics"
            )

            Result.success(
                HomeComicsData(
                    recComics = recComics,
                    hotComics = hotComics,
                    newComics = newComics,
                    finishComics = finishComics
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("MangaRepository", "Exception loading comics: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getComicDetail(pathWord: String): Result<ComicDetailResult> {
        return try {
            val response = apiService.getComicDetail(pathWord)
            val body = response.body()
            if (response.isSuccessful && body?.code == 200) {
                body.results.let {
                    Result.success(it)
                }
            } else {
                val errorCode = body?.code ?: response.code()
                val errorMessage = body?.message ?: response.message()
                android.util.Log.e(
                    "MangaRepository",
                    "获取详情失败: Code=$errorCode, Message=$errorMessage"
                )
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            android.util.Log.e("MangaRepository", "获取详情异常: ${e.message}", e)
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