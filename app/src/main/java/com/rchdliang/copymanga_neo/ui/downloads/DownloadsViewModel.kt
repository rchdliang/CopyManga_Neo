package com.rchdliang.copymanga_neo.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.data.download.DownloadProgressInfo
import com.rchdliang.copymanga_neo.data.download.DownloadTaskStatus
import com.rchdliang.copymanga_neo.domain.model.DownloadedComicInfo
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadsUiState(
    val downloadedComics: List<DownloadedComicInfo> = emptyList(),
    val activeDownloads: Map<String, DownloadProgressInfo> = emptyMap(),
    val pausedComics: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val selectedPathWords: Set<String> = emptySet()
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("DownloadsViewModel", "Unhandled coroutine exception", throwable)
    }

    init {
        viewModelScope.launch(exceptionHandler) {
            combine(
                downloadRepository.getAllDownloadedComics(),
                downloadRepository.getDownloadProgress(),
                downloadRepository.getActiveTask()
            ) { comics, progress, _ ->
                val paused = progress.filter {
                    it.value.status == DownloadTaskStatus.PAUSED ||
                            it.value.status == DownloadTaskStatus.PAUSED_BY_RISK
                }.keys
                Triple(comics, progress, paused)
            }.collect { (comics, progress, paused) ->
                _uiState.update {
                    it.copy(
                        downloadedComics = comics,
                        activeDownloads = progress,
                        pausedComics = paused
                    )
                }
            }
        }
    }

    fun setImageConcurrency(count: Int) = downloadRepository.setImageConcurrency(count)
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
            state.copy(selectedPathWords = state.downloadedComics.map { it.pathWord }.toSet())
        }
    }

    fun invertSelection() {
        _uiState.update { state ->
            val all = state.downloadedComics.map { it.pathWord }.toSet()
            state.copy(selectedPathWords = all - state.selectedPathWords)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPathWords = emptySet()) }
    }

    fun deleteSelected() {
        val pathWords = _uiState.value.selectedPathWords.toList()
        if (pathWords.isEmpty()) return
        exitSelectionMode()
        viewModelScope.launch(exceptionHandler) {
            pathWords.forEach { pathWord ->
                downloadRepository.deleteComicAndChapters(pathWord)
            }
        }
    }
}
