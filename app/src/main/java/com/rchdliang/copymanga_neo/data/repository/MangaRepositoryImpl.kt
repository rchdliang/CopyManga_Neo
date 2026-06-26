package com.rchdliang.copymanga_neo.data.repository

import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.data.remote.api.MangaApiService
import com.rchdliang.copymanga_neo.data.remote.dto.ChapterListResult
import com.rchdliang.copymanga_neo.data.remote.util.ChapterDecryptor
import com.rchdliang.copymanga_neo.di.WwwRetrofit
import com.rchdliang.copymanga_neo.domain.model.ChapterContent
import com.rchdliang.copymanga_neo.domain.model.ChapterList
import com.rchdliang.copymanga_neo.domain.model.ComicDetail
import com.rchdliang.copymanga_neo.domain.model.ComicStatus
import com.rchdliang.copymanga_neo.domain.model.ComicSummary
import com.rchdliang.copymanga_neo.domain.model.FavoriteItem
import com.rchdliang.copymanga_neo.domain.model.HomeComicsData
import com.rchdliang.copymanga_neo.domain.model.QueryType
import com.rchdliang.copymanga_neo.domain.model.TrialContent
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 漫画数据仓库实现
 */
@Singleton
class MangaRepositoryImpl @Inject constructor(
    private val apiService: MangaApiService,
    @param:WwwRetrofit private val wwwApiService: MangaApiService
) : MangaRepository {

    override suspend fun getHomeComics(): Result<HomeComicsData> = supervisorScope {
        try {
            val homeResponseDeferred = async { apiService.getHomeComics() }
            val recResponseDeferred = async { apiService.getRecComics(freeType = null) }

            val homeResponse = homeResponseDeferred.await()
            val recResponse = recResponseDeferred.await()

            if (!homeResponse.isSuccessful) {
                val errorBody = homeResponse.errorBody()?.string()
                android.util.Log.e("MangaRepository", "Error response: $errorBody")
                return@supervisorScope Result.failure(Exception("HTTP ${homeResponse.code()}: ${homeResponse.message()}"))
            }

            val body = homeResponse.body()
            android.util.Log.d("MangaRepository", "Response body is null: ${body == null}")

            if (body == null) {
                return@supervisorScope Result.failure(Exception("Response body is null"))
            }

            // 优先使用 getRecComics 的结果
            val recComics = if (recResponse.isSuccessful) {
                recResponse.body()?.results?.list?.map { it.comic } ?: emptyList()
            } else {
                body.results.recComics?.list?.map { it.comic } ?: emptyList()
            }
            
            val hotComics = body.results.hotComics?.map { it.comic } ?: emptyList()
            val newComics = body.results.newComics?.map { it.comic } ?: emptyList()
            val finishComics = body.results.finishComics?.list ?: emptyList()

            android.util.Log.d(
                "MangaRepository",
                "Loaded ${recComics.size} recComics, ${hotComics.size} hotComics, ${newComics.size} newComics, ${finishComics.size} finishComics"
            )

            Result.success(
                HomeComicsData(
                    recComics = recComics.map { it.toSummary() },
                    hotComics = hotComics.map { it.toSummary() },
                    newComics = newComics.map { it.toSummary() },
                    finishComics = finishComics.map { it.toSummary() }
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("MangaRepository", "Exception loading comics: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getComicDetail(pathWord: String): Result<ComicDetail> {
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
                Result.success(body.results.toDetail())
            } else {
                val errorCode = body?.code ?: response.code()
                val errorMessage = body?.message ?: response.message()
                android.util.Log.e(
                    "MangaRepository",
                    "获取详情失败: Code=$errorCode, Message=$errorMessage"
                )
                Result.failure(Exception(errorMessage))
            }
        } catch (e: CancellationException) {
            throw e
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
    ): Result<ChapterList> {
        return try {
            val response = apiService.getChapters(pathWord, group, limit = limit, offset = offset)
            if (response.isSuccessful && response.body()?.code == 200) {
//                android.util.Log.i("MangaRepositoryImpl", "Chapter[0]=" + response.body()!!.results.component1()[0].toString())
                Result.success(
                    (response.body()?.results ?: ChapterListResult(
                        emptyList(),
                        0,
                        20,
                        0
                    )).toList()
                )
            } else {
                Result.failure(Exception(response.body()?.message ?: "获取章节列表失败"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun trialChapterContent(
        comicPathWord: String,
        chapterUuid: String
    ): Result<TrialContent> {
        return try {
            val response = apiService.getTrialContent(comicPathWord, chapterUuid)
            if (response.isSuccessful && response.body()?.code == 200) {
                Result.success(response.body()!!.results.toContent())
            } else {
                android.util.Log.w(
                    "MangaRepository",
                    "试看请求失败: code=${response.code()}, msg=${response.body()?.message}"
                )
                Result.failure(
                    Exception(response.body()?.message ?: "试看请求失败")
                )
            }
        } catch (e: CancellationException) {
            throw e
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
            // 先从章节列表 API 获取上下章 UUID 及章节名称
            var prevUuid: String? = null
            var nextUuid: String? = null
            var chapterName: String? = null
            try {
                val chaptersResult = getChapters(comicPathWord, group = group, limit = 500)
                chaptersResult.onSuccess { chapterList ->
                    val currentChapter = chapterList.list.find { it.uuid == chapterUuid }
                    if (currentChapter != null) {
                        prevUuid = currentChapter.prevChapter
                        nextUuid = currentChapter.nextChapter
                        chapterName = currentChapter.name
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w(
                    "MangaRepository",
                    "获取章节列表失败，上下章跳转将不可用: ${e.message}"
                )
            }

            val response = wwwApiService.getChapterHtml(comicPathWord, chapterUuid)
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
                result.map { imageUrls ->
                    ChapterContent(
                        imageUrls,
                        prevUuid,
                        nextUuid,
                        chapterName ?: ""
                    )
                }
            } else {
                Result.failure(Exception("获取章节页面失败: ${response.code()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getRecComics(limit: Int, offset: Int): Result<List<ComicSummary>> {
        return try {
            val response = apiService.getRecComics(limit = limit, offset = offset, freeType = 0)
            if (response.isSuccessful && response.body()?.code == 200) {
                Result.success(response.body()?.results?.list?.map { it.comic.toSummary() }
                    ?: emptyList())
            } else {
                Result.failure(Exception(response.body()?.message ?: "获取推荐失败"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchComics(
        query: String,
        page: Int,
        queryType: QueryType?
    ): Result<List<ComicSummary>> {
        return try {
            val pageSize = 20
            val offset = (page - 1) * pageSize
            val response = apiService.search(
                query = query,
                limit = pageSize,
                offset = offset,
                queryType = queryType
            )
            val body = response.body()
            if (response.isSuccessful && body?.code == 200) {
                Result.success(body.results.list.map { it.toSummary() })
            } else {
                Result.failure(Exception(body?.message ?: "搜索失败"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getFavoriteComics(
        offset: Int,
        limit: Int
    ): Result<List<FavoriteItem>> {
        return try {
            val response = apiService.getFavoriteComics(offset = offset, limit = limit)
            if (response.isSuccessful && response.body()?.code == 200) {
                Result.success(response.body()?.results?.list?.map { it.toFavoriteItem() }
                    ?: emptyList())
            } else {
                val message = when (val code = response.code()) {
                    401 -> "401 未登录，请先登录账号"
                    210 -> "210 触发风控，请稍后再试"
                    502 -> "502 服务器错误，请稍后重试"
                    503 -> "503 服务不可用，请稍后重试"
                    504 -> "504 服务器响应超时"
                    else -> response.body()?.message ?: "获取书架失败 (HTTP $code)"
                }
                Result.failure(Exception(message))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("请求超时，请检查网络后重试"))
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("无法连接服务器，请检查网络"))
        } catch (e: javax.net.ssl.SSLException) {
            Result.failure(Exception("网络连接异常 (SSL)，请检查网络"))
        } catch (e: Exception) {
            Result.failure(Exception("获取书架失败：${e.message}"))
        }
    }

    override suspend fun queryComicStatus(pathWord: String): Result<ComicStatus> {
        return try {
            val response = apiService.queryComicStatus(pathWord)
            if (response.isSuccessful && response.body()?.code == 200) {
                val results = response.body()?.results
                if (results != null) {
                    Result.success(results.toStatus())
                } else {
                    Result.failure(Exception("查询状态结果为空"))
                }
            } else {
                Result.failure(
                    Exception(response.body()?.message ?: "查询状态失败")
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addFavorite(comicId: String): Result<Unit> {
        return try {
            val response = apiService.addFavorite(comicId)
            if (response.isSuccessful && response.body()?.code == 200) {
                Result.success(Unit)
            } else {
                Result.failure(
                    Exception(response.body()?.message ?: "添加收藏失败")
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeFavorite(collectId: Int): Result<Unit> {
        return try {
            val response = apiService.removeFavorite(collectId)
            if (response.isSuccessful && response.body()?.code == 200) {
                Result.success(Unit)
            } else {
                Result.failure(
                    Exception(response.body()?.message ?: "移除收藏失败")
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}