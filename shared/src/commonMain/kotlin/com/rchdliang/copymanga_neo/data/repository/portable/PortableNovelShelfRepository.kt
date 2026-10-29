package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.data.novel.portable.PortableNovelJson
import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.data.remote.portable.api.MemberWriteApiService
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.NovelShelfRepository
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

class PortableNovelShelfRepository(private val client:CopyMangaClient):NovelShelfRepository {
    private val api=MemberWriteApiService(client)
    private fun session():NetworkSnapshot=client.state.snapshot.also{require(it.session().token.isNotBlank()){"请先登录后使用书架"}}
    private fun check(s:NetworkSnapshot){client.state.requireCurrent(s.generation)}
    private suspend fun authentication(s:NetworkSnapshot,http:Int) {
        if(http==401){client.invalidateSession(s.settings.requestMode,s.generation,s.session().token);error("登录已过期，请重新登录")}
    }
    private suspend fun contains(s:NetworkSnapshot,path:String):Boolean {
        check(s);val response=api.novelQuery(path,"Token ${s.session().token}",requestMode=s.settings.requestMode);authentication(s,response.code());val body=response.body()?:error("书架状态读取失败")
        check(s);val status=body.results?:error("书架状态缺少数据");require(status.isLogin){"登录已过期，请重新登录"};return status.collect!=null
    }
    override suspend fun list(offset:Int, sort:FavoritesSort):Result<NovelShelfPage> = result {
        require(offset>=0);val s=session();val app=s.settings.requestMode==RequestMode.OFFICIAL_APP
        val response=api.novelFavorites("Token ${s.session().token}",offset,limit=if(app)20 else 21,ordering=sort.ordering,update=if(app)null else true,requestMode=s.settings.requestMode)
        authentication(s,response.code());check(s);val body=response.body()?:error("小说书架加载失败")
        PortableNovelJson.shelfPage(requireNotNull(body["results"] as? JsonObject){"小说书架缺少数据"},offset)
    }
    override suspend fun contains(path:String):Result<Boolean> = result { require(path.isNotBlank());contains(session(),path) }
    override suspend fun add(bookId:String,path:String)=setCollected(bookId,path,true)
    override suspend fun remove(bookId:String,path:String)=setCollected(bookId,path,false)
    private suspend fun setCollected(bookId:String,path:String,collected:Boolean):Result<Unit> = result {
        require(bookId.isNotBlank()&&path.isNotBlank());val s=session()
        val response=api.addNovelFavorite(bookId,"Token ${s.session().token}",isCollect=if(collected)1 else 0,requestMode=s.settings.requestMode)
        authentication(s,response.code());check(s);check(response.isSuccessful&&response.body()?.code==200){"收藏操作未确认，请刷新后检查"}
        check(contains(s,path)==collected){"云端收藏状态尚未确认，请刷新后检查"}
    }
    override suspend fun removeAll(collectIds:List<Int>):Result<Unit> = result {
        require(collectIds.isNotEmpty()&&collectIds.all{it>0});val ids=collectIds.distinct();val s=session();val mode=s.settings.requestMode;val authorization="Token ${s.session().token}"
        val response=if(mode==RequestMode.OFFICIAL_APP)api.removeNovelFavoritesApp(ids,authorization,requestMode=mode) else api.removeNovelFavoritesWeb(ids.mapIndexed{index,id->"ids[$index]" to id}.toMap(),authorization,requestMode=mode)
        authentication(s,response.code());check(s);val removed=(response.body()?.get("results") as? JsonArray)?.map{it.jsonPrimitive.int}.orEmpty()
        check(removed.containsAll(ids)){"部分收藏未确认移除，请刷新书架后检查"}
    }
    private suspend fun <T> result(block:suspend()->T):Result<T> = try{Result.success(block())}catch(e:CancellationException){throw e}catch(e:Exception){Result.failure(e)}
}
