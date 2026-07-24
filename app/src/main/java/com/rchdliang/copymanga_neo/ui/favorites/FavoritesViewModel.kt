package com.rchdliang.copymanga_neo.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.FavoriteItem
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoritesUiState(
    val isLoading: Boolean = false,
    val favorites: List<FavoriteItem> = emptyList(),
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val toastMessage: String? = null,
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<Int> = emptySet(),
    val isDeleting: Boolean = false
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val mangaRepository: MangaRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

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

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    init {
        viewModelScope.launch(exceptionHandler) {
            userRepository.isLoggedIn().collect { loggedIn ->
                _uiState.update { state ->
                    state.copy(
                        isLoggedIn = loggedIn,
                        // 登出时退出选择模式并清空选中
                        isSelectionMode = if (loggedIn) state.isSelectionMode else false,
                        selectedIds = if (loggedIn) state.selectedIds else emptySet(),
                        error = if (!loggedIn) null else state.error
                    )
                }
                if (loggedIn && _uiState.value.favorites.isEmpty() && !_uiState.value.isLoading) {
                    loadFavorites()
                } else if (!loggedIn) {
                    // 登出时清空收藏列表，避免重新登录后显示过时数据
                    _uiState.update { it.copy(favorites = emptyList(), error = null) }
                }
            }
        }
    }

    fun loadFavorites() {
        if (!_uiState.value.isLoggedIn) return

        viewModelScope.launch(exceptionHandler) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            mangaRepository.getFavoriteComics()
                .onSuccess { favorites ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            favorites = favorites,
                            // 空收藏不是错误，不设置 error 以避免显示重试按钮
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message,
                            toastMessage = error.message ?: "加载失败"
                        )
                    }
                }
        }
    }

    // region 多选模式

    /** 长按进入选择模式，并选中初始项 */
    fun enterSelectionMode(uuid: Int) {
        _uiState.update { it.copy(isSelectionMode = true, selectedIds = setOf(uuid)) }
    }

    /** 退出选择模式，清空选中 */
    fun exitSelectionMode() {
        _uiState.update { it.copy(isSelectionMode = false, selectedIds = emptySet()) }
    }

    /** 切换单项选中状态；选中清空时自动退出选择模式 */
    fun toggleSelection(uuid: Int) {
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
            state.copy(selectedIds = state.favorites.map { it.uuid }.toSet())
        }
    }

    /** 反选 */
    fun invertSelection() {
        _uiState.update { state ->
            val all = state.favorites.map { it.uuid }.toSet()
            state.copy(selectedIds = all - state.selectedIds)
        }
    }

    /** 批量移除选中的收藏 */
    fun removeSelected() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        // 先退出选择模式并标记删除中，避免重复触发
        _uiState.update { it.copy(isSelectionMode = false, isDeleting = true) }

        viewModelScope.launch(exceptionHandler) {
            mangaRepository.removeFavorites(ids)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            selectedIds = emptySet(),
                            toastMessage = "已移除 ${ids.size} 部"
                        )
                    }
                    // 移除成功后刷新书架
                    loadFavorites()
                }
                .onFailure { error ->
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
                    loadFavorites()
                }
        }
    }

    // endregion
}
