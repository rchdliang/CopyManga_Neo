package com.rchdliang.copymanga_neo.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.data.remote.dto.FavoritesDto
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoritesUiState(
    val isLoading: Boolean = false,
    val favorites: List<FavoritesDto> = emptyList(),
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

    init {
        viewModelScope.launch {
            userRepository.isLoggedIn().collect { loggedIn ->
                _uiState.update { state ->
                    state.copy(
                        isLoggedIn = loggedIn,
                        error = if (!loggedIn) null else state.error
                    )
                }
                if (loggedIn && _uiState.value.favorites.isEmpty() && !_uiState.value.isLoading) {
                    loadFavorites()
                }
            }
        }
    }

    fun loadFavorites() {
        if (!_uiState.value.isLoggedIn) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            mangaRepository.getFavoriteComics()
                .onSuccess { favorites ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            favorites = favorites,
                            error = if (favorites.isEmpty()) "暂无收藏，快去添加喜欢的漫画吧！" else null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }
}
