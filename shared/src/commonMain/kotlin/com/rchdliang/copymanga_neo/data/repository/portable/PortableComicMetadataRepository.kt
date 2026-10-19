package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.data.remote.ThemeRequestScheduler
import com.rchdliang.copymanga_neo.data.remote.portable.CopyMangaClient
import com.rchdliang.copymanga_neo.data.remote.portable.NetworkSnapshot
import com.rchdliang.copymanga_neo.data.remote.portable.api.MangaApiService
import com.rchdliang.copymanga_neo.data.remote.portable.dto.ComicDetailResult
import com.rchdliang.copymanga_neo.domain.model.ComicCardMetadata
import com.rchdliang.copymanga_neo.domain.model.ThemeLoadingPolicy
import com.rchdliang.copymanga_neo.domain.repository.ComicMetadataRepository
import com.rchdliang.copymanga_neo.domain.repository.RiskControlException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 全平台共用详情补全、并发限制、失败退避和来源隔离；生命周期由应用图持有。 */
class PortableComicMetadataRepository(
    private val client: CopyMangaClient,
    private val cache: PortableComicCardCache? = null,
    private val legacyThemes: PortableComicThemeCache = MemoryComicThemeCache(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val now: () -> Long = ::currentTimeMillis
) : ComicMetadataRepository {
    private val api = MangaApiService(client)
    private val scheduler = ThemeRequestScheduler(client.state.snapshots.map { it.settings },
        { client.state.snapshot.settings })
    private val guard = Mutex()
    private val restoreGuard = Mutex()
    private var restored = false
    private var stored = emptyMap<String, ComicCardMetadata>()
    private val legacyScope = source(client.state.snapshot)
    private var observedGeneration = -1L
    private var observedSource = ""
    private val values = MutableStateFlow<Map<String, ComicCardMetadata>>(emptyMap())
    override val metadata = values.asStateFlow()
    private val errors = MutableStateFlow<Map<String, String>>(emptyMap())
    override val failures = errors.asStateFlow()
    private val sourceState = MutableStateFlow("")
    override val cacheScope = sourceState.asStateFlow()
    private val queued = mutableSetOf<String>()
    private val promoted = mutableSetOf<String>()
    private val retryAfter = mutableMapOf<String, Long>()
    private var stoppedUntil = 0L
    private var failureCount = 0
    private data class Ticket(val path: String, val snapshot: NetworkSnapshot) {
        val id get() = "${snapshot.generation}:$path"
    }
    private val queue = Channel<Ticket>(Channel.UNLIMITED)
    private var workersStarted = false
    private val recent = linkedMapOf<String, Pair<Long, ComicDetailResult>>()

    private fun source(snapshot: NetworkSnapshot): String = snapshot.settings.let {
        "${it.requestMode}:${it.apiHost}:${it.selection}:${it.fixedHost}:${it.routes.getOrNull(it.routeIndex)}|"
    }

    /** 首次读取延后到需要展示资料时，避免增加冷启动阻塞。 */
    private suspend fun prepare(snapshot: NetworkSnapshot) {
        restoreGuard.withLock {
            if (!restored) {
                stored = cache?.read().orEmpty()
                restored = true
            }
        }
        guard.withLock {
            client.state.requireCurrent(snapshot.generation)
            val key = source(snapshot)
            if (observedGeneration == snapshot.generation) return@withLock
            if (observedSource != key) {
                val inherited = if (key == legacyScope) legacyThemes.entries.value.mapValues { (path, entry) ->
                    ComicCardMetadata(path, entry.themes, null, null, null, 0L)
                } else emptyMap()
                values.value = inherited + stored.filterKeys { it.startsWith(key) }.values.associateBy { it.pathWord }
            }
            observedSource = key
            observedGeneration = snapshot.generation
            errors.value = emptyMap()
            recent.clear()
            retryAfter.clear()
            failureCount = 0
            stoppedUntil = 0
            sourceState.value = "$key:${snapshot.generation}"
        }
    }

    private suspend fun startWorkers() = guard.withLock {
        if (workersStarted) return@withLock
        workersStarted = true
        scope.launch {
            client.state.snapshots.map { it.generation }.distinctUntilChanged().collect {
                try { prepare(client.state.snapshot) }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { /* 快速切换来源时由下一次快照恢复。 */ }
            }
        }
        repeat(ThemeLoadingPolicy.MAX_CONCURRENCY) {
            scope.launch {
                for (ticket in queue) {
                    try {
                        client.risk.notice.first { it == null }
                        if (ticket.snapshot.generation == client.state.snapshot.generation) {
                            load(ticket.path, false, snapshot = ticket.snapshot)
                        }
                    } catch (cancel: CancellationException) {
                        // 线路切换/风控可取消单次 HTTP 子任务，但不能永久杀掉队列消费者。
                        currentCoroutineContext().ensureActive()
                    } finally { guard.withLock { queued.remove(ticket.id) } }
                }
            }
        }
    }

    override fun preload(paths: List<String>, priority: Boolean) {
        val snapshot = client.state.snapshot
        scope.launch {
            try {
                prepare(snapshot)
                startWorkers()
                paths.filter { it.isNotBlank() && !it.startsWith("novel:") }.distinct().forEach { path ->
                    val ticket = Ticket(path, snapshot)
                    val accepted = guard.withLock {
                        if (snapshot.generation != client.state.snapshot.generation) return@withLock false
                        val cached = values.value[path]
                        if (cached?.isFresh(now()) == true && cached.themes != null) return@withLock false
                        if (stoppedUntil > now()) {
                            errors.update { it + (path to "预加载暂缓，请重试") }
                            return@withLock false
                        }
                        if ((retryAfter[path] ?: 0) > now()) return@withLock false
                        if (priority) promoted.add(ticket.id) else queued.add(ticket.id)
                    }
                    if (accepted) {
                        if (priority) scope.launch {
                            try {
                                client.risk.notice.first { it == null }
                                load(path, false, true, snapshot)
                            } catch (cancel: CancellationException) {
                                currentCoroutineContext().ensureActive()
                            } finally { guard.withLock { promoted.remove(ticket.id) } }
                        } else queue.send(ticket)
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* 已过时的预加载不更新当前页面。 */ }
        }
    }

    override fun retry(path: String) {
        val snapshot = client.state.snapshot
        scope.launch {
            try {
                prepare(snapshot)
                val waiting = guard.withLock {
                    retryAfter.clear(); stoppedUntil = 0; failureCount = 0
                    errors.value.keys.toList()
                }
                client.risk.notice.first { it == null }
                load(path, true, true, snapshot)
                preload(waiting.filter { it != path })
            } catch (cancel: CancellationException) {
                currentCoroutineContext().ensureActive()
            } catch (_: Exception) { }
        }
    }

    suspend fun detail(path: String): Result<ComicDetailResult> =
        load(path, true, true, client.state.snapshot)

    suspend fun themes(path: String, refresh: Boolean): Result<List<com.rchdliang.copymanga_neo.domain.model.Theme>> {
        val snapshot = client.state.snapshot
        try { prepare(snapshot) }
        catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) { return Result.failure(error) }
        val cached = values.value[path]
        if (!refresh || cached?.isFresh(now()) == true) cached?.themes?.let { return Result.success(it) }
        return load(path, false, snapshot = snapshot).fold(
            onSuccess = { result -> result.comicDetail.theme?.map { it.toTheme() }?.let { Result.success(it) }
                ?: Result.failure(Exception("漫画详情未返回标签信息")) },
            onFailure = { error ->
                if (snapshot.generation == client.state.snapshot.generation &&
                    values.value[path]?.let { it.isFresh(now()) && it.themes != null } == true)
                    Result.success(values.value.getValue(path).themes.orEmpty())
                else Result.failure(error)
            })
    }

    private suspend fun load(
        path: String, force: Boolean, priority: Boolean = false,
        snapshot: NetworkSnapshot
    ): Result<ComicDetailResult> {
        if (path.isBlank()) return Result.failure(IllegalArgumentException("漫画标识为空"))
        val started = now()
        val requestKey = "${snapshot.generation}:$path"
        return try {
            prepare(snapshot)
            scheduler.withKey(requestKey) {
                client.state.requireCurrent(snapshot.generation)
                val cachedResult = guard.withLock {
                    recent[requestKey]?.takeIf { it.first >= started }?.second
                }
                if (cachedResult != null) return@withKey Result.success(cachedResult)
                suspend fun skip(): String? = guard.withLock {
                    when {
                        force -> null
                        stoppedUntil > now() -> "预加载暂缓，请重试"
                        (retryAfter[path] ?: 0) > now() -> "详情请求暂缓"
                        values.value[path]?.let { it.isFresh(now()) && it.themes != null } == true -> "已有有效展示缓存"
                        else -> null
                    }
                }
                skip()?.let {
                    if (it == "预加载暂缓，请重试") errors.update { current -> current + (path to it) }
                    return@withKey Result.failure(Exception(it))
                }
                scheduler.request(priority) {
                    client.state.requireCurrent(snapshot.generation)
                    skip()?.let {
                        if (it == "预加载暂缓，请重试") errors.update { current -> current + (path to it) }
                        return@request Result.failure(Exception(it))
                    }
                    val response = withTimeout(8_000) { api.getComicDetail(path) }
                    val body = response.body()
                    if (response.code() == 210 || body?.code == 210) throw RiskControlException(body?.message ?: "访问暂受限制")
                    check(response.isSuccessful && body?.code == 200) { body?.message ?: "详情加载失败" }
                    val result = requireNotNull(body).results
                    val comic = result.comicDetail
                    val key = source(snapshot) + path
                    val value = guard.withLock {
                        client.state.requireCurrent(snapshot.generation)
                        val previous = values.value[path]
                        ComicCardMetadata(path, comic.theme?.map { it.toTheme() } ?: previous?.themes,
                            comic.brief ?: previous?.brief, comic.popular ?: result.popular,
                            comic.lastUpdate, now()).also { value ->
                            values.update { it + (path to value) }
                            errors.update { it - path }
                            retryAfter.remove(path); failureCount = 0
                            if (value.themes == null) {
                                errors.update { it + (path to "详情未返回标签，请重试") }
                                retryAfter[path] = now() + 15 * 60_000
                            }
                            recent.entries.removeAll { now() - it.value.first > 30_000 }
                            recent[requestKey] = value.fetchedAt to result
                            while (recent.size > 32) recent.remove(recent.keys.first())
                            stored = stored + (key to value)
                        }
                    }
                    cache?.save(key, value)
                    client.state.requireCurrent(snapshot.generation)
                    Result.success(result)
                }
            }
        } catch (cancel: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            failed(snapshot, path, Exception("详情加载超时", cancel))
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) { failed(snapshot, path, error) }
    }

    private suspend fun failed(snapshot: NetworkSnapshot, path: String, error: Exception): Result<ComicDetailResult> {
        guard.withLock {
            if (snapshot.generation == client.state.snapshot.generation) {
                errors.update { it + (path to (error.message ?: "详情加载失败")) }
                retryAfter[path] = now() + 15 * 60_000
                if (++failureCount >= 3) stoppedUntil = now() + 15 * 60_000
            }
        }
        return Result.failure(error)
    }
}
