package com.rchdliang.copymanga_neo.data.repository

import com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore
import com.rchdliang.copymanga_neo.data.remote.api.MangaApiService
import com.rchdliang.copymanga_neo.domain.model.ChapterCatalog
import com.rchdliang.copymanga_neo.domain.model.ChapterContent
import com.rchdliang.copymanga_neo.domain.model.ChapterGroup
import com.rchdliang.copymanga_neo.domain.model.ChapterInfo
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** App 目录与阅读独立实现，失败直接交给界面，不隐式切回 WWW。 */
@Singleton
class OfficialAppContentSource @Inject constructor(
    private val api: MangaApiService,
    private val settings: NetworkSettingsStore
) {
    suspend fun catalog(pathWord: String): Result<ChapterCatalog> = capture {
        val generation = settings.generation
        fun checkGeneration() {
            if (settings.generation != generation) throw IOException("网络设置已更改，请刷新重试")
        }

        val response = api.getComicDetail(pathWord)
        checkGeneration()
        val body = response.body()
        if (!response.isSuccessful || body?.code != 200) {
            response.errorBody()?.close()
            throw IOException(body?.message ?: "App 目录分组获取失败")
        }
        val detail = body.results ?: throw IOException("App 详情为空")
        if (detail.isBanned || detail.isLock) throw IOException("漫画暂不可访问")
        val groups = detail.groups?.values?.map { ChapterGroup(it.pathWord, it.total, it.name) }
            ?: throw IOException("App 未返回章节分组")
        val chapters = linkedMapOf<String, List<ChapterInfo>>()
        for (group in groups) {
            val collected = mutableListOf<ChapterInfo>()
            val seen = mutableSetOf<String>()
            var total = group.total
            var offset = 0
            while (offset < total) {
                checkGeneration()
                if (offset >= 50_000) throw IOException("章节目录过大，请稍后重试")
                val pageResponse =
                    api.getChapters(pathWord, group.pathWord, limit = 100, offset = offset)
                checkGeneration()
                val pageBody = pageResponse.body()
                if (!pageResponse.isSuccessful || pageBody?.code != 200) {
                    pageResponse.errorBody()?.close()
                    throw IOException(pageBody?.message ?: "App 章节目录获取失败")
                }
                val page = pageBody.results ?: throw IOException("App 章节目录结果为空")
                if (page.list.isNullOrEmpty() || page.list.any { !seen.add(it.uuid) }) {
                    throw IOException("App 章节目录为空或重复，未保存不完整目录")
                }
                collected.addAll(page.toList().list)
                offset += page.list.size
                total = maxOf(total, page.total)
            }
            chapters[group.pathWord] = collected
        }
        checkGeneration()
        ChapterCatalog(groups, chapters)
    }

    suspend fun chapter(pathWord: String, uuid: String): Result<ChapterContent> = capture {
        val response = api.getOfficialChapter(pathWord, uuid)
        val body = response.body()
        if (!response.isSuccessful || body?.code != 200) {
            response.errorBody()?.close()
            throw IOException(body?.message ?: "App 章节内容获取失败")
        }
        val result = body.results ?: throw IOException("App 章节结果为空")
        if (result.isLock || result.isBanned) throw IOException("章节暂不可阅读")
        val chapter = result.chapter ?: throw IOException("App 未返回章节内容")
        if (chapter.uuid != uuid) throw IOException("App 返回的章节与请求不一致")
        val contents = chapter.contents.orEmpty()
        val words = chapter.words ?: throw IOException("App 未返回章节图片页序")
        if (words.size != contents.size || words.any { it < 0 } || words.distinct().size != words.size) {
            throw IOException("App 章节图片页序无效，未保存不完整章节")
        }
        // words[i] 是 contents[i] 的页序，配对后排序；不能作为 contents 的数组下标。
        val urls = words.zip(contents).sortedBy { it.first }.map { (_, item) ->
            val url = item.url?.toHttpUrlOrNull() ?: throw IOException("App 图片地址无效")
            if (url.username.isNotEmpty() || url.password.isNotEmpty()) throw IOException("App 图片地址无效")
            url.toString()
        }.orEmpty()
        if (urls.isEmpty() || chapter.size?.let { it > 0 && urls.size != it } == true) {
            throw IOException("App 返回的章节图片为空或不完整，未切换网页路径")
        }
        ChapterContent(urls, chapter.prev, chapter.next, chapter.name.orEmpty())
    }

    private suspend fun <T> capture(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
