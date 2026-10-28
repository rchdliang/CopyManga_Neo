package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Clock

/** 合并本机和云端续读位置，保留真实追更资格；按作品最新章节判断跨组更新。 */
class PortableShelfReading(
    storage: DocumentStorage,
    private val sessions: ReadingSessionRepository,
    private val reading: LocalReadingRepository,
    private val scope: CoroutineScope,
    private val remoteProgress: ReadingProgressRepository? = null,
    private val loadCatalog: suspend (ShelfReadingKey) -> Result<List<ShelfChapter>>
) : ShelfReadingRepository {
    private val document = DocumentStore(storage, "shelf-reading.json", ShelfReadingSnapshot.serializer(), ShelfReadingSnapshot(),
        schemaVersion = SCHEMA_VERSION, migrations = migrations)
    override val snapshot = document.state
    override val error = document.error
    private val operations = Mutex()
    private val requests = mutableSetOf<String>()
    private val checked = mutableSetOf<Pair<Long, String>>()
    private val slots = Semaphore(2)
    private data class ReadEvent(val session: LocalReadingSession, val reading: LocalReadingRecord, val deletion: Long)
    private val events = Channel<ReadEvent>(Channel.UNLIMITED)
    private data class LocalRead(val version: Long, val chapter: String)
    private val localReads = MutableStateFlow<Map<String, LocalRead>>(emptyMap())

    init {
        scope.launch {
            document.ensureLoaded()
            reading.snapshot.collect { pruneDeleted() }
        }
        // 正文事件按上报顺序处理，避免“完成旧章”晚于“开始新章”落盘而重新点亮 badge。
        scope.launch { for (event in events) accept(event) }
        // 收藏和详情接口也能更新云端续读章节，已有书架状态必须跟随同一进度源。
        remoteProgress?.let { progress ->
            scope.launch {
                document.ensureLoaded()
                combine(progress.progress, reading.snapshot) { remote, local -> remote to local }.collect { (remote, local) ->
                    val session = sessions.getReadingSessionSync() ?: return@collect
                    val owner = session.owner ?: return@collect
                    if (!remote.isReady || remote.error != null || remote.owner != owner || owner == "guest") return@collect
                    val books = snapshot.value.books(session, "comic")
                    val positions = local.records.filter { it.owner == owner && it.kind == "comic" }
                        .groupBy { it.path }.mapValues { (_, records) -> records.maxBy { it.updatedAt } }
                    val changed = remote.records.filter { (path, position) ->
                        val localPosition = positions[path]
                        val realCompletion = localPosition?.let { !it.cloudImported && it.completed } == true
                        (!position.pendingSync || localPosition?.cloudImported == false) &&
                            (books[path]?.let { it.chapter != position.chapterUuid || realCompletion && !it.completed ||
                                localPosition?.cloudImported == false && it.chapterName != localPosition.name }
                                ?: (localPosition?.cloudImported == false)) &&
                            localPosition?.chapter == position.chapterUuid &&
                            acceptsProgress(session, path, position) &&
                            reading.permitsCoarse(owner, "comic", path)
                    }
                    if (changed.isEmpty()) return@collect
                    val items = changed.map { (path, position) ->
                        BrowseHistoryItem(0, "", path, null, emptyList(),
                            position.chapterUuid, position.chapterName, null, null)
                    }
                    importCloudHistory(session, items) { item ->
                        current(session) && progress.progress.value.owner == owner &&
                            progress.progress.value.records[item.pathWord] == changed[item.pathWord] &&
                            reading.latest(owner, "comic", item.pathWord)?.chapter == item.lastReadChapterId &&
                            acceptsProgress(session, item.pathWord, changed.getValue(item.pathWord)) &&
                            reading.permitsCoarse(owner, "comic", item.pathWord)
                    }.onSuccess {
                        refresh(session, "comic", changed.keys)
                        // 恢复真实完成记录时重新核对目录，不依赖旧书架保存的最新章指针。
                        snapshot.value.books(session, "comic").values
                            .filter { it.key.path in changed && it.completed && it.caughtUpChapter == null }
                            .forEach { record -> scope.launch { checkCatalog(session, record.key) } }
                    }
                }
            }
        }
    }

    private fun current(session: LocalReadingSession) = sessions.getReadingSessionSync() == session
    private fun acceptsProgress(session: LocalReadingSession, path: String, position: ReadingProgress): Boolean {
        val owner = session.owner ?: return false
        val local = localReads.value[ShelfReadingKey(owner, session.mode, "comic", path).bookKey] ?: return true
        return position.chapterUuid == local.chapter || position.version > local.version
    }
    private fun survives(record: ShelfReadingRecord): Boolean = record.historyCutoff >= deletion(record.key)
    private suspend fun pruneDeleted() = operations.withLock {
        document.update { it.copy(records = it.records.filterValues(::survives)) }
    }

    override fun record(session: LocalReadingSession, reading: LocalReadingRecord) {
        if (!current(session) || session.owner != reading.owner || reading.chapter.isBlank()) return
        val key = ShelfReadingKey(reading.owner, session.mode, reading.kind, reading.path,
            if (reading.kind == "comic") reading.group else "")
        // 在排队落盘之前立即作废旧快照；其他作品的同步也会重发整张进度表。
        if (reading.kind == "comic") remoteProgress?.let { progress ->
            val version = progress.requestVersion()
            localReads.update { it + (key.bookKey to LocalRead(version, reading.chapter)) }
        }
        events.trySend(ReadEvent(session, reading, deletion(key)))
    }

    private suspend fun accept(event: ReadEvent) {
        val (session, reading, deletionAtStart) = event
        val key = ShelfReadingKey(reading.owner, session.mode, reading.kind, reading.path,
            if (reading.kind == "comic") reading.group else "")
        var needsCheck = false
        operations.withLock {
            if (!current(session) || deletion(key) != deletionAtStart) return@withLock
            document.update { state ->
                val latest = state.books(session, reading.kind)[reading.path]
                val old = latest?.takeIf(::survives) ?: ShelfReadingRecord(key)
                val changed = old.chapter != reading.chapter || (reading.completed && !old.completed) ||
                    latest?.key != key
                needsCheck = (session.generation to key.id) !in checked ||
                        (changed && (reading.chapter !in old.catalog || reading.completed &&
                            reading.chapter == old.latestChapterId.ifBlank { old.catalog.lastOrNull().orEmpty() }))
                if (!changed) state else {
                    val at = maxOf(Clock.System.now().toEpochMilliseconds(), (latest?.readAt ?: 0) + 1)
                    state.copy(records = state.records.filterValues { it.key.bookKey != key.bookKey } +
                        (key.id to old.copy(key = key).read(reading.chapter, reading.name, reading.completed, at)
                        .copy(historyCutoff = deletionAtStart)))
                }
            }.onFailure { needsCheck = false }
        }
        if (needsCheck) scope.launch { checkCatalog(session, key) }
    }

    private fun deletion(key: ShelfReadingKey): Long = reading.snapshot.value.let {
        maxOf(it.clearedOwners[key.owner] ?: -1, it.deletedBooks[key.bookKey] ?: -1)
    }

    /** 合并已被统一进度仓库接受的云端章节，不推定完成或追平。 */
    suspend fun importCloudHistory(session: LocalReadingSession, items: List<BrowseHistoryItem>,
        eligible: (BrowseHistoryItem) -> Boolean): Result<Unit> = operations.withLock {
        document.update { state ->
            if (!current(session) || session.owner == null) return@update state
            val records = state.records.toMutableMap()
            items.forEach { item ->
                val chapter = item.lastReadChapterId?.takeIf { it.isNotBlank() } ?: return@forEach
                val key = ShelfReadingKey(session.owner, session.mode, "comic", item.pathWord)
                if (!eligible(item)) return@forEach
                val old = records.values.filter { it.key.bookKey == key.bookKey && survives(it) }
                    .maxByOrNull { it.readAt }?.withoutCloudAssumption() ?: ShelfReadingRecord(key)
                val precise = reading.latest(session.owner, "comic", item.pathWord)
                    ?.takeIf { it.chapter == chapter && !it.cloudImported }
                val name = precise?.name?.takeIf { it.isNotBlank() } ?: item.lastReadChapterName.orEmpty()
                var next = old.copy(
                    latestChapterId = item.latestChapterId?.takeIf { it.isNotBlank() } ?: old.latestChapterId,
                    latestName = item.latestChapterName?.takeIf { it.isNotBlank() } ?: old.latestName,
                    historyCutoff = deletion(key))
                // 同一云端章节的重复拉取不能再次消费之后出现的新提醒。
                if (old.chapter != chapter) {
                    next = next.copy(key = key).read(chapter, name, precise?.completed == true,
                        maxOf(Clock.System.now().toEpochMilliseconds(), old.readAt + 1))
                } else {
                    next = next.copy(chapterName = name.ifBlank { old.chapterName },
                        completed = old.completed || precise?.completed == true)
                }
                records.entries.removeAll { it.value.key.bookKey == key.bookKey }
                records[next.key.id] = next
            }
            state.copy(records = records)
        }
    }

    override fun refresh(session: LocalReadingSession, kind: String, paths: Set<String>) {
        scope.launch {
            if (document.ensureLoaded().isFailure || !current(session)) return@launch
            pruneDeleted()
            snapshot.value.books(session, kind).values.filter {
                it.key.path in paths && (it.caughtUpChapter != null ||
                    it.completed && (it.chapter == it.latestChapterId.ifBlank { it.catalog.lastOrNull().orEmpty() } || it.chapter !in it.catalog))
            }.forEach { record -> launch { checkCatalog(session, record.key.copy(mode = session.mode)) } }
        }
    }

    private suspend fun checkCatalog(session: LocalReadingSession, key: ShelfReadingKey) {
        val requestKey = "${session.generation}:${key.id}"
        if (!operations.withLock { current(session) && requests.add(requestKey) }) return
        try {
            slots.withPermit {
                if (!current(session)) return@withPermit
                operations.withLock { checked += session.generation to key.id }
                val result = try { loadCatalog(key) }
                catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) { Result.failure(error) }
                val chapters = result.getOrNull() ?: return@withPermit
                operations.withLock {
                    if (!current(session)) return@withLock
                    document.update { state ->
                        val old = state.records[key.id]?.takeIf(::survives) ?: return@update state
                        state.copy(records = state.records + (key.id to old.copy(key = key).observeCatalog(chapters)))
                    }
                }
            }
        } finally { operations.withLock { requests.remove(requestKey) } }
    }

    companion object {
        const val SCHEMA_VERSION = 3
        val migrations: Map<Int, (kotlinx.serialization.json.JsonElement) -> kotlinx.serialization.json.JsonElement> = mapOf(
            1 to { payload ->
                storageJson.encodeToJsonElement(ShelfReadingSnapshot.serializer(),
                    storageJson.decodeFromJsonElement(ShelfReadingSnapshot.serializer(), payload).unified())
            },
            2 to { payload ->
                val previous = storageJson.decodeFromJsonElement(ShelfReadingSnapshot.serializer(), payload)
                storageJson.encodeToJsonElement(ShelfReadingSnapshot.serializer(),
                    previous.copy(records = previous.records.mapValues { it.value.withoutCloudAssumption() }))
            }
        )
    }
}
