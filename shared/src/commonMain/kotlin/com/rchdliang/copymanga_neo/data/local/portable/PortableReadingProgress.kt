package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.data.remote.portable.PortableNetworkState
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.time.Clock

class PortableReadingSession(private val state: PortableNetworkState) : ReadingSessionRepository {
    private val ready = MutableStateFlow(true)
    override val cacheReadyFlow = ready.asStateFlow()
    override val sessionChanges = state.snapshots.map { it.generation }.distinctUntilChanged()
    override suspend fun awaitReady() { ready.first { it } }
    override fun getReadingSessionSync(): LocalReadingSession {
        val snapshot = state.snapshot
        return LocalReadingSession(snapshot.settings.requestMode, snapshot.session().owner, snapshot.generation)
    }
    override fun isLoggedInSync() = state.snapshot.session().token.isNotBlank()
}

@Serializable
internal data class ProgressDocument(
    val owners: Map<String, Map<String, ReadingProgress>> = emptyMap(),
    val cleared: Map<String, Long> = emptyMap()
)

/** Persists before submitting; stale remote responses cannot replace pending local progress. */
class PortableReadingProgress(
    storage: DocumentStorage,
    private val state: PortableNetworkState,
    private val manga: MangaRepository,
    private val scope: CoroutineScope
) : ReadingProgressRepository {
    private val document = DocumentStore(storage, "reading-progress.json", ProgressDocument.serializer(), ProgressDocument())
    private data class Load(val version: Long = 0, val finished: Boolean = false)
    private data class Version(val value: Long, val generation: Long, val previousGenerationCeiling: Long = -1)
    private val load = MutableStateFlow(Load())
    private val requestedLoads = MutableStateFlow(0L)
    private val versions = MutableStateFlow(Version(Clock.System.now().toEpochMilliseconds(), state.snapshot.generation))
    private val loadMutex = Mutex()
    private val firstLoad = CompletableDeferred<Unit>()
    private val syncMutex = Mutex()
    private val signals = Channel<Unit>(Channel.CONFLATED)
    override val currentOwner: String get() = state.snapshot.session().owner
    override val progress = combine(state.snapshots, document.state, document.error, load) { session, data, error, loading ->
        val owner = session.session().owner
        ReadingProgressSnapshot(owner, data.owners[owner].orEmpty(), loading.finished, error, loading.version)
    }.stateIn(scope, SharingStarted.Eagerly, ReadingProgressSnapshot())

    init {
        scope.launch { initialize(0) }
        scope.launch {
            state.snapshots.map { it.generation }.distinctUntilChanged().collectLatest { generation ->
                observeGeneration()
                firstLoad.await()
                val owner = currentOwner
                signals.trySend(Unit)
                for (ignored in signals) {
                    try {
                        syncMutex.withLock { syncPending(owner, generation) }
                    } catch (cancel: CancellationException) {
                        // A cancelled request must not permanently kill the application collector.
                        currentCoroutineContext().ensureActive()
                    } catch (error: Exception) {
                        println("ReadingProgress: 同步失败，保留本地进度并等待明确重试：${error.message.orEmpty()}")
                    }
                }
            }
        }
    }

    private suspend fun initialize(version: Long) = loadMutex.withLock {
        if (version < requestedLoads.value) return@withLock
        load.value = Load(version)
        document.load()
        val data = document.state.value
        val highest = maxOf(data.owners.values.flatMap { it.values }.maxOfOrNull { it.version } ?: 0,
            data.cleared.values.maxOrNull() ?: 0)
        versions.update { it.copy(value = maxOf(it.value, highest)) }
        // Ready means the attempt finished. The snapshot error distinguishes failure from success.
        if (version == requestedLoads.value) load.value = Load(version, true)
        firstLoad.complete(Unit)
        if (document.error.value == null) signals.trySend(Unit)
    }

    override suspend fun awaitOwner(): String = currentOwner
    override suspend fun awaitReady(owner: String, minimumLoadVersion: Long): ReadingProgressSnapshot =
        withTimeoutOrNull(15_000) {
            progress.first { it.owner == owner && it.isReady && it.loadVersion >= minimumLoadVersion }
        } ?: ReadingProgressSnapshot(owner, isReady = true, error = "阅读进度读取超时，请重试", loadVersion = minimumLoadVersion)

    override fun retryLoading(): Long = requestedLoads.updateAndGet { it + 1 }.also { version ->
        load.value = Load(version)
        scope.launch { initialize(version) }
    }

    private fun observeGeneration(): Version {
        val generation = state.snapshot.generation
        return versions.updateAndGet { old ->
            if (old.generation >= generation) old
            else old.copy(generation = generation, previousGenerationCeiling = old.value)
        }
    }

    override fun requestVersion(): Long {
        observeGeneration()
        return versions.updateAndGet { it.copy(value = maxOf(it.value + 1, Clock.System.now().toEpochMilliseconds())) }.value
    }

    override fun recordRead(owner: String, comicPathWord: String, chapterUuid: String, chapterName: String, group: String) {
        if (owner != currentOwner || comicPathWord.isBlank() || chapterUuid.isBlank()) return
        val wasLoaded = firstLoad.isCompleted
        val requestedVersion = requestVersion()
        scope.launch {
            firstLoad.await()
            // Reads queued during startup are newer than the persisted timestamps discovered by load.
            val version = if (wasLoaded) requestedVersion else requestVersion()
            document.update { data ->
                if (version <= (data.cleared[owner] ?: 0)) data else {
                    val records = data.owners[owner].orEmpty()
                    if ((records[comicPathWord]?.version ?: -1) >= version) data
                    else data.copy(owners = data.owners + (owner to (records + (comicPathWord to
                        ReadingProgress(chapterUuid, chapterName, group, version, owner != "guest")))))
                }
            }.onSuccess { retryPending() }
        }
    }

    override suspend fun acceptRemote(owner: String, records: Map<String, BrowseInfo?>, requestVersion: Long, onlyMissing: Boolean): Result<Unit> {
        val epoch = observeGeneration()
        if (owner != currentOwner || requestVersion <= epoch.previousGenerationCeiling) return Result.success(Unit)
        firstLoad.await()
        return document.update { data ->
            if (owner != currentOwner || epoch.generation != state.snapshot.generation || requestVersion <= (data.cleared[owner] ?: 0)) data
            else {
                val local = data.owners[owner].orEmpty().toMutableMap()
                records.forEach { (path, browse) ->
                    val previous = local[path]
                    if (onlyMissing && previous != null) return@forEach
                    if (previous != null && previous.version >= requestVersion) return@forEach
                    if (previous?.pendingSync == true && previous.chapterUuid != browse?.chapterUuid) return@forEach
                    val uuid = browse?.chapterUuid?.takeIf { it.isNotBlank() }
                    if (uuid != null) local[path] = ReadingProgress(uuid,
                        browse.chapterName.orEmpty().ifBlank { previous?.takeIf { it.chapterUuid == uuid }?.chapterName.orEmpty() },
                        previous?.takeIf { it.chapterUuid == uuid }?.groupPathWord, requestVersion, false, previous?.lastReadGroupPathWord)
                }
                data.copy(owners = data.owners + (owner to local))
            }
        }
    }

    override fun retryPending() { signals.trySend(Unit) }
    private suspend fun syncPending(owner: String, generation: Long) {
        if (owner == "guest" || !load.value.finished || document.error.value != null) return
        fun current() = owner == currentOwner && generation == state.snapshot.generation
        if (!current()) return
        for ((path, read) in document.state.value.owners[owner].orEmpty().filterValues { it.pendingSync }) {
            if (!current()) return
            if (document.state.value.owners[owner]?.get(path) != read) continue
            val submitted = manga.trialChapterContent(path, read.chapterUuid)
            if (!current()) return
            if (submitted.isFailure) continue
            // Version is captured before confirmation; newer local reading cannot be overwritten.
            val version = requestVersion()
            val confirmed = manga.queryComicStatus(path)
            if (!current()) return
            confirmed.onSuccess { acceptRemote(owner, mapOf(path to it.browse), version) }
        }
    }

    override suspend fun clearHistory(): Result<Unit> = try {
        firstLoad.await()
        val owner = currentOwner
        val generation = state.snapshot.generation
        val cutoff = requestVersion()
        syncMutex.withLock {
            state.requireCurrent(generation)
            // Do not perform a destructive remote action while its local tombstone cannot be loaded.
            document.error.value?.let { return@withLock Result.failure(IllegalStateException(it)) }
            manga.clearBrowseHistory().fold(onSuccess = {
                state.requireCurrent(generation)
                document.update { data -> data.copy(cleared = data.cleared + (owner to maxOf(cutoff, data.cleared[owner] ?: 0)),
                    owners = data.owners + (owner to data.owners[owner].orEmpty().filterValues { it.version > cutoff })) }
            }, onFailure = { Result.failure(it) })
        }
    } catch (cancel: CancellationException) { throw cancel }
    catch (error: Exception) { Result.failure(error) }
}
