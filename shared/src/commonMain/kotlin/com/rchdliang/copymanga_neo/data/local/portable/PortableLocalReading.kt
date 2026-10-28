package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.reading.ReadingMergePolicy
import com.rchdliang.copymanga_neo.domain.repository.LocalReadingRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/** Account-isolated, app-scope persistence; deletion cutoffs also fence in-flight position writes. */
class PortableLocalReading(storage: DocumentStorage, private val scope: CoroutineScope, initializeImmediately: Boolean = true) : LocalReadingRepository {
    private val document = DocumentStore(storage, "reading-library.json", LocalReadingSnapshot.serializer(), LocalReadingSnapshot(),
        validate = PortableBackupCodec::validateReading, maximumBytes = PortableBackupCodec.MAX_READING_BYTES)
    override val snapshot = document.state
    override val error = document.error
    private val pending = MutableStateFlow<Map<String, LocalReadingRecord>>(emptyMap())
    private val completed = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val versions = MutableStateFlow(Clock.System.now().toEpochMilliseconds())
    private val writes = Channel<Unit>(Channel.CONFLATED)
    private val operations = Mutex()
    private var initial: Result<Unit>? = null
    init {
        if (initializeImmediately) scope.launch { operations.withLock { initializeLocked() } }
        scope.launch { for (ignored in writes) { delay(120); flush() } }
    }
    private fun nextVersion() = versions.updateAndGet { maxOf(it + 1, Clock.System.now().toEpochMilliseconds()) }
    private fun observeVersions(s: LocalReadingSnapshot) {
        versions.update { old -> maxOf(old, (s.records + s.bookmarks).maxOfOrNull { it.updatedAt } ?: 0,
            (s.deleted.values + s.deletedBooks.values + s.clearedOwners.values).maxOrNull() ?: 0) }
    }
    private suspend fun initializeLocked(): Result<Unit> = initial ?: loadLocked()
    private suspend fun loadLocked(): Result<Unit> {
        val firstSuccessfulLoad = initial?.isSuccess != true
        return document.load().also { result ->
            initial = result
            if (result.isSuccess) {
                observeVersions(snapshot.value)
                // User reading that arrived before disk initialization is newer than persisted tombstones.
                if (firstSuccessfulLoad) pending.update { records ->
                    records.values.sortedBy { it.updatedAt }.associate { record ->
                        record.key to record.copy(updatedAt = nextVersion())
                    }
                }
            }
        }
    }
    override suspend fun retryRead(): Result<Unit> = operations.withLock { loadLocked() }
    suspend fun awaitReady(): Result<Unit> = operations.withLock { initializeLocked() }
    private suspend fun mutate(transform: (LocalReadingSnapshot) -> LocalReadingSnapshot): Result<Unit> = operations.withLock {
        initializeLocked().exceptionOrNull()?.let { return@withLock Result.failure(it) }
        document.update(transform).onSuccess { observeVersions(snapshot.value) }
    }
    override fun position(owner: String, kind: String, path: String, chapter: String) = snapshot.value.records.firstOrNull {
        it.owner == owner && it.kind == kind && it.path == path && it.chapter == chapter
    }
    override fun latest(owner: String, kind: String, path: String) = snapshot.value.records.filter {
        it.owner == owner && it.kind == kind && it.path == path
    }.maxByOrNull { it.updatedAt }
    override fun permitsCoarse(owner: String, kind: String, path: String) = ReadingMergePolicy.canUseCoarse(
        owner, kind, path, snapshot.value.clearedOwners, snapshot.value.deletedBooks)

    /** 只补缺失作品；云端空结果、旧进度均不能覆盖本机位置、完成状态或书签。 */
    suspend fun importCloudHistory(owner: String, records: List<LocalReadingRecord>, current: () -> Boolean): Result<Unit> = mutate { s ->
        if (!current()) return@mutate s
        val occupied = (s.records + pending.value.values).map { it.bookKey }.toMutableSet()
        val additions = records.filter { r ->
            r.owner == owner && r.kind == "comic" && r.path.isNotBlank() && r.chapter.isNotBlank() &&
                ReadingMergePolicy.canUseCoarse(owner, r.kind, r.path, s.clearedOwners, s.deletedBooks) &&
                r.key !in s.deleted && occupied.add(r.bookKey)
        }.map { it.copy(completed = false, atChapterEnd = false, cloudImported = true, group = "") }
        s.copy(records = s.records + additions)
    }

    fun canAssumeCloudCatchUp(owner: String, path: String, chapter: String): Boolean {
        if (!permitsCoarse(owner, "comic", path)) return false
        if (pending.value.values.any { it.owner == owner && it.kind == "comic" && it.path == path }) return false
        val records = snapshot.value.records.filter { it.owner == owner && it.kind == "comic" && it.path == path }
        return records.isNotEmpty() && records.all { it.cloudImported } && records.any { it.chapter == chapter }
    }
    override fun record(value: LocalReadingRecord) {
        val record = value.copy(title = safeBookTitle(value.path, value.title), updatedAt = nextVersion())
        if (record.completed) completed.update { it + (record.key to record.updatedAt) }
        pending.update { old ->
            if ((old[record.key]?.updatedAt ?: -1) > record.updatedAt) old else old + (record.key to record)
        }
        writes.trySend(Unit)
    }
    override fun flushAsync() { scope.launch { flush() } }
    override suspend fun flush(): Result<Unit> = withContext(NonCancellable) {
        operations.withLock {
            initializeLocked().exceptionOrNull()?.let { return@withLock Result.failure(it) }
            val batch = pending.value
            if (batch.isEmpty()) return@withLock Result.success(Unit)
            document.update { s ->
                val records = s.records.associateBy { it.key }.toMutableMap()
                batch.values.forEach { r ->
                    val old = records[r.key]
                    val deletion = cutoff(s, r)
                    if (ReadingMergePolicy.accepts(r.updatedAt, old?.updatedAt ?: -1, deletion)) {
                        records[r.key] = r.copy(title = safeBookTitle(r.path, r.title, old?.title),
                            cover = r.cover.ifBlank { old?.cover.orEmpty() }, authors = r.authors.ifBlank { old?.authors.orEmpty() },
                            completed = r.completed || (completed.value[r.key] ?: -1) > deletion || old?.completed == true)
                    }
                }
                s.copy(records = records.values.toList())
            }.onSuccess {
                observeVersions(snapshot.value)
                pending.update { current -> current.filterNot { (key, value) -> batch[key] == value } }
            }
        }
    }
    private fun cutoff(s: LocalReadingSnapshot, r: LocalReadingRecord) = maxOf(
        s.deleted[r.key] ?: -1, s.deletedBooks[r.bookKey] ?: -1, s.clearedOwners[r.owner] ?: -1)
    override fun complete(owner: String, path: String, chapter: String) {
        val r = LocalReadingRecord(owner, "comic", path, chapter, "")
        val version = nextVersion()
        completed.update { old -> old + (r.key to maxOf(version, old[r.key] ?: -1)) }
        scope.launch { mutate { s ->
            if (!ReadingMergePolicy.survives(version, cutoff(s, r))) s
            else s.copy(records = s.records.map { if (it.key == r.key) it.copy(completed = true) else it })
        } }
    }
    override suspend fun toggleBookmark(r: LocalReadingRecord) = mutate { s ->
        fun same(value: LocalReadingRecord) = value.key == r.key && value.page == r.page && value.section == r.section && value.line == r.line && value.character == r.character
        s.copy(bookmarks = if (s.bookmarks.any(::same)) s.bookmarks.filterNot(::same)
            else s.bookmarks + r.copy(title = safeBookTitle(r.path, r.title)))
    }
    override suspend fun updateMetadata(owner: String, kind: String, path: String, title: String, cover: String, authors: String): Result<Unit> {
        fun enrich(r: LocalReadingRecord) = if (r.owner == owner && r.kind == kind && r.path == path) r.copy(
            title = safeBookTitle(path, title, r.title), cover = cover.ifBlank { r.cover }, authors = authors.ifBlank { r.authors }) else r
        return mutate { s ->
            pending.update { map -> map.mapValues { enrich(it.value) } }
            s.copy(records = s.records.map(::enrich), bookmarks = s.bookmarks.map(::enrich))
        }
    }
    override suspend fun removeBookmark(r: LocalReadingRecord) = mutate { it.copy(bookmarks = it.bookmarks - r) }
    override suspend fun removeHistory(r: LocalReadingRecord): Result<Unit> {
        var time = -1L
        return mutate { s ->
            val removed = s.records.filter { it.bookKey == r.bookKey }
            time = nextVersion()
            s.copy(records = s.records - removed.toSet(), deletedBooks = s.deletedBooks + (r.bookKey to time),
                deleted = s.deleted + removed.associate { it.key to time })
        }.onSuccess {
            completed.update { keys -> keys.filterNot { (key, version) -> key.startsWith(r.bookKey) && version <= time } }
            pending.update { records -> records.filterNot { (_, value) -> value.bookKey == r.bookKey && value.updatedAt <= time } }
        }
    }
    override suspend fun clearHistory(owner: String): Result<Unit> {
        var time = -1L
        return mutate { s ->
            val removed = s.records.filter { it.owner == owner }
            time = nextVersion()
            s.copy(records = s.records - removed.toSet(), clearedOwners = s.clearedOwners + (owner to time),
                deleted = s.deleted + removed.associate { it.key to time })
        }.onSuccess {
            completed.update { keys -> keys.filterNot { (key, version) -> key.startsWith("${owner.length}:$owner") && version <= time } }
            pending.update { records -> records.filterNot { (_, value) -> value.owner == owner && value.updatedAt <= time } }
        }
    }
    suspend fun merge(incoming: LocalReadingSnapshot) = mutate { current ->
        fun mergeTimes(a: Map<String, Long>, b: Map<String, Long>) = (a.keys + b.keys).associateWith { maxOf(a[it] ?: -1, b[it] ?: -1) }
        val merged = current.copy(deleted = mergeTimes(current.deleted, incoming.deleted),
            deletedBooks = mergeTimes(current.deletedBooks, incoming.deletedBooks), clearedOwners = mergeTimes(current.clearedOwners, incoming.clearedOwners))
        merged.copy(records = (current.records + incoming.records).filter { ReadingMergePolicy.survives(it.updatedAt, cutoff(merged, it)) }
            .groupBy { it.key }.values.map { values -> values.maxBy { it.updatedAt }.copy(completed = values.any { it.completed }) },
            bookmarks = (current.bookmarks + incoming.bookmarks).sortedByDescending { it.updatedAt }
                .distinctBy { listOf(it.key, it.page, it.section, it.line, it.character) })
    }
}
