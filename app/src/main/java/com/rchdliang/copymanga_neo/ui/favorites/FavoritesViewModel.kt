package com.rchdliang.copymanga_neo.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.FavoriteItem
import com.rchdliang.copymanga_neo.domain.model.NovelShelfEntry
import com.rchdliang.copymanga_neo.domain.repository.LocalReadingRepository
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.NovelShelfRepository
import com.rchdliang.copymanga_neo.domain.repository.ReadingProgressRepository
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject

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
    val novels: List<NovelShelfEntry> = emptyList(),
    val novelReadingStatuses: Map<String, NovelShelfReadingStatus> = emptyMap(),
    val novelsLoading: Boolean = false,
    val novelsLoadingMore: Boolean = false,
    val novelsError: String? = null,
    val novelLoadMoreError: String? = null,
    val novelsHaveMore: Boolean = false
) {
    /** 角标按漫画计数，与卡片上的“有新内容”共用判定。 */
    val updatedComicCount: Int
        get() = if (isLoggedIn) favorites.count { it.comic.hasUnreadUpdate } else 0
}

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val mangaRepository: MangaRepository,
    private val userRepository: UserRepository,
    private val readingProgress: ReadingProgressRepository,
    private val novelShelf: NovelShelfRepository,
    private val localReading: LocalReadingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> =
        combine(_uiState, readingProgress.progress, localReading.snapshot) { state, progress, localSnapshot ->
            if (!state.isLoggedIn || progress.owner != readingProgress.currentOwner) state else state.copy(
                novelReadingStatuses = novelShelfReadingStatuses(
                    state.novels, localSnapshot.records, progress.owner
                ),
                favorites = state.favorites.map { item ->
                    val local = progress.records[item.comic.pathWord]
                    if (local == null) item else item.copy(
                        comic = item.comic.copy(
                            browse = local.toBrowseInfo(item.comic.pathWord)
                        )
                    )
                }
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, FavoritesUiState())
    private var loadJob: Job? = null
    private var deleteJob: Job? = null
    private var novelLoadJob: Job? = null
    private var novelOffset = 0
    private var novelsLoaded = false
    private var hasResumedMainScreen = false

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("FavoritesViewModel", "Unhandled coroutine exception", throwable)
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
        android.util.Log.e("FavoritesViewModel", "Novel shelf request failed", throwable)
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

    init {
        viewModelScope.launch(exceptionHandler) {
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
                    isLoggedIn = loggedIn, selectedKind = _uiState.value.selectedKind
                )
                if (loggedIn) {
                    refreshFavorites(showError = false)
                    if (_uiState.value.selectedKind == FavoritesKind.NOVEL) loadNovels()
                }
            }
        }
    }

    fun loadFavorites() {
        refreshFavorites(showError = true)
    }

    fun selectKind(kind: FavoritesKind) {
        if (_uiState.value.isDeleting) return
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

    fun loadMoreNovels() {
        val state = _uiState.value
        if (state.novelsHaveMore && state.novelLoadMoreError == null) loadNovels(append = true)
    }

    fun retryMoreNovels() {
        _uiState.update { it.copy(novelLoadMoreError = null) }
        loadMoreNovels()
    }

    /** 复用书架刷新语义：保留当前作品，追加失败只重试失败页。 */
    private fun loadNovels(append: Boolean = false) {
        if (!_uiState.value.isLoggedIn || _uiState.value.isDeleting || novelLoadJob?.isActive == true) return
        val requestedOffset = if (append) novelOffset else 0
        _uiState.update {
            it.copy(
                novelsLoading = !append, novelsLoadingMore = append,
                novelsError = null, novelLoadMoreError = null
            )
        }
        novelLoadJob = viewModelScope.launch(novelExceptionHandler) {
            val result = novelShelf.list(requestedOffset)
            currentCoroutineContext().ensureActive()
            result.fold(
                onSuccess = { page ->
                    novelOffset = page.nextOffset
                    novelsLoaded = true
                    _uiState.update {
                        it.copy(
                            novelsLoading = false, novelsLoadingMore = false,
                            novels = (if (append) it.novels + page.entries else page.entries)
                                .distinctBy { entry -> entry.collectId },
                            novelsHaveMore = page.hasMore && page.nextOffset > requestedOffset
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    val message = error.message ?: "轻小说书架加载失败"
                    _uiState.update {
                        it.copy(
                            novelsLoading = false, novelsLoadingMore = false,
                            novelsError = if (append) null else message,
                            novelLoadMoreError = if (append) message else null
                        )
                    }
                }
            )
        }
    }

    /** 首次加载由登录状态触发；回到主界面或从后台恢复时再同步。 */
    fun onMainScreenResumed() {
        readingProgress.retryPending()
        if (hasResumedMainScreen) {
            refreshFavorites(showError = false)
            if (_uiState.value.selectedKind == FavoritesKind.NOVEL) loadNovels()
            else novelsLoaded = false
        }
        hasResumedMainScreen = true
    }

    private fun refreshFavorites(showError: Boolean) {
        if (!_uiState.value.isLoggedIn || loadJob?.isActive == true) return

        loadJob = viewModelScope.launch(exceptionHandler) {
            val owner = readingProgress.awaitOwner()
            val requestVersion = readingProgress.requestVersion()
            readingProgress.retryPending()
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = mangaRepository.getFavoriteComics()
            currentCoroutineContext().ensureActive()
            result.fold(
                onSuccess = { favorites ->
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
