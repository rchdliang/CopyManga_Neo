package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import com.rchdliang.copymanga_neo.multiplatform.ui.components.userFacingError
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiLog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.ChapterContent
import com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord
import com.rchdliang.copymanga_neo.domain.model.ReaderPreferences
import com.rchdliang.copymanga_neo.domain.model.offlineContent
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
import com.rchdliang.copymanga_neo.domain.repository.LocalReadingRepository
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.ReaderPreferencesRepository
import com.rchdliang.copymanga_neo.domain.repository.ReadingProgressRepository
import com.rchdliang.copymanga_neo.domain.repository.RiskControlException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReaderViewModel constructor(
    private val repository: MangaRepository,
    private val prefs: ReaderPreferencesRepository,
    private val downloadRepository: DownloadRepository,
    private val readingProgress: ReadingProgressRepository,
    private val tools: com.rchdliang.copymanga_neo.domain.repository.ReaderToolsRepository,
    private val localReading: LocalReadingRepository,
    private val auth: com.rchdliang.copymanga_neo.domain.repository.ReadingSessionRepository,
    private val shelfReading: com.rchdliang.copymanga_neo.domain.repository.ShelfReadingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    val toolsSettings = tools.settings
    val bookmarks = combine(localReading.snapshot, readingProgress.progress) { local, progress ->
        local.bookmarks.filter { it.owner == progress.owner && it.kind == "comic" }.map {
            com.rchdliang.copymanga_neo.domain.model.ReaderBookmark(
                it.path,
                it.chapter,
                it.name,
                it.group,
                it.page,
                it.updatedAt,
                it.fraction
            )
        }.sortedByDescending { it.createdAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val localReadingError = localReading.error
    private var readerOwner: String? = null
    private var readerSession: com.rchdliang.copymanga_neo.domain.model.LocalReadingSession? = null
    fun savedPosition(chapter: String) =
        readerOwner?.takeIf { it == readingProgress.currentOwner && it == auth.getReadingSessionSync()?.owner }
            ?.let { localReading.position(it, "comic", currentComicPathWord, chapter) }

    fun recordPosition(chapter: String, page: Int, fraction: Double, completed: Boolean) {
        val owner =
            readerOwner?.takeIf { it == readingProgress.currentOwner && it == auth.getReadingSessionSync()?.owner }
                ?: return
        val item = chain.firstOrNull { it.uuid == chapter } ?: return
        val record = LocalReadingRecord(
            owner,
            "comic",
            currentComicPathWord,
            chapter,
            item.content.chapterName,
            group = item.group,
            title = comicTitle,
            cover = comicCover,
            authors = comicAuthors,
            page = page,
            fraction = fraction,
            completed = completed,
            atChapterEnd = completed
        )
        localReading.record(record)
        readerSession?.let { shelfReading.record(it, record) }
        if (currentChapterUuid != chapter || lastReportedChapter != chapter) {
            readingProgress.recordRead(
                owner,
                currentComicPathWord,
                chapter,
                item.content.chapterName,
                item.group
            )
            lastReportedChapter = chapter
        }
    }

    fun completeChapter(chapter: String) {
        readerOwner?.takeIf { it == readingProgress.currentOwner && it == auth.getReadingSessionSync()?.owner }
            ?.let { localReading.complete(it, currentComicPathWord, chapter) }
    }

    fun flushPosition() = localReading.flushAsync()
    private var lastReportedChapter: String? = null

    // 未确认权限时允许查看已下载评论；发表由评论区单独确认权限。
    private val mutableCommentsAllowed = MutableStateFlow(true)
    val commentsAllowed = mutableCommentsAllowed.asStateFlow()
    private val mutableClosedChapters = MutableStateFlow<Set<String>>(emptySet())
    val closedChapters = mutableClosedChapters.asStateFlow()
    private val mutableAppending = MutableStateFlow(false)
    val appending = mutableAppending.asStateFlow()
    private var chain = emptyList<ReaderSessionChapter>()
    private var appendJob: Job? = null
    private var prependJob: Job? = null
    private var appendBoundary: String? = null
    private var prependBoundary: String? = null
    private var attemptedPrevious: String? = null
    private val navigationJobs = mutableMapOf<String, Job>()
    private var policyJob: Job? = null
    private var attemptedNext: String? = null
    private var automaticAdjacentPaused = false
    private var sessionRevision = 0L
    private val mutableCatalog = MutableStateFlow(ReaderCatalogUiState())
    val catalog = mutableCatalog.asStateFlow()
    private var catalogJob: Job? = null

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        UiLog.e("ReaderViewModel", "Unhandled coroutine exception", throwable)
        _uiState.value = ReaderUiState.Error(userFacingError(throwable, "未知错误"))
        _toastMessage.value = userFacingError(throwable, "操作失败")
    }

    fun clearToastMessage() {
        _toastMessage.value = null
    }

    private val mutablePreferences = MutableStateFlow<ReaderPreferences?>(null)
    val readerPreferences = mutablePreferences.asStateFlow()
    private var preferencesJob: Job? = null
    private var preferencesComic: String? = null

    private var initialized = false
    private var loadJob: Job? = null
    private var currentComicPathWord: String = ""
    private var comicTitle: String = ""
    private var comicCover: String = ""
    private var comicAuthors: String = ""
    private var currentChapterUuid: String = ""
    private var currentGroup: String = "default"

    private var openedInitialChapter: String? = null

    init {
        viewModelScope.launch {
            readingProgress.progress.map { it.owner }.distinctUntilChanged().collect { owner ->
                if (owner != null && readerOwner != null && owner != readerOwner) {
                    loadJob?.cancel(); appendJob?.cancel(); prependJob?.cancel(); policyJob?.cancel(); catalogJob?.cancel()
                    navigationJobs.values.forEach { it.cancel() }; navigationJobs.clear()
                    initialized = false; readerOwner = null; chain = emptyList()
                    mutableCommentsAllowed.value = false
                    _uiState.value = ReaderUiState.Error("阅读会话已改变，请返回并重新打开")
                }
            }
        }
    }

    fun openReader(
        comicPathWord: String,
        initialChapterPathWord: String,
        groupPathWord: String
    ) {
        if (initialized &&
            currentComicPathWord == comicPathWord &&
            currentGroup == groupPathWord && openedInitialChapter == initialChapterPathWord
        ) {
            return
        }
        initialized = true
        if (currentComicPathWord != comicPathWord) {
            comicTitle = ""; comicCover = ""; comicAuthors = ""
        }
        openedInitialChapter = initialChapterPathWord
        currentComicPathWord = comicPathWord
        currentGroup = groupPathWord
        mutableCommentsAllowed.value = true
        mutableClosedChapters.value = emptySet()
        policyJob?.cancel()
        policyJob = viewModelScope.launch(exceptionHandler) {
            val owner = readingProgress.awaitOwner()
            downloadRepository.getDownloadedComic(comicPathWord)?.let { cached ->
                comicTitle = cached.name
                comicCover = cached.cover.orEmpty()
                comicAuthors = cached.authorNames
            }
            repository.getComicDetail(comicPathWord).fold(
                onSuccess = { detail ->
                    ensureActive()
                    if (currentComicPathWord == comicPathWord) mutableCommentsAllowed.value =
                        !detail.comic.closeRoast
                    if (currentComicPathWord == comicPathWord && readingProgress.currentOwner == owner) {
                        comicTitle = detail.comic.name
                        comicCover = detail.comic.cover.orEmpty()
                        comicAuthors = detail.comic.author.orEmpty().joinToString(" / ") { it.name }
                    }
                },
                onFailure = { ensureActive() }
            )
            if (currentComicPathWord == comicPathWord && readingProgress.currentOwner == owner) {
                localReading.updateMetadata(
                    owner,
                    "comic",
                    comicPathWord,
                    comicTitle,
                    comicCover,
                    comicAuthors
                )
                    .onFailure { if (it is CancellationException) throw it }
            }
        }
        loadChapter(comicPathWord, initialChapterPathWord, groupPathWord)
    }

    fun loadChapter(
        comicPathWord: String,
        chapterUuid: String,
        group: String = "default",
        preserveContent: Boolean = false
    ) {
        // 刷新保留同一阅读会话，页码和连续滚动偏移仍由已存在的界面状态维护。
        val keepContent = preserveContent && currentComicPathWord == comicPathWord &&
                currentChapterUuid == chapterUuid && _uiState.value is ReaderUiState.Success
        val previousContent = (_uiState.value as? ReaderUiState.Success)
            ?.takeIf { currentComicPathWord == comicPathWord }
        val preserveOnFailure = keepContent || previousContent != null
        if (preferencesComic != comicPathWord) {
            preferencesJob?.cancel()
            preferencesJob = null
            preferencesComic = null
            mutablePreferences.value = null
        }
        currentComicPathWord = comicPathWord
        if (previousContent == null) {
            currentChapterUuid = chapterUuid
            currentGroup = group
        }
        appendJob?.cancel(); prependJob?.cancel()
        appendBoundary = null; prependBoundary = null; attemptedPrevious = null
        if (keepContent) {
            // 同一会话内刷新一章，不打断其他已追加章节尚未完成的在线导航。
            navigationJobs.remove(chapterUuid)?.cancel()
        } else {
            navigationJobs.values.toList().forEach { it.cancel() }
            navigationJobs.clear()
            sessionRevision++
            catalogJob?.cancel()
            mutableCatalog.value = ReaderCatalogUiState()
            if (previousContent == null) chain = emptyList()
        }
        mutableAppending.value = false
        attemptedNext = null
        automaticAdjacentPaused = false
        val revision = sessionRevision

        loadJob?.cancel()
        loadJob = viewModelScope.launch(exceptionHandler) {
            if (!preserveOnFailure) _uiState.value = ReaderUiState.Loading

            try {
                tools.awaitReady().fold(
                    onSuccess = {},
                    onFailure = {
                        if (it is CancellationException) throw it
                        loadFailed("无法读取阅读工具设置：${it.message}", preserveOnFailure)
                        return@launch
                    }
                )
                val progressOwner = readingProgress.awaitOwner()
                val loadedProgress = readingProgress.awaitReady(progressOwner)
                loadedProgress.error?.let { _toastMessage.value = it }
                readerOwner = progressOwner
                readerSession = auth.getReadingSessionSync()
                // 先加载漫画的完整设置，再展示图片，避免方向或缩放先闪回全局值。
                prefs.initializeComic(comicPathWord).fold(
                    onSuccess = { ensureActive(); mutablePreferences.value = it },
                    onFailure = {
                        ensureActive()
                        if (it is CancellationException) throw it
                        loadFailed("无法读取阅读设置：${it.message}", preserveOnFailure)
                        return@launch
                    }
                )
                ensureActive()
                if (preferencesComic != comicPathWord) {
                    preferencesComic = comicPathWord
                    preferencesJob = viewModelScope.launch(exceptionHandler) {
                        prefs.comicPreferences(comicPathWord).collect {
                            if (currentComicPathWord == comicPathWord) mutablePreferences.value = it
                        }
                    }
                }
                val downloadedChapter = downloadRepository.getDownloadedChapter(chapterUuid)
                if (downloadedChapter != null && downloadedChapter.comicPathWord == comicPathWord) {
                    val allChapters = downloadRepository.getDownloadedChapters(comicPathWord)
                    ensureActive()
                    val localContent = downloadedChapter.offlineContent(allChapters)
                    if (!keepContent) chain = emptyList()
                    currentChapterUuid = chapterUuid
                    currentGroup = downloadedChapter.groupPathWord ?: group
                    publish(localContent, chapterUuid, downloadedChapter.groupPathWord ?: group)
                    val resolvedGroup = downloadedChapter.groupPathWord ?: group
                    refreshDownloadedNavigation(
                        comicPathWord, chapterUuid, resolvedGroup, revision
                    )
                    return@launch
                }

                repository.getChapterContent(comicPathWord, chapterUuid, group)
                    .onSuccess { content ->
                        ensureActive()
                        if (!keepContent) chain = emptyList()
                        currentChapterUuid = chapterUuid
                        currentGroup = group
                        publish(content, chapterUuid, group)

                    }
                    .onFailure { error ->
                        ensureActive()
                        if (error is CancellationException) throw error
                        loadFailed(userFacingError(error, "未知错误"), preserveOnFailure)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiLog.e("ReaderViewModel", "loadChapter failed", e)
                ensureActive()
                loadFailed(userFacingError(e, "加载失败"), preserveOnFailure)
            }
        }
    }

    private fun loadFailed(message: String, keepContent: Boolean) {
        if (keepContent) {
            _toastMessage.value = "章节加载失败，保留当前内容：$message"
        } else if (message == "contentKey为空" || message.startsWith("code 210:")) {
            _uiState.value = ReaderUiState.RiskControl(message)
        } else {
            _uiState.value = ReaderUiState.Error(message)
            _toastMessage.value = message
        }
    }

    /** 本地图像先展示，再补齐在线导航；迟到的目录不能写回已切换的阅读会话。 */
    private fun refreshDownloadedNavigation(
        comic: String, uuid: String, group: String, revision: Long
    ) {
        navigationJobs.remove(uuid)?.cancel()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = repository.getChapterInfo(comic, uuid, group)
                ensureActive()
                if (revision != sessionRevision || comic != currentComicPathWord) return@launch
                result.fold(
                    onSuccess = { chapter ->
                        val entry = chain.firstOrNull { it.uuid == uuid } ?: return@fold
                        publish(
                            entry.content.copy(
                                prevUuid = chapter.prevChapter,
                                nextUuid = chapter.nextChapter,
                                chapterName = chapter.name
                            ), uuid, group
                        )
                        try {
                            downloadRepository.updateChapterMetadata(comic, group, listOf(chapter))
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            UiLog.w("ReaderViewModel", "保存章节导航失败", error)
                        }
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        UiLog.d("ReaderViewModel", "目录刷新失败，使用离线导航")
                    }
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                UiLog.w("ReaderViewModel", "目录刷新失败，使用离线导航", error)
            } finally {
                if (navigationJobs[uuid] === coroutineContext[Job]) navigationJobs.remove(uuid)
            }
        }
        navigationJobs[uuid] = job
        job.start()
    }

    fun loadPrevChapter() {
        val currentState = _uiState.value
        if (currentState is ReaderUiState.Success) {
            val prevUuid = currentState.content.prevUuid
            if (prevUuid != null) {
                loadChapter(currentComicPathWord, prevUuid, currentGroup)
            }
        }
    }

    private fun publish(content: ChapterContent, uuid: String, group: String) {
        val item = ReaderSessionChapter(uuid, group, content)
        chain =
            if (chain.any { it.uuid == uuid }) chain.map { if (it.uuid == uuid) item else it } else listOf(
                item
            )
        val active = chain.firstOrNull { it.uuid == currentChapterUuid } ?: item
        _uiState.value = ReaderUiState.Success(active.content, active.uuid, chain)
    }

    /** 当前可见图片变化才更新活跃章节；预加载不会写入阅读历史。 */
    fun activateChapter(uuid: String) {
        if (currentChapterUuid == uuid) return
        val chapter = chain.firstOrNull { it.uuid == uuid } ?: return
        currentChapterUuid = uuid
        currentGroup = chapter.group
        _uiState.value = ReaderUiState.Success(chapter.content, uuid, chain)

    }

    /** 每次只追加下一章；同一个失败目标不因滚动重组被反复请求。 */
    fun appendNextChapter(retry: Boolean = false) = loadAdjacent(previous = false, retry = retry)
    fun prependPreviousChapter(retry: Boolean = false) = loadAdjacent(previous = true, retry = retry)

    /** 两侧最多各一个请求；迟到结果只允许追加到请求时的边界。 */
    private fun loadAdjacent(previous: Boolean, retry: Boolean) {
        if (automaticAdjacentPaused && !retry) return
        if (!tools.settings.value.continuousChapters || chain.size >= ReaderWindowPolicy.MAX_RESIDENT_CHAPTERS) return
        if ((if (previous) prependJob else appendJob)?.isActive == true) return
        val boundary = (if (previous) chain.firstOrNull() else chain.lastOrNull()) ?: return
        val next = (if (previous) boundary.content.prevUuid else boundary.content.nextUuid) ?: return
        val attempted = if (previous) attemptedPrevious else attemptedNext
        if (chain.any { it.uuid == next } || (!retry && attempted == next)) return
        if (previous) { attemptedPrevious = next; prependBoundary = boundary.uuid }
        else { attemptedNext = next; appendBoundary = boundary.uuid; mutableAppending.value = true }
        val revision = sessionRevision
        val comic = currentComicPathWord
        val request = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                val downloaded = downloadRepository.getDownloadedChapter(next)
                val result = if (downloaded != null && downloaded.comicPathWord == comic) {
                    Result.success(downloaded.offlineContent(downloadRepository.getDownloadedChapters(comic)))
                } else repository.getChapterContent(comic, next, boundary.group)
                ensureActive()
                if (revision != sessionRevision) return@launch
                result.fold(
                    onSuccess = { content ->
                        val edge = if (previous) chain.firstOrNull() else chain.lastOrNull()
                        if (content.imageUrls.isEmpty() || edge?.uuid != boundary.uuid || chain.any { it.uuid == next }) return@fold
                        if (retry) automaticAdjacentPaused = false
                        if (chain.size >= ReaderWindowPolicy.MAX_RESIDENT_CHAPTERS) {
                            if (previous) attemptedPrevious = null else attemptedNext = null
                            return@fold
                        }
                        val group = downloaded?.takeIf { it.comicPathWord == comic }?.groupPathWord ?: boundary.group
                        val item = ReaderSessionChapter(next, group, content)
                        chain = if (previous) listOf(item) + chain else chain + item
                        val active = chain.firstOrNull { it.uuid == currentChapterUuid } ?: boundary
                        _uiState.value = ReaderUiState.Success(active.content, active.uuid, chain)
                        if (downloaded?.comicPathWord == comic) refreshDownloadedNavigation(comic, next, group, revision)
                    },
                    onFailure = { adjacentFailed(previous, it) }
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                UiLog.w("ReaderViewModel", "相邻章节加载失败", error)
                if (revision == sessionRevision) adjacentFailed(previous, error)
            } finally {
                val job = coroutineContext[Job]
                if (previous && prependJob === job) { prependJob = null; prependBoundary = null }
                if (!previous && appendJob === job) { appendJob = null; appendBoundary = null; mutableAppending.value = false }
            }
        }
        if (previous) prependJob = request else appendJob = request
        request.start()
    }

    private fun adjacentFailed(previous: Boolean, error: Throwable) {
        if (error is CancellationException) throw error
        if (error is RiskControlException) {
            automaticAdjacentPaused = true
            _toastMessage.value = "请求过于频繁（210），已暂停自动加载，稍后手动重试"
        } else {
            _toastMessage.value = if (previous) "上一章加载失败，可用上一章按钮重试" else "下一章加载失败，可在章末重试"
        }
    }

    /** 界面停稳并保存锚点后才能裁剪，不能在拖动中改变页面下标。 */
    fun retainWindow(uuid: String) {
        if (uuid != currentChapterUuid) return
        val retained = ReaderWindowPolicy.around(chain, uuid) { it.uuid }
        if (retained == chain) return
        val oldHead = chain.firstOrNull()?.uuid
        val oldTail = chain.lastOrNull()?.uuid
        val keys = retained.map { it.uuid }.toSet()
        chain = retained
        navigationJobs.keys.filter { it !in keys }.forEach { navigationJobs.remove(it)?.cancel() }
        if (appendBoundary != null && appendBoundary !in keys) {
            appendJob?.cancel(); appendJob = null; appendBoundary = null; mutableAppending.value = false
        }
        if (prependBoundary != null && prependBoundary !in keys) {
            prependJob?.cancel(); prependJob = null; prependBoundary = null
        }
        // 裁剪另一端不代表本端失败已经恢复，不得重新自动请求同一失败目标。
        if (oldTail != retained.lastOrNull()?.uuid) attemptedNext = null
        if (oldHead != retained.firstOrNull()?.uuid) attemptedPrevious = null
        mutableClosedChapters.value = mutableClosedChapters.value.intersect(keys)
        val active = retained.firstOrNull { it.uuid == uuid } ?: return
        _uiState.value = ReaderUiState.Success(active.content, active.uuid, retained)
    }

    fun closeChapterComments(uuid: String) {
        if (chain.none { it.uuid == uuid }) return
        mutableClosedChapters.value += uuid
    }

    fun jumpToBookmark(mark: com.rchdliang.copymanga_neo.domain.model.ReaderBookmark) {
        if (mark.comicPathWord == currentComicPathWord) loadChapter(
            mark.comicPathWord,
            mark.chapterUuid,
            mark.group
        )
    }

    fun loadCatalog() {
        if (catalogJob?.isActive == true) return
        val comic = currentComicPathWord
        val group = currentGroup
        mutableCatalog.value = ReaderCatalogUiState(loading = true)
        catalogJob = viewModelScope.launch {
            var local = emptyList<ReaderChapterLink>()
            try {
                local = downloadRepository.getDownloadedChapters(comic)
                    .filter { (it.groupPathWord ?: "default") == group }
                    .sortedBy { it.index }.map { ReaderChapterLink(it.uuid, it.name) }
                ensureActive()
                mutableCatalog.value =
                    ReaderCatalogUiState(chapters = local, loading = true, downloadedOnly = true)
                val catalogResult = repository.getChapterCatalog(comic)
                ensureActive()
                val full = catalogResult.getOrNull()?.chaptersByGroup?.get(group)
                if (full != null) {
                    mutableCatalog.value = ReaderCatalogUiState(chapters = full.map {
                        ReaderChapterLink(
                            it.uuid,
                            it.name
                        )
                    })
                    return@launch
                }
                if (catalogResult.exceptionOrNull() is com.rchdliang.copymanga_neo.domain.repository.RiskControlException) {
                    mutableCatalog.value = ReaderCatalogUiState(
                        chapters = local,
                        downloadedOnly = true,
                        error = "访问受到限制，显示已下载章节"
                    )
                    return@launch
                }
                val result = mutableListOf<ReaderChapterLink>()
                var offset = 0
                do {
                    val pageResult = repository.getChapters(comic, group, offset, 100)
                    ensureActive()
                    val page = pageResult.getOrNull()
                    if (page == null) {
                        mutableCatalog.value =
                            ReaderCatalogUiState(
                                chapters = result.ifEmpty { local },
                                downloadedOnly = result.isEmpty(),
                                error = "在线目录加载失败，点击重试"
                            )
                        return@launch
                    }
                    val fresh =
                        page.list.filter { chapter -> result.none { it.uuid == chapter.uuid } }
                    if (fresh.isEmpty()) break
                    result += fresh.map { ReaderChapterLink(it.uuid, it.name) }
                    offset += page.list.size
                } while (offset < page.total)
                mutableCatalog.value = ReaderCatalogUiState(chapters = result)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableCatalog.value = ReaderCatalogUiState(
                    chapters = local,
                    downloadedOnly = true,
                    error = "在线目录加载失败，点击重试"
                )
            }
        }
    }

    fun selectCatalogChapter(uuid: String) = loadChapter(currentComicPathWord, uuid, currentGroup)

    fun updateTools(transform: (com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings) -> com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings) {
        viewModelScope.launch(exceptionHandler) {
            tools.updateSettings(transform)
                .fold(onSuccess = {}, onFailure = { _toastMessage.value = "阅读设置保存失败" })
        }
    }

    fun toggleBookmark(chapterUuid: String, page: Int, fraction: Double) {
        val chapter = chain.firstOrNull { it.uuid == chapterUuid } ?: return
        val owner =
            readerOwner?.takeIf { it == readingProgress.currentOwner && it == auth.getReadingSessionSync()?.owner }
                ?: return
        viewModelScope.launch(exceptionHandler) {
            val result = localReading.toggleBookmark(
                LocalReadingRecord(
                    owner,
                    "comic",
                    currentComicPathWord,
                    chapterUuid,
                    chapter.content.chapterName,
                    group = chapter.group,
                    title = comicTitle,
                    cover = comicCover,
                    authors = comicAuthors,
                    page = page,
                    fraction = fraction
                )
            )
            result.fold(onSuccess = {}, onFailure = { _toastMessage.value = "书签保存失败" })
        }
    }

    fun loadNextChapter() {
        val currentState = _uiState.value
        if (currentState is ReaderUiState.Success) {
            val nextUuid = currentState.content.nextUuid
            if (nextUuid != null) {
                loadChapter(currentComicPathWord, nextUuid, currentGroup)
            }
        }
    }

    fun refreshCurrentChapter() {
        val comic = currentComicPathWord.ifEmpty { return }
        val chapter = currentChapterUuid.ifEmpty { return }
        loadChapter(comic, chapter, currentGroup.ifEmpty { "default" }, preserveContent = true)
    }

    fun setReadingDirection(value: String) {
        updatePreferences { it.copy(readingDirection = value) }
    }

    fun setPageLayout(value: String) { updatePreferences { it.copy(pageLayout = value) } }

    fun setPageScale(value: String) {
        updatePreferences { it.copy(pageScale = value) }
    }

    fun setScreenOrientation(value: String) {
        updatePreferences { it.copy(screenOrientation = value) }
    }

    private fun updatePreferences(transform: (ReaderPreferences) -> ReaderPreferences) {
        val comic = currentComicPathWord.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch(exceptionHandler) {
            prefs.updateComic(comic, transform).fold(
                onSuccess = {},
                onFailure = { _toastMessage.value = "阅读设置保存失败，请重试" }
            )
        }
    }

    fun resetComicPreferences() {
        val comic = currentComicPathWord.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch(exceptionHandler) {
            prefs.resetComic(comic).fold(
                onSuccess = { _toastMessage.value = "已恢复阅读默认值" },
                onFailure = { _toastMessage.value = "恢复失败，请重试" }
            )
        }
    }
}

sealed class ReaderUiState {
    object Loading : ReaderUiState()
    data class Success(
        val content: ChapterContent, val chapterUuid: String = "",
        val chapters: List<ReaderSessionChapter> = listOf(
            ReaderSessionChapter(
                chapterUuid,
                "default",
                content
            )
        )
    ) : ReaderUiState()

    data class Error(val message: String) : ReaderUiState()
    data class RiskControl(val reason: String) : ReaderUiState()
}

data class ReaderSessionChapter(val uuid: String, val group: String, val content: ChapterContent)

/** 评论页不计入图片页码；稳定 key 用于追加章节和切换评论开关时保持位置。 */
data class ReaderPageEntry(
    val key: String, val chapterUuid: String, val chapterName: String,
    val source: String?, val page: Int, val imageCount: Int
)

data class ReaderChapterLink(val uuid: String, val name: String)
data class ReaderCatalogUiState(
    val chapters: List<ReaderChapterLink> = emptyList(),
    val loading: Boolean = false, val error: String? = null,
    val downloadedOnly: Boolean = false
)
