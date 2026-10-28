package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.data.local.portable.DocumentStorage
import com.rchdliang.copymanga_neo.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface PortableComicThemeCatalog {
    val themes:StateFlow<List<ComicFilterOption>>
    suspend fun save(themes:List<ComicFilterOption>)
}
interface PortableNovelThemeCatalog {
    fun get(mode:RequestMode):List<NovelTag>
    suspend fun save(mode:RequestMode,themes:List<NovelTag>)
}
class MemoryComicThemeCatalog:PortableComicThemeCatalog {
    private val mutable=MutableStateFlow(ComicFilterDefaults.themes)
    override val themes=mutable.asStateFlow()
    override suspend fun save(themes:List<ComicFilterOption>) { mutable.value=themes }
}
class MemoryNovelThemeCatalog:PortableNovelThemeCatalog {
    private val mutable=MutableStateFlow<List<NovelTag>>(NovelThemeDefaults.themes)
    override fun get(mode:RequestMode)=mutable.value
    override suspend fun save(mode:RequestMode,themes:List<NovelTag>) { mutable.value=themes }
}

@Serializable
private data class ThemeCatalogDocument(
    val comicThemes:List<ComicFilterOption> = ComicFilterDefaults.themes,
    val novelThemes:List<NovelTag> = emptyList(),
    val comicEntries:Map<String,CachedComicThemes> = emptyMap(),
)

/** Public metadata only. Successful network results remain usable when persistence fails. */
class PersistentThemeCatalogs(private val storage:DocumentStorage):
    PortableComicThemeCache,PortableComicThemeCatalog,PortableNovelThemeCatalog {
    private val mutex=Mutex()
    private val json=Json{ignoreUnknownKeys=true;encodeDefaults=true}
    private var document=ThemeCatalogDocument()
    private var initialized=false
    private var readable=false
    private val mutableEntries=MutableStateFlow(document.comicEntries)
    private val mutableThemes=MutableStateFlow(document.comicThemes)
    private val mutableNovel=MutableStateFlow(document.novelThemes)
    private val mutableError=MutableStateFlow<String?>(null)
    override val entries=mutableEntries.asStateFlow()
    override val themes=mutableThemes.asStateFlow()
    val persistenceError=mutableError.asStateFlow()

    suspend fun initialize():Result<Unit> = mutex.withLock { initializeLocked() }
    private suspend fun initializeLocked():Result<Unit> {
        if(initialized)return if(readable)Result.success(Unit) else Result.failure(IllegalStateException(mutableError.value))
        return try {
            val restored=storage.read(KEY)?.let{json.decodeFromString(ThemeCatalogDocument.serializer(),it)}?:ThemeCatalogDocument()
            require(restored.comicThemes.all{it.name.isNotBlank()&&it.pathWord.isNotBlank()&&it.count!=null&&it.count>=0})
            require(restored.novelThemes.all{it.name.isNotBlank()&&it.path.isNotBlank()&&it.count!=null&&it.count>=0})
            document=restored.copy(comicEntries=restored.comicEntries.filter { (path,value)->path.isNotBlank()&&value.themes.all{it.name.isNotBlank()} })
            readable=true;initialized=true;publish();Result.success(Unit)
        } catch(error:CancellationException) { throw error
        } catch(error:Exception) {
            initialized=true;readable=false;mutableError.value="标签缓存读取失败，原数据已保留";Result.failure(error)
        }
    }
    override fun get(mode:RequestMode)=mutableNovel.value.ifEmpty { NovelThemeDefaults.themes }
    override suspend fun save(path:String,themes:CachedComicThemes)=update { it.copy(comicEntries=it.comicEntries+(path to themes)) }
    override suspend fun save(themes:List<ComicFilterOption>)=update { it.copy(comicThemes=themes) }
    override suspend fun save(mode:RequestMode,themes:List<NovelTag>)=update { it.copy(novelThemes=themes) }
    private suspend fun update(transform:(ThemeCatalogDocument)->ThemeCatalogDocument)=mutex.withLock {
        if(!initialized)initializeLocked()
        document=transform(document);publish()
        if(readable)try {
            storage.write(KEY,json.encodeToString(ThemeCatalogDocument.serializer(),document))
            mutableError.value=null
        } catch(error:CancellationException) { throw error
        } catch(_:Exception) { mutableError.value="标签缓存暂时无法落盘，当前会话仍使用最新标签" }
    }
    private fun publish() {
        mutableEntries.value=document.comicEntries
        mutableThemes.value=document.comicThemes
        mutableNovel.value=document.novelThemes
    }
    private companion object { const val KEY="shared-public-theme-catalogs" }
}
