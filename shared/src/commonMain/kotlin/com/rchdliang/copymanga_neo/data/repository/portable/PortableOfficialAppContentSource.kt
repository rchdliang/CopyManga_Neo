package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.data.remote.portable.api.*
import com.rchdliang.copymanga_neo.data.remote.portable.dto.*
import com.rchdliang.copymanga_neo.domain.model.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PortableOfficialAppContentSource(private val client:CopyMangaClient) {
    private val api=MangaApiService(client)
    private val mutex=Mutex()
    private val downloadContents=BoundedLruMap<String,ChapterContent>(32)
    suspend fun catalog(pathWord:String):Result<ChapterCatalog> = capture {
        val generation=client.state.snapshot.generation
        val detail=api.getComicDetail(pathWord).body()?.results?:error("App 目录分组获取失败")
        client.state.requireCurrent(generation)
        require(!detail.isBanned&&!detail.isLock){"漫画暂不可访问"}
        val groups=detail.groups.values.map { ChapterGroup(it.pathWord,it.total,it.name) }
        val chapters=linkedMapOf<String,List<ChapterInfo>>()
        for(group in groups) {
            val collected=mutableListOf<ChapterInfo>();val seen=mutableSetOf<String>();var total=group.total;var offset=0
            while(offset<total) {
                client.state.requireCurrent(generation);require(offset<50_000){"章节目录过大，请稍后重试"}
                val page=api.getChapters(pathWord,group.pathWord,limit=100,offset=offset).body()?.results?:error("App 章节目录获取失败")
                client.state.requireCurrent(generation)
                require(page.offset==offset&&page.list.isNotEmpty()&&page.list.all { seen.add(it.uuid) }){"App 章节目录为空或重复，未保存不完整目录"}
                require(page.list.all { it.comicPathWord==pathWord && (it.groupPathWord==null||it.groupPathWord==group.pathWord) }){"App 章节目录标识不匹配"}
                collected+=page.toList().list;offset+=page.list.size;total=maxOf(total,page.total)
            }
            chapters[group.pathWord]=collected
        }
        client.state.requireCurrent(generation);ChapterCatalog(groups,chapters)
    }
    suspend fun chapter(pathWord:String,uuid:String):Result<ChapterContent> = capture {
        loadGuestChapter(api, pathWord, uuid)
    }
    suspend fun download(pathWord: String, uuid: String, bound: CopyMangaClient): Result<ChapterContent> = capture {
        mutex.withLock {
            val snapshot = client.state.snapshot
            require(snapshot.session().token.isNotBlank()) { "请先登录后下载" }
            val generation = snapshot.generation
            val key = "$generation:$pathWord:$uuid"
            downloadContents.keys.filterNot { it.startsWith("$generation:") }.forEach(downloadContents::remove)
            downloadContents[key]?.let { return@withLock it }
            // 以游客获取完整内容，不把后台下载写成账号阅读记录。
            val content = loadGuestChapter(MangaApiService(bound), pathWord, uuid)
            client.state.requireCurrent(generation)
            downloadContents[key] = content
            content
        }
    }
    private suspend fun loadGuestChapter(service: MangaApiService, pathWord: String, uuid: String): ChapterContent {
        val result = service.getOfficialChapterContent(pathWord, uuid).body()?.results ?: error("App 章节结果为空")
        return parseChapter(result, uuid)
    }
    private fun parseChapter(result:OfficialChapterResult,uuid:String):ChapterContent {
        require(!result.isLock&&!result.isBanned){"章节暂不可阅读"}
        val chapter=result.chapter?:error("App 未返回章节内容")
        require(chapter.uuid==uuid){"App 返回的章节与请求不一致"}
        val contents=chapter.contents.orEmpty();val words=chapter.words?:error("App 未返回章节图片页序")
        require(words.size==contents.size&&words.all{it>=0}&&words.distinct().size==words.size){"App 章节图片页序无效，未保存不完整章节"}
        val urls=words.zip(contents).sortedBy{it.first}.map { (_,item)->
            val raw=item.url?:error("App 图片地址无效");val url=Url(raw)
            require(url.protocol in setOf(URLProtocol.HTTP,URLProtocol.HTTPS)&&url.host.isNotBlank()&&url.user.isNullOrEmpty()&&url.password.isNullOrEmpty()){"App 图片地址无效"};raw
        }
        require(urls.isNotEmpty()&&(chapter.size==null||chapter.size<=0||chapter.size==urls.size)){"App 返回的章节图片为空或不完整，未切换网页路径"}
        return ChapterContent(urls,chapter.prev,chapter.next,chapter.name.orEmpty())
    }
    private suspend fun <T> capture(block:suspend()->T):Result<T> = try { Result.success(block()) } catch(e:CancellationException){throw e}catch(e:Exception){Result.failure(e)}
}
