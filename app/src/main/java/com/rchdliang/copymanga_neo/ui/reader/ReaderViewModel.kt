package com.rchdliang.copymanga_neo.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.data.local.ReaderPreferencesStore
import com.rchdliang.copymanga_neo.domain.model.ChapterContent
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val prefs: ReaderPreferencesStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    val readingDirection: StateFlow<String> = prefs.readingDirection
        .stateIn(viewModelScope, SharingStarted.Eagerly, "RIGHT_TO_LEFT")

    val pageScale: StateFlow<String> = prefs.pageScale
        .stateIn(viewModelScope, SharingStarted.Eagerly, "FIT_SCREEN")

    val screenOrientation: StateFlow<String> = prefs.screenOrientation
        .stateIn(viewModelScope, SharingStarted.Eagerly, "SYSTEM_DEFAULT")

    private var initialized = false
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
        if (currentChapterUuid == chapterUuid && _uiState.value is ReaderUiState.Success) {
            android.util.Log.i(
                "ReaderViewModel",
                "loadChapter skipped: already loaded $chapterUuid"
            )
            return
        }
        currentComicPathWord = comicPathWord
        currentChapterUuid = chapterUuid
        currentGroup = group

        viewModelScope.launch {
            repository.trialChapterContent(comicPathWord, chapterUuid)
                .onFailure { error ->
                    android.util.Log.w(
                        "ReaderViewModel",
                        "trialChapterContent failed: ${error.message}"
                    )
                }

            _uiState.value = ReaderUiState.Loading
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
                    }
                }
        }
    }

    fun loadPrevChapter() {
        android.util.Log.i("ReaderViewModel", "entering -> loadPrevChapter()")
        val currentState = _uiState.value
        if (currentState is ReaderUiState.Success) {
            android.util.Log.i("ReaderViewModel", "ReaderUiState: Success")
            val prevUuid = currentState.content.prevUuid
            android.util.Log.i("ReaderViewModel", currentState.content.toString())
            if (prevUuid != null) {
                loadChapter(currentComicPathWord, prevUuid, currentGroup)
                android.util.Log.i("ReaderViewModel", "Loading Prev Chapter")
            }
        }
    }

    fun loadNextChapter() {
        val currentState = _uiState.value
        if (currentState is ReaderUiState.Success) {
            val nextUuid = currentState.content.nextUuid
            if (nextUuid != null) {
                loadChapter(currentComicPathWord, nextUuid, currentGroup)
                android.util.Log.i("ReaderViewModel", "Loading Next Chapter")
            }
        }
    }

    fun refreshCurrentChapter() {
        val comic = currentComicPathWord.ifEmpty { return }
        val chapter = currentChapterUuid.ifEmpty { return }
        loadChapter(comic, chapter, currentGroup.ifEmpty { "default" })
    }

    fun setReadingDirection(value: String) {
        viewModelScope.launch { prefs.setReadingDirection(value) }
    }

    fun setPageScale(value: String) {
        viewModelScope.launch { prefs.setPageScale(value) }
    }

    fun setScreenOrientation(value: String) {
        viewModelScope.launch { prefs.setScreenOrientation(value) }
    }
}

sealed class ReaderUiState {
    object Loading : ReaderUiState()
    data class Success(val content: ChapterContent) : ReaderUiState()
    data class Error(val message: String) : ReaderUiState()
    data class RiskControl(val reason: String) : ReaderUiState()
}
