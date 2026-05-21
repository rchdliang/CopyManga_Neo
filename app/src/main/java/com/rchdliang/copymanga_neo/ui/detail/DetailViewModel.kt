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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 详情页 UI 状态
 */
data class DetailUiState(
    val comicDetail: ComicDetailDto? = null,
    val chapters: List<ChapterDto> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingChapters: Boolean = false,
    val chapterTotal: Int = 0,
    val error: String? = null,
    val riskControlReason: String? = null
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

    // 记录当前已加载的漫画 pathWord，避免重复加载同一漫画
    private var currentPathWord: String? = null

    fun loadDetail(pathWord: String) {
        // 如果已经成功加载了同一个漫画的数据，则跳过重复加载
        if (currentPathWord == pathWord && _uiState.value.comicDetail != null) {
            return
        }

        // 切换了新漫画，重置缓存标识
        currentPathWord = pathWord

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    riskControlReason = null,
                    chapters = emptyList(),
                    chapterTotal = 0
                )
            }

            mangaRepository.getComicDetail(pathWord).fold(
                onSuccess = { detail ->
                    _uiState.update {
                        it.copy(
                            comicDetail = detail.comicDetail,
                            isLoading = false
                        )
                    }
                    loadAllChapters(pathWord)
                },
                onFailure = { error ->
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
                                error = message
                            )
                        }
                    }
                }
            )
        }
    }

    /**
     * 强制刷新数据（忽略缓存，重新加载）
     */
    fun refresh(pathWord: String) {
        currentPathWord = null
        loadDetail(pathWord)
    }

    private fun loadAllChapters(pathWord: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingChapters = true) }

            val allChapters = mutableListOf<ChapterDto>()
            var offset = 0
            val limit = 100
            var total = 0

            do {
                mangaRepository.getChapters(pathWord, offset = offset, limit = limit).fold(
                    onSuccess = { result ->
                        total = result.total
                        allChapters.addAll(result.list)
                        _uiState.update {
                            it.copy(
                                chapters = allChapters.toList(),
                                chapterTotal = total
                            )
                        }
                        offset += result.list.size
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                error = error.message,
                                isLoadingChapters = false
                            )
                        }
                        return@launch
                    }
                )
            } while (offset < total)
            _uiState.update { it.copy(isLoadingChapters = false) }
        }
    }
}