package com.rchdliang.copymanga_neo.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.DownloadKind
import com.rchdliang.copymanga_neo.domain.model.DownloadProgressInfo
import com.rchdliang.copymanga_neo.domain.model.DownloadTaskStatus
import com.rchdliang.copymanga_neo.domain.model.DownloadedComicInfo
import com.rchdliang.copymanga_neo.domain.model.LocalReadingSession
import com.rchdliang.copymanga_neo.domain.model.NovelBook
import com.rchdliang.copymanga_neo.domain.model.NovelCachedVolume
import com.rchdliang.copymanga_neo.domain.model.novelDownloadKey
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
import com.rchdliang.copymanga_neo.domain.repository.NovelRepository
import com.rchdliang.copymanga_neo.domain.repository.ReadingSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadedNovelInfo(val book: NovelBook, val volumes: List<NovelCachedVolume>)

data class DownloadsUiState(
    val downloadedComics: List<DownloadedComicInfo> = emptyList(),
    val activeDownloads: Map<String, DownloadProgressInfo> = emptyMap(),
    val pausedComics: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val selectedPathWords: Set<String> = emptySet(),
    val message: String? = null,
    val kind: DownloadKind = DownloadKind.COMIC,
    val downloadedNovels: List<DownloadedNovelInfo> = emptyList(),
    val activeNovelDownloads: Map<String, DownloadProgressInfo> = emptyMap()
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val novels: NovelRepository,
    private val sessions: ReadingSessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()
    private var displayedSession: LocalReadingSession? = null

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("DownloadsViewModel", "Unhandled coroutine exception", throwable)
        _uiState.update { it.copy(message = throwable.message ?: "下载操作失败") }
    }

    init {
        viewModelScope.launch(exceptionHandler) {
            combine(
                downloadRepository.getAllDownloadedComics(),
                downloadRepository.getDownloadProgress(),
                downloadRepository.getActiveTask()
            ) { comics, progress, _ ->
                val comicProgress = progress.values.filter { it.kind == DownloadKind.COMIC }
                    .associateBy { it.comicPathWord }
                val paused = comicProgress.filter {
                    it.value.status == DownloadTaskStatus.PAUSED ||
                            it.value.status == DownloadTaskStatus.PAUSED_BY_RISK
                }.keys
                Triple(comics, comicProgress, paused)
            }.collect { (comics, progress, paused) ->
                _uiState.update {
                    it.copy(
                        downloadedComics = comics,
                        activeDownloads = progress.filterValues { info ->
                            info.status != DownloadTaskStatus.COMPLETED
                        },
                        pausedComics = paused
                    )
                }
            }
        }
        viewModelScope.launch(exceptionHandler) {
            combine(
                novels.cachedVolumes, downloadRepository.getDownloadProgress(),
                sessions.cacheReadyFlow, sessions.sessionChanges
            ) { cached, progress, ready, _ ->
                val session = if (ready) sessions.getReadingSessionSync() else null
                val owned = cached.filter { it.owner == session?.owner && it.mode == session.mode }
                val books = owned.groupBy { it.book.path }.values.map { volumes ->
                    DownloadedNovelInfo(
                        volumes.maxBy { it.lastAccessedAt }.book,
                        volumes.sortedBy { it.volume.index })
                }
                val tasks = progress.filterValues {
                    it.kind == DownloadKind.NOVEL && it.owner == session?.owner && it.mode == session?.mode &&
                            it.status != DownloadTaskStatus.COMPLETED
                }
                Triple(session, books, tasks)
            }.collect { (session, books, tasks) ->
                val changedSession = displayedSession != session
                displayedSession = session
                _uiState.update { state ->
                    state.copy(
                        downloadedNovels = books, activeNovelDownloads = tasks,
                        isSelectionMode = state.isSelectionMode && (!changedSession || state.kind != DownloadKind.NOVEL),
                        selectedPathWords = if (changedSession && state.kind == DownloadKind.NOVEL) emptySet()
                        else if (state.kind == DownloadKind.NOVEL)
                            state.selectedPathWords.intersect(books.map { it.book.path }
                                .toSet()) else state.selectedPathWords
                    )
                }
            }
        }
        viewModelScope.launch(exceptionHandler) {
            novels.refreshCachedVolumes().fold(
                onSuccess = {}, onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(message = error.message ?: "小说下载目录加载失败") }
                }
            )
        }
    }

    fun selectKind(kind: DownloadKind) {
        _uiState.update {
            it.copy(
                kind = kind,
                isSelectionMode = false,
                selectedPathWords = emptySet()
            )
        }
    }

    fun pauseNovel(key: String) = downloadRepository.pauseDownload(key)
    fun resumeNovel(key: String) = downloadRepository.resumeDownload(key)
    fun cancelNovel(key: String) {
        viewModelScope.launch(exceptionHandler) { downloadRepository.cancelDownload(key) }
    }

    fun setImageConcurrency(count: Int) = downloadRepository.setImageConcurrency(count)
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
    fun pauseComic(pathWord: String) = downloadRepository.pauseComic(pathWord)
    fun resumeComic(pathWord: String) = downloadRepository.resumeComic(pathWord)
    fun cancelComic(pathWord: String) {
        viewModelScope.launch(exceptionHandler) { downloadRepository.cancelComic(pathWord) }
    }

    fun cancelAll() {
        viewModelScope.launch(exceptionHandler) { downloadRepository.cancelAll() }
    }

    fun enterSelectionMode(pathWord: String) {
        _uiState.update { it.copy(isSelectionMode = true, selectedPathWords = setOf(pathWord)) }
    }

    fun exitSelectionMode() {
        _uiState.update { it.copy(isSelectionMode = false, selectedPathWords = emptySet()) }
    }

    fun toggleSelection(pathWord: String) {
        _uiState.update { state ->
            val set = state.selectedPathWords.toMutableSet()
            if (set.contains(pathWord)) set.remove(pathWord) else set.add(pathWord)
            state.copy(selectedPathWords = set)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            val all =
                if (state.kind == DownloadKind.NOVEL) state.downloadedNovels.map { it.book.path }
                else state.downloadedComics.map { it.pathWord }
            state.copy(selectedPathWords = all.toSet())
        }
    }

    fun invertSelection() {
        _uiState.update { state ->
            val all =
                (if (state.kind == DownloadKind.NOVEL) state.downloadedNovels.map { it.book.path }
                else state.downloadedComics.map { it.pathWord }).toSet()
            state.copy(selectedPathWords = all - state.selectedPathWords)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPathWords = emptySet()) }
    }

    fun deleteSelected() {
        val selected = _uiState.value
        val session = sessions.getReadingSessionSync()
        val pathWords = selected.selectedPathWords.toList()
        if (pathWords.isEmpty()) return
        exitSelectionMode()
        viewModelScope.launch(exceptionHandler) {
            pathWords.forEach { pathWord ->
                if (selected.kind == DownloadKind.NOVEL) {
                    if (session == null || sessions.getReadingSessionSync() != session) return@launch
                    val owner = session.owner ?: return@launch
                    downloadRepository.cancelDownload(
                        novelDownloadKey(
                            session.mode,
                            owner,
                            pathWord
                        )
                    )
                    novels.removeBook(session.mode, owner, pathWord).fold(
                        onSuccess = {}, onFailure = { error ->
                            if (error is CancellationException) throw error
                            _uiState.update {
                                it.copy(
                                    message = error.message ?: "删除小说下载失败"
                                )
                            }
                            return@launch
                        }
                    )
                } else downloadRepository.deleteComicAndChapters(pathWord)
            }
        }
    }
}
