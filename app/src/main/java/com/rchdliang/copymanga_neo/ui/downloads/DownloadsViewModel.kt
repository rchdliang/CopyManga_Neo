package com.rchdliang.copymanga_neo.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.data.download.ComicDownloadTask
import com.rchdliang.copymanga_neo.data.download.DownloadProgressInfo
import com.rchdliang.copymanga_neo.domain.model.DownloadedComicInfo
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadsUiState(
    val downloadedComics: List<DownloadedComicInfo> = emptyList(),
    val activeDownloads: Map<String, DownloadProgressInfo> = emptyMap(),
    val activeTasks: Map<String, ComicDownloadTask> = emptyMap(),
    val isLoading: Boolean = false
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                downloadRepository.getAllDownloadedComics(),
                downloadRepository.getDownloadProgress(),
                downloadRepository.getActiveTask()
            ) { comics, progress, activeTask ->
                val activeTasksMap = mutableMapOf<String, ComicDownloadTask>()
                activeTask?.let { activeTasksMap[it.pathWord] = it }
                DownloadsUiState(
                    downloadedComics = comics,
                    activeDownloads = progress,
                    activeTasks = activeTasksMap
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setImageConcurrency(count: Int) {
        downloadRepository.setImageConcurrency(count)
    }

    fun cancelComic(pathWord: String) {
        downloadRepository.cancelComic(pathWord)
    }

    fun cancelAll() {
        downloadRepository.cancelAll()
    }

    fun deleteComic(pathWord: String) {
        viewModelScope.launch {
            downloadRepository.deleteComic(pathWord)
        }
    }
}
