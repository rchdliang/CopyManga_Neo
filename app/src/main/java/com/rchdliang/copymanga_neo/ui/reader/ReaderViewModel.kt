package com.rchdliang.copymanga_neo.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.ChapterContent
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: MangaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var currentComicPathWord: String = ""
    private var currentChapterUuid: String = ""

    fun loadChapter(comicPathWord: String, chapterUuid: String) {
        currentComicPathWord = comicPathWord
        currentChapterUuid = chapterUuid

        viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading
            repository.getChapterContent(comicPathWord, chapterUuid)
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
                loadChapter(currentComicPathWord, prevUuid)
                android.util.Log.i("ReaderViewModel", "Loading Prev Chapter")
            }
        }
    }

    fun loadNextChapter() {
        val currentState = _uiState.value
        if (currentState is ReaderUiState.Success) {
            val nextUuid = currentState.content.nextUuid
            if (nextUuid != null) {
                loadChapter(currentComicPathWord, nextUuid)
                android.util.Log.i("ReaderViewModel", "Loading Next Chapter")
            }
        }
    }
}

sealed class ReaderUiState {
    object Loading : ReaderUiState()
    data class Success(val content: ChapterContent) : ReaderUiState()
    data class Error(val message: String) : ReaderUiState()
    data class RiskControl(val reason: String) : ReaderUiState()
}
