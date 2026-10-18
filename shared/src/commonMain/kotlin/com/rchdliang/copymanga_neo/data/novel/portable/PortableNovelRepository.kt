package com.rchdliang.copymanga_neo.data.novel.portable

import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.data.image.portable.ReaderImageMetadata
import com.rchdliang.copymanga_neo.data.repository.portable.PortableNovelThemeCatalog
import com.rchdliang.copymanga_neo.data.repository.portable.MemoryNovelThemeCatalog
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.reading.NovelImagePositionDebugStore
import com.rchdliang.copymanga_neo.domain.reading.NovelRequestSpec
import com.rchdliang.copymanga_neo.domain.reading.NovelTextDocument
import com.rchdliang.copymanga_neo.domain.repository.NovelRepository
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** Native cache implementation is injected: network success is never presented as persisted data. */
class PortableNovelRepository(
    private val client:CopyMangaClient,
    private val cache:PortableNovelCache,
    private val requireDownloadNetwork: suspend ()->Unit = { error("此平台尚未配置下载网络策略") },
    private val downloadClient: (suspend () -> CopyMangaClient)? = null,
    private val imagePositionDebug: NovelImagePositionDebugStore? = null,
    private val imagePositionDebugEnabled: () -> Boolean = { false },
    private val themeCatalog:PortableNovelThemeCatalog = MemoryNovelThemeCatalog(),
    private val inspectImage: suspend (ByteArray) -> ReaderImageMetadata = { error("此平台尚未配置小说插图校验") },
) : NovelRepository {
    private val mutex=Mutex()
    private val mutableCached=MutableStateFlow<List<NovelCachedVolume>>(emptyList())
    override val cachedVolumes=mutableCached.asStateFlow()
    private val bookHints=MutableStateFlow<Map<String,NovelBook>>(emptyMap())
    private fun session(mode:RequestMode,owner:String):Long {
        val s=client.state.snapshot
        require(s.settings.requestMode==mode&&s.session(mode).owner==owner){"阅读会话已改变，请重新打开"}
        return s.generation
    }
    override suspend fun discover(mode:RequestMode,owner:String,offset:Int,order:String,theme:String,author:String):Result<NovelPage> = result {
        page(api(mode,owner,listOf("books"),NovelRequestSpec.browse(mode==RequestMode.OFFICIAL_APP,offset,order,theme,author))).also{remember(mode,owner,it.books)}
    }
    override suspend fun search(mode:RequestMode,owner:String,offset:Int,query:String,type:String):Result<NovelPage> = result {
        page(api(mode,owner,listOf("search","books"),NovelRequestSpec.search(mode==RequestMode.OFFICIAL_APP,offset,query,type))).also{remember(mode,owner,it.books)}
    }
    override suspend fun tags(mode:RequestMode,owner:String):Result<List<NovelTag>> = result {
        val generation=session(mode,owner);val tags=mutableListOf<NovelTag>();var offset=0
        do {
            val response=api(mode,owner,listOf("theme","book","count"),NovelRequestSpec.themeCounts(mode==RequestMode.OFFICIAL_APP,offset))
            require(response["offset"]?.jsonPrimitive?.int==offset){"小说题材分页位置无效"}
            val total=requireNotNull(response["total"]).jsonPrimitive.int;require(total>=0)
            val page=PortableNovelJson.themeCounts(response);tags+=page;offset+=page.size
            client.state.requireCurrent(generation)
            if(offset>=total)break
            require(page.isNotEmpty()){"小说题材目录未返回完整数据"}
        }while(true)
        tags.distinctBy{it.path}.also{client.state.requireCurrent(generation);themeCatalog.save(mode,it)}
    }
    override fun cachedTags(mode:RequestMode)=themeCatalog.get(mode)
    override suspend fun detail(mode:RequestMode,owner:String,path:String):Result<NovelBook> = result {
        PortableNovelJson.book(requireNotNull(api(mode,owner,listOf("book",path))["book"] as? JsonObject)).also{remember(mode,owner,listOf(it))}
    }
    override suspend fun volumes(mode:RequestMode,owner:String,path:String):Result<List<NovelVolume>> = result {
        requireNotNull(api(mode,owner,listOf("book",path,"volumes"))["list"] as? JsonArray).map{PortableNovelJson.volume(it.jsonObject)}.sortedBy{it.index}
    }
    override suspend fun readVolume(mode:RequestMode,owner:String,path:String,id:String,allImages:Boolean,refresh:Boolean,onProgress:(NovelCacheProgress)->Unit,forDownload:Boolean):Result<NovelReadingVolume> = result {
        val generation=session(mode,owner)
        if(!forDownload&&!allImages&&!refresh)readCached(mode,owner,path,id)?.let{client.state.requireCurrent(generation);return@result it}
        mutex.withLock {
            client.state.requireCurrent(generation)
            // Fully cached volumes remain available offline; bind a download network only for I/O.
            var selected:CopyMangaClient?=null
            suspend fun networkClient():CopyMangaClient {
                if(forDownload)requireDownloadNetwork()
                return selected?:((if(forDownload)downloadClient?.invoke()?:error("此平台尚未配置绑定下载网络的客户端") else client).also {
                    require(it.state===client.state){"下载客户端会话不匹配"}
                    selected=it
                })
            }
            var reading=readCached(mode,owner,path,id)
            if(refresh||reading==null) {
                onProgress(NovelCacheProgress(NovelCacheStage.TEXT))
                val response=api(mode,owner,listOf("book",path,"volume",id),selected=networkClient(),fallback=false)
                if(imagePositionDebug != null && imagePositionDebugEnabled()) {
                    try {
                        NovelImagePositionDebugJson.sample(mode,path,id,response)?.let(imagePositionDebug::offer)
                    } catch(error:CancellationException) { throw error
                    } catch(_:Exception) { /* Optional debug capture must not prevent reading. */ }
                }
                require((response["is_lock"] as? JsonPrimitive)?.booleanOrNull!=true){"本卷受限，请在当前模式登录或检查官方阅读权限"}
                val volume=response["volume"] as? JsonObject?:error("官方未返回可读内容")
                require(PortableNovelJson.text(volume,"id")==id){"官方分卷与请求不一致"}
                val address=PortableNovelJson.text(volume,"txt_addr");require(address.isNotBlank()){"官方未返回正文地址，请检查阅读权限"}
                val book=PortableNovelJson.book(requireNotNull(response["book"] as? JsonObject)).withFallback(bookHints.value[key(mode,owner,path)]).withFallback(reading?.book)
                require(book.path==path){"官方小说与请求不一致"}
                val metadata=JsonObject(response+mapOf("book" to PortableNovelJson.bookJson(book)))
                if(forDownload)requireDownloadNetwork()
                val bytes=resource(address,16*1024*1024,networkClient())
                client.state.requireCurrent(generation);currentCoroutineContext().ensureActive()
                onProgress(NovelCacheProgress(NovelCacheStage.TEXT,bytes=bytes.size.toLong()))
                reading=cache.publish(mode,owner,path,id,metadata.toString(),bytes)
                publishCached()
            }
            // Cache hits still need promotion; ordinary reads/refreshes never clear this intent.
            if(forDownload) {
                client.state.requireCurrent(generation);currentCoroutineContext().ensureActive()
                cache.markDownloadRequested(mode,owner,path,id)
                publishCached()
            }
            var current=requireNotNull(reading)
            val urls=current.sections.filter{it.type==2}.map{requireNotNull(it.image)}.distinct()
            onProgress(NovelCacheProgress(NovelCacheStage.IMAGES,current.cachedImages.size,urls.size))
            if(allImages)for(url in urls) {
                currentCoroutineContext().ensureActive();client.state.requireCurrent(generation)
                if(url in current.cachedImages)continue
                if(forDownload)requireDownloadNetwork()
                val bytes=resource(url,16*1024*1024,networkClient())
                if(forDownload)requireDownloadNetwork()
                client.state.requireCurrent(generation);currentCoroutineContext().ensureActive()
                val bounds=inspectImage(bytes)
                require(bounds.width>0&&bounds.height>0){"官方插图资源无法解码"}
                client.state.requireCurrent(generation);currentCoroutineContext().ensureActive()
                current=cache.writeImage(mode,owner,path,id,url,bytes)
                publishCached();onProgress(NovelCacheProgress(NovelCacheStage.IMAGES,current.cachedImages.size,urls.size,bytes.size.toLong()))
            }
            client.state.requireCurrent(generation);current
        }
    }
    /** A damaged optional cache must not prevent an online repair; cancellation still stops work. */
    private suspend fun readCached(mode:RequestMode,owner:String,path:String,id:String):NovelReadingVolume? =
        try { cache.read(mode,owner,path,id) }
        catch(cancel:CancellationException) { throw cancel }
        catch(_:Exception) { null }

    override suspend fun removeVolume(mode:RequestMode,owner:String,path:String,id:String):Result<Unit> = result { mutex.withLock { session(mode,owner);cache.removeVolume(mode,owner,path,id);publishCached() } }
    override suspend fun removeBook(mode:RequestMode,owner:String,path:String):Result<Unit> = result { mutex.withLock { session(mode,owner);cache.removeBook(mode,owner,path);publishCached() } }
    private var cacheIndexed = false
    override suspend fun awaitCachedVolumes(): Result<Unit> = result {
        mutex.withLock { if (!cacheIndexed) { publishCached(); cacheIndexed = true } }
    }
    override suspend fun refreshCachedVolumes():Result<Unit> = result {
        mutex.withLock { publishCached(); cacheIndexed = true }
    }
    override suspend fun cacheUsage():Result<NovelCacheUsage> = result { cache.usage() }
    override suspend fun clearCache():Result<Unit> = result { mutex.withLock { cache.clear();publishCached() } }
    private suspend fun publishCached(){mutableCached.value=cache.list().sortedByDescending{it.lastAccessedAt}}
    private fun page(o:JsonObject)=NovelPage(requireNotNull(o["list"] as? JsonArray).map{PortableNovelJson.book(it.jsonObject)},requireNotNull(o["total"]).jsonPrimitive.int,requireNotNull(o["offset"]).jsonPrimitive.int,requireNotNull(o["limit"]).jsonPrimitive.int)
    private fun key(mode:RequestMode,owner:String,path:String)="${mode.name}\u0000$owner\u0000$path"
    private fun remember(mode:RequestMode,owner:String,books:List<NovelBook>) {
        bookHints.update { current ->
            val next=LinkedHashMap(current)
            books.forEach { book -> val key=key(mode,owner,book.path);next[key]=book.withFallback(next.remove(key)) }
            while(next.size>256)next.remove(next.keys.first())
            next
        }
    }
    private suspend fun api(mode:RequestMode,owner:String,segments:List<String>,query:Map<String,String> = emptyMap(),selected:CopyMangaClient=client,fallback:Boolean=true):JsonObject {
        val generation=session(mode,owner);require(segments.all{it.isNotBlank()})
        val path="/api/v3/"+segments.joinToString("/"){it.encodeURLPathPart()}
        val key=NovelTextDocument.sha256(owner+"\u0000"+mode.name+"\u0000"+path+"?"+query.entries.joinToString("&"){it.key.encodeURLParameter()+"="+it.value.encodeURLParameter()})
        val cacheGeneration=cache.generation
        try {
            val result=selected.results(ApiRequest(path,query=query.toList(),mode=mode))
            client.state.requireCurrent(generation)
            if(fallback)try { cache.writeMetadata(key,result.toString(),cacheGeneration) }catch(e:CancellationException){throw e}catch(_:Exception){ /* Browsing may continue when optional metadata persistence fails. */ }
            return result
        }catch(e:PortableTransportException) {
            if(!fallback)throw e
            client.state.requireCurrent(generation)
            return cache.readMetadata(key)?.let{Json.parseToJsonElement(it).jsonObject}?:throw e
        }
    }
    private suspend fun resource(address:String,maximum:Int,selected:CopyMangaClient):ByteArray {
        val url=Url(address);val settings=client.state.snapshot.settings
        require(!url.encodedPath.startsWith("/api/")&&url.host !in setOf(CopyMangaNetworkConfig.API_HOST,CopyMangaNetworkConfig.WWW_HOST,settings.apiHost,settings.wwwHost,settings.officialApp.apiHost,settings.officialApp.loginHost)){"资源地址不能是会员接口"}
        return selected.resource(address,maximum)
    }
    private suspend fun <T> result(block:suspend()->T):Result<T> = try{Result.success(block())}catch(e:CancellationException){throw e}catch(e:Exception){Result.failure(e)}
}
