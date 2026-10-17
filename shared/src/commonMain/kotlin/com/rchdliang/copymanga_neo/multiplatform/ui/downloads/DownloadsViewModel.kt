package com.rchdliang.copymanga_neo.multiplatform.ui.downloads

import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiLog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.DownloadKind
import com.rchdliang.copymanga_neo.domain.model.DownloadProgressInfo
import com.rchdliang.copymanga_neo.domain.model.DownloadTaskStatus
import com.rchdliang.copymanga_neo.domain.model.DownloadedComicInfo
import com.rchdliang.copymanga_neo.domain.model.LocalReadingSession
import com.rchdliang.copymanga_neo.domain.model.NovelBook
import com.rchdliang.copymanga_neo.domain.model.NovelCachedVolume
import com.rchdliang.copymanga_neo.domain.model.comicDownloadKey
import com.rchdliang.copymanga_neo.domain.model.novelDownloadKey
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
import com.rchdliang.copymanga_neo.domain.repository.NovelRepository
import com.rchdliang.copymanga_neo.domain.repository.ReadingSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DownloadedNovelInfo(val book: NovelBook, val volumes: List<NovelCachedVolume>)

/** 多选使用完整下载身份，漫画与小说即使 path 相同也不会串项。 */
sealed interface DownloadedBookItem {
    val key: String
    val updatedAt: Long

    data class Comic(val info: DownloadedComicInfo) : DownloadedBookItem {
        override val key get() = comicDownloadKey(info.pathWord)
        override val updatedAt get() = info.downloadAt
    }

    data class Novel(val info: DownloadedNovelInfo) : DownloadedBookItem {
        val mode get() = info.volumes.first().mode
        val owner get() = info.volumes.first().owner
        override val key get() = novelDownloadKey(mode, owner, info.book.path)
        override val updatedAt get() = info.volumes.maxOf { it.lastAccessedAt }
    }
}

data class DownloadsUiState(
    val downloadedBooks: List<DownloadedBookItem> = emptyList(),
    val activeDownloads: Map<String, DownloadProgressInfo> = emptyMap(),
    val isSelectionMode: Boolean = false,
    val selectedKeys: Set<String> = emptySet(),
    val message: String? = null
) {
    val selectedComicPathWords: Set<String>
        get() = downloadedBooks.filterIsInstance<DownloadedBookItem.Comic>()
            .filter { it.key in selectedKeys }.map { it.info.pathWord }.toSet()
}

class DownloadsViewModel constructor(
    private val downloadRepository: DownloadRepository,
    private val novels: NovelRepository,
    private val sessions: ReadingSessionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()
    private var displayedSession: LocalReadingSession? = null

    private data class Contents(
        val session: LocalReadingSession?,
        val books: List<DownloadedBookItem>
    )

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        UiLog.e("DownloadsViewModel", "Unhandled coroutine exception", throwable)
        _uiState.update { it.copy(message = throwable.message ?: "下载操作失败") }
    }

    init {
        viewModelScope.launch(exceptionHandler) {
            val contents = combine(
                downloadRepository.getAllDownloadedComics().catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(message = error.message ?: "下载记录无法读取，原数据已保留") }
                    emit(emptyList())
                }, novels.cachedVolumes,
                sessions.cacheReadyFlow, sessions.sessionChanges
            ) { comics, cached, ready, _ ->
                val session = if (ready) sessions.getReadingSessionSync() else null
                val owned = if (session?.owner == null) emptyList() else cached.filter {
                    it.visibleInDownloads && it.owner == session.owner && it.mode == session.mode
                }
                val books = comics.map { DownloadedBookItem.Comic(it) } +
                        owned.groupBy { it.book.path }.values.map { volumes ->
                            DownloadedBookItem.Novel(
                                DownloadedNovelInfo(
                                    volumes.maxBy { it.lastAccessedAt }.book,
                                    volumes.sortedBy { it.volume.index })
                            )
                        }
                Contents(session, books.sortedByDescending { it.updatedAt })
            }
            combine(contents, downloadRepository.getDownloadProgress()) { content, progress ->
                content to progress.values.filter {
                    it.status != DownloadTaskStatus.COMPLETED &&
                            (it.kind == DownloadKind.COMIC ||
                                    (content.session?.owner != null && it.owner == content.session.owner &&
                                            it.mode == content.session.mode))
                }.associateBy { it.taskKey }
            }.collect { (content, progress) ->
                val changedSession = displayedSession != content.session
                displayedSession = content.session
                val validKeys = content.books.map { it.key }.toSet()
                val comicKeys =
                    content.books.filterIsInstance<DownloadedBookItem.Comic>().map { it.key }
                        .toSet()
                _uiState.update { state ->
                    val selected =
                        state.selectedKeys.intersect(if (changedSession) comicKeys else validKeys)
                    state.copy(
                        downloadedBooks = content.books,
                        activeDownloads = progress,
                        selectedKeys = selected,
                        isSelectionMode = state.isSelectionMode && content.books.isNotEmpty() &&
                                (!changedSession || selected.isNotEmpty())
                    )
                }
            }
        }
        viewModelScope.launch(exceptionHandler) {
            novels.refreshCachedVolumes().fold(
                onSuccess = {},
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(message = error.message ?: "小说下载目录加载失败") }
                }
            )
        }
    }

    fun pauseTask(key: String) = viewModelScope.launch(exceptionHandler) {
        downloadRepository.awaitReady().getOrThrow()
        downloadRepository.pauseDownload(key)
    }
    fun resumeTask(key: String) = viewModelScope.launch(exceptionHandler) {
        downloadRepository.awaitReady().getOrThrow()
        downloadRepository.resumeDownload(key)
    }
    fun cancelTask(key: String) {
        viewModelScope.launch(exceptionHandler) { downloadRepository.awaitReady().getOrThrow(); downloadRepository.cancelDownload(key) }
    }
    fun setImageConcurrency(count: Int) = downloadRepository.setImageConcurrency(count)
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
    fun cancelAll() {
        viewModelScope.launch(exceptionHandler) { downloadRepository.awaitReady().getOrThrow(); downloadRepository.cancelAll() }
    }

    fun enterSelectionMode(key: String) {
        if (_uiState.value.downloadedBooks.none { it.key == key }) return
        _uiState.update { it.copy(isSelectionMode = true, selectedKeys = setOf(key)) }
    }
    fun exitSelectionMode() {
        _uiState.update { it.copy(isSelectionMode = false, selectedKeys = emptySet()) }
    }

    fun toggleSelection(key: String) {
        _uiState.update { state ->
            if (state.downloadedBooks.none { it.key == key }) state
            else state.copy(selectedKeys = if (key in state.selectedKeys) state.selectedKeys - key else state.selectedKeys + key)
        }
    }
    fun selectAll() {
        _uiState.update {
            it.copy(selectedKeys = it.downloadedBooks.map { book -> book.key }.toSet())
        }
    }
    fun invertSelection() {
        _uiState.update { state ->
            state.copy(selectedKeys = state.downloadedBooks.map { it.key }
                .toSet() - state.selectedKeys)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedKeys = emptySet()) }
    }

    /** 混合批量删除按各项身份执行，小说账号或模式变化时保留该项数据。 */
    fun deleteSelected() {
        val selected =
            _uiState.value.downloadedBooks.filter { it.key in _uiState.value.selectedKeys }
        val session = sessions.getReadingSessionSync()
        if (selected.isEmpty()) return
        exitSelectionMode()
        viewModelScope.launch(exceptionHandler) {
            var removed = 0
            val failures = mutableListOf<String>()
            selected.forEach { item ->
                try {
                    when (item) {
                        is DownloadedBookItem.Comic -> downloadRepository.deleteComicAndChapters(
                            item.info.pathWord
                        )

                        is DownloadedBookItem.Novel -> {
                            check(
                                session != null && session.owner == item.owner && session.mode == item.mode &&
                                        sessions.getReadingSessionSync() == session
                            ) { "账号或请求模式已变化，小说下载已保留" }
                            downloadRepository.cancelDownload(item.key)
                            check(sessions.getReadingSessionSync() == session) { "账号或请求模式已变化，小说缓存已保留" }
                            novels.removeBook(item.mode, item.owner, item.info.book.path)
                                .fold(onSuccess = {}, onFailure = { throw it })
                        }
                    }
                    removed++
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failures += error.message ?: "删除失败"
                }
            }
            _uiState.update {
                it.copy(
                    message = if (failures.isEmpty()) "已删除 $removed 部下载"
                    else "已删除 $removed 部，${failures.size} 部未完成：${failures.first()}"
                )
            }
        }
    }
}
