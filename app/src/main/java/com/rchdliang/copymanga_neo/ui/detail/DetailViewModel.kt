package com.rchdliang.copymanga_neo.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.data.download.ChapterDownloadItem
import com.rchdliang.copymanga_neo.data.download.ChapterDownloadStatus
import com.rchdliang.copymanga_neo.data.download.ComicDownloadTask
import com.rchdliang.copymanga_neo.domain.model.Author
import com.rchdliang.copymanga_neo.domain.model.BrowseInfo
import com.rchdliang.copymanga_neo.domain.model.ChapterCatalog
import com.rchdliang.copymanga_neo.domain.model.ChapterGroup
import com.rchdliang.copymanga_neo.domain.model.ChapterInfo
import com.rchdliang.copymanga_neo.domain.model.ComicDetail
import com.rchdliang.copymanga_neo.domain.model.ComicInfo
import com.rchdliang.copymanga_neo.domain.model.DisplayValue
import com.rchdliang.copymanga_neo.domain.model.DownloadedComicInfo
import com.rchdliang.copymanga_neo.domain.model.LastChapter
import com.rchdliang.copymanga_neo.domain.model.ReadingProgressSnapshot
import com.rchdliang.copymanga_neo.domain.model.Theme
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.ReadingProgressRepository
import com.rchdliang.copymanga_neo.domain.repository.ThemeBlacklistRepository
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

sealed interface ChapterBatchAction {
    data object ManageDownloads : ChapterBatchAction
}

data class ChapterBatchSelectionState(
    val action: ChapterBatchAction,
    val selectedChapterUuids: Set<String> = emptySet(),
    val visitedChapters: Map<String, ChapterInfo> = emptyMap()
) {
    /** 保留已访问组的章节资料，以便切组后仍能统一入队。 */
    fun managedChapters(currentChapters: List<ChapterInfo>): Map<String, ChapterInfo> =
        visitedChapters + currentChapters.associateBy { it.uuid }
}

data class DetailUiState(
    val comicDetail: ComicInfo? = null,
    val chapters: ImmutableList<ChapterInfo> = persistentListOf(),
    val isLoading: Boolean = false,
    val isLoadingChapters: Boolean = false,
    val isChapterCatalogReady: Boolean = false,
    val chapterCatalog: ChapterCatalog? = null,
    val chapterTotal: Int = 0,
    val error: String? = null,
    val riskControlReason: String? = null,
    val groups: ImmutableList<ChapterGroup> = persistentListOf(),
    val selectedGroup: String = "",
    val sortAscending: Boolean = true,
    val isLoggedIn: Boolean = false,
    val isInShelf: Boolean = false,
    val shelfCollectId: Int? = null,
    val browse: BrowseInfo? = null,
    val browseGroup: String? = null,
    val isProgressLoading: Boolean = true,
    val isShelfLoading: Boolean = false,
    val toastMessage: String? = null,
    val batchSelection: ChapterBatchSelectionState? = null,
    val downloadedChapterUuids: Set<String> = emptySet(),
    val chapterDownloadStatuses: Map<String, ChapterDownloadStatus> = emptyMap(),
    val chapterDownloadProgresses: Map<String, Float> = emptyMap(),
    val hasLocalCache: Boolean = false,
    val blockedThemes: List<Theme> = emptyList(),
    val isBlacklistReady: Boolean = false,
    val isSavingBlacklist: Boolean = false
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val mangaRepository: MangaRepository,
    private val userRepository: UserRepository,
    private val downloadRepository: DownloadRepository,
    private val themeBlacklistRepository: ThemeBlacklistRepository,
    private val readingProgress: ReadingProgressRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    private var currentPathWord: String? = null
    private var hasManuallySelectedGroup = false
    val uiState: StateFlow<DetailUiState> =
        combine(_uiState, readingProgress.progress) { state, progress ->
            withReadingProgress(state, progress)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, DetailUiState())

    private fun withReadingProgress(
        state: DetailUiState,
        progress: ReadingProgressSnapshot
    ): DetailUiState {
        val path = currentPathWord
        val local =
            if (progress.owner == readingProgress.currentOwner) progress.records[path] else null
        val browse = if (local != null && path != null) local.toBrowseInfo(path) else state.browse
        return state.copy(
            browse = browse,
            browseGroup = state.chapterCatalog?.findGroup(browse?.chapterUuid)
                ?: local?.groupPathWord ?: local?.lastReadGroupPathWord,
            isProgressLoading = local == null && (state.isProgressLoading || !progress.isReady)
        )
    }

    /** 云端章节优先从完整目录定位；本地待同步的较新进度仍由进度仓库保护。 */
    private fun chooseGroup(
        groups: Collection<ChapterGroup>,
        catalog: ChapterCatalog?,
        preferred: String? = null
    ): String {
        val state = withReadingProgress(
            _uiState.value.copy(chapterCatalog = catalog),
            readingProgress.progress.value
        )
        val candidates = listOfNotNull(
            _uiState.value.selectedGroup.takeIf { hasManuallySelectedGroup },
            state.browseGroup, preferred, "default", groups.firstOrNull()?.pathWord
        )
        return candidates.firstOrNull { candidate -> groups.any { it.pathWord == candidate } }
            ?: "default"
    }

    private suspend fun loadRemoteDetail(pathWord: String): Pair<Result<ComicDetail>, ChapterCatalog?> =
        supervisorScope {
            val catalog = async { mangaRepository.getChapterCatalog(pathWord, forceRefresh = true) }
            val detail = async { mangaRepository.getComicDetail(pathWord) }
            val detailResult = detail.await()
            val catalogResult = catalog.await()
            val risk =
                catalogResult.exceptionOrNull() as? com.rchdliang.copymanga_neo.domain.repository.RiskControlException
            if (risk != null) Result.failure<ComicDetail>(risk) to null
            else detailResult to catalogResult.getOrNull()
        }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("DetailViewModel", "Unhandled coroutine exception", throwable)
        _uiState.update {
            it.copy(
                isLoading = false,
                isLoadingChapters = false,
                error = throwable.message,
                toastMessage = throwable.message ?: "操作失败"
            )
        }
    }

    init {
        viewModelScope.launch(exceptionHandler) {
            themeBlacklistRepository.blockedThemes.collect { themes ->
                _uiState.update { it.copy(blockedThemes = themes, isBlacklistReady = true) }
            }
        }
        viewModelScope.launch(exceptionHandler) {
            uiState.collect { state ->
                // 网络进度迟到时可以纠正初始分组，但不能抢走用户已经手动选择的组。
                if (hasManuallySelectedGroup || _uiState.value.isLoading ||
                    _uiState.value.isProgressLoading || state.batchSelection != null
                ) return@collect
                val group = state.browseGroup ?: return@collect
                if (state.groups.any { it.pathWord == group } && group != _uiState.value.selectedGroup) {
                    changeGroup(group)
                }
            }
        }
    }

    fun setThemeBlocked(theme: Theme, blocked: Boolean) {
        if (_uiState.value.isSavingBlacklist || !_uiState.value.isBlacklistReady) return
        viewModelScope.launch(exceptionHandler) {
            _uiState.update { it.copy(isSavingBlacklist = true) }
            themeBlacklistRepository.setBlocked(theme, blocked).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSavingBlacklist = false,
                            toastMessage = if (blocked) "已屏蔽「${theme.name}」" else "已取消屏蔽「${theme.name}」"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSavingBlacklist = false,
                            toastMessage = error.message ?: "保存黑名单失败"
                        )
                    }
                }
            )
        }
    }

    private var observeDownloadJob: kotlinx.coroutines.Job? = null

    private var loadDetailJob: kotlinx.coroutines.Job? = null
    private var loadChaptersJob: kotlinx.coroutines.Job? = null
    private var queryStatusJob: kotlinx.coroutines.Job? = null
    private var hasResumedScreen = false

    /** 首次进入由 loadDetail 查询；后续从阅读页返回或应用恢复时再同步。 */
    fun onScreenResumed() {
        if (!hasResumedScreen) {
            hasResumedScreen = true
            return
        }
        refreshReadingStatus()
    }

    fun loadDetail(pathWord: String) {
        if (currentPathWord == pathWord && _uiState.value.comicDetail != null) {
            return
        }

        val preferredGroup = _uiState.value.selectedGroup.takeIf { currentPathWord == pathWord }
        if (currentPathWord != pathWord) hasManuallySelectedGroup = false
        currentPathWord = pathWord
        queryStatusJob?.cancel()
        loadChaptersJob?.cancel()
        observeDownloadJob?.cancel()
        observeDownloadJob = viewModelScope.launch(exceptionHandler) { observeDownload(pathWord) }

        loadDetailJob?.cancel()
        loadDetailJob = viewModelScope.launch(exceptionHandler) {
            userRepository.isAuthCacheReady().first { it }
            val isLoggedIn = userRepository.isLoggedIn().first()

            _uiState.update {
                it.copy(
                    comicDetail = null,
                    hasLocalCache = false,
                    isLoadingChapters = false,
                    isLoading = true,
                    error = null,
                    riskControlReason = null,
                    chapters = persistentListOf(),
                    chapterTotal = 0,
                    isChapterCatalogReady = false,
                    chapterCatalog = null,
                    groups = persistentListOf(),
                    selectedGroup = "",
                    isLoggedIn = isLoggedIn,
                    isInShelf = false,
                    shelfCollectId = null,
                    browse = null,
                    browseGroup = null,
                    isProgressLoading = isLoggedIn,
                    batchSelection = null
                )
            }

            refreshReadingStatus()
            val hasLocal = loadLocalCache(pathWord)
            refreshDownloadStatus(pathWord)

            val (detailResult, catalog) = loadRemoteDetail(pathWord)
            detailResult.fold(
                onSuccess = { detail ->
                    val groups = catalog?.groups ?: detail.groups.values.toList()
                    val selectedGroup = chooseGroup(groups, catalog, preferredGroup)

                    _uiState.update {
                        it.copy(
                            comicDetail = detail.comic,
                            groups = groups.toImmutableList(),
                            chapterCatalog = catalog,
                            selectedGroup = selectedGroup,
                            chapters = persistentListOf(),
                            chapterTotal = 0,
                            hasLocalCache = false,
                            isLoading = false
                        )
                    }
                    saveLocalCache(detail.comic)
                    loadAllChapters(pathWord, _uiState.value.selectedGroup)
                },
                onFailure = { error ->
                    if (hasLocal) {
                        android.util.Log.d("DetailViewModel", "Network failed, showing local cache")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isChapterCatalogReady = true,
                                toastMessage = "网络加载失败，显示本地缓存"
                            )
                        }
                    } else {
                        val message = error.message ?: "未知错误"
                        if (message.startsWith("code 210:")) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    riskControlReason = message
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = message,
                                    toastMessage = message
                                )
                            }
                        }
                    }
                }
            )
        }
    }

    fun refreshDetail() {
        val pathWord = currentPathWord ?: return
        refreshReadingStatus()
        loadDetailJob?.cancel()
        loadChaptersJob?.cancel()
        loadDetailJob = viewModelScope.launch(exceptionHandler) {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isLoadingChapters = false,
                    error = null,
                    riskControlReason = null
                )
            }
            val (detailResult, catalog) = loadRemoteDetail(pathWord)
            detailResult.fold(
                onSuccess = { detail ->
                    val groups = catalog?.groups ?: detail.groups.values.toList()
                    val selectedGroup = chooseGroup(groups, catalog, _uiState.value.selectedGroup)
                    _uiState.update { state ->
                        state.copy(
                            comicDetail = detail.comic,
                            groups = groups.toImmutableList(),
                            chapterCatalog = catalog,
                            selectedGroup = selectedGroup,
                            chapters = state.chapters.filter {
                                (it.groupPathWord ?: "default") == selectedGroup
                            }.toImmutableList(),
                            isLoading = false,
                            hasLocalCache = false,
                            batchSelection = null
                        )
                    }
                    saveLocalCache(detail.comic)
                    loadAllChapters(pathWord, _uiState.value.selectedGroup)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            toastMessage = error.message ?: "刷新失败"
                        )
                    }
                }
            )
        }
    }

    private suspend fun loadLocalCache(pathWord: String): Boolean {
        val cachedComic = downloadRepository.getDownloadedComic(pathWord) ?: return false
        val cachedChapters = downloadRepository.getDownloadedChapters(pathWord)

        val authorList = if (cachedComic.authorNames.isNotBlank()) {
            cachedComic.authorNames.split(",").map { name ->
                Author(name.trim(), "")
            }
        } else null

        val detail = ComicInfo(
            uuid = "",
            b404 = false,
            bHidden = false,
            ban = 0,
            name = cachedComic.name,
            comicAlias = "",
            pathWord = cachedComic.pathWord,
            closeComment = false,
            closeRoast = false,
            freeType = DisplayValue("免费", 0),
            restrict = DisplayValue("一般向", 0),
            reclass = DisplayValue("", 0),
            females = null,
            males = null,
            clubs = null,
            region = DisplayValue("", 0),
            status = DisplayValue(
                cachedComic.statusDisplay,
                if (cachedComic.statusDisplay == "已完结") 1 else 0
            ),
            author = authorList,
            theme = null,
            parodies = null,
            brief = cachedComic.brief,
            lastUpdate = cachedComic.lastUpdate,
            cover = cachedComic.cover,
            lastChapter = if (cachedComic.lastChapterName != null) LastChapter(
                "",
                cachedComic.lastChapterName
            ) else null,
            popular = null,
            channel = null
        )

        val chapters = cachedChapters.map { it.toChapterInfo() }.toImmutableList()

        val localGroups = chapters.groupBy { it.groupPathWord ?: "default" }
        val groups = localGroups.map { (group, items) ->
            ChapterGroup(group, items.size, if (group == "default") "本地章节" else group)
        }
        val selectedGroup = chooseGroup(groups, catalog = null)
        _uiState.update {
            it.copy(
                hasLocalCache = true,
                selectedGroup = selectedGroup,
                groups = groups.toImmutableList(),
                comicDetail = detail,
                chapters = (localGroups[selectedGroup] ?: emptyList()).toImmutableList(),
                chapterTotal = localGroups[selectedGroup]?.size ?: 0,
                isLoading = false
            )
        }

        return true
    }

    private suspend fun saveLocalCache(detail: ComicInfo) {
        val existing = downloadRepository.getDownloadedComic(detail.pathWord)
        if (existing == null) return
        val authors = detail.author?.joinToString(",") { it.name } ?: ""
        val entity = DownloadedComicInfo(
            pathWord = detail.pathWord,
            name = detail.name,
            cover = detail.cover,
            authorNames = authors,
            statusDisplay = if (detail.status.value == 1) "已完结" else "连载中",
            lastUpdate = detail.lastUpdate,
            lastChapterName = detail.lastChapter?.name,
            brief = detail.brief,
            totalChapters = existing.totalChapters,
            downloadedChapters = existing.downloadedChapters,
            downloadAt = existing.downloadAt
        )
        downloadRepository.saveComicCache(entity)
    }

    private suspend fun refreshDownloadStatus(pathWord: String) {
        val downloadedUuids = downloadRepository.getDownloadedChapterUuids(pathWord)
        val task = downloadRepository.getActiveTask().value?.takeIf { it.pathWord == pathWord }
        val statuses = mutableMapOf<String, ChapterDownloadStatus>()
        val progresses = mutableMapOf<String, Float>()

        android.util.Log.d(
            "DetailViewModel",
            "refreshDownloadStatus: pathWord=$pathWord, downloadedUuids=${downloadedUuids.size}, taskPathWord=${task?.pathWord}"
        )

        downloadedUuids.forEach { uuid ->
            statuses[uuid] = ChapterDownloadStatus.DOWNLOADED
        }

        _uiState.value.chapters.forEach { chapter ->
            downloadRepository.getChapterStatus(pathWord, chapter.uuid)?.let { status ->
                if (chapter.uuid !in downloadedUuids && status != ChapterDownloadStatus.DOWNLOADED) {
                    statuses[chapter.uuid] = status
                }
            }
        }
        task?.let {
            it.chapters.forEach { item ->
                if (item.chapter.uuid !in downloadedUuids && item.status != ChapterDownloadStatus.DOWNLOADED) {
                    statuses[item.chapter.uuid] = item.status
                }
                progresses[item.chapter.uuid] = item.progress
            }
        }

        _uiState.update {
            it.copy(
                downloadedChapterUuids = downloadedUuids,
                chapterDownloadStatuses = statuses,
                chapterDownloadProgresses = progresses
            )
        }
    }

    private suspend fun observeDownload(pathWord: String) {
        try {
            kotlinx.coroutines.flow.combine(
                downloadRepository.getDownloadProgress(),
                downloadRepository.getAllDownloadedComics()
            ) { _, _ -> Unit }.collect {
                refreshDownloadStatus(pathWord)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("DetailViewModel", "observeDownload error for $pathWord", e)
        }
    }

    private suspend fun loadQueryStatus(pathWord: String) {
        if (!_uiState.value.isLoggedIn) {
            _uiState.update { it.copy(isProgressLoading = false) }
            return
        }
        val owner = readingProgress.awaitOwner()
        val version = readingProgress.requestVersion()
        _uiState.update { it.copy(isProgressLoading = true) }
        val result = mangaRepository.queryComicStatus(pathWord)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (pathWord != currentPathWord) return
        result.fold(
            onSuccess = { result ->
                readingProgress.acceptRemote(owner, mapOf(pathWord to result.browse), version)
                _uiState.update {
                    it.copy(
                        isInShelf = result.collect != null,
                        shelfCollectId = result.collect,
                        browse = result.browse,
                        isProgressLoading = false
                    )
                }
            },
            onFailure = { _uiState.update { it.copy(isProgressLoading = false) } }
        )
    }

    /** 阅读后返回只更新阅读状态，不重载整份章节目录。 */
    fun refreshReadingStatus() {
        val path = currentPathWord ?: return
        readingProgress.retryPending()
        queryStatusJob?.cancel()
        queryStatusJob = viewModelScope.launch(exceptionHandler) { loadQueryStatus(path) }
    }

    fun selectGroup(groupPathWord: String) {
        if (_uiState.value.groups.none { it.pathWord == groupPathWord }) return
        hasManuallySelectedGroup = true
        changeGroup(groupPathWord)
    }

    private fun changeGroup(groupPathWord: String) {
        val pathWord = currentPathWord ?: return
        if (_uiState.value.selectedGroup == groupPathWord) return

        _uiState.update {
            it.copy(
                selectedGroup = groupPathWord,
                chapters = persistentListOf(),
                chapterTotal = 0,
                batchSelection = it.batchSelection?.let { batch ->
                    batch.copy(visitedChapters = batch.managedChapters(it.chapters))
                }
            )
        }
        loadAllChapters(pathWord, groupPathWord)
    }

    fun toggleSortOrder() {
        _uiState.update { it.copy(sortAscending = !it.sortAscending) }
    }

    fun toggleShelf() {
        val state = _uiState.value
        val comicDetail = state.comicDetail ?: return
        if (!state.isLoggedIn) return

        viewModelScope.launch(exceptionHandler) {
            _uiState.update { it.copy(isShelfLoading = true) }

            if (state.isInShelf && state.shelfCollectId != null) {
                mangaRepository.removeFavorite(state.shelfCollectId).fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                isInShelf = false,
                                shelfCollectId = null,
                                isShelfLoading = false,
                                toastMessage = "已从书架中删除"
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isShelfLoading = false,
                                toastMessage = error.message ?: "删除失败"
                            )
                        }
                    }
                )
            } else {
                mangaRepository.addFavorite(comicDetail.uuid).fold(
                    onSuccess = {
                        val pathWord = currentPathWord ?: return@fold
                        mangaRepository.queryComicStatus(pathWord).fold(
                            onSuccess = { result ->
                                _uiState.update {
                                    it.copy(
                                        isInShelf = result.collect != null,
                                        shelfCollectId = result.collect,
                                        isShelfLoading = false,
                                        toastMessage = "已添加到书架"
                                    )
                                }
                            },
                            onFailure = {
                                _uiState.update {
                                    it.copy(isInShelf = true, isShelfLoading = false)
                                }
                            }
                        )
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isShelfLoading = false,
                                toastMessage = error.message ?: "添加失败"
                            )
                        }
                    }
                )
            }
        }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun getStartChapter(): StartChapterInfo? {
        val state = uiState.value
        val chapters = state.chapters.sortedBy { it.index }

        val browse = state.browse
        if (browse != null && browse.chapterUuid != null) {
            val lastReadIndex = chapters.indexOfFirst { it.uuid == browse.chapterUuid }
            if (lastReadIndex >= 0) {
                // 跳到下一章；若已是最后一章，停留在当前章（避免 fallback 回第一章）
                val current = chapters[lastReadIndex]
                val next = chapters.firstOrNull { it.uuid == current.nextChapter }
                val target = next ?: current
                // isContinue 表示目标章节是否被阅读过：仅当已是最后一章、停留当前章时为 true
                return StartChapterInfo(
                    uuid = target.uuid,
                    name = target.name,
                    groupPathWord = target.groupPathWord ?: "default",
                    isContinue = next == null
                )
            }
        }

        // 记录不在尚未加载完整的目录中，也不能退回本地第一章。
        if (!browse?.chapterUuid.isNullOrBlank()) {
            return StartChapterInfo(
                browse.chapterUuid,
                browse.chapterName?.takeIf { it.isNotBlank() } ?: "上次阅读章节",
                state.browseGroup ?: "default", isContinue = true)
        }
        if (state.isProgressLoading || state.isLoading || state.isLoadingChapters || !state.isChapterCatalogReady) return null
        // 确认无阅读记录、且目录加载结束后，才从首章开始。
        val first = chapters.firstOrNull() ?: return null
        return StartChapterInfo(
            uuid = first.uuid,
            name = first.name,
            groupPathWord = first.groupPathWord ?: "default",
            isContinue = false
        )
    }

    // region 批量管理章节

    fun enterManageMode(chapterUuid: String? = null) {
        _uiState.update { state ->
            // 已有下载默认保持勾选，跨组操作不会把未操作的下载误判为删除。
            val downloadableUuids = state.chapterDownloadStatuses
                .filter {
                    it.value == ChapterDownloadStatus.DOWNLOADED ||
                            it.value == ChapterDownloadStatus.WAITING ||
                            it.value == ChapterDownloadStatus.DOWNLOADING
                }
                .keys
            val initialSelection = if (chapterUuid != null) {
                downloadableUuids + chapterUuid
            } else {
                downloadableUuids
            }
            state.copy(
                batchSelection = ChapterBatchSelectionState(
                    action = ChapterBatchAction.ManageDownloads,
                    selectedChapterUuids = initialSelection,
                    visitedChapters = state.chapters.associateBy { it.uuid }
                )
            )
        }
    }

    fun exitBatchMode() {
        _uiState.update { it.copy(batchSelection = null) }
    }

    fun toggleChapterSelection(chapterUuid: String) {
        _uiState.update { state ->
            val batch = state.batchSelection ?: return@update state
            val selected = batch.selectedChapterUuids.toMutableSet()
            if (selected.contains(chapterUuid)) {
                selected.remove(chapterUuid)
            } else {
                selected.add(chapterUuid)
            }
            state.copy(batchSelection = batch.copy(selectedChapterUuids = selected))
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            val batch = state.batchSelection ?: return@update state
            state.copy(
                batchSelection = batch.copy(
                    selectedChapterUuids = batch.selectedChapterUuids + state.chapters.map { it.uuid }
                )
            )
        }
    }

    fun invertSelection() {
        _uiState.update { state ->
            val batch = state.batchSelection ?: return@update state
            val allUuids = state.chapters.map { it.uuid }.toSet()
            val inverted = replaceVisibleSelection(
                batch.selectedChapterUuids, allUuids, allUuids - batch.selectedChapterUuids
            )
            state.copy(batchSelection = batch.copy(selectedChapterUuids = inverted))
        }
    }

    fun selectToLatest() {
        setRangeSelected(
            fromFirstSelected = true,
            toEnd = true,
            selected = true
        )
    }

    fun unselectToLatest() {
        setRangeSelected(
            fromFirstSelected = true,
            toEnd = true,
            selected = false
        )
    }

    fun selectToFirst() {
        setRangeSelected(
            fromFirstSelected = false,
            toEnd = false,
            selected = true
        )
    }

    fun unselectToFirst() {
        setRangeSelected(
            fromFirstSelected = false,
            toEnd = false,
            selected = false
        )
    }

    fun selectBetween() {
        setRangeSelected(
            fromFirstSelected = true,
            toEnd = false,
            selected = true
        )
    }

    fun unselectBetween() {
        setRangeSelected(
            fromFirstSelected = true,
            toEnd = false,
            selected = false
        )
    }

    private fun setRangeSelected(fromFirstSelected: Boolean, toEnd: Boolean, selected: Boolean) {
        _uiState.update { state ->
            val batch = state.batchSelection ?: return@update state
            val chapters = if (state.sortAscending) state.chapters
            else state.chapters.reversed()
            if (chapters.isEmpty() || batch.selectedChapterUuids.isEmpty()) return@update state
            val sortedSelected = chapters.filter { it.uuid in batch.selectedChapterUuids }
            if (sortedSelected.isEmpty()) return@update state

            val startIndex: Int
            val endIndex: Int
            if (fromFirstSelected) {
                val first = sortedSelected.first()
                startIndex = chapters.indexOf(first)
                endIndex = if (toEnd) chapters.lastIndex
                else chapters.indexOf(sortedSelected.last())
            } else {
                val last = sortedSelected.last()
                startIndex = 0
                endIndex = chapters.indexOf(last)
            }
            if (startIndex < 0 || endIndex < 0) return@update state

            val range = chapters.subList(startIndex, endIndex + 1).map { it.uuid }.toSet()
            val newSelected = if (selected) {
                batch.selectedChapterUuids + range
            } else {
                batch.selectedChapterUuids - range
            }
            state.copy(batchSelection = batch.copy(selectedChapterUuids = newSelected))
        }
    }

    fun canSelectBetween(): Boolean {
        val state = _uiState.value
        val selected = state.batchSelection?.selectedChapterUuids ?: return false
        if (selected.size < 2) return false
        val chapters = if (state.sortAscending) state.chapters
        else state.chapters.reversed()
        val sortedSelected = chapters.filter { it.uuid in selected }
        if (sortedSelected.size < 2) return false
        val firstIndex = chapters.indexOf(sortedSelected.first())
        val lastIndex = chapters.indexOf(sortedSelected.last())
        val rangeSize = lastIndex - firstIndex + 1
        return rangeSize > sortedSelected.size
    }

    fun clearSelection() {
        _uiState.update { state ->
            val batch = state.batchSelection ?: return@update state
            state.copy(
                batchSelection = batch.copy(
                    selectedChapterUuids = replaceVisibleSelection(
                        batch.selectedChapterUuids,
                        state.chapters.map { it.uuid }.toSet(),
                        emptySet()
                    )
                )
            )
        }
    }

    fun hasDownloadableChapters(): Boolean {
        return _uiState.value.chapterDownloadStatuses.any {
            it.value == ChapterDownloadStatus.DOWNLOADED ||
                    it.value == ChapterDownloadStatus.WAITING ||
                    it.value == ChapterDownloadStatus.DOWNLOADING
        }
    }

    fun getSelectedChapterUuids(): Set<String> {
        return _uiState.value.batchSelection?.selectedChapterUuids ?: emptySet()
    }

    fun confirmBatchAction() {
        val state = _uiState.value
        val pathWord = currentPathWord ?: return
        val detail = state.comicDetail ?: return
        val batch = state.batchSelection ?: return
        val managedChapters = batch.managedChapters(state.chapters)
        val changes = chapterDownloadChanges(
            managedChapters.keys, batch.selectedChapterUuids,
            state.chapterDownloadStatuses
        )
        val toDownloadUuids = changes.download
        val toDeleteLocalUuids = changes.delete
        val toCancelDownloadUuids = changes.cancel

        if (toDownloadUuids.isEmpty() && toDeleteLocalUuids.isEmpty() && toCancelDownloadUuids.isEmpty()) {
            exitBatchMode()
            return
        }

        viewModelScope.launch(exceptionHandler) {
            if (toCancelDownloadUuids.isNotEmpty()) {
                downloadRepository.removeChaptersFromTask(pathWord, toCancelDownloadUuids)
            }
            if (toDeleteLocalUuids.isNotEmpty()) {
                downloadRepository.deleteChapters(pathWord, toDeleteLocalUuids.toList())
            }
            if (toDownloadUuids.isNotEmpty()) {
                val chaptersToDownload =
                    managedChapters.values.filter { it.uuid in toDownloadUuids }
                enqueueChaptersDownload(detail, chaptersToDownload)
            }
            exitBatchMode()
            refreshDownloadStatus(pathWord)
        }
    }

    private fun enqueueChaptersDownload(detail: ComicInfo, chapters: List<ChapterInfo>) {
        val authors = detail.author?.joinToString(",") { it.name } ?: ""

        viewModelScope.launch(exceptionHandler) {
            val existing = downloadRepository.getDownloadedComic(detail.pathWord)
            val entity = DownloadedComicInfo(
                pathWord = detail.pathWord,
                name = detail.name,
                cover = detail.cover,
                authorNames = authors,
                statusDisplay = if (detail.status.value == 1) "已完结" else "连载中",
                lastUpdate = detail.lastUpdate,
                lastChapterName = detail.lastChapter?.name,
                brief = detail.brief,
                totalChapters = existing?.downloadedChapters ?: 0,
                downloadedChapters = existing?.downloadedChapters ?: 0,
                downloadAt = System.currentTimeMillis()
            )
            downloadRepository.saveComicCache(entity)

            val downloadItems = chapters.map { chapter ->
                ChapterDownloadItem(
                    chapter = chapter,
                    status = ChapterDownloadStatus.WAITING
                )
            }

            val task = ComicDownloadTask(
                pathWord = detail.pathWord,
                name = detail.name,
                cover = detail.cover,
                chapters = downloadItems,
                authorNames = authors,
                statusDisplay = if (detail.status.value == 1) "已完结" else "连载中",
                lastUpdate = detail.lastUpdate,
                brief = detail.brief
            )

            downloadRepository.enqueueDownload(task)
            refreshDownloadStatus(detail.pathWord)
        }
    }

    // endregion

    private fun loadAllChapters(pathWord: String, group: String = "default") {
        loadChaptersJob?.cancel()
        loadChaptersJob = viewModelScope.launch(exceptionHandler) {
            _uiState.update { it.copy(isLoadingChapters = true, isChapterCatalogReady = false) }

            _uiState.value.chapterCatalog?.chaptersByGroup?.get(group)?.let { chapters ->
                _uiState.update {
                    it.copy(
                        chapters = chapters.toImmutableList(), chapterTotal = chapters.size,
                        isChapterCatalogReady = true, isLoadingChapters = false
                    )
                }
                downloadRepository.updateChapterMetadata(pathWord, group, chapters)
                refreshDownloadStatus(pathWord)
                return@launch
            }

            val cached = downloadRepository.getDownloadedChapters(pathWord)
                .filter { (it.groupPathWord ?: "default") == group }
                .map { it.toChapterInfo() }
            _uiState.update {
                if (it.chapters.isNotEmpty()) it else it.copy(
                    chapters = cached.toImmutableList(),
                    chapterTotal = cached.size
                )
            }
            val allChapters = mutableListOf<ChapterInfo>()
            var offset = 0
            val limit = 100
            var total = 0

            do {
                mangaRepository.getChapters(pathWord, group = group, offset = offset, limit = limit)
                    .fold(
                        onSuccess = { result ->
                            total = result.total
                            if (result.list.isEmpty()) {
                                android.util.Log.w(
                                    "DetailViewModel",
                                    "章节分页提前结束: offset=$offset, total=$total"
                                )
                            }
                            val known = allChapters.map { it.uuid }.toSet()
                            if (result.list.isNotEmpty() && result.list.all { it.uuid in known }) {
                                _uiState.update {
                                    it.copy(
                                        isLoadingChapters = false,
                                        isChapterCatalogReady = true,
                                        toastMessage = "章节分页未推进，请重试"
                                    )
                                }
                                return@launch
                            }
                            allChapters.addAll(result.list.filter { it.uuid !in known })
                            offset = advanceChapterOffset(offset, total, result.list.size)
                            _uiState.update { it.copy(chapterTotal = total) }
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    isLoadingChapters = false,
                                    isChapterCatalogReady = true,
                                    toastMessage = error.message ?: "章节加载失败"
                                )
                            }
                            return@launch
                        }
                    )
            } while (offset < total)

            _uiState.update {
                it.copy(
                    chapters = allChapters.toImmutableList(),
                    isChapterCatalogReady = true,
                    isLoadingChapters = false
                )
            }

            downloadRepository.updateChapterMetadata(pathWord, group, allChapters)
            refreshDownloadStatus(pathWord)
        }
    }
}

/**
 * 推进章节分页游标。服务端返回空页时直接移动到 total，确保调用方终止分页循环。
 */
internal fun advanceChapterOffset(currentOffset: Int, total: Int, receivedCount: Int): Int {
    return if (receivedCount <= 0) total else currentOffset + receivedCount
}

data class StartChapterInfo(
    val uuid: String,
    val name: String,
    val groupPathWord: String,
    val isContinue: Boolean
)

/** 本地目录保留已知分组和关系；旧记录的未知关系不作推断。 */
private fun com.rchdliang.copymanga_neo.domain.model.DownloadedChapterInfo.toChapterInfo() =
    ChapterInfo(
        index = index, uuid = uuid, count = 0, ordered = index, size = imageCount,
        name = name, comicId = "", comicPathWord = comicPathWord,
        groupId = null, groupPathWord = groupPathWord ?: "default", type = 0,
        news = null, datetimeCreated = null, prevChapter = prevChapter, nextChapter = nextChapter
    )
