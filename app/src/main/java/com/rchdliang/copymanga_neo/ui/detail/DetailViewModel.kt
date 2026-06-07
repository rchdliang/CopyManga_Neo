package com.rchdliang.copymanga_neo.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.data.download.ChapterDownloadItem
import com.rchdliang.copymanga_neo.data.download.ChapterDownloadStatus
import com.rchdliang.copymanga_neo.data.download.ComicDownloadTask
import com.rchdliang.copymanga_neo.data.local.entity.DownloadedComicEntity
import com.rchdliang.copymanga_neo.data.remote.dto.BrowseDto
import com.rchdliang.copymanga_neo.data.remote.dto.ChapterDto
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDetailDto
import com.rchdliang.copymanga_neo.data.remote.dto.GroupsDto
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
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
    val isShelfLoading: Boolean = false,
    val shelfMessage: String? = null,
    val isSelectionMode: Boolean = false,
    val selectedChapterUuids: Set<String> = emptySet(),
    val downloadedChapterUuids: Set<String> = emptySet(),
    val chapterDownloadStatuses: Map<String, ChapterDownloadStatus> = emptyMap(),
    val chapterDownloadProgresses: Map<String, Float> = emptyMap(),
    val hasLocalCache: Boolean = false
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val mangaRepository: MangaRepository,
    private val userRepository: UserRepository,
    private val downloadRepository: DownloadRepository
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
                    browse = null,
                    isSelectionMode = false,
                    selectedChapterUuids = emptySet()
                )
            }

            loadLocalCache(pathWord)
            refreshDownloadStatus(pathWord)

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
                    saveLocalCache(detail.comicDetail)
                    loadAllChapters(pathWord, selectedGroup)
                    loadQueryStatus(pathWord)
                },
                onFailure = { error ->
                    val localDetail = _uiState.value.comicDetail
                    if (localDetail != null) {
                        _uiState.update { it.copy(isLoading = false) }
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
                                    error = message
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, riskControlReason = null) }
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
                            isLoading = false,
                            chapters = persistentListOf(),
                            chapterTotal = 0,
                            isSelectionMode = false,
                            selectedChapterUuids = emptySet()
                        )
                    }
                    saveLocalCache(detail.comicDetail)
                    loadAllChapters(pathWord, selectedGroup)
                    loadQueryStatus(pathWord)
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false) }
                }
            )
        }
    }

    private suspend fun loadLocalCache(pathWord: String) {
        val comic = downloadRepository.getDownloadedComic(pathWord)
        if (comic != null) {
            _uiState.update {
                it.copy(
                    hasLocalCache = true
                )
            }
        }
    }

    private suspend fun saveLocalCache(detail: ComicDetailDto) {
        val existing = downloadRepository.getDownloadedComic(detail.pathWord)
        if (existing == null) return
        val authors = detail.author?.joinToString(",") { it.name } ?: ""
        val entity = DownloadedComicEntity(
            pathWord = detail.pathWord,
            name = detail.name,
            cover = detail.cover,
            authorNames = authors,
            statusDisplay = if (detail.status.value == 1) "已完结" else "连载中",
            lastUpdate = detail.lastUpdate,
            lastChapterName = detail.lastChapter?.lastChapterName,
            brief = detail.brief,
            totalChapters = existing.totalChapters,
            downloadedChapters = existing.downloadedChapters,
            downloadAt = existing.downloadAt
        )
        downloadRepository.saveComicCache(entity)
    }

    fun startDownload() {
        val state = _uiState.value
        val detail = state.comicDetail ?: return
        val selectedChapters = state.chapters.filter { it.uuid in state.selectedChapterUuids }
        if (selectedChapters.isEmpty()) return

        exitSelectionMode()

        val authors = detail.author?.joinToString(",") { it.name } ?: ""

        viewModelScope.launch {
            val existing = downloadRepository.getDownloadedComic(detail.pathWord)
            val entity = DownloadedComicEntity(
                pathWord = detail.pathWord,
                name = detail.name,
                cover = detail.cover,
                authorNames = authors,
                statusDisplay = if (detail.status.value == 1) "已完结" else "连载中",
                lastUpdate = detail.lastUpdate,
                lastChapterName = detail.lastChapter?.lastChapterName,
                brief = detail.brief,
                totalChapters = selectedChapters.size + (existing?.totalChapters ?: 0),
                downloadedChapters = existing?.downloadedChapters ?: 0,
                downloadAt = System.currentTimeMillis()
            )
            downloadRepository.saveComicCache(entity)

            val downloadItems = selectedChapters.map { chapter ->
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

    private suspend fun refreshDownloadStatus(pathWord: String) {
        val downloadedUuids = downloadRepository.getDownloadedChapterUuids(pathWord)
        val task = downloadRepository.getActiveTask().value
        val statuses = mutableMapOf<String, ChapterDownloadStatus>()
        val progresses = mutableMapOf<String, Float>()

        downloadedUuids.forEach { uuid ->
            statuses[uuid] = ChapterDownloadStatus.DOWNLOADED
        }

        task?.let {
            it.chapters.forEach { item ->
                statuses[item.chapter.uuid] = item.status
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
            onFailure = { /* 静默失败 */ }
        )
    }

    fun selectGroup(groupPathWord: String) {
        val pathWord = currentPathWord ?: return
        if (_uiState.value.selectedGroup == groupPathWord) return

        _uiState.update {
            it.copy(
                selectedGroup = groupPathWord,
                chapters = persistentListOf(),
                chapterTotal = 0,
                isSelectionMode = false,
                selectedChapterUuids = emptySet()
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
                                isShelfLoading = false,
                                shelfMessage = "已从书架中删除"
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isShelfLoading = false,
                                shelfMessage = error.message ?: "删除失败"
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
                                        shelfMessage = "已添加到书架"
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
                                shelfMessage = error.message ?: "添加失败"
                            )
                        }
                    }
                )
            }
        }
    }

    fun clearShelfMessage() {
        _uiState.update { it.copy(shelfMessage = null) }
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

    // region 多选模式

    fun enterSelectionMode(chapterUuid: String) {
        _uiState.update {
            it.copy(
                isSelectionMode = true,
                selectedChapterUuids = setOf(chapterUuid)
            )
        }
    }

    fun exitSelectionMode() {
        _uiState.update {
            it.copy(
                isSelectionMode = false,
                selectedChapterUuids = emptySet()
            )
        }
    }

    fun toggleChapterSelection(chapterUuid: String) {
        _uiState.update { state ->
            val selected = state.selectedChapterUuids.toMutableSet()
            if (selected.contains(chapterUuid)) {
                selected.remove(chapterUuid)
                if (selected.isEmpty()) {
                    return@update state.copy(
                        isSelectionMode = false,
                        selectedChapterUuids = emptySet()
                    )
                }
            } else {
                selected.add(chapterUuid)
            }
            state.copy(selectedChapterUuids = selected)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(
                selectedChapterUuids = state.chapters.map { it.uuid }.toSet()
            )
        }
    }

    fun invertSelection() {
        _uiState.update { state ->
            val allUuids = state.chapters.map { it.uuid }.toSet()
            val inverted = allUuids - state.selectedChapterUuids
            state.copy(selectedChapterUuids = inverted)
        }
    }

    fun selectToLatest() {
        _uiState.update { state ->
            val chapters = if (state.sortAscending) state.chapters
            else state.chapters.reversed()
            if (chapters.isEmpty() || state.selectedChapterUuids.isEmpty()) return@update state
            val sortedSelected = chapters.filter { it.uuid in state.selectedChapterUuids }
            val firstSelected = sortedSelected.first()
            val firstIndex = chapters.indexOf(firstSelected)
            val range = chapters.drop(firstIndex).map { it.uuid }.toSet()
            state.copy(selectedChapterUuids = range)
        }
    }

    fun selectBeforeLatest() {
        _uiState.update { state ->
            val chapters = if (state.sortAscending) state.chapters
            else state.chapters.reversed()
            if (chapters.isEmpty() || state.selectedChapterUuids.isEmpty()) return@update state
            val sortedSelected = chapters.filter { it.uuid in state.selectedChapterUuids }
            val lastSelected = sortedSelected.last()
            val lastIndex = chapters.indexOf(lastSelected)
            val range = chapters.take(lastIndex + 1).map { it.uuid }.toSet()
            state.copy(selectedChapterUuids = range)
        }
    }

    fun selectBetween() {
        _uiState.update { state ->
            val chapters = if (state.sortAscending) state.chapters
            else state.chapters.reversed()
            if (chapters.isEmpty() || state.selectedChapterUuids.size < 2) return@update state
            val sortedSelected = chapters.filter { it.uuid in state.selectedChapterUuids }
            val firstSelected = sortedSelected.first()
            val lastSelected = sortedSelected.last()
            val firstIndex = chapters.indexOf(firstSelected)
            val lastIndex = chapters.indexOf(lastSelected)
            val range = chapters.subList(firstIndex, lastIndex + 1).map { it.uuid }.toSet()
            if (range.size == state.selectedChapterUuids.size) return@update state
            state.copy(selectedChapterUuids = range)
        }
    }

    fun canSelectBetween(): Boolean {
        val state = _uiState.value
        if (state.selectedChapterUuids.size < 2) return false
        val chapters = if (state.sortAscending) state.chapters
        else state.chapters.reversed()
        val sortedSelected = chapters.filter { it.uuid in state.selectedChapterUuids }
        val firstIndex = chapters.indexOf(sortedSelected.first())
        val lastIndex = chapters.indexOf(sortedSelected.last())
        val rangeSize = lastIndex - firstIndex + 1
        return rangeSize > state.selectedChapterUuids.size
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(selectedChapterUuids = emptySet())
        }
    }

    fun getSelectedChapters(): List<ChapterDto> {
        val state = _uiState.value
        return state.chapters.filter { it.uuid in state.selectedChapterUuids }
    }

    // endregion

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

            refreshDownloadStatus(pathWord)
        }
    }
}

data class StartChapterInfo(
    val uuid: String,
    val name: String,
    val groupPathWord: String,
    val isContinue: Boolean
)
