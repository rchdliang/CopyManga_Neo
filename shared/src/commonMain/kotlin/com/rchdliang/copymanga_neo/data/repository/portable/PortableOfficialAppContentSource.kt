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
    private val downloads=OfficialDownloadApiService(client)
    private val mutex=Mutex()
    private val downloadContents=BoundedLruMap<String,ChapterContent>(32)
    private var exhaustedGeneration:Long?=null
    private var checkedGeneration:Long?=null
    private var quotaCheckedAt=0L
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
        parseChapter(api.getOfficialChapter(pathWord,uuid).body()?.results?:error("App 章节结果为空"),uuid)
    }
    suspend fun download(pathWord:String,uuid:String,bound:CopyMangaClient):Result<ChapterContent> = capture {
        val api=MangaApiService(bound)
        val downloads=OfficialDownloadApiService(bound)
        mutex.withLock {
            val snapshot=client.state.snapshot;val token=snapshot.session().token
            require(token.isNotBlank()){"请先登录后下载"}
            val generation=snapshot.generation;val key="$generation:$pathWord:$uuid"
            downloadContents.keys.filter { !it.startsWith("$generation:") }.forEach(downloadContents::remove)
            downloadContents[key]?.let { return@withLock it }
            if(checkedGeneration!=generation||monotonicNanos()-quotaCheckedAt>=300_000_000_000L) {
                val info=api.getMemberInfoWithCredential("Token $token")
                client.state.requireCurrent(generation)
                if(info.isSuccessful&&info.body()?.code==200) {
                    checkedGeneration=generation;quotaCheckedAt=monotonicNanos()
                    val remaining=info.body()?.results?.dayDownloads
                    if(remaining==0)exhaustedGeneration=generation else if(remaining!=null&&remaining>0)exhaustedGeneration=null
                }
            }
            suspend fun readingContent():ChapterContent {
                val value=api.getOfficialChapter(pathWord,uuid,headers=CopyMangaNetworkConfig.noAuthApiHeaders(),authorization="Token $token")
                client.state.requireCurrent(generation)
                val content=parseChapter(value.body()?.results?:error("阅读章节结果为空"),uuid)
                downloadContents[key]=content;return content
            }
            if(exhaustedGeneration==generation)return@withLock readingContent()
            val response=try { downloads.chapter(pathWord,uuid,"Token $token") } catch(error:ApiException) {
                if(error.status!=401&&DownloadQuotaPolicy.isExhausted(error.message)) {
                    client.state.requireCurrent(generation);exhaustedGeneration=generation;return@withLock readingContent()
                }
                throw error
            }
            client.state.requireCurrent(generation)
            val result=response.body()?.results?:error("下载授权结果为空，未自动重试")
            require(result.isLogin==true){"请先登录后下载"}
            val content=parseChapter(result,uuid)
            if(result.dayDownloads==0)exhaustedGeneration=generation
            downloadContents[key]=content;content
        }
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
