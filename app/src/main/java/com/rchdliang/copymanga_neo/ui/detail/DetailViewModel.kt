package com.rchdliang.copymanga_neo.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.data.remote.dto.ChapterDto
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDetailDto
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 详情页 UI 状态
 */
data class DetailUiState(
    val comicDetail: ComicDetailDto? = null,
    val chapters: List<ChapterDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * 漫画详情 ViewModel
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val mangaRepository: MangaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadDetail(pathWord: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            mangaRepository.getComicDetail(pathWord).fold(
                onSuccess = { detail ->
                    _uiState.value = _uiState.value.copy(
                        comicDetail = detail.comicDetail,
                        isLoading = false
                    )
                    loadChapters(pathWord)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
            )
        }
    }

    private fun loadChapters(pathWord: String) {
        viewModelScope.launch {
            mangaRepository.getChapters(pathWord).fold(
                onSuccess = { chapters ->
                    _uiState.value = _uiState.value.copy(chapters = chapters)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message
                    )
                }
            )
        }
    }
}
