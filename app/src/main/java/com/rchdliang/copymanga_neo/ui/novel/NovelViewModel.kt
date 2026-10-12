package com.rchdliang.copymanga_neo.ui.novel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.ChapterDownloadStatus
import com.rchdliang.copymanga_neo.domain.model.DownloadProgressInfo
import com.rchdliang.copymanga_neo.domain.model.DownloadTaskStatus
import com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord
import com.rchdliang.copymanga_neo.domain.model.LocalReadingSession
import com.rchdliang.copymanga_neo.domain.model.NovelBook
import com.rchdliang.copymanga_neo.domain.model.NovelCachedVolume
import com.rchdliang.copymanga_neo.domain.model.NovelDownloadItem
import com.rchdliang.copymanga_neo.domain.model.NovelDownloadTask
import com.rchdliang.copymanga_neo.domain.model.NovelReadingVolume
import com.rchdliang.copymanga_neo.domain.model.NovelTag
import com.rchdliang.copymanga_neo.domain.model.NovelVolume
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import com.rchdliang.copymanga_neo.domain.model.novelDownloadKey
import com.rchdliang.copymanga_neo.domain.model.safeBookTitle
import com.rchdliang.copymanga_neo.domain.model.withFallback
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
import com.rchdliang.copymanga_neo.domain.repository.LocalReadingRepository
import com.rchdliang.copymanga_neo.domain.repository.NetworkSettingsRepository
import com.rchdliang.copymanga_neo.domain.repository.NovelRepository
import com.rchdliang.copymanga_neo.domain.repository.NovelShelfRepository
import com.rchdliang.copymanga_neo.domain.repository.ReadingProgressRepository
import com.rchdliang.copymanga_neo.domain.repository.ReadingSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

enum class NovelView { BROWSE, DETAIL, READER }
data class NovelUiState(
    val owner: String? = null,
    val view: NovelView = NovelView.BROWSE,
    val mode: RequestMode = RequestMode.WEB,
    val busy: Boolean = false,
    val caching: Boolean = false,
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val books: List<NovelBook> = emptyList(),
    val tags: List<NovelTag> = emptyList(),
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val loadMoreError: String? = null,
    val browseRevision: Int = 0,
    val query: String = "",
    val searchType: String = "",
    val ordering: String = "-datetime_updated",
    val theme: String = "",
    val promotedTheme: NovelTag? = null,
    val author: String = "",
    val isLoggedIn: Boolean = false,
    val isInShelf: Boolean = false,
    val shelfLoading: Boolean = false,
    val downloading: Boolean = false,
    val downloadProgress: String? = null,
    val downloadInfo: DownloadProgressInfo? = null,
    val downloadTask: NovelDownloadTask? = null,
    val cachedVolumes: List<NovelCachedVolume> = emptyList(),
    val book: NovelBook? = null,
    val detailPath: String = "",
    val bookLoading: Boolean = false,
    val volumesLoading: Boolean = false,
    val bookError: String? = null,
    val volumesError: String? = null,
    val volumes: List<NovelVolume> = emptyList(),
    val reader: NovelReadingVolume? = null,
    val restore: LocalReadingRecord? = null,
    val anchorRevision: Int = 0
)

@HiltViewModel
class NovelViewModel @Inject constructor(
    private val repository: NovelRepository,
    private val shelf: NovelShelfRepository,
    private val reading: LocalReadingRepository, private val progress: ReadingProgressRepository,
    private val network: NetworkSettingsRepository,
    private val auth: ReadingSessionRepository,
    private val downloads: DownloadRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(NovelUiState(mode = network.settings.value.requestMode))
    val state = combine(
        mutable, repository.cachedVolumes, downloads.getTasks(), downloads.getDownloadProgress()
    ) { ui, cached, tasks, progressMap ->
        val path = ui.reader?.book?.path ?: ui.detailPath
        val key = ui.owner?.let { novelDownloadKey(ui.mode, it, path) }
        val info = key?.let(progressMap::get)
        val task = tasks.filterIsInstance<NovelDownloadTask>().firstOrNull { it.taskKey == key }
        val downloading = info?.status in setOf(
            DownloadTaskStatus.WAITING, DownloadTaskStatus.WAITING_FOR_WIFI,
            DownloadTaskStatus.REQUESTING_PAGE, DownloadTaskStatus.DOWNLOADING
        )
        ui.copy(
            cachedVolumes = cached.filter { it.owner == ui.owner && it.mode == ui.mode && it.book.path == path },
            downloadTask = task, downloadInfo = info,
            downloading = downloading,
            caching = downloading && task?.volumes?.any {
                it.volume.id == ui.reader?.volume?.id && it.status != ChapterDownloadStatus.DOWNLOADED
            } == true,
            downloadProgress = info?.statusText
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, mutable.value)
    val localData = reading.snapshot
    private var job: Job? = null
    private var cacheJob: Job? = null
    private var shelfJob: Job? = null
    private var metadataJob: Job? = null
    private var requestedBook: String? = null
    private var requestedVolume: Triple<String, String, LocalReadingRecord?>? = null
    private var offset = 0
    private val entryGate = NovelEntryGate()
    private var sessionOwner: String? = null
    private var lastPosition: LocalReadingRecord? = null
    private val errors = CoroutineExceptionHandler { _, e ->
        mutable.update {
            it.copy(
                busy = false,
                caching = false,
                refreshing = false,
                error = e.message ?: "小说请求失败"
            )
        }
    }

    init {
        viewModelScope.launch(errors) {
            combine(auth.cacheReadyFlow, auth.sessionChanges) { ready, _ ->
                if (ready) auth.getReadingSessionSync() else null
            }.distinctUntilChanged().collect { session ->
                val previous = mutable.value
                val previousPosition = currentPosition()
                job?.cancel(); cacheJob?.cancel(); shelfJob?.cancel(); metadataJob?.cancel(); lastPosition =
                null
                sessionOwner = session?.owner
                mutable.value = previous.copy(
                    owner = session?.owner,
                    isLoggedIn = auth.isLoggedInSync(),
                    isInShelf = false,
                    shelfLoading = false,
                    downloading = false,
                    downloadProgress = null,
                    mode = session?.mode ?: network.settings.value.requestMode,
                    books = emptyList(),
                    tags = emptyList(),
                    promotedTheme = null,
                    book = null,
                    volumes = emptyList(),
                    reader = null,
                    restore = null,
                    busy = false,
                    caching = false,
                    loadingMore = false,
                    loadMoreError = null,
                    hasMore = false,
                    bookLoading = false,
                    volumesLoading = false,
                    bookError = null,
                    volumesError = null,
                    refreshing = false,
                    refreshFailed = false,
                    error = null
                )
                // 首次会话由路由入口初始化；后续账号或请求模式变化才重新加载当前页面。
                if (session?.owner != null && previous.owner != null) {
                    when (previous.view) {
                        NovelView.BROWSE -> refresh()
                        NovelView.DETAIL -> requestedBook?.let(::openBook) ?: refresh()
                        NovelView.READER -> requestedVolume?.let {
                            openVolume(
                                it.first, it.second,
                                (previousPosition
                                    ?: it.third)?.takeIf { record -> record.owner == session.owner })
                        } ?: refresh()
                    }
                }
            }
        }
        viewModelScope.launch {
            reading.error.collect { error ->
                if (error != null) mutable.update {
                    it.copy(
                        error = error
                    )
                }
            }
        }
        viewModelScope.launch(errors) {
            repository.cachedVolumes.collect { cached ->
                val selected = mutable.value
                val reader = selected.reader ?: return@collect
                val owner = selected.owner ?: return@collect
                if (reader.offlineReady || !cached.any {
                        it.owner == owner && it.mode == selected.mode && it.book.path == reader.book.path &&
                                it.volume.id == reader.volume.id && it.offlineReady
                    }) return@collect
                repository.readVolume(selected.mode, owner, reader.book.path, reader.volume.id)
                    .fold(
                        onSuccess = { updated ->
                            ensureActive()
                            if (current(owner, selected.mode)) mutable.update {
                                if (it.reader?.book?.path == reader.book.path && it.reader.volume.id == reader.volume.id)
                                    it.copy(
                                        reader = updated.copy(
                                            book = updated.book.withFallback(
                                                reader.book
                                            )
                                        )
                                    ) else it
                            }
                        },
                        onFailure = { if (it is CancellationException) throw it }
                    )
            }
        }
    }

    /** 首页或路由进入时等待全局账号会话，避免首屏详情被初始化列表请求覆盖。 */
    fun openInitialBook(path: String?, resume: LocalReadingRecord? = null) {
        // 与漫画 openReader 相同：同一路由重建组合不重开已加载的书、分卷或正文。
        if (!entryGate.accept(path, resume)) return
        viewModelScope.launch(errors) {
            auth.awaitReady()
            progress.awaitReady(progress.awaitOwner())
            val session = auth.getReadingSessionSync() ?: return@launch
            val targetPath = resume?.path ?: path
            // 下载列表使用已发布索引；已有目标不能等待后台插图下载持有的扫描锁。
            if (targetPath != null && repository.cachedVolumes.value.none {
                    it.owner == session.owner && it.mode == session.mode && it.book.path == targetPath
                }) {
                repository.refreshCachedVolumes().fold(
                    onSuccess = {}, onFailure = { if (it is CancellationException) throw it }
                )
            }
            if (auth.getReadingSessionSync() != session) return@launch
            sessionOwner = session.owner
            mutable.update {
                it.copy(
                    owner = session.owner,
                    mode = session.mode,
                    isLoggedIn = auth.isLoggedInSync()
                )
            }
            if (resume != null) this@NovelViewModel.resume(resume)
            else if (path == null) filter("", "", "-datetime_updated", "") else openBook(path)
        }
    }

    fun filter(query: String, type: String, order: String, theme: String, author: String = "") {
        val previous = mutable.value
        val changed = previous.query != query || previous.searchType != type ||
                previous.ordering != order || previous.theme != theme || previous.author != author
        mutable.update {
            it.copy(
                query = query,
                searchType = type,
                ordering = order,
                theme = theme,
                author = author,
                books = if (changed) emptyList() else it.books
            )
        }
        refresh()
    }

    /** 详情跳转保留标签名称，并只把这一入口的题材置前；页内筛选维持现有顺序。 */
    fun selectTheme(tag: NovelTag) {
        mutable.update {
            it.copy(promotedTheme = tag, tags = (it.tags + tag).distinctBy { value -> value.path })
        }
        filter("", "", mutable.value.ordering, tag.path)
    }

    fun sortDiscovery(order: String) {
        val selected = mutable.value
        filter("", "", order, selected.theme, selected.author)
    }

    fun refresh() {
        offset = 0
        mutable.update { it.copy(browseRevision = it.browseRevision + 1) }
        loadPage(false)
    }

    fun loadMore() {
        val state = mutable.value
        if (state.view == NovelView.BROWSE && state.hasMore && !state.busy &&
            !state.loadingMore && state.loadMoreError == null
        ) loadPage(true)
    }

    fun retryPage() {
        if (mutable.value.loadMoreError != null) {
            mutable.update { it.copy(loadMoreError = null) }
            loadMore()
        } else refresh()
    }

    private fun loadPage(append: Boolean) {
        val selected = mutable.value
        val owner = sessionOwner ?: return
        val requestedOffset = if (append) offset else 0
        job?.cancel()
        shelfJob?.cancel()
        metadataJob?.cancel()
        mutable.update {
            it.copy(
                busy = !append, loadingMore = append, loadMoreError = null,
                error = null, view = NovelView.BROWSE, shelfLoading = false,
                hasMore = append && it.hasMore
            )
        }
        job = viewModelScope.launch(errors) {
            if (!append) launch {
                repository.tags(selected.mode, owner).fold(
                    onSuccess = { tags ->
                        ensureActive()
                        if (current(owner, selected.mode)) mutable.update {
                            it.copy(tags = (tags + listOfNotNull(it.promotedTheme)).distinctBy { tag -> tag.path })
                        }
                    },
                    onFailure = { error -> if (error is CancellationException) throw error }
                )
            }
            val result = if (selected.query.isBlank()) repository.discover(
                selected.mode,
                owner,
                requestedOffset,
                selected.ordering,
                selected.theme,
                selected.author
            ) else repository.search(
                selected.mode, owner, requestedOffset, selected.query, selected.searchType
            )
            ensureActive()
            if (!current(owner, selected.mode)) return@launch
            result.fold(
                onSuccess = { page ->
                    offset = page.nextOffset
                    mutable.update {
                        it.copy(
                            busy = false, loadingMore = false,
                            books = (if (append) it.books + page.books else page.books).distinctBy { book -> book.path },
                            hasMore = page.hasMore && page.nextOffset > requestedOffset
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    mutable.update {
                        it.copy(
                            busy = false, loadingMore = false,
                            error = if (append) null else error.message ?: "小说列表加载失败",
                            loadMoreError = if (append) error.message
                                ?: "后续作品加载失败" else null
                        )
                    }
                }
            )
        }
    }

    fun openBook(path: String) {
        requestedBook = path
        loadBook(path, reloadInfo = true, reloadVolumes = true)
    }

    fun retryBookInfo() {
        if (!mutable.value.busy) requestedBook?.let {
            loadBook(
                it,
                reloadInfo = true,
                reloadVolumes = false
            )
        }
    }

    fun retryVolumes() {
        if (!mutable.value.busy) requestedBook?.let {
            loadBook(
                it,
                reloadInfo = false,
                reloadVolumes = true
            )
        }
    }

    /** 本地信息先展示；联网详情和目录独立更新，离线仍能进入已下载分卷。 */
    private fun loadBook(path: String, reloadInfo: Boolean, reloadVolumes: Boolean) {
        job?.cancel()
        shelfJob?.cancel()
        metadataJob?.cancel()
        val owner = sessionOwner ?: return
        val mode = mutable.value.mode
        val local = repository.cachedVolumes.value.filter {
            it.owner == owner && it.mode == mode && it.book.path == path
        }
        mutable.update {
            it.copy(
                view = NovelView.DETAIL, detailPath = path,
                book = it.book.takeIf { value -> value?.path == path } ?: local.firstOrNull()?.book,
                volumes = it.volumes.takeIf { _ -> it.detailPath == path && it.volumes.isNotEmpty() }
                    ?: local.map { cached -> cached.volume }.sortedBy { volume -> volume.index },
                isInShelf = it.isInShelf && it.detailPath == path, shelfLoading = false,
                bookLoading = reloadInfo, volumesLoading = reloadVolumes,
                bookError = if (reloadInfo) null else it.bookError,
                volumesError = if (reloadVolumes) null else it.volumesError,
                reader = null, busy = true, error = null
            )
        }
        job = viewModelScope.launch(errors) {
            supervisorScope {
                if (reloadInfo) launch {
                    repository.detail(mode, owner, path).fold(
                        onSuccess = { loaded ->
                            ensureActive()
                            if (current(owner, mode)) {
                                val detail = loaded.withFallback(mutable.value.book)
                                mutable.update {
                                    it.copy(
                                        book = detail,
                                        bookLoading = false,
                                        busy = it.volumesLoading
                                    )
                                }
                                refreshShelf(detail)
                            }
                        },
                        onFailure = { error ->
                            if (error is CancellationException) throw error
                            ensureActive()
                            if (current(owner, mode)) mutable.update {
                                it.copy(
                                    bookLoading = false, busy = it.volumesLoading,
                                    bookError = error.message ?: "作品详情加载失败"
                                )
                            }
                        }
                    )
                }
                if (reloadVolumes) launch {
                    repository.volumes(mode, owner, path).fold(
                        onSuccess = { catalogue ->
                            ensureActive()
                            if (current(owner, mode)) mutable.update {
                                it.copy(
                                    volumes = catalogue,
                                    volumesLoading = false,
                                    busy = it.bookLoading
                                )
                            }
                        },
                        onFailure = { error ->
                            if (error is CancellationException) throw error
                            ensureActive()
                            if (current(owner, mode)) mutable.update {
                                it.copy(
                                    volumesLoading = false, busy = it.bookLoading,
                                    volumesError = error.message ?: "分卷目录加载失败"
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    fun resume(record: LocalReadingRecord) {
        viewModelScope.launch(errors) {
            val ready = progress.awaitReady(progress.awaitOwner())
            if (record.owner != ready.owner) {
                mutable.update { it.copy(error = "请先切换到这条记录所属账号") }; return@launch
            }
            sessionOwner = ready.owner
            mutable.update {
                it.copy(
                    owner = ready.owner,
                    mode = network.settings.value.requestMode
                )
            }
            openVolume(record.path, record.chapter, record)
        }
    }

    fun openVolume(path: String, id: String, target: LocalReadingRecord? = null) {
        requestedVolume = Triple(path, id, target)
        job?.cancel(); cacheJob?.cancel(); metadataJob?.cancel()
        val owner = sessionOwner ?: return
        val mode = mutable.value.mode
        val session =
            auth.getReadingSessionSync()?.takeIf { it.owner == owner && it.mode == mode } ?: return
        mutable.update {
            it.copy(
                view = NovelView.READER, busy = true, reader = null,
                refreshing = false, refreshFailed = false, error = null
            )
        }
        job = viewModelScope.launch(errors) {
            val ready = progress.awaitReady(owner)
            ready.error?.let { message -> mutable.update { it.copy(notice = message) } }
            repository.readVolume(mode, owner, path, id).fold(
                onSuccess = { volume ->
                    val saved = target ?: reading.position(owner, "novel", path, id)
                    val savedBook = saved?.let {
                        NovelBook(
                            path, safeBookTitle(path, it.title), it.cover,
                            it.authors.takeIf(String::isNotBlank)
                                ?.let { names -> listOf(NovelTag(names, "")) }.orEmpty()
                        )
                    }
                    val book = volume.book.withFallback(mutable.value.book).withFallback(savedBook)
                    ensureActive()
                    if (!current(
                            owner,
                            mode
                        ) || auth.getReadingSessionSync() != session
                    ) return@fold
                    lastPosition = saved?.copy(title = safeBookTitle(path, book.name, saved.title))
                    // 正文成功后立即展示，展示资料不得阻塞离线阅读。
                    mutable.update {
                        it.copy(
                            reader = volume.copy(book = book), busy = false,
                            restore = saved, anchorRevision = it.anchorRevision + 1
                        )
                    }
                    if (book.cover.isBlank() || book.authors.isEmpty()) {
                        refreshReaderMetadata(session, path, id)
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    ensureActive()
                    if (current(
                            owner,
                            mode
                        ) && auth.getReadingSessionSync() == session
                    ) mutable.update {
                        it.copy(busy = false, error = error.message ?: "正文加载失败")
                    }
                }
            )
        }
    }

    /** 独立且可取消的展示资料补全；会话代数、作品与分卷须同时匹配。 */
    private fun refreshReaderMetadata(
        session: LocalReadingSession,
        path: String,
        volumeId: String
    ) {
        val owner = session.owner ?: return
        metadataJob?.cancel()
        metadataJob = viewModelScope.launch(errors) {
            repository.detail(session.mode, owner, path).fold(
                onSuccess = { book ->
                    ensureActive()
                    if (auth.getReadingSessionSync() != session || !current(
                            owner,
                            session.mode
                        )
                    ) return@fold
                    mutable.update {
                        val reader = it.reader
                        if (it.view == NovelView.READER && reader?.book?.path == path &&
                            reader.volume.id == volumeId && it.owner == owner && it.mode == session.mode
                        ) it.copy(reader = reader.copy(book = book.withFallback(reader.book))) else it
                    }
                },
                // 正文已经可读，资料补全失败不覆盖阅读状态或显示全页错误。
                onFailure = { if (it is CancellationException) throw it }
            )
        }
    }

    fun continueBook() {
        val book = mutable.value.book ?: return
        val owner = sessionOwner ?: return
        val saved = reading.latest(owner, "novel", book.path)
            ?.takeIf { record -> mutable.value.volumes.any { it.id == record.chapter } }
        if (saved != null) openVolume(book.path, saved.chapter, saved)
        else mutable.value.volumes.firstOrNull()?.let { openVolume(book.path, it.id) }
    }

    fun position(section: String, line: Int, character: Int, fraction: Double, completed: Boolean) {
        val volume = mutable.value.reader ?: return
        val owner = sessionOwner?.takeIf { it == progress.currentOwner } ?: return
        if (network.settings.value.requestMode != mutable.value.mode || auth.getReadingSessionSync()?.owner != owner) return
        val r = LocalReadingRecord(
            owner,
            "novel",
            volume.book.path,
            volume.volume.id,
            volume.volume.name,
            group = mutable.value.mode.name,
            title = safeBookTitle(volume.book.path, volume.book.name),
            cover = volume.book.cover,
            authors = volume.book.authors.joinToString(" / ") { it.name },
            section = section,
            line = line,
            character = character,
            fraction = fraction,
            completed = completed
        )
        lastPosition = r
        reading.record(r)
    }

    fun currentPosition() = lastPosition ?: mutable.value.restore
    fun flushPosition() = reading.flushAsync()
    fun bookmark() {
        val r = lastPosition ?: return
        if (auth.getReadingSessionSync()?.owner != r.owner || !current(
                r.owner,
                mutable.value.mode
            )
        ) return
        viewModelScope.launch(errors) {
            reading.toggleBookmark(r).fold(
                onSuccess = { mutable.update { it.copy(notice = "书签已更新") } },
                onFailure = { error ->
                    if (error is CancellationException) throw error; mutable.update {
                    it.copy(
                        error = error.message ?: "书签保存失败"
                    )
                }
                }
            )
        }
    }

    /** 阅读器与详情共用持久下载任务，离开页面不会中断下载。 */
    fun cache() {
        val selected = state.value
        if (selected.refreshing) return
        val volume = selected.reader ?: return
        val included =
            selected.downloadTask?.volumes?.any { it.volume.id == volume.volume.id } == true
        when (selected.downloadInfo?.status.takeIf { included }) {
            DownloadTaskStatus.PAUSED, DownloadTaskStatus.PAUSED_BY_RISK, DownloadTaskStatus.ERROR -> resumeDownloads()
            DownloadTaskStatus.WAITING, DownloadTaskStatus.WAITING_FOR_WIFI,
            DownloadTaskStatus.REQUESTING_PAGE, DownloadTaskStatus.DOWNLOADING -> pauseDownloads()

            else -> enqueueVolumes(volume.book, listOf(volume.volume))
        }
    }

    fun refreshVolume() {
        if (mutable.value.refreshing) return
        val previous = mutable.value.reader ?: return
        val owner = sessionOwner ?: return
        val mode = mutable.value.mode
        cacheJob?.cancel()
        mutable.update { it.copy(refreshing = true, refreshFailed = false, error = null) }
        cacheJob = viewModelScope.launch(errors) {
            repository.readVolume(
                mode,
                owner,
                previous.book.path,
                previous.volume.id,
                refresh = true
            ).fold(
                onSuccess = { updated ->
                    ensureActive()
                    if (!current(owner, mode)) return@fold
                    val saved = currentPosition()?.let { old ->
                        val line = updated.document.clampLine(old.line)
                        old.copy(
                            line = line,
                            character = updated.document.clampCharacter(line, old.character),
                            section = old.section.takeIf { key -> updated.sections.any { it.key == key } }
                                .orEmpty()
                        )
                    }
                    lastPosition = saved
                    mutable.update {
                        it.copy(
                            reader = updated.copy(book = updated.book.withFallback(previous.book)),
                            refreshing = false,
                            restore = saved,
                            anchorRevision = it.anchorRevision + 1,
                            notice = "已检查官方正文和目录；阅读位置已恢复"
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    ensureActive()
                    if (current(owner, mode)) mutable.update {
                        it.copy(
                            refreshing = false, refreshFailed = true,
                            error = "检查更新失败，原缓存仍可阅读：${error.message ?: "请重试"}"
                        )
                    }
                }
            )
        }
    }

    fun deleteCache() {
        val volume = mutable.value.reader ?: return
        removeDownloadedVolumes(setOf(volume.volume.id))
    }

    fun pauseDownloads() {
        currentDownloadKey()?.let(downloads::pauseDownload)
    }

    fun resumeDownloads() {
        currentDownloadKey()?.let(downloads::resumeDownload)
    }

    fun cancelDownloads() {
        val key = currentDownloadKey() ?: return
        viewModelScope.launch(errors) { downloads.cancelDownload(key) }
    }

    fun removeDownloadedVolumes(ids: Set<String>) {
        if (ids.isEmpty()) return
        val selected = mutable.value
        val owner = sessionOwner ?: return
        val path = selected.reader?.book?.path ?: selected.detailPath
        viewModelScope.launch(errors) {
            downloads.removeNovelVolumes(selected.mode, owner, path, ids).fold(
                onSuccess = {
                    ensureActive()
                    if (current(owner, selected.mode)) mutable.update {
                        it.copy(
                            reader = it.reader?.let { reader ->
                                if (reader.book.path == path && reader.volume.id in ids)
                                    reader.copy(
                                        cachedImages = emptyMap(),
                                        offlineReady = false
                                    ) else reader
                            },
                            notice = "已删除 ${ids.size} 卷下载，阅读记录和书签保留"
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    if (current(owner, selected.mode)) mutable.update {
                        it.copy(notice = error.message ?: "删除下载失败")
                    }
                }
            )
        }
    }

    private fun currentDownloadKey(): String? {
        val selected = mutable.value
        val owner = sessionOwner ?: return null
        val path = selected.reader?.book?.path ?: selected.detailPath
        return path.takeIf(String::isNotBlank)?.let { novelDownloadKey(selected.mode, owner, it) }
    }

    private fun refreshShelf(book: NovelBook) {
        if (!mutable.value.isLoggedIn || book.uuid.isBlank()) return
        val owner = sessionOwner ?: return
        val mode = mutable.value.mode
        shelfJob?.cancel()
        mutable.update { it.copy(shelfLoading = true) }
        shelfJob = viewModelScope.launch(errors) {
            val result = shelf.contains(book.path)
            ensureActive()
            if (!current(owner, mode) || mutable.value.detailPath != book.path) return@launch
            result.fold(
                onSuccess = { added ->
                    mutable.update {
                        it.copy(
                            isInShelf = added,
                            shelfLoading = false
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    mutable.update {
                        it.copy(
                            shelfLoading = false,
                            notice = error.message ?: "书架状态加载失败"
                        )
                    }
                }
            )
        }
    }

    /** 从后台返回时同步云端状态；正在提交的收藏操作不被刷新打断。 */
    fun refreshShelfState() {
        if (mutable.value.view != NovelView.DETAIL || shelfJob?.isActive == true) return
        mutable.value.book?.let(::refreshShelf)
    }

    fun toggleShelf() {
        val selected = mutable.value
        val book = selected.book ?: return
        val owner = sessionOwner ?: return
        if (!selected.isLoggedIn || selected.shelfLoading || book.uuid.isBlank()) return
        val collected = !selected.isInShelf
        mutable.update { it.copy(shelfLoading = true) }
        shelfJob = viewModelScope.launch(errors) {
            val result = if (collected) shelf.add(book.uuid, book.path) else shelf.remove(
                book.uuid,
                book.path
            )
            ensureActive()
            if (!current(
                    owner,
                    selected.mode
                ) || mutable.value.detailPath != book.path
            ) return@launch
            result.fold(
                onSuccess = {
                    mutable.update {
                        it.copy(
                            isInShelf = collected,
                            shelfLoading = false,
                            notice = if (collected) "已加入书架" else "已取消收藏"
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    mutable.update {
                        it.copy(
                            shelfLoading = false,
                            notice = error.message
                                ?: if (collected) "加入书架失败" else "取消收藏失败"
                        )
                    }
                }
            )
        }
    }

    /** 下载选中的分卷正文和插图；应用级队列负责后台执行和恢复。 */
    fun downloadVolumes(ids: Set<String>) {
        val selected = mutable.value
        val book = selected.book ?: return
        enqueueVolumes(book, selected.volumes.filter { it.id in ids })
    }

    private fun enqueueVolumes(book: NovelBook, volumes: List<NovelVolume>) {
        val owner = sessionOwner ?: return
        val mode = mutable.value.mode
        if (volumes.isEmpty() || !current(owner, mode)) return
        downloads.enqueueDownload(
            NovelDownloadTask(
                mode,
                owner,
                book,
                volumes.map { NovelDownloadItem(it) })
        )
        mutable.update { it.copy(notice = "已加入下载队列，可在下载页管理") }
    }

    fun retry() {
        when (mutable.value.view) {
            NovelView.BROWSE -> refresh()
            NovelView.DETAIL -> requestedBook?.let(::openBook)
            NovelView.READER -> if (mutable.value.refreshFailed) refreshVolume() else if (mutable.value.reader != null) cache() else requestedVolume?.let {
                openVolume(
                    it.first,
                    it.second,
                    it.third
                )
            }
        }
    }

    fun clearNotice() {
        mutable.update { it.copy(notice = null) }
    }

    fun back(): Boolean {
        reading.flushAsync()
        job?.cancel(); cacheJob?.cancel()
        shelfJob?.cancel()
        metadataJob?.cancel()
        val s = mutable.value
        return when (s.view) {
            NovelView.READER -> {
                val detailPath = requestedBook?.takeIf { it == requestedVolume?.first }
                if (detailPath != null && (s.bookLoading || s.volumesLoading)) {
                    // 阅读器取消了未完成的详情请求；返回时仅补取尚未完成的部分。
                    loadBook(
                        detailPath,
                        reloadInfo = s.bookLoading,
                        reloadVolumes = s.volumesLoading
                    )
                    return true
                }
                mutable.update {
                    it.copy(
                        view = if (it.book != null || detailPath != null) NovelView.DETAIL else NovelView.BROWSE,
                        reader = null,
                        busy = false,
                        caching = false,
                        refreshing = false,
                        refreshFailed = false,
                        shelfLoading = false
                    )
                }
                s.book?.let(::refreshShelf)
                true
            }

            NovelView.DETAIL -> {
                mutable.update {
                    it.copy(
                        view = NovelView.BROWSE,
                        busy = false,
                        loadingMore = false,
                        error = null,
                        shelfLoading = false
                    )
                }; true
            }

            NovelView.BROWSE -> false
        }
    }

    private fun current(owner: String, mode: RequestMode) =
        owner == sessionOwner && owner == progress.currentOwner && mode == network.settings.value.requestMode && auth.getReadingSessionSync()
            ?.let { it.owner == owner && it.mode == mode } == true
}
