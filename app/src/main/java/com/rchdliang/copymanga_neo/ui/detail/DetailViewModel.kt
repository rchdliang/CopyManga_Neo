package com.rchdliang.copymanga_neo.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.data.remote.dto.ChapterDto
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDetailDto
import com.rchdliang.copymanga_neo.data.remote.dto.GroupsDto
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
    val riskControlReason: String? = null,
    /** 所有汉化组分组 */
    val groups: List<GroupsDto> = emptyList(),
    /** 当前选中的分组 pathWord */
    val selectedGroup: String = ""
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
                    chapterTotal = 0,
                    groups = emptyList(),
                    selectedGroup = ""
                )
            }

            mangaRepository.getComicDetail(pathWord).fold(
                onSuccess = { detail ->
                    // 确定要使用的分组：优先默认组，否则取第一个
                    // API 返回的是 Map<String, GroupsDto>，转为 List 供 UI 使用
                    val groupsMap = detail.groups
                    val defaultGroup = groupsMap["default"]
                    val selectedGroup =
                        defaultGroup?.pathWord ?: groupsMap.values.firstOrNull()?.pathWord
                        ?: "default"

                    _uiState.update {
                        it.copy(
                            comicDetail = detail.comicDetail,
                            groups = groupsMap.values.toList(),
                            selectedGroup = selectedGroup,
                            isLoading = false
                        )
                    }
                    loadAllChapters(pathWord, selectedGroup)
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
     * 切换汉化组分组并重新加载章节
     */
    fun selectGroup(groupPathWord: String) {
        val pathWord = currentPathWord ?: return

        // 如果已经是同一分组，跳过
        if (_uiState.value.selectedGroup == groupPathWord) return

        _uiState.update {
            it.copy(
                selectedGroup = groupPathWord,
                chapters = emptyList(),
                chapterTotal = 0
            )
        }
        loadAllChapters(pathWord, groupPathWord)
    }

    /**
     * 强制刷新数据（忽略缓存，重新加载）
     */
    fun refresh(pathWord: String) {
        currentPathWord = null
        loadDetail(pathWord)
    }

    private fun loadAllChapters(pathWord: String, group: String = "default") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingChapters = true) }

            val allChapters = mutableListOf<ChapterDto>()
            var offset = 0
            val limit = 100
            var total = 0

            do {
                mangaRepository.getChapters(pathWord, group = group, offset = offset, limit = limit)
                    .fold(
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
