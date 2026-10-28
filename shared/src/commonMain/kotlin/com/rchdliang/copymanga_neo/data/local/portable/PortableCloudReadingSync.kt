package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.data.remote.portable.NetworkSnapshot
import com.rchdliang.copymanga_neo.data.remote.portable.PortableNetworkState
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Clock

/** 登录及恢复前台时只读拉取云端历史；不执行云端写入，也不删除任何本地个人数据。 */
class PortableCloudReadingSync(
    private val state: PortableNetworkState,
    private val manga: MangaRepository,
    private val progress: PortableReadingProgress,
    private val local: PortableLocalReading,
    private val shelf: PortableShelfReading,
    private val scope: CoroutineScope
) {
    private val requests = MutableStateFlow(0L)
    private val failure = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = failure.asStateFlow()
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            combine(state.snapshots.map { it.generation }.distinctUntilChanged(), requests) { _, _ -> state.snapshot }
                .collectLatest { snapshot ->
                    failure.value = null
                    if (snapshot.session().token.isBlank() || snapshot.session().owner == "guest") return@collectLatest
                    try { pull(snapshot) }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) {
                        if (current(snapshot)) {
                            failure.value = "云端阅读记录同步未完成，本地记录已保留；返回应用后重试"
                            println("CloudReadingSync: ${failure.value}")
                        }
                    }
                }
        }
    }

    fun retry() { requests.update { it + 1 } }
    private fun current(snapshot: NetworkSnapshot) = state.snapshot.generation == snapshot.generation &&
        state.snapshot.session().owner == snapshot.session().owner && state.snapshot.session().token.isNotBlank()

    private suspend fun pull(snapshot: NetworkSnapshot) {
        val owner = snapshot.session().owner
        val session = LocalReadingSession(snapshot.settings.requestMode, owner, snapshot.generation)
        local.awaitReady().getOrThrow()
        progress.awaitReady(owner).error?.let { error(it) }
        if (!current(snapshot)) return
        val version = progress.requestVersion()
        val localVersion = local.cloudRequestVersion()
        val importedAt = Clock.System.now().toEpochMilliseconds()
        val seenBooks = mutableSetOf<String>()
        var offset = 0
        var ordinal = 0L
        do {
            val page = manga.getBrowseHistory(offset).getOrThrow()
            currentCoroutineContext().ensureActive()
            if (!current(snapshot)) return
            val entries = page.items.filter { it.pathWord.isNotBlank() && !it.lastReadChapterId.isNullOrBlank() && seenBooks.add(it.pathWord) }
            val positions = entries.map { item ->
                LocalReadingRecord(owner, "comic", item.pathWord, requireNotNull(item.lastReadChapterId),
                    item.lastReadChapterName.orEmpty(), group = "", title = item.name, cover = item.cover.orEmpty(),
                    authors = item.authors.joinToString(",") { it.name },
                    updatedAt = (importedAt - ordinal++).coerceAtLeast(0), cloudImported = true)
            }
            val resumes = entries.filter { local.canMergeCloudHistory(owner, it.pathWord, localVersion) }
                .associate { it.pathWord to BrowseInfo(null, it.pathWord, it.lastReadChapterId, it.lastReadChapterName) }
            progress.acceptRemote(owner, resumes, version).getOrThrow()
            if (!current(snapshot)) return
            fun accepted(path: String, chapter: String) =
                current(snapshot) && progress.matchesRemote(owner, path, chapter, version)
            val merged = local.importCloudHistory(owner, positions, localVersion) {
                accepted(it.path, it.chapter)
            }.getOrThrow()
            if (!current(snapshot)) return
            shelf.importCloudHistory(session, entries) { item ->
                merged[item.pathWord]?.let { record ->
                    accepted(record.path, record.chapter) && local.isCurrentCloudPosition(record)
                } == true
            }.getOrThrow()
            shelf.refresh(session, "comic", merged.keys)
            val next = page.nextOffset ?: return
            require(page.items.isNotEmpty() && next > offset) { "云端历史分页没有前进" }
            offset = next
        } while (current(snapshot))
    }
}
