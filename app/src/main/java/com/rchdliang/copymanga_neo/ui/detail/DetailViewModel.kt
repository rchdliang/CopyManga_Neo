package com.rchdliang.copymanga_neo.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.data.remote.dto.BrowseDto
import com.rchdliang.copymanga_neo.data.remote.dto.ChapterDto
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDetailDto
import com.rchdliang.copymanga_neo.data.remote.dto.GroupsDto
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val comicDetail: ComicDetailDto? = null,
    val chapters: ImmutableList<ChapterDto> = persistentListOf(),
    val isLoading: Boolean = false,
    val isLoadingChapters: Boolean = false,
    val chapterTotal: Int = 0,
    val error: String? = null,
    val riskControlReason: String? = null,
    val groups: ImmutableList<GroupsDto> = persistentListOf(),
    val selectedGroup: String = "",
    val sortAscending: Boolean = true,
    val isLoggedIn: Boolean = false,
    val isInShelf: Boolean = false,
    val shelfCollectId: Int? = null,
    val browse: BrowseDto? = null,
    val isShelfLoading: Boolean = false
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val mangaRepository: MangaRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private var currentPathWord: String? = null

    fun loadDetail(pathWord: String) {
        if (currentPathWord == pathWord && _uiState.value.comicDetail != null) {
            return
        }

        currentPathWord = pathWord

        viewModelScope.launch {
            val isLoggedIn = userRepository.isLoggedIn().first()

            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    riskControlReason = null,
                    chapters = persistentListOf(),
                    chapterTotal = 0,
                    groups = persistentListOf(),
                    selectedGroup = "",
                    isLoggedIn = isLoggedIn,
                    isInShelf = false,
                    shelfCollectId = null,
                    browse = null
                )
            }

            mangaRepository.getComicDetail(pathWord).fold(
                onSuccess = { detail ->
                    val groupsMap = detail.groups
                    val defaultGroup = groupsMap["default"]
                    val selectedGroup =
                        defaultGroup?.pathWord ?: groupsMap.values.firstOrNull()?.pathWord
                        ?: "default"

                    _uiState.update {
                        it.copy(
                            comicDetail = detail.comicDetail,
                            groups = groupsMap.values.toImmutableList(),
                            selectedGroup = selectedGroup,
                            isLoading = false
                        )
                    }
                    loadAllChapters(pathWord, selectedGroup)
                    loadQueryStatus(pathWord)
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

    private suspend fun loadQueryStatus(pathWord: String) {
        if (!_uiState.value.isLoggedIn) return

        mangaRepository.queryComicStatus(pathWord).fold(
            onSuccess = { result ->
                _uiState.update {
                    it.copy(
                        isInShelf = result.collect != null,
                        shelfCollectId = result.collect,
                        browse = result.browse
                    )
                }
            },
            onFailure = { /* 静默失败，不影响详情展示 */ }
        )
    }

    fun selectGroup(groupPathWord: String) {
        val pathWord = currentPathWord ?: return
        if (_uiState.value.selectedGroup == groupPathWord) return

        _uiState.update {
            it.copy(
                selectedGroup = groupPathWord,
                chapters = persistentListOf(),
                chapterTotal = 0
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

        viewModelScope.launch {
            _uiState.update { it.copy(isShelfLoading = true) }

            if (state.isInShelf && state.shelfCollectId != null) {
                mangaRepository.removeFavorite(state.shelfCollectId).fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                isInShelf = false,
                                shelfCollectId = null,
                                isShelfLoading = false
                            )
                        }
                    },
                    onFailure = {
                        _uiState.update { it.copy(isShelfLoading = false) }
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
                                        isShelfLoading = false
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
                    onFailure = {
                        _uiState.update { it.copy(isShelfLoading = false) }
                    }
                )
            }
        }
    }

    fun getStartChapter(): StartChapterInfo? {
        val state = _uiState.value
        val chapters = if (state.sortAscending) state.chapters else state.chapters.reversed()

        val browse = state.browse
        if (browse != null && browse.chapterUuid != null) {
            val lastReadIndex = chapters.indexOfFirst { it.uuid == browse.chapterUuid }
            if (lastReadIndex >= 0) {
                val nextIndex = lastReadIndex + 1
                if (nextIndex < chapters.size) {
                    val next = chapters[nextIndex]
                    return StartChapterInfo(
                        uuid = next.uuid,
                        name = next.name,
                        groupPathWord = next.groupPathWord ?: "default",
                        isContinue = true
                    )
                }
            }
        }

        val first = chapters.firstOrNull() ?: return null
        return StartChapterInfo(
            uuid = first.uuid,
            name = first.name,
            groupPathWord = first.groupPathWord ?: "default",
            isContinue = false
        )
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
                            offset += result.list.size
                            _uiState.update { it.copy(chapterTotal = total) }
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

            _uiState.update {
                it.copy(
                    chapters = allChapters.toImmutableList(),
                    isLoadingChapters = false
                )
            }
        }
    }
}

data class StartChapterInfo(
    val uuid: String,
    val name: String,
    val groupPathWord: String,
    val isContinue: Boolean
)
