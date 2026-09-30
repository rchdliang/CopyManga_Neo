package com.rchdliang.copymanga_neo.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.ChapterContent
import com.rchdliang.copymanga_neo.domain.model.ReaderPreferences
import com.rchdliang.copymanga_neo.domain.model.offlineContent
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.ReaderPreferencesRepository
import com.rchdliang.copymanga_neo.domain.repository.ReadingProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: MangaRepository,
    private val prefs: ReaderPreferencesRepository,
    private val downloadRepository: DownloadRepository,
    private val readingProgress: ReadingProgressRepository
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

    private val mutablePreferences = MutableStateFlow<ReaderPreferences?>(null)
    val readerPreferences = mutablePreferences.asStateFlow()
    private var preferencesJob: Job? = null
    private var preferencesComic: String? = null

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
        if (preferencesComic != comicPathWord) {
            preferencesJob?.cancel()
            preferencesJob = null
            preferencesComic = null
            mutablePreferences.value = null
        }
        currentComicPathWord = comicPathWord
        currentChapterUuid = chapterUuid
        currentGroup = group

        loadJob?.cancel()
        loadJob = viewModelScope.launch(exceptionHandler) {
            _uiState.value = ReaderUiState.Loading

            try {
                val progressOwner = readingProgress.awaitOwner()
                // 先加载漫画的完整设置，再展示图片，避免方向或缩放先闪回全局值。
                prefs.initializeComic(comicPathWord).fold(
                    onSuccess = { ensureActive(); mutablePreferences.value = it },
                    onFailure = {
                        _uiState.value = ReaderUiState.Error("无法读取阅读设置：${it.message}")
                        return@launch
                    }
                )
                ensureActive()
                if (preferencesComic != comicPathWord) {
                    preferencesComic = comicPathWord
                    preferencesJob = viewModelScope.launch(exceptionHandler) {
                        prefs.comicPreferences(comicPathWord).collect {
                            if (currentComicPathWord == comicPathWord) mutablePreferences.value = it
                        }
                    }
                }
                val downloadedChapter = downloadRepository.getDownloadedChapter(chapterUuid)
                if (downloadedChapter != null && downloadedChapter.comicPathWord == comicPathWord) {
                    val allChapters = downloadRepository.getDownloadedChapters(comicPathWord)
                    val localContent = downloadedChapter.offlineContent(allChapters)
                    _uiState.value = ReaderUiState.Success(localContent, chapterUuid)
                    val resolvedGroup = downloadedChapter.groupPathWord ?: group
                    currentGroup = resolvedGroup
                    readingProgress.recordRead(
                        progressOwner, comicPathWord, chapterUuid,
                        localContent.chapterName, resolvedGroup
                    )
                    // 图片立即离线展示；目录刷新失败不影响当前章节阅读。
                    repository.getChapterInfo(comicPathWord, chapterUuid, resolvedGroup).fold(
                        onSuccess = { chapter ->
                            ensureActive()
                            _uiState.value = ReaderUiState.Success(
                                localContent.copy(
                                    prevUuid = chapter.prevChapter,
                                    nextUuid = chapter.nextChapter,
                                    chapterName = chapter.name
                                ), chapterUuid
                            )
                            try {
                                downloadRepository.updateChapterMetadata(
                                    comicPathWord, resolvedGroup, listOf(chapter)
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                android.util.Log.w("ReaderViewModel", "保存章节导航失败", e)
                            }
                        },
                        onFailure = { error ->
                            if (error is CancellationException) throw error
                            android.util.Log.d("ReaderViewModel", "目录刷新失败，使用离线导航")
                        }
                    )
                    return@launch
                }

                repository.getChapterContent(comicPathWord, chapterUuid, group)
                    .onSuccess { content ->
                        ensureActive()
                        _uiState.value = ReaderUiState.Success(content, chapterUuid)
                        if (content.imageUrls.isNotEmpty()) readingProgress.recordRead(
                            progressOwner,
                            comicPathWord, chapterUuid, content.chapterName, group
                        )
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
        updatePreferences { it.copy(readingDirection = value) }
    }

    fun setPageScale(value: String) {
        updatePreferences { it.copy(pageScale = value) }
    }

    fun setScreenOrientation(value: String) {
        updatePreferences { it.copy(screenOrientation = value) }
    }

    private fun updatePreferences(transform: (ReaderPreferences) -> ReaderPreferences) {
        val comic = currentComicPathWord.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch(exceptionHandler) {
            prefs.updateComic(comic, transform).fold(
                onSuccess = {},
                onFailure = { _toastMessage.value = "阅读设置保存失败，请重试" }
            )
        }
    }

    fun resetComicPreferences() {
        val comic = currentComicPathWord.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch(exceptionHandler) {
            prefs.resetComic(comic).fold(
                onSuccess = { _toastMessage.value = "本漫画已恢复为当前全局默认值" },
                onFailure = { _toastMessage.value = "恢复失败，请重试" }
            )
        }
    }
}

sealed class ReaderUiState {
    object Loading : ReaderUiState()
    data class Success(val content: ChapterContent, val chapterUuid: String = "") : ReaderUiState()
    data class Error(val message: String) : ReaderUiState()
    data class RiskControl(val reason: String) : ReaderUiState()
}
