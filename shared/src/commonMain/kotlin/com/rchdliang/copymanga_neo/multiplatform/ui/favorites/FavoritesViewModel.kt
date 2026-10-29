package com.rchdliang.copymanga_neo.multiplatform.ui.favorites

import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.FavoriteItem
import com.rchdliang.copymanga_neo.domain.model.FavoritesSort
import kotlinx.coroutines.CompletableDeferred
import com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord
import com.rchdliang.copymanga_neo.domain.model.ShelfReadingRecord
import com.rchdliang.copymanga_neo.domain.model.ShelfReadingStatus
import com.rchdliang.copymanga_neo.domain.repository.ShelfReadingRepository
import com.rchdliang.copymanga_neo.domain.model.NovelShelfEntry
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.NovelShelfRepository
import com.rchdliang.copymanga_neo.domain.repository.ReadingProgressRepository
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import com.rchdliang.copymanga_neo.domain.repository.AppSettingsRepository
import com.rchdliang.copymanga_neo.domain.repository.LocalReadingRepository
import com.rchdliang.copymanga_neo.domain.repository.ReadingSessionRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class FavoritesKind(val label: String) { COMIC("漫画"), NOVEL("轻小说") }

data class FavoritesUiState(
    val isLoading: Boolean = false,
    val favorites: List<FavoriteItem> = emptyList(),
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val toastMessage: String? = null,
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<Int> = emptySet(),
    val isDeleting: Boolean = false,
    val selectedKind: FavoritesKind = FavoritesKind.COMIC,
    val sort: FavoritesSort = FavoritesSort.ADDED,
    val novels: List<NovelShelfEntry> = emptyList(),
    val novelsLoading: Boolean = false,
    val novelsLoadingMore: Boolean = false,
    val novelsError: String? = null,
    val novelLoadMoreError: String? = null,
    val novelsHaveMore: Boolean = false,
    val comicReadingStates: Map<String, ShelfReadingRecord> = emptyMap(),
    val novelReadingStates: Map<String, ShelfReadingRecord> = emptyMap(),
    val novelReadingProgress: Map<String, LocalReadingRecord> = emptyMap()
) {
    /** 按有新内容的作品计数，不是新增章节/卷数。 */
    val updatedComicCount: Int
        get() = if (isLoggedIn) favorites.count { comicReadingStates[it.comic.pathWord]?.status == ShelfReadingStatus.UPDATED } else 0
    val updatedNovelCount: Int
        get() = if (isLoggedIn) novels.count { novelReadingStates[it.book.path]?.status == ShelfReadingStatus.UPDATED } else 0
    val updatedCount: Int
        get() = updatedComicCount + updatedNovelCount
}

class FavoritesViewModel constructor(
    private val mangaRepository: MangaRepository,
    private val userRepository: UserRepository,
    private val readingProgress: ReadingProgressRepository,
    private val novelShelf: NovelShelfRepository,
    private val appSettings: AppSettingsRepository,
    private val localReading: LocalReadingRepository,
    private val sessions: ReadingSessionRepository,
    private val shelfReading: ShelfReadingRepository,
    autoStart: Boolean = true
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState(
        isLoggedIn = userRepository.isLoggedInSync(), isLoading = userRepository.isLoggedInSync()))
    val uiState: StateFlow<FavoritesUiState> =
        combine(_uiState, readingProgress.progress, localReading.snapshot, sessions.sessionChanges, shelfReading.snapshot) { state, progress, local, _, shelf ->
            val session = sessions.getReadingSessionSync()
            val latestNovels = local.records.asSequence()
                .filter { state.isLoggedIn && session != null && it.kind == "novel" && it.owner == session.owner }
                .groupBy { it.path }
                .mapValues { (_, records) -> records.maxBy { it.updatedAt } }
            val withNovelUpdates = state.copy(
                comicReadingStates = if (state.isLoggedIn) shelf.books(session, "comic") else emptyMap(),
                novelReadingStates = if (state.isLoggedIn) shelf.books(session, "novel") else emptyMap(),
                novelReadingProgress = latestNovels
            )
            if (!state.isLoggedIn || progress.owner != readingProgress.currentOwner) withNovelUpdates else withNovelUpdates.copy(
                favorites = state.favorites.map { item ->
                    val local = progress.records[item.comic.pathWord]
                    if (local == null) item else item.copy(
                        comic = item.comic.copy(
                            browse = local.toBrowseInfo(item.comic.pathWord)
                        )
                    )
                }
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value)
    private var loadJob: Job? = null
    private var deleteJob: Job? = null
    private var novelLoadJob: Job? = null
    private var novelOffset = 0
    private var novelsLoaded = false
    private var hasResumedMainScreen = false
    private var kindSelectedByUser = false
    private val kindWrites = Channel<FavoritesKind>(Channel.CONFLATED)
    private val sortWrites = Channel<FavoritesSort>(Channel.CONFLATED)
    private val sortReady = CompletableDeferred<Unit>()
    private var sortSelectedByUser = false

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        UiLog.e("FavoritesViewModel", "Unhandled coroutine exception", throwable)
        _uiState.update {
            it.copy(
                isLoading = false,
                isDeleting = false,
                error = throwable.message,
                toastMessage = throwable.message ?: "操作失败"
            )
        }
    }

    private val novelExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        UiLog.e("FavoritesViewModel", "Novel shelf request failed", throwable)
        _uiState.update {
            val message = throwable.message ?: "轻小说书架加载失败"
            it.copy(
                novelsLoading = false, novelsLoadingMore = false,
                novelsError = if (it.novelsLoadingMore) null else message,
                novelLoadMoreError = if (it.novelsLoadingMore) message else null
            )
        }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private var started = false
    init {
        viewModelScope.launch {
            appSettings.getFavoritesSort().fold(
                onSuccess = { sort ->
                    if (!sortSelectedByUser) _uiState.update { it.copy(sort = sort) }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    UiLog.w("FavoritesViewModel", "读取书架排序失败", error)
                }
            )
            sortReady.complete(Unit)
            for (sort in sortWrites) {
                appSettings.setFavoritesSort(sort).onFailure { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(toastMessage = "书架排序保存失败") }
                }
            }
        }
        viewModelScope.launch {
            appSettings.getFavoritesNovelSelected().fold(
                onSuccess = { selected ->
                    if (!kindSelectedByUser) _uiState.update {
                        it.copy(selectedKind = if (selected) FavoritesKind.NOVEL else FavoritesKind.COMIC)
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    UiLog.w("FavoritesViewModel", "读取书架 tab 失败", error)
                }
            )
            for (kind in kindWrites) {
                appSettings.setFavoritesNovelSelected(kind == FavoritesKind.NOVEL).onFailure { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(toastMessage = "书架 tab 保存失败") }
                }
            }
        }
        if (autoStart) start()
    }

    /** 首页先绘制再同步角标；直接进入书架时也可以立即调用，重复调用不重复订阅。 */
    fun start() {
        if (started) return
        started = true
        viewModelScope.launch(exceptionHandler) {
            sortReady.await()
            userRepository.isAuthCacheReady().first { it }
            userRepository.sessionChanges().collect {
                val loggedIn = userRepository.isLoggedInSync()
                // 取消旧会话请求，防止登出后旧结果恢复书架和角标。
                loadJob?.cancel()
                deleteJob?.cancel()
                novelLoadJob?.cancel()
                novelOffset = 0
                novelsLoaded = false
                _uiState.value = FavoritesUiState(
                    isLoggedIn = loggedIn, isLoading = loggedIn, selectedKind = _uiState.value.selectedKind,
                    sort = _uiState.value.sort
                )
                if (loggedIn) {
                    refreshFavorites(showError = false)
                    loadNovels()
                }
            }
        }
    }

    fun loadFavorites() {
        refreshFavorites(showError = true)
    }

    fun selectKind(kind: FavoritesKind) {
        if (_uiState.value.isDeleting) return
        kindSelectedByUser = true
        kindWrites.trySend(kind)
        _uiState.update {
            it.copy(
                selectedKind = kind,
                isSelectionMode = false,
                selectedIds = emptySet()
            )
        }
        if (kind == FavoritesKind.NOVEL && !novelsLoaded) loadNovels()
    }

    fun refreshSelectedShelf() {
        if (_uiState.value.selectedKind == FavoritesKind.NOVEL) loadNovels()
        else loadFavorites()
    }

    /** 切换排序后重置两类书架，取消旧请求，防止不同顺序的分页混合。 */
    fun selectSort(sort: FavoritesSort) {
        val state = _uiState.value
        if (state.isDeleting || state.sort == sort) return
        sortSelectedByUser = true
        sortWrites.trySend(sort)
        loadJob?.cancel()
        novelLoadJob?.cancel()
        loadJob = null
        novelLoadJob = null
        novelOffset = 0
        novelsLoaded = false
        _uiState.update {
            it.copy(
                sort = sort, favorites = emptyList(), novels = emptyList(),
                isSelectionMode = false, selectedIds = emptySet(),
                isLoading = false, novelsLoading = false, novelsLoadingMore = false,
                novelsHaveMore = false, error = null, novelsError = null, novelLoadMoreError = null
            )
        }
        if (started) {
            loadFavorites()
            loadNovels()
        }
    }

    fun loadMoreNovels() {
        val state = _uiState.value
        if (state.novelsHaveMore && state.novelLoadMoreError == null) loadNovels(append = true)
    }

    fun retryMoreNovels() {
        _uiState.update { it.copy(novelLoadMoreError = null) }
        loadMoreNovels()
    }

    /** 顺序读取所有分页以统计完整角标；保留已显示作品，追加失败只重试失败页。 */
    private fun loadNovels(append: Boolean = false) {
        if (!_uiState.value.isLoggedIn || _uiState.value.isDeleting || novelLoadJob?.isActive == true) return
        val sort = _uiState.value.sort
        _uiState.update {
            it.copy(
                novelsLoading = !append, novelsLoadingMore = append,
                novelsError = null, novelLoadMoreError = null
            )
        }
        novelLoadJob = viewModelScope.launch(novelExceptionHandler) {
            val session = sessions.getReadingSessionSync()
            var appending = append
            var requestedOffset = if (append) novelOffset else 0
            do {
                val result = novelShelf.list(requestedOffset, sort)
                currentCoroutineContext().ensureActive()
                val page = result.getOrElse { error ->
                    if (error is CancellationException) throw error
                    val message = error.message ?: "轻小说书架加载失败"
                    _uiState.update {
                        it.copy(
                            novelsLoading = false, novelsLoadingMore = false,
                            novelsError = if (appending) null else message,
                            novelLoadMoreError = if (appending) message else null
                        )
                    }
                    return@launch
                }
                if (session != null) shelfReading.refresh(session, "novel", page.entries.map { it.book.path }.toSet())
                novelOffset = page.nextOffset
                novelsLoaded = true
                val hasMore = page.hasMore && page.nextOffset > requestedOffset
                _uiState.update {
                    it.copy(
                        novelsLoading = false, novelsLoadingMore = hasMore,
                        novels = (if (appending) it.novels + page.entries else page.entries)
                            .distinctBy { entry -> entry.collectId },
                        novelsHaveMore = hasMore
                    )
                }
                appending = true
                requestedOffset = page.nextOffset
            } while (hasMore)
        }
    }

    /** 首次加载由登录状态触发；回到主界面或从后台恢复时再同步。 */
    fun onMainScreenResumed() {
        readingProgress.retryPending()
        if (hasResumedMainScreen) {
            refreshFavorites(showError = false)
            loadNovels()
        }
        hasResumedMainScreen = true
    }

    private fun refreshFavorites(showError: Boolean) {
        if (!_uiState.value.isLoggedIn || loadJob?.isActive == true) return
        val sort = _uiState.value.sort

        loadJob = viewModelScope.launch(exceptionHandler) {
            val session = sessions.getReadingSessionSync()
            val owner = readingProgress.awaitOwner()
            val requestVersion = readingProgress.requestVersion()
            readingProgress.retryPending()
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = withContext(Dispatchers.Default) { mangaRepository.getFavoriteComics(sort) }
            currentCoroutineContext().ensureActive()
            result.fold(
                onSuccess = { favorites ->
                    if (session != null) shelfReading.refresh(session, "comic", favorites.map { it.comic.pathWord }.toSet())
                    readingProgress.acceptRemote(
                        owner,
                        favorites.associate { it.comic.pathWord to it.comic.browse },
                        requestVersion
                    )
                    currentCoroutineContext().ensureActive()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            favorites = favorites,
                            // 空收藏不是错误，不设置 error 以避免显示重试按钮
                            error = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message,
                            toastMessage = if (showError) error.message ?: "加载失败" else null
                        )
                    }
                }
            )
        }
    }

    // region 多选模式

    /** 长按进入选择模式，并选中初始项 */
    fun enterSelectionMode(uuid: Int) {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(isSelectionMode = true, selectedIds = setOf(uuid)) }
    }

    /** 退出选择模式，清空选中 */
    fun exitSelectionMode() {
        _uiState.update { it.copy(isSelectionMode = false, selectedIds = emptySet()) }
    }

    /** 切换单项选中状态；选中清空时自动退出选择模式 */
    fun toggleSelection(uuid: Int) {
        if (_uiState.value.isDeleting) return
        _uiState.update { state ->
            val set = state.selectedIds.toMutableSet()
            if (set.contains(uuid)) set.remove(uuid) else set.add(uuid)
            if (set.isEmpty()) {
                state.copy(isSelectionMode = false, selectedIds = emptySet())
            } else {
                state.copy(selectedIds = set)
            }
        }
    }

    /** 全选 */
    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedIds = selectionIds(state))
        }
    }

    /** 反选 */
    fun invertSelection() {
        _uiState.update { state ->
            val all = selectionIds(state)
            state.copy(selectedIds = all - state.selectedIds)
        }
    }

    /** 批量移除选中的收藏 */
    fun removeSelected() {
        if (_uiState.value.isDeleting) return
        if (_uiState.value.selectedKind == FavoritesKind.NOVEL) {
            removeSelectedNovels()
            return
        }
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        // 先退出选择模式并标记删除中，避免重复触发
        _uiState.update { it.copy(isSelectionMode = false, isDeleting = true) }

        deleteJob = viewModelScope.launch(exceptionHandler) {
            val result = mangaRepository.removeFavorites(ids)
            currentCoroutineContext().ensureActive()
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            favorites = it.favorites.filterNot { favorite -> favorite.uuid in ids },
                            selectedIds = emptySet(),
                            toastMessage = "已移除 ${ids.size} 部"
                        )
                    }
                    // 移除成功后刷新书架
                    loadJob?.cancel()
                    loadFavorites()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            // 刷新失败时仍恢复选择模式以便重试
                            isSelectionMode = true,
                            selectedIds = ids.toSet(),
                            toastMessage = error.message ?: "批量移除失败"
                        )
                    }
                    // 失败后也刷新一次，确保 UI 与服务端一致
                    loadJob?.cancel()
                    loadFavorites()
                }
            )
        }
    }

    private fun selectionIds(state: FavoritesUiState): Set<Int> =
        if (state.selectedKind == FavoritesKind.NOVEL) state.novels.map { it.collectId }.toSet()
        else state.favorites.map { it.uuid }.toSet()

    /** 两种书架的 ID 独立使用，操作后退出多选并重新读取服务端列表。 */
    private fun removeSelectedNovels() {
        val selected = _uiState.value
        val ids = selected.selectedIds.intersect(selectionIds(selected)).toList()
        if (!selected.isLoggedIn || ids.isEmpty()) return
        novelLoadJob?.cancel()
        _uiState.update {
            it.copy(
                isSelectionMode = false, selectedIds = emptySet(), isDeleting = true,
                novelsLoading = false, novelsLoadingMore = false
            )
        }
        deleteJob = viewModelScope.launch(exceptionHandler) {
            val result = novelShelf.removeAll(ids)
            currentCoroutineContext().ensureActive()
            result.fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            isDeleting = false,
                            novels = state.novels.filterNot { it.collectId in ids },
                            toastMessage = "已取消收藏 ${ids.size} 部轻小说"
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(isDeleting = false, toastMessage = error.message ?: "取消收藏失败")
                    }
                }
            )
            // 包括部分成功与响应丢失，均通过刷新重新同步，避免自动重放写入。
            novelsLoaded = false
            novelLoadJob = null
            loadNovels()
        }
    }

    // endregion
}
