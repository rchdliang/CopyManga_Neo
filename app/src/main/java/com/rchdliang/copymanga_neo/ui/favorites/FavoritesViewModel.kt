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
    val isLoggedIn: Boolean = false
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
        _uiState.update { it.copy(isLoading = false, error = throwable.message) }
    }

    init {
        viewModelScope.launch(exceptionHandler) {
            userRepository.isLoggedIn().collect { loggedIn ->
                _uiState.update { state ->
                    state.copy(
                        isLoggedIn = loggedIn,
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
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }
}
