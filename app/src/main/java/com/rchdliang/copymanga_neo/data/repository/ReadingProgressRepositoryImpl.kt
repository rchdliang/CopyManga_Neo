package com.rchdliang.copymanga_neo.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.domain.model.BrowseInfo
import com.rchdliang.copymanga_neo.domain.model.ReadingProgress
import com.rchdliang.copymanga_neo.domain.model.ReadingProgressSnapshot
import com.rchdliang.copymanga_neo.domain.model.readingProgressOwner
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.ReadingProgressRepository
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

private val Context.readingProgressDataStore by preferencesDataStore(name = "reading_progress")

/** 先保存真实阅读，再异步上报；服务器确认前，旧响应不能覆盖本机新进度。 */
@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReadingProgressRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val mangaRepository: MangaRepository,
    private val userRepository: UserRepository
) : ReadingProgressRepository {
    private val store = context.readingProgressDataStore
    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            Log.e("ReadingProgress", "阅读进度操作失败", error)
        })
    private val versions = AtomicLong(System.currentTimeMillis())
    private val syncRequests = Channel<Unit>(Channel.CONFLATED)
    private val syncMutex = Mutex()
    private val owners = flow {
        userRepository.isAuthCacheReady().first { it }
        combine(userRepository.isLoggedIn(), userRepository.getUsername(), ::readingProgressOwner)
            .distinctUntilChanged().collect { emit(it) }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    override val currentOwner: String? get() = owners.value
    override val progress = owners.flatMapLatest { owner ->
        flow {
            emit(ReadingProgressSnapshot(owner))
            if (owner != null) store.data.map { decode(it, owner) }.collect {
                emit(ReadingProgressSnapshot(owner, it, isReady = true))
            }
        }
    }.stateIn(scope, SharingStarted.Eagerly, ReadingProgressSnapshot())

    init {
        scope.launch {
            // 切换账号会取消旧会话的网络请求，不把 A 的阅读记录上报到 B。
            owners.collectLatest { owner ->
                if (owner == null) return@collectLatest
                syncRequests.trySend(Unit)
                for (ignored in syncRequests) syncMutex.withLock { syncPending(owner) }
            }
        }
    }

    override suspend fun awaitOwner(): String = owners.filterNotNull().first()
    override fun requestVersion(): Long =
        versions.updateAndGet { maxOf(it + 1, System.currentTimeMillis()) }

    override fun recordRead(
        owner: String,
        comicPathWord: String,
        chapterUuid: String,
        chapterName: String,
        group: String
    ) {
        if (owner != currentOwner || comicPathWord.isBlank() || chapterUuid.isBlank()) return
        val version = requestVersion()
        // 使用应用级作用域，退出阅读器不会取消尚未落盘或上报的进度。
        scope.launch {
            store.edit { prefs ->
                if (version <= (prefs[clearKey(owner)] ?: 0L)) return@edit
                val records = decode(prefs, owner).toMutableMap()
                val previous = records[comicPathWord]
                if (previous == null || previous.version < version) {
                    records[comicPathWord] = ReadingProgress(
                        chapterUuid, chapterName, group, version,
                        pendingSync = owner != "guest"
                    )
                    prefs[key(owner)] = encode(records)
                }
            }
            retryPending()
        }
    }

    override suspend fun acceptRemote(
        owner: String,
        records: Map<String, BrowseInfo?>,
        requestVersion: Long
    ): Result<Unit> = result {
        if (owner != currentOwner) return@result
        store.edit { prefs ->
            if (requestVersion <= (prefs[clearKey(owner)] ?: 0L)) return@edit
            val local = decode(prefs, owner).toMutableMap()
            records.forEach { (path, browse) ->
                val previous = local[path]
                if (previous != null && previous.version >= requestVersion) return@forEach
                if (previous?.pendingSync == true && previous.chapterUuid != browse?.chapterUuid) return@forEach
                val uuid = browse?.chapterUuid?.takeIf { it.isNotBlank() }
                if (uuid == null) local.remove(path) else {
                    local[path] = ReadingProgress(
                        uuid, browse.chapterName.orEmpty().ifBlank {
                        previous?.takeIf { it.chapterUuid == uuid }?.chapterName.orEmpty()
                    }, previous?.takeIf { it.chapterUuid == uuid }?.groupPathWord,
                        requestVersion, pendingSync = false,
                        lastReadGroupPathWord = previous?.lastReadGroupPathWord
                    )
                }
            }
            prefs[key(owner)] = encode(local)
        }
    }

    override fun retryPending() {
        syncRequests.trySend(Unit)
    }

    private suspend fun syncPending(owner: String) {
        if (owner == "guest" || owner != currentOwner) return
        val pending = decode(store.data.first(), owner).filterValues { it.pendingSync }
        for ((path, read) in pending) {
            if (owner != currentOwner) return
            if (decode(store.data.first(), owner)[path] != read) continue
            mangaRepository.trialChapterContent(path, read.chapterUuid).fold(
                onSuccess = {
                    val version = requestVersion()
                    mangaRepository.queryComicStatus(path).fold(
                        onSuccess = { acceptRemote(owner, mapOf(path to it.browse), version) },
                        onFailure = { Log.d("ReadingProgress", "云端确认暂不可用，保留本地进度") }
                    )
                },
                onFailure = { Log.d("ReadingProgress", "阅读进度尚未同步，将在返回或刷新时重试") }
            )
        }
    }

    override suspend fun clearHistory(): Result<Unit> = result {
        val owner = awaitOwner()
        val cutoff = requestVersion()
        syncMutex.withLock {
            if (owner != currentOwner) error("账号已切换，请重试")
            mangaRepository.clearBrowseHistory().fold(
                onSuccess = {
                    store.edit { prefs ->
                        prefs[clearKey(owner)] = cutoff
                        prefs[key(owner)] =
                            encode(decode(prefs, owner).filterValues { it.version > cutoff })
                    }
                },
                onFailure = { throw it }
            )
        }
    }

    private fun key(owner: String) = stringPreferencesKey("owner:$owner")
    private fun clearKey(owner: String) = longPreferencesKey("cleared:$owner")
    private fun decode(prefs: Preferences, owner: String): Map<String, ReadingProgress> {
        val raw = prefs[key(owner)] ?: return emptyMap()
        return JsonParser.parseString(raw).asJsonObject.entrySet().associate { (path, value) ->
            val entry = value.asJsonObject
            path to ReadingProgress(
                entry["chapter"].asString, entry["name"].asString,
                entry["group"]?.takeUnless { it.isJsonNull }?.asString, entry["version"].asLong,
                entry["pending"].asBoolean,
                (entry["last_read_group"] ?: entry["group"])?.takeUnless { it.isJsonNull }?.asString
            )
        }
    }

    private fun encode(records: Map<String, ReadingProgress>) = JsonObject().apply {
        records.forEach { (path, read) ->
            add(path, JsonObject().apply {
                addProperty("chapter", read.chapterUuid)
                addProperty("name", read.chapterName)
                addProperty("group", read.groupPathWord)
                addProperty("last_read_group", read.lastReadGroupPathWord)
                addProperty("version", read.version)
                addProperty("pending", read.pendingSync)
            })
        }
    }.toString()

    private suspend fun result(block: suspend () -> Unit): Result<Unit> = try {
        block()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
