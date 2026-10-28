package com.rchdliang.copymanga_neo.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rchdliang.copymanga_neo.domain.repository.*
import com.rchdliang.copymanga_neo.ui.comments.CommentDraftSnapshot
import com.rchdliang.copymanga_neo.ui.comments.CommentsViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** 阅读器拥有独立的章节模型；淘汰时只保留用户输入，不保留评论、头像或订阅。 */
@dagger.hilt.android.lifecycle.HiltViewModel
class ReaderCommentModels @javax.inject.Inject constructor(
    private val repository: CommentRepository,
    private val users: UserRepository,
    private val manga: MangaRepository,
    private val downloads: DownloadRepository,
    private val sessions: ReadingSessionRepository
) : ViewModel() {
    private class Entry : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
        var inline: CommentsViewModel? = null
        var observer: Job? = null
    }
    private val entries = mutableMapOf<String, Entry>()
    private val drafts = mutableMapOf<String, CommentDraftSnapshot>()
    private var retained = emptySet<String>()
    private fun ownerKey() = sessions.getReadingSessionSync()?.let { it.mode to it.owner }
    private var owner = ownerKey()

    private fun ensureOwner() {
        val next = ownerKey()
        if (next != owner) {
            clearEntries(); drafts.clear()
            owner = next
        }
    }
    private fun entry(chapter: String): Entry {
        ensureOwner()
        return entries.getOrPut(chapter) { Entry() }
    }
    private inline fun <reified T : ViewModel> model(entry: Entry, noinline create: () -> T): T =
        ViewModelProvider.create(entry, viewModelFactory { initializer { create() } })[T::class]

    fun chapter(chapter: String): ReaderChapterCommentsViewModel =
        model(entry(chapter)) { ReaderChapterCommentsViewModel(repository) }

    fun inline(chapter: String): CommentsViewModel {
        val entry = entry(chapter)
        return entry.inline ?: model(entry) {
            CommentsViewModel(repository, users, manga, downloads).also {
                drafts.remove(chapter)?.let(it::restoreReaderDrafts)
            }
        }.also { value ->
            entry.inline = value
            entry.observer = viewModelScope.launch {
                value.uiState.map { it.posting || it.reporting || it.deleting || it.downloadingComments }
                    .distinctUntilChanged().drop(1).collect { busy -> if (!busy) prune() }
            }
        }
    }

    fun retain(chapters: Set<String>) {
        ensureOwner()
        retained = chapters
        prune()
    }

    private fun prune() {
        entries.keys.filter { it !in retained }.forEach { chapter ->
            val entry = entries.getValue(chapter)
            val state = entry.inline?.uiState?.value
            // 已提交的写请求要完成后再释放，不能因为滚出窗口而取消或重试发表。
            if (state?.posting == true || state?.reporting == true || state?.deleting == true || state?.downloadingComments == true) return@forEach
            val draft = entry.inline?.captureReaderDrafts()
            if (draft != null && !draft.isEmpty) drafts[chapter] = draft else drafts.remove(chapter)
            entries.remove(chapter)
            entry.observer?.cancel()
            entry.viewModelStore.clear()
        }
    }

    private fun clearEntries() {
        entries.values.forEach { it.observer?.cancel(); it.viewModelStore.clear() }
        entries.clear()
    }
    override fun onCleared() { clearEntries(); drafts.clear() }
}
