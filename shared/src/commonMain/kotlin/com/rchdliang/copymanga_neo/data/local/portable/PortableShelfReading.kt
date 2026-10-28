package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Clock

/** 单独保存追更基线，旧的“最后读到哪章”不会在迁移时生成更新提醒。 */
class PortableShelfReading(
    storage: DocumentStorage,
    private val sessions: ReadingSessionRepository,
    private val reading: LocalReadingRepository,
    private val scope: CoroutineScope,
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

    init {
        scope.launch {
            document.ensureLoaded()
            reading.snapshot.collect { pruneDeleted() }
        }
        // 正文事件按上报顺序处理，避免“完成旧章”晚于“开始新章”落盘而重新点亮 badge。
        scope.launch { for (event in events) accept(event) }
    }

    private fun current(session: LocalReadingSession) = sessions.getReadingSessionSync() == session
    private fun survives(record: ShelfReadingRecord): Boolean = record.historyCutoff >= deletion(record.key)
    private suspend fun pruneDeleted() = operations.withLock {
        document.update { it.copy(records = it.records.filterValues(::survives)) }
    }

    override fun record(session: LocalReadingSession, reading: LocalReadingRecord) {
        if (!current(session) || session.owner != reading.owner || reading.chapter.isBlank()) return
        val key = ShelfReadingKey(reading.owner, session.mode, reading.kind, reading.path,
            if (reading.kind == "comic") reading.group else "")
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
                val old = state.records[key.id]?.takeIf(::survives) ?: ShelfReadingRecord(key)
                val latest = state.books(session, reading.kind)[reading.path]
                val changed = old.chapter != reading.chapter || (reading.completed && !old.completed) ||
                    latest?.key != key
                needsCheck = (session.generation to key.id) !in checked ||
                    (changed && (reading.chapter !in old.catalog || reading.completed && reading.chapter == old.catalog.lastOrNull()))
                if (!changed) state else {
                    val at = maxOf(Clock.System.now().toEpochMilliseconds(), (latest?.readAt ?: 0) + 1)
                    state.copy(records = state.records + (key.id to old.copy(key = key).read(reading.chapter, reading.name, reading.completed, at)
                        .copy(historyCutoff = deletionAtStart)))
                }
            }.onFailure { needsCheck = false }
        }
        if (needsCheck) scope.launch { checkCatalog(session, key) }
    }

    private fun deletion(key: ShelfReadingKey): Long = reading.snapshot.value.let {
        maxOf(it.clearedOwners[key.owner] ?: -1, it.deletedBooks[key.bookKey] ?: -1)
    }

    override fun refresh(session: LocalReadingSession, kind: String, paths: Set<String>) {
        scope.launch {
            if (document.ensureLoaded().isFailure || !current(session)) return@launch
            pruneDeleted()
            snapshot.value.books(session, kind).values.filter {
                it.key.path in paths && (it.caughtUpChapter != null ||
                    it.completed && (it.chapter == it.catalog.lastOrNull() || it.chapter !in it.catalog))
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
        const val SCHEMA_VERSION = 2
        val migrations: Map<Int, (kotlinx.serialization.json.JsonElement) -> kotlinx.serialization.json.JsonElement> = mapOf(
            1 to { payload ->
                storageJson.encodeToJsonElement(ShelfReadingSnapshot.serializer(),
                    storageJson.decodeFromJsonElement(ShelfReadingSnapshot.serializer(), payload).unified())
            }
        )
    }
}
