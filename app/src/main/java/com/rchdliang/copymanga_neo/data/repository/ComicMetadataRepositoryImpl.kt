package com.rchdliang.copymanga_neo.data.repository

import android.util.Log
import com.google.gson.Gson
import com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore
import com.rchdliang.copymanga_neo.data.local.db.ComicCardCacheDao
import com.rchdliang.copymanga_neo.data.local.entity.ComicCardCacheEntity
import com.rchdliang.copymanga_neo.data.remote.ThemeRequestScheduler
import com.rchdliang.copymanga_neo.data.remote.api.MangaApiService
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDetailResult
import com.rchdliang.copymanga_neo.domain.model.ComicCardMetadata
import com.rchdliang.copymanga_neo.domain.repository.ComicMetadataRepository
import com.rchdliang.copymanga_neo.domain.repository.RiskControlException
import com.rchdliang.copymanga_neo.domain.repository.RiskControlRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** 限流、去重及落盘由所有列表和详情入口共用，界面退出不会中断其他消费者。 */
@Singleton
class ComicMetadataRepositoryImpl @Inject constructor(
    private val api: MangaApiService,
    private val settings: NetworkSettingsStore,
    private val dao: ComicCardCacheDao,
    private val risk: RiskControlRepository,
    private val auth: com.rchdliang.copymanga_neo.data.local.AuthDataStore,
    private val legacyThemes: com.rchdliang.copymanga_neo.data.local.ComicThemeCache
) : ComicMetadataRepository {
    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Log.w("ComicMetadata", "详情预加载已停止", e)
        })
    private val gson = Gson()
    private val scheduler = ThemeRequestScheduler(settings.settings, { settings.settings.value })
    private val _metadata = MutableStateFlow<Map<String, ComicCardMetadata>>(emptyMap())
    override val metadata = _metadata.asStateFlow()
    private val _failures = MutableStateFlow<Map<String, String>>(emptyMap())
    override val failures = _failures.asStateFlow()
    private val legacyScope = scopeKey()
    private val _cacheScope = MutableStateFlow(scopeKey())
    private val requestScope = MutableStateFlow("${scopeKey()}:${settings.generation}")
    override val cacheScope = requestScope.asStateFlow()
    private var observedGeneration = settings.generation
    private val restored = MutableStateFlow(false)
    private val queued = ConcurrentHashMap.newKeySet<String>()
    private val retryAfter = ConcurrentHashMap<String, Long>()
    private val consecutiveFailures = java.util.concurrent.atomic.AtomicInteger()

    @Volatile
    private var stoppedUntil = 0L

    private data class Ticket(val path: String, val scopeKey: String, val generation: Long)

    private val queue = Channel<Ticket>(Channel.UNLIMITED)

    // 仅短期保留完整响应供同时到达的请求复用；账号相关字段不写入磁盘。
    private val recent = ConcurrentHashMap<String, Pair<Long, ComicDetailResult>>()

    private fun scopeKey(): String = settings.settings.value.let {
        "${it.requestMode}:${it.apiHost}:${it.selection}:${it.fixedHost}:${it.routes.getOrNull(it.routeIndex)}|"
    }

    init {
        scope.launch {
            combine(dao.observeAll().catch { error ->
                if (error is CancellationException) throw error
                Log.w("ComicMetadata", "缓存读取失败，按首次加载处理", error)
                emit(emptyList())
            }, settings.settings, legacyThemes.entries) { rows, _, legacy ->
                Triple(
                    scopeKey(),
                    rows,
                    legacy
                )
            }
                .collect { (key, rows, legacy) ->
                    if (_cacheScope.value != key || observedGeneration != settings.generation) {
                        if (_cacheScope.value != key) _metadata.value = emptyMap()
                        _failures.value = emptyMap()
                        recent.clear()
                        retryAfter.clear()
                        stoppedUntil = 0L
                        consecutiveFailures.set(0)
                        _cacheScope.value = key
                        observedGeneration = settings.generation
                    }
                    // 旧版本只保存题材：在升级时的来源下继续用于判定，不把它当作新详情。
                    val inherited = if (key == legacyScope) legacy.mapValues { (path, entry) ->
                        ComicCardMetadata(path, entry.themes, null, null, null, 0L)
                    } else emptyMap()
                    val stored = rows.filter { it.cacheKey.startsWith(key) }.mapNotNull { row ->
                        try {
                            gson.fromJson(row.metadataJson, ComicCardMetadata::class.java)
                                ?.let { it.pathWord to it }
                        } catch (e: Exception) {
                            Log.w("ComicMetadata", "忽略损坏的展示缓存", e)
                            null
                        }
                    }.toMap()
                    _metadata.update { memory ->
                        inherited + stored + memory.filterValues {
                            (stored[it.pathWord]?.fetchedAt ?: 0L) < it.fetchedAt
                        }
                    }
                    restored.value = true
                    requestScope.value = "$key:$observedGeneration"
                }
        }
        repeat(6) {
            scope.launch {
                for (ticket in queue) {
                    try {
                        risk.notice.first { it == null }
                        if (ticket.generation == settings.generation && ticket.scopeKey == scopeKey()) {
                            load(ticket.path, false)
                        }
                    } finally {
                        queued.remove("${ticket.generation}:${ticket.scopeKey}${ticket.path}")
                    }
                }
            }
        }
    }

    override fun preload(paths: List<String>, priority: Boolean) {
        scope.launch {
            restored.first { it }
            paths.filter { it.isNotBlank() && !it.startsWith("novel:") }.distinct()
                .forEach { path ->
                    val generation = settings.generation
                    val key = scopeKey() + path
                    val ticketKey = "$generation:$key"
                    if (stoppedUntil > System.currentTimeMillis()) {
                        _failures.update { it + (path to "预加载暂缓，请重试") }
                        return@forEach
                    }
                    if ((_cacheScope.value != scopeKey() || (_metadata.value[path]?.isFresh(System.currentTimeMillis()) != true || _metadata.value[path]?.themes == null)) &&
                        (retryAfter[key] ?: 0L) <= System.currentTimeMillis() && queued.add(
                            ticketKey
                        )
                    ) {
                        if (priority) scope.launch {
                            try {
                                load(path, false, priority = true)
                            } finally {
                                queued.remove(ticketKey)
                            }
                        } else queue.send(Ticket(path, scopeKey(), generation))
                    } else if (priority && queued.contains(ticketKey)) {
                        scope.launch { load(path, false, priority = true) }
                    }
                }
        }
    }

    override fun retry(path: String) {
        retryAfter.clear()
        stoppedUntil = 0L
        consecutiveFailures.set(0)
        val waiting = _failures.value.keys.toList()
        scope.launch { load(path, true, priority = true) }
        preload(waiting.filter { it != path })
    }

    /** 进入详情页正常刷新；与同时进行的预加载合并，不重复发送同一详情请求。 */
    suspend fun detail(path: String): Result<ComicDetailResult> = load(path, true, priority = true)

    suspend fun themes(path: String, refresh: Boolean): Result<List<com.rchdliang.copymanga_neo.domain.model.Theme>> {
        restored.first { it }
        val generation = settings.generation
        val cached = _metadata.value[path].takeIf { _cacheScope.value == scopeKey() }
        if (!refresh || cached?.isFresh(System.currentTimeMillis()) == true) {
            cached?.themes?.let { return Result.success(it) }
        }
        return load(path, false).fold(
            onSuccess = { result -> result.comicDetail.theme?.map { it.toTheme() }?.let { Result.success(it) }
                ?: Result.failure(Exception("漫画详情未返回标签信息")) },
            onFailure = { error ->
                val current = _metadata.value[path]
                if (generation == settings.generation && _cacheScope.value == scopeKey() &&
                    current?.isFresh(System.currentTimeMillis()) == true && current.themes != null)
                    Result.success(current.themes)
                else Result.failure(error)
            })
    }

    private suspend fun load(
        path: String,
        force: Boolean,
        priority: Boolean = false
    ): Result<ComicDetailResult> {
        restored.first { it }
        val generation = settings.generation
        val key = scopeKey() + path
        val started = System.currentTimeMillis()
        val authGeneration = auth.getAuthGeneration()
        val requestKey = "$key:$generation:$authGeneration"
        return scheduler.withKey(requestKey) {
            if (generation != settings.generation || authGeneration != auth.getAuthGeneration()) {
                return@withKey Result.failure(IllegalStateException("账号或网络设置已变化，请重试"))
            }
            if (!force && stoppedUntil > System.currentTimeMillis()) {
                _failures.update { it + (path to "预加载暂缓，请重试") }
                return@withKey Result.failure(Exception("详情预加载暂缓"))
            }
            recent[requestKey]?.takeIf { it.first >= started }
                ?.let { return@withKey Result.success(it.second) }
            if (!force && _cacheScope.value == scopeKey() && _metadata.value[path]?.isFresh(System.currentTimeMillis()) == true && _metadata.value[path]?.themes != null) {
                return@withKey Result.failure(Exception("已有有效展示缓存"))
            }
            if (!force && (retryAfter[key] ?: 0L) > System.currentTimeMillis()) {
                return@withKey Result.failure(Exception("详情请求暂缓"))
            }
            try {
                scheduler.request(priority) {
                    check(generation == settings.generation && key == scopeKey() + path) { "网络设置已变化" }
                    check(authGeneration == auth.getAuthGeneration()) { "账号状态已变化，请重试" }
                    // 排队期间其他请求可能已触发失败退避，不能继续消耗整个队列。
                    if (!force && stoppedUntil > System.currentTimeMillis()) {
                        _failures.update { it + (path to "预加载暂缓，请重试") }
                        return@request Result.failure(Exception("详情预加载暂缓"))
                    }
                    val response = withTimeout(8_000) { api.getComicDetail(path) }
                    val body = response.body()
                    if (response.code() == 210 || body?.code == 210) throw RiskControlException(
                        body?.message ?: "访问暂受限制"
                    )
                    check(response.isSuccessful && body?.code == 200) {
                        body?.message ?: "详情加载失败"
                    }
                    val result = requireNotNull(body).results
                    check(generation == settings.generation) { "网络设置已变化" }
                    val comic = result.comicDetail
                    val previous = _metadata.value[path].takeIf { _cacheScope.value == scopeKey() }
                    val value = ComicCardMetadata(
                        path,
                        comic.theme?.map { it.toTheme() } ?: previous?.themes,
                        comic.brief ?: previous?.brief,
                        comic.popular ?: result.popular,
                        comic.lastUpdate,
                        System.currentTimeMillis())
                    settings.ifCurrent(generation) {
                        _metadata.update { it + (path to value) }
                        _failures.update { it - path }
                    }
                    consecutiveFailures.set(0)
                    retryAfter.remove(key)
                    if (value.themes == null) {
                        _failures.update { it + (path to "详情未返回标签，请重试") }
                        retryAfter[key] = System.currentTimeMillis() + 15 * 60_000
                    }
                    recent.entries.removeIf { System.currentTimeMillis() - it.value.first > 30_000 }
                    recent[requestKey] = value.fetchedAt to result
                    try {
                        dao.save(ComicCardCacheEntity(key, gson.toJson(value)))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("ComicMetadata", "展示缓存暂时无法落盘", e)
                    }
                    if (force && authGeneration != auth.getAuthGeneration()) {
                        Result.failure(IllegalStateException("账号状态已变化，请重试"))
                    } else Result.success(result)
                }
            } catch (e: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                if (generation == settings.generation) {
                    _failures.update { it + (path to "详情加载超时") }
                    retryAfter[key] = System.currentTimeMillis() + 15 * 60_000
                }
                if (generation == settings.generation && consecutiveFailures.incrementAndGet() >= 3) stoppedUntil =
                    System.currentTimeMillis() + 15 * 60_000
                Result.failure(Exception("详情加载超时", e))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == settings.generation) {
                    _failures.update { it + (path to (e.message ?: "详情加载失败")) }
                    retryAfter[key] = System.currentTimeMillis() + 15 * 60_000
                }
                if (generation == settings.generation && consecutiveFailures.incrementAndGet() >= 3) {
                    stoppedUntil = System.currentTimeMillis() + 15 * 60_000
                }
                Result.failure(e)
            }
        }
    }
}
