package com.rchdliang.copymanga_neo.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.data.local.ReaderPreferencesStore
import com.rchdliang.copymanga_neo.domain.model.ChapterContent
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: MangaRepository,
    private val prefs: ReaderPreferencesStore,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("ReaderViewModel", "Unhandled coroutine exception", throwable)
        _uiState.value = ReaderUiState.Error(throwable.message ?: "未知错误")
        _toastMessage.value = throwable.message ?: "操作失败"
    }

    fun clearToastMessage() {
        _toastMessage.value = null
    }

    val readingDirection: StateFlow<String> = prefs.readingDirection
        .stateIn(viewModelScope, SharingStarted.Eagerly, "RIGHT_TO_LEFT")

    val pageScale: StateFlow<String> = prefs.pageScale
        .stateIn(viewModelScope, SharingStarted.Eagerly, "FIT_SCREEN")

    val screenOrientation: StateFlow<String> = prefs.screenOrientation
        .stateIn(viewModelScope, SharingStarted.Eagerly, "SYSTEM_DEFAULT")

    private var initialized = false
    private var loadJob: Job? = null
    private var currentComicPathWord: String = ""
    private var currentChapterUuid: String = ""
    private var currentGroup: String = "default"

    fun openReader(
        comicPathWord: String,
        initialChapterPathWord: String,
        groupPathWord: String
    ) {
        if (initialized &&
            currentComicPathWord == comicPathWord &&
            currentGroup == groupPathWord
        ) {
            return
        }
        initialized = true
        currentComicPathWord = comicPathWord
        currentGroup = groupPathWord
        loadChapter(comicPathWord, initialChapterPathWord, groupPathWord)
    }

    fun loadChapter(comicPathWord: String, chapterUuid: String, group: String = "default") {
        currentComicPathWord = comicPathWord
        currentChapterUuid = chapterUuid
        currentGroup = group

        loadJob?.cancel()
        loadJob = viewModelScope.launch(exceptionHandler) {
            _uiState.value = ReaderUiState.Loading

            try {
                val downloadedChapter = downloadRepository.getDownloadedChapter(chapterUuid)
                if (downloadedChapter != null) {
                    val allChapters = downloadRepository.getDownloadedChapters(comicPathWord)
                    val currentIndex = allChapters.indexOfFirst { it.uuid == chapterUuid }
                    val prevUuid = if (currentIndex > 0) allChapters[currentIndex - 1].uuid else null
                    val nextUuid =
                        if (currentIndex >= 0 && currentIndex < allChapters.lastIndex) allChapters[currentIndex + 1].uuid else null

                    _uiState.value = ReaderUiState.Success(
                        ChapterContent(
                            imageUrls = downloadedChapter.imagePaths,
                            prevUuid = prevUuid,
                            nextUuid = nextUuid,
                            chapterName = downloadedChapter.name
                        )
                    )
                    return@launch
                }

                repository.trialChapterContent(comicPathWord, chapterUuid)
                    .onFailure { error ->
                        android.util.Log.w(
                            "ReaderViewModel",
                            "trialChapterContent failed: ${error.message}"
                        )
                    }

                repository.getChapterContent(comicPathWord, chapterUuid, group)
                    .onSuccess { content ->
                        _uiState.value = ReaderUiState.Success(content)
                    }
                    .onFailure { error ->
                        val message = error.message ?: "未知错误"
                        if (message == "contentKey为空" || message.startsWith("code 210:")) {
                            _uiState.value = ReaderUiState.RiskControl(message)
                        } else {
                            _uiState.value = ReaderUiState.Error(message)
                            _toastMessage.value = message
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ReaderViewModel", "loadChapter failed", e)
                _uiState.value = ReaderUiState.Error(e.message ?: "未知错误")
                _toastMessage.value = e.message ?: "加载失败"
            }
        }
    }

    fun loadPrevChapter() {
        val currentState = _uiState.value
        if (currentState is ReaderUiState.Success) {
            val prevUuid = currentState.content.prevUuid
            if (prevUuid != null) {
                loadChapter(currentComicPathWord, prevUuid, currentGroup)
            }
        }
    }

    fun loadNextChapter() {
        val currentState = _uiState.value
        if (currentState is ReaderUiState.Success) {
            val nextUuid = currentState.content.nextUuid
            if (nextUuid != null) {
                loadChapter(currentComicPathWord, nextUuid, currentGroup)
            }
        }
    }

    fun refreshCurrentChapter() {
        val comic = currentComicPathWord.ifEmpty { return }
        val chapter = currentChapterUuid.ifEmpty { return }
        loadChapter(comic, chapter, currentGroup.ifEmpty { "default" })
    }

    fun setReadingDirection(value: String) {
        viewModelScope.launch(exceptionHandler) { prefs.setReadingDirection(value) }
    }

    fun setPageScale(value: String) {
        viewModelScope.launch(exceptionHandler) { prefs.setPageScale(value) }
    }

    fun setScreenOrientation(value: String) {
        viewModelScope.launch(exceptionHandler) { prefs.setScreenOrientation(value) }
    }
}

sealed class ReaderUiState {
    object Loading : ReaderUiState()
    data class Success(val content: ChapterContent) : ReaderUiState()
    data class Error(val message: String) : ReaderUiState()
    data class RiskControl(val reason: String) : ReaderUiState()
}
