package com.rchdliang.copymanga_neo.data.local

import android.content.Context
import com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord
import com.rchdliang.copymanga_neo.domain.model.LocalReadingSnapshot
import com.rchdliang.copymanga_neo.domain.model.safeBookTitle
import com.rchdliang.copymanga_neo.domain.reading.ReadingMergePolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** Application scope survives reader navigation. Corrupt data is retained and never silently replaced. */
@Singleton
class LocalReadingStore @Inject constructor(@ApplicationContext context: Context) :
    com.rchdliang.copymanga_neo.domain.repository.LocalReadingRepository {
    private val prefs = context.getSharedPreferences("local_reading_library", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var initial = runCatching {
        prefs.getString("data", null)?.let(LocalReadingCodec::decode) ?: LocalReadingSnapshot()
    }
    private val mutable = MutableStateFlow(initial.getOrDefault(LocalReadingSnapshot()))
    override val snapshot = mutable.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(
        initial.exceptionOrNull()?.let { "阅读数据无法完整读取，原文件已保留" })
    override val error = mutableError.asStateFlow()
    private val versions = AtomicLong(
        maxOf(
            System.currentTimeMillis(),
            mutable.value.records.maxOfOrNull { it.updatedAt } ?: 0L,
            listOf(
                mutable.value.deleted,
                mutable.value.deletedBooks,
                mutable.value.clearedOwners
            ).flatMap { it.values }.maxOrNull() ?: 0L
        )
    )
    private val completedKeys = ConcurrentHashMap.newKeySet<String>()
    private fun nextVersion() = versions.updateAndGet { maxOf(it + 1, System.currentTimeMillis()) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun position(owner: String, kind: String, path: String, chapter: String) =
        snapshot.value.records.firstOrNull {
            it.owner == owner && it.kind == kind && it.path == path && it.chapter == chapter
        }

    override fun permitsCoarse(owner: String, kind: String, path: String) =
        ReadingMergePolicy.canUseCoarse(
            owner, kind, path, snapshot.value.clearedOwners, snapshot.value.deletedBooks
        )

    override suspend fun retryRead(): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val loaded = runCatching {
                prefs.getString("data", null)?.let(LocalReadingCodec::decode)
                    ?: LocalReadingSnapshot()
            }
            initial = loaded
            loaded.fold(onSuccess = { value ->
                mutable.value = value
                versions.updateAndGet {
                    maxOf(it, value.records.maxOfOrNull { r -> r.updatedAt } ?: 0L,
                        listOf(
                            value.deleted,
                            value.clearedOwners,
                            value.deletedBooks
                        ).flatMap { it.values }.maxOrNull() ?: 0L)
                }
                mutableError.value = null
                Result.success(Unit)
            }, onFailure = { e ->
                mutableError.value = "本地阅读数据读取失败，原文件已保留，请重试"
                Result.failure(e)
            })
        }
    }

    override fun latest(owner: String, kind: String, path: String) = snapshot.value.records.filter {
        it.owner == owner && it.kind == kind && it.path == path
    }.maxByOrNull { it.updatedAt }

    private val pending = ConcurrentHashMap<String, LocalReadingRecord>()
    private val writes = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (signal in writes) {
                delay(120); flush()
            }
        }
    }

    override fun record(value: LocalReadingRecord) {
        val r =
            value.copy(title = safeBookTitle(value.path, value.title), updatedAt = nextVersion())
        if (r.completed) completedKeys.add(r.key)
        pending.compute(r.key) { _, old -> if (old != null && old.updatedAt > r.updatedAt) old else r }
        writes.trySend(Unit)
    }

    override fun flushAsync() {
        scope.launch { flush() }
    }

    override suspend fun flush(): Result<Unit> {
        val batch = pending.values.toList()
        if (batch.isEmpty()) return Result.success(Unit)
        val result = withContext(NonCancellable) {
            mutate { s ->
                val records = s.records.associateBy { it.key }.toMutableMap()
                batch.forEach { r ->
                    val old = records[r.key]
                    if (ReadingMergePolicy.accepts(r.updatedAt, old?.updatedAt ?: -1, cutoff(s, r)))
                        records[r.key] = r.copy(
                            title = safeBookTitle(r.path, r.title, old?.title),
                            cover = r.cover.ifBlank { old?.cover.orEmpty() },
                            authors = r.authors.ifBlank { old?.authors.orEmpty() },
                            completed = r.completed || r.key in completedKeys || old?.completed == true
                        )
                }
                s.copy(records = records.values.toList())
            }
        }
        if (result.isSuccess) batch.forEach { pending.remove(it.key, it) }
        return result
    }

    private fun cutoff(s: LocalReadingSnapshot, r: LocalReadingRecord) =
        maxOf(
            s.deleted[r.key] ?: -1L,
            s.deletedBooks[r.bookKey] ?: -1L,
            s.clearedOwners[r.owner] ?: -1L
        )

    override fun complete(owner: String, path: String, chapter: String) {
        val key = LocalReadingRecord(owner, "comic", path, chapter, "").key
        val version = nextVersion()
        completedKeys.add(key)
        scope.launch {
            mutate { s ->
                val r = LocalReadingRecord(owner, "comic", path, chapter, "")
                if (!ReadingMergePolicy.survives(
                        version,
                        cutoff(s, r)
                    )
                ) s else s.copy(records = s.records.map {
                    if (it.owner == owner && it.kind == "comic" && it.path == path && it.chapter == chapter) it.copy(
                        completed = true
                    ) else it
                })
            }
        }
    }

    override suspend fun toggleBookmark(r: LocalReadingRecord): Result<Unit> = mutate { s ->
        val matches: (LocalReadingRecord) -> Boolean =
            { it.key == r.key && it.page == r.page && it.section == r.section && it.line == r.line && it.character == r.character }
        s.copy(
            bookmarks = if (s.bookmarks.any(matches)) s.bookmarks.filterNot(matches)
            else s.bookmarks + r.copy(title = safeBookTitle(r.path, r.title))
        )
    }

    /** 详情晚于首个阅读位置返回时，同步补齐该会话已保存的记录和书签，不改变阅读时间。 */
    override suspend fun updateMetadata(
        owner: String,
        kind: String,
        path: String,
        title: String,
        cover: String,
        authors: String
    ): Result<Unit> {
        fun enrich(r: LocalReadingRecord): LocalReadingRecord =
            if (r.owner == owner && r.kind == kind && r.path == path) r.copy(
                title = safeBookTitle(path, title, r.title),
                cover = cover.ifBlank { r.cover }, authors = authors.ifBlank { r.authors }
            ) else r
        pending.replaceAll { _, r -> enrich(r) }
        return mutate { s ->
            s.copy(
                records = s.records.map(::enrich),
                bookmarks = s.bookmarks.map(::enrich)
            )
        }
    }

    override suspend fun removeBookmark(r: LocalReadingRecord) =
        mutate { it.copy(bookmarks = it.bookmarks - r) }

    override suspend fun removeHistory(r: LocalReadingRecord) = mutate { s ->
        val removed = s.records.filter { it.bookKey == r.bookKey }
        completedKeys.removeIf { it.startsWith(r.bookKey) }
        val cutoff = nextVersion()
        s.copy(
            records = s.records - removed.toSet(),
            deletedBooks = s.deletedBooks + (r.bookKey to cutoff),
            deleted = s.deleted + removed.associate { it.key to cutoff })
    }

    override suspend fun clearHistory(owner: String) = mutate { s ->
        val removed = s.records.filter { it.owner == owner }
        completedKeys.removeIf { it.startsWith("${owner.length}:$owner") }
        val cutoff = nextVersion()
        s.copy(
            records = s.records - removed.toSet(),
            clearedOwners = s.clearedOwners + (owner to cutoff),
            deleted = s.deleted + removed.associate { it.key to cutoff })
    }

    suspend fun merge(incoming: LocalReadingSnapshot): Result<Unit> = mutate { current ->
        val deleted = (current.deleted.keys + incoming.deleted.keys).associateWith {
            maxOf(
                current.deleted[it] ?: -1, incoming.deleted[it] ?: -1
            )
        }

        fun mergeTimes(a: Map<String, Long>, b: Map<String, Long>) =
            (a.keys + b.keys).associateWith { maxOf(a[it] ?: -1, b[it] ?: -1) }

        val merged = current.copy(
            deleted = deleted,
            clearedOwners = mergeTimes(current.clearedOwners, incoming.clearedOwners),
            deletedBooks = mergeTimes(current.deletedBooks, incoming.deletedBooks)
        )
        val records = (current.records + incoming.records).filter {
            ReadingMergePolicy.survives(
                it.updatedAt,
                cutoff(merged, it)
            )
        }
            .groupBy { it.key }.values.map { group ->
                group.maxBy { it.updatedAt }.copy(completed = group.any { it.completed })
            }
        merged.copy(
            records = records,
            bookmarks = (current.bookmarks + incoming.bookmarks).sortedByDescending { it.updatedAt }
                .distinctBy { listOf(it.key, it.page, it.section, it.line, it.character) },
            deleted = deleted
        )
    }

    suspend fun replace(snapshot: LocalReadingSnapshot): Result<Unit> = mutate { snapshot }
    private suspend fun mutate(transform: (LocalReadingSnapshot) -> LocalReadingSnapshot): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                mutex.withLock {
                    initial.fold(onSuccess = {}, onFailure = { throw it })
                    val next = transform(mutable.value)
                    if (next == mutable.value) return@withLock
                    val raw = LocalReadingCodec.encode(next)
                    require(raw.toByteArray().size <= LocalReadingCodec.MAX_BYTES) { "本地阅读数据过大，请清理后重试" }
                    @Suppress("UseKtx")
                    if (!prefs.edit().putString("data", raw)
                            .commit()
                    ) throw IOException("阅读数据保存失败")
                    versions.updateAndGet {
                        maxOf(
                            it,
                            next.records.maxOfOrNull { r -> r.updatedAt } ?: 0L,
                            listOf(
                                next.deleted,
                                next.deletedBooks,
                                next.clearedOwners
                            ).flatMap { map -> map.values }.maxOrNull() ?: 0L
                        )
                    }
                    mutable.value = next
                    mutableError.value = null
                }
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableError.value = e.message ?: "阅读数据保存失败"; Result.failure(e)
            }
        }
}
