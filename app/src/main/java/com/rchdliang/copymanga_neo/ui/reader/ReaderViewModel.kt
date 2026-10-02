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
    private val readingProgress: ReadingProgressRepository,
    private val tools: com.rchdliang.copymanga_neo.domain.repository.ReaderToolsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    val toolsSettings = tools.settings
    val bookmarks = tools.bookmarks
    private val mutableCommentsAllowed = MutableStateFlow(false)
    val commentsAllowed = mutableCommentsAllowed.asStateFlow()
    private val mutableClosedChapters = MutableStateFlow<Set<String>>(emptySet())
    val closedChapters = mutableClosedChapters.asStateFlow()
    private val mutableAppending = MutableStateFlow(false)
    val appending = mutableAppending.asStateFlow()
    private var chain = emptyList<ReaderSessionChapter>()
    private var appendJob: Job? = null
    private var policyJob: Job? = null
    private var attemptedNext: String? = null
    private var sessionRevision = 0L
    private val mutableCatalog = MutableStateFlow(ReaderCatalogUiState())
    val catalog = mutableCatalog.asStateFlow()
    private var catalogJob: Job? = null

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
        mutableCommentsAllowed.value = false
        mutableClosedChapters.value = emptySet()
        policyJob?.cancel()
        policyJob = viewModelScope.launch(exceptionHandler) {
            repository.getComicDetail(comicPathWord).fold(
                onSuccess = { detail ->
                    ensureActive()
                    if (currentComicPathWord == comicPathWord) mutableCommentsAllowed.value =
                        !detail.comic.closeRoast
                },
                onFailure = { ensureActive() }
            )
        }
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
        appendJob?.cancel()
        mutableAppending.value = false
        attemptedNext = null
        sessionRevision++
        chain = emptyList()

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
                    publish(localContent, chapterUuid, downloadedChapter.groupPathWord ?: group)
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
                            publish(
                                localContent.copy(
                                    prevUuid = chapter.prevChapter,
                                    nextUuid = chapter.nextChapter,
                                    chapterName = chapter.name
                                ), chapterUuid, resolvedGroup
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
                        publish(content, chapterUuid, group)
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

    private fun publish(content: ChapterContent, uuid: String, group: String) {
        val item = ReaderSessionChapter(uuid, group, content)
        chain =
            if (chain.any { it.uuid == uuid }) chain.map { if (it.uuid == uuid) item else it } else listOf(
                item
            )
        val active = chain.firstOrNull { it.uuid == currentChapterUuid } ?: item
        _uiState.value = ReaderUiState.Success(active.content, active.uuid, chain)
    }

    /** 当前可见图片变化才更新活跃章节；预加载不会写入阅读历史。 */
    fun activateChapter(uuid: String) {
        if (currentChapterUuid == uuid) return
        val chapter = chain.firstOrNull { it.uuid == uuid } ?: return
        currentChapterUuid = uuid
        currentGroup = chapter.group
        _uiState.value = ReaderUiState.Success(chapter.content, uuid, chain)
        readingProgress.currentOwner?.let { owner ->
            readingProgress.recordRead(
                owner,
                currentComicPathWord,
                uuid,
                chapter.content.chapterName,
                chapter.group
            )
        }
    }

    /** 每次只追加下一章；同一个失败目标不因滚动重组被反复请求。 */
    fun appendNextChapter(retry: Boolean = false) {
        if (!tools.settings.value.continuousChapters || appendJob?.isActive == true) return
        val last = chain.lastOrNull() ?: return
        val next = last.content.nextUuid ?: return
        if (chain.any { it.uuid == next } || (!retry && attemptedNext == next)) return
        attemptedNext = next
        val revision = sessionRevision
        val comic = currentComicPathWord
        mutableAppending.value = true
        appendJob = viewModelScope.launch {
            try {
                val downloaded = downloadRepository.getDownloadedChapter(next)
                val result = if (downloaded != null && downloaded.comicPathWord == comic) {
                    Result.success(
                        downloaded.offlineContent(
                            downloadRepository.getDownloadedChapters(
                                comic
                            )
                        )
                    )
                } else repository.getChapterContent(comic, next, last.group)
                ensureActive()
                if (revision != sessionRevision) return@launch
                result.fold(
                    onSuccess = { content ->
                        if (content.imageUrls.isEmpty()) return@fold
                        val group = downloaded?.takeIf { it.comicPathWord == comic }?.groupPathWord
                            ?: last.group
                        chain = chain + ReaderSessionChapter(next, group, content)
                        val active = chain.firstOrNull { it.uuid == currentChapterUuid } ?: last
                        _uiState.value = ReaderUiState.Success(active.content, active.uuid, chain)
                    },
                    onFailure = { _toastMessage.value = "下一章未加载，可在章末重试" }
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.w("ReaderViewModel", "下一章加载失败", error)
                if (revision == sessionRevision) _toastMessage.value = "下一章加载失败，请重试"
            } finally {
                if (revision == sessionRevision) mutableAppending.value = false
            }
        }
    }

    fun closeChapterComments(uuid: String) {
        mutableClosedChapters.value += uuid
    }

    fun jumpToBookmark(mark: com.rchdliang.copymanga_neo.domain.model.ReaderBookmark) {
        if (mark.comicPathWord == currentComicPathWord) loadChapter(
            mark.comicPathWord,
            mark.chapterUuid,
            mark.group
        )
    }

    fun loadCatalog() {
        if (catalogJob?.isActive == true) return
        val comic = currentComicPathWord
        val group = currentGroup
        mutableCatalog.value = ReaderCatalogUiState(loading = true)
        catalogJob = viewModelScope.launch {
            try {
                val catalogResult = repository.getChapterCatalog(comic)
                val full = catalogResult.getOrNull()?.chaptersByGroup?.get(group)
                if (full != null) {
                    mutableCatalog.value = ReaderCatalogUiState(chapters = full.map {
                        ReaderChapterLink(
                            it.uuid,
                            it.name
                        )
                    })
                    return@launch
                }
                if (catalogResult.exceptionOrNull() is com.rchdliang.copymanga_neo.domain.repository.RiskControlException) {
                    mutableCatalog.value = ReaderCatalogUiState(error = "访问受到限制，请稍后重试")
                    return@launch
                }
                val result = mutableListOf<ReaderChapterLink>()
                var offset = 0
                do {
                    val pageResult = repository.getChapters(comic, group, offset, 100)
                    val page = pageResult.getOrNull()
                    if (page == null) {
                        mutableCatalog.value =
                            ReaderCatalogUiState(chapters = result, error = "目录加载失败，点击重试")
                        return@launch
                    }
                    val fresh =
                        page.list.filter { chapter -> result.none { it.uuid == chapter.uuid } }
                    if (fresh.isEmpty()) break
                    result += fresh.map { ReaderChapterLink(it.uuid, it.name) }
                    offset += page.list.size
                } while (offset < page.total)
                mutableCatalog.value = ReaderCatalogUiState(chapters = result)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableCatalog.value = ReaderCatalogUiState(error = "目录加载失败，点击重试")
            }
        }
    }

    fun selectCatalogChapter(uuid: String) = loadChapter(currentComicPathWord, uuid, currentGroup)

    fun updateTools(transform: (com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings) -> com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings) {
        viewModelScope.launch(exceptionHandler) {
            tools.updateSettings(transform)
                .fold(onSuccess = {}, onFailure = { _toastMessage.value = "阅读设置保存失败" })
        }
    }

    fun toggleBookmark(chapterUuid: String, page: Int) {
        val chapter = chain.firstOrNull { it.uuid == chapterUuid } ?: return
        viewModelScope.launch(exceptionHandler) {
            tools.toggleBookmark(
                com.rchdliang.copymanga_neo.domain.model.ReaderBookmark(
                    currentComicPathWord,
                    chapterUuid,
                    chapter.content.chapterName,
                    chapter.group,
                    page
                )
            ).fold(onSuccess = {}, onFailure = { _toastMessage.value = "书签保存失败" })
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
    data class Success(
        val content: ChapterContent, val chapterUuid: String = "",
        val chapters: List<ReaderSessionChapter> = listOf(
            ReaderSessionChapter(
                chapterUuid,
                "default",
                content
            )
        )
    ) : ReaderUiState()

    data class Error(val message: String) : ReaderUiState()
    data class RiskControl(val reason: String) : ReaderUiState()
}

data class ReaderSessionChapter(val uuid: String, val group: String, val content: ChapterContent)

/** 评论页不计入图片页码；稳定 key 用于追加章节和切换评论开关时保持位置。 */
data class ReaderPageEntry(
    val key: String, val chapterUuid: String, val chapterName: String,
    val source: String?, val page: Int, val imageCount: Int
)

data class ReaderChapterLink(val uuid: String, val name: String)
data class ReaderCatalogUiState(
    val chapters: List<ReaderChapterLink> = emptyList(),
    val loading: Boolean = false, val error: String? = null
)
