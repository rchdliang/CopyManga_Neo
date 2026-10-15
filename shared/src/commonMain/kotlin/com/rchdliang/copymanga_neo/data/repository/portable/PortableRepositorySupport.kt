package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.domain.model.*
import kotlinx.coroutines.flow.*
import kotlin.time.Clock
import kotlin.time.TimeSource
import kotlinx.serialization.json.*

interface PortableComicThemeCache {
    val entries: StateFlow<Map<String, CachedComicThemes>>
    suspend fun save(path: String, themes: CachedComicThemes)
}
class MemoryComicThemeCache : PortableComicThemeCache {
    private val mutable=MutableStateFlow<Map<String,CachedComicThemes>>(emptyMap())
    override val entries=mutable.asStateFlow()
    override suspend fun save(path:String,themes:CachedComicThemes) { mutable.update { it+(path to themes) } }
}
internal class NetworkSettingsAdapter(val state: PortableNetworkState) {
    val generation get()=state.snapshot.generation
    val settings get()=MutableStateFlow(state.snapshot.settings)
    suspend fun ifCurrent(generation:Long,block:suspend ()->Unit) { if(this.generation==generation)block() }
}
internal class AuthStateAdapter(val client:CopyMangaClient) {
    private val state get()=client.state
    fun getTokenSync()=state.snapshot.session().token.takeIf { it.isNotBlank() }
    fun getAuthGeneration()=state.snapshot.generation
    suspend fun clearAuthSyncIfTokenMatches(token:String,mode:RequestMode,generation:Long) {
        client.invalidateSession(mode,generation,token)
    }
}
internal class BoundedLruMap<K,V>(private val maximum:Int,private val backing:LinkedHashMap<K,V> = linkedMapOf()): MutableMap<K,V> by backing {
    override fun get(key:K):V? { val value=backing.remove(key)?:return null;backing[key]=value;return value }
    override fun put(key:K,value:V):V? { val old=backing.remove(key);backing[key]=value;while(backing.size>maximum)backing.remove(backing.keys.first());return old }
}
private val clockOrigin=TimeSource.Monotonic.markNow()
internal fun monotonicNanos()=clockOrigin.elapsedNow().inWholeNanoseconds
internal fun currentTimeMillis()=Clock.System.now().toEpochMilliseconds()
internal object TimeUnit {
    object SECONDS { fun toNanos(v:Long)=v*1_000_000_000L }
    object DAYS { fun toMillis(v:Long)=v*86_400_000L }
    object MINUTES { fun toNanos(v:Long)=v*60_000_000_000L }
    object MILLISECONDS { fun toNanos(v:Long)=v*1_000_000L }
    object NANOSECONDS { fun toMillis(v:Long)=v/1_000_000L }
}
internal fun jsonMessage(text:String?):String? = text?.let { runCatching { Json.parseToJsonElement(it).jsonObject["message"]?.jsonPrimitive?.contentOrNull }.getOrNull() }

/** Inject a durable implementation bound to the completed-image download store. */
interface PortableDownloadedCommentStore {
    suspend fun hasCompleteChapter(uuid:String):Boolean
    suspend fun read(uuid:String):DownloadedChapterComments?
    suspend fun save(uuid:String,value:DownloadedChapterComments)
}
