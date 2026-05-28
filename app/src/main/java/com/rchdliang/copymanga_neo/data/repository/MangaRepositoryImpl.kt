package com.rchdliang.copymanga_neo.data.repository

import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.data.remote.api.MangaApiService
import com.rchdliang.copymanga_neo.data.remote.dto.ChapterListResult
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDetailResult
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDto
import com.rchdliang.copymanga_neo.data.remote.dto.FavoritesDto
import com.rchdliang.copymanga_neo.data.remote.dto.TrialContentResult
import com.rchdliang.copymanga_neo.data.remote.util.ChapterDecryptor
import com.rchdliang.copymanga_neo.domain.model.ChapterContent
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

            val code = body?.code ?: response.code()
            if (code == 210) {
                val errorMsg = body?.message ?: response.errorBody()?.string()?.let {
                    try {
                        JsonParser.parseString(it).asJsonObject.get("message")?.asString
                    } catch (e: Exception) {
                        null
                    }
                } ?: "未知风控原因"
                return Result.failure(Exception("code 210: $errorMsg"))
            }

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

    override suspend fun getChapters(
        pathWord: String,
        group: String,
        offset: Int,
        limit: Int
    ): Result<ChapterListResult> {
        return try {
            val response = apiService.getChapters(pathWord, group, limit = limit, offset = offset)
            if (response.isSuccessful && response.body()?.code == 200) {
//                android.util.Log.i("MangaRepositoryImpl", "Chapter[0]=" + response.body()!!.results.component1()[0].toString())
                Result.success(response.body()?.results ?: ChapterListResult(emptyList(), 0, 20, 0))
            } else {
                Result.failure(Exception(response.body()?.message ?: "获取章节列表失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun trialChapterContent(
        comicPathWord: String,
        chapterUuid: String
    ): Result<TrialContentResult> {
        return try {
            val response = apiService.getTrialContent(comicPathWord, chapterUuid)
            if (response.isSuccessful && response.body()?.code == 200) {
                Result.success(response.body()!!.results)
            } else {
                android.util.Log.w(
                    "MangaRepository",
                    "试看请求失败: code=${response.code()}, msg=${response.body()?.message}"
                )
                Result.failure(
                    Exception(response.body()?.message ?: "试看请求失败")
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("MangaRepository", "试看请求异常: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getChapterContent(
        comicPathWord: String,
        chapterUuid: String,
        group: String
    ): Result<ChapterContent> {
        return try {
            // 先从章节列表 API 获取上下章 UUID
            var prevUuid: String? = null
            var nextUuid: String? = null
            try {
                val chaptersResult = getChapters(comicPathWord, group = group, limit = 500)
                chaptersResult.onSuccess { chapterList ->
                    val currentChapter = chapterList.list.find { it.uuid == chapterUuid }
                    if (currentChapter != null) {
                        prevUuid = currentChapter.prevChapter
                        nextUuid = currentChapter.nextChapter
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w(
                    "MangaRepository",
                    "获取章节列表失败，上下章跳转将不可用: ${e.message}"
                )
            }

            val response = apiService.getChapterHtml(comicPathWord, chapterUuid)
            val code = response.code()
            if (code == 210) {
                val bodyString = response.errorBody()?.string() ?: ""
                val message = try {
                    val json = JsonParser.parseString(bodyString).asJsonObject
                    json.get("message")?.asString ?: "未知风控原因"
                } catch (e: Exception) {
                    bodyString.take(100)
                }
                return Result.failure(Exception("code 210: $message"))
            }

            if (response.isSuccessful) {
                val html = response.body()?.string() ?: ""
                val result = ChapterDecryptor.decryptChapterImages(html)
                result
                    .onSuccess { imageUrls ->
                        android.util.Log.i(
                            "MangaRepository",
                            "章节内容解析成功: ${imageUrls.size} 张图片, prev=$prevUuid, next=$nextUuid"
                        )
                    }
                    .onFailure {
                        android.util.Log.e(
                            "MangaRepository",
                            "解析章节内容失败(${it.message})，HTML 前 500 字符: ${html.take(500)}"
                        )
                    }
                result.map { imageUrls -> ChapterContent(imageUrls, prevUuid, nextUuid) }
            } else {
                Result.failure(Exception("获取章节页面失败: ${response.code()}"))
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

    override suspend fun getFavoriteComics(
        offset: Int,
        limit: Int
    ): Result<List<FavoritesDto>> {
        return try {
            val response = apiService.getFavoriteComics(offset = offset, limit = limit)
            if (response.isSuccessful && response.body()?.code == 200) {
                Result.success(response.body()?.results?.list ?: emptyList())
            } else {
                Result.failure(Exception(response.body()?.message ?: "获取书架失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}