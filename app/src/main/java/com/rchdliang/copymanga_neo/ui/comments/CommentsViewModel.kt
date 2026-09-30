package com.rchdliang.copymanga_neo.ui.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.CommentKind
import com.rchdliang.copymanga_neo.domain.model.CommentTarget
import com.rchdliang.copymanga_neo.domain.model.CommentText
import com.rchdliang.copymanga_neo.domain.model.MangaComment
import com.rchdliang.copymanga_neo.domain.repository.CommentRepository
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CommentFeed(
    val items: List<MangaComment> = emptyList(),
    val total: Int = 0,
    val nextOffset: Int? = null,
    val loading: Boolean = false,
    val error: String? = null
)

data class CommentsUiState(
    val target: CommentTarget? = null,
    val feed: CommentFeed = CommentFeed(),
    val parent: MangaComment? = null,
    val replyTo: MangaComment? = null,
    val draft: String = "",
    val loggedIn: Boolean = false,
    val posting: Boolean = false,
    val postingAllowed: Boolean = true,
    val message: String? = null
)

/** 一个打开的评论窗口只有一个活动请求上下文，切章、退出和切账号都会失效旧请求。 */
@HiltViewModel
class CommentsViewModel @Inject constructor(
    private val repository: CommentRepository,
    private val users: UserRepository
) : ViewModel() {
    private val state = MutableStateFlow(CommentsUiState(loggedIn = users.isLoggedInSync()))
    val uiState = state.asStateFlow()
    private var rootFeed = CommentFeed()
    private var rootDraft = ""
    private val replyDrafts = mutableMapOf<String, String>()
    private var readJob: Job? = null
    private var postJob: Job? = null
    private var revision = 0L
    private val handler = CoroutineExceptionHandler { _, error ->
        android.util.Log.e("Comments", "评论操作失败", error)
        state.update {
            it.copy(
                posting = false,
                feed = it.feed.copy(loading = false),
                message = "评论操作失败，请重试"
            )
        }
    }

    init {
        viewModelScope.launch(handler) {
            var previous: Pair<Boolean, String?>? = null
            combine(users.isLoggedIn(), users.getUsername()) { loggedIn, name -> loggedIn to name }
                .distinctUntilChanged().collect { account ->
                    if (previous != null && previous != account) {
                        postJob?.cancel()
                        state.update {
                            it.copy(
                                posting = false,
                                message = "登录状态已变化，请确认后再发表"
                            )
                        }
                    }
                    previous = account
                    state.update { it.copy(loggedIn = account.first) }
                }
        }
    }

    fun open(target: CommentTarget, postingAllowed: Boolean) {
        close()
        state.value = CommentsUiState(
            target = target,
            loggedIn = users.isLoggedInSync(),
            postingAllowed = postingAllowed
        )
        rootFeed = CommentFeed()
        rootDraft = ""
        replyDrafts.clear()
        load(refresh = true)
    }

    fun close() {
        revision++
        readJob?.cancel()
        postJob?.cancel()
        state.update { it.copy(target = null, posting = false) }
    }

    fun setDraft(value: String) {
        if (!state.value.posting) state.update { it.copy(draft = value) }
    }

    fun hasDraft(): Boolean =
        state.value.draft.isNotBlank() || rootDraft.isNotBlank() || replyDrafts.values.any { it.isNotBlank() }

    fun clearMessage() {
        state.update { it.copy(message = null) }
    }

    fun cancelReply() {
        state.update { it.copy(replyTo = null) }
    }

    fun showReplies(comment: MangaComment) {
        val current = state.value
        if (current.posting || current.target?.kind != CommentKind.COMIC) return
        if (current.parent == null) {
            rootFeed = current.feed.copy(loading = false)
            rootDraft = current.draft
            readJob?.cancel()
            revision++
            state.update {
                it.copy(
                    parent = comment,
                    replyTo = comment,
                    draft = replyDrafts[comment.id].orEmpty(),
                    feed = CommentFeed()
                )
            }
            load(refresh = true)
        } else state.update { it.copy(replyTo = comment) }
    }

    fun backToComments() {
        if (state.value.posting) return
        readJob?.cancel()
        revision++
        state.value.parent?.let { replyDrafts[it.id] = state.value.draft }
        state.update {
            it.copy(
                parent = null,
                replyTo = null,
                draft = rootDraft,
                feed = rootFeed,
                message = null
            )
        }
    }

    fun load(refresh: Boolean) {
        val current = state.value
        val target = current.target ?: return
        if (current.posting || (!refresh && current.feed.loading)) return
        val offset = if (refresh) 0 else current.feed.nextOffset ?: return
        readJob?.cancel()
        val requestRevision = ++revision
        val parentId = current.parent?.id
        state.update { it.copy(feed = it.feed.copy(loading = true, error = null)) }
        readJob = viewModelScope.launch(handler) {
            repository.getComments(target, offset, parentId).fold(
                onSuccess = { page ->
                    ensureActive()
                    if (requestRevision != revision) return@fold
                    state.update {
                        val items =
                            (if (refresh) page.comments else it.feed.items + page.comments).distinctBy { comment -> comment.id }
                        it.copy(feed = CommentFeed(items, page.total, page.nextOffset))
                    }
                },
                onFailure = { error ->
                    ensureActive()
                    if (requestRevision == revision) state.update {
                        it.copy(
                            feed = it.feed.copy(
                                loading = false,
                                error = error.message ?: "评论加载失败"
                            )
                        )
                    }
                }
            )
        }
    }

    fun post() {
        val current = state.value
        val target = current.target ?: return
        if (current.posting || !current.postingAllowed) return
        if (!current.loggedIn) {
            state.update { it.copy(message = "请先到“我的”登录") }; return
        }
        if (!CommentText.isValid(current.draft)) {
            state.update { it.copy(message = "评论需要 3～200 个字") }; return
        }
        readJob?.cancel()
        val requestRevision = ++revision
        val reply = current.replyTo ?: current.parent
        state.update {
            it.copy(
                posting = true,
                message = null,
                feed = it.feed.copy(loading = false)
            )
        }
        postJob = viewModelScope.launch(handler) {
            repository.postComment(target, current.draft, reply?.id).fold(
                onSuccess = {
                    ensureActive()
                    if (requestRevision != revision) return@fold
                    current.parent?.let { parent ->
                        replyDrafts.remove(parent.id)
                        rootFeed = rootFeed.copy(items = rootFeed.items.map {
                            if (it.id == parent.id) it.copy(replyCount = it.replyCount + 1) else it
                        })
                    }
                    if (current.parent == null) rootDraft = ""
                    state.update {
                        it.copy(
                            posting = false,
                            draft = "",
                            replyTo = null,
                            message = "发表成功"
                        )
                    }
                    load(refresh = true)
                },
                onFailure = { error ->
                    ensureActive()
                    if (requestRevision == revision) state.update {
                        it.copy(
                            posting = false,
                            message = (error.message
                                ?: "发表失败") + if (error is java.io.IOException) "。结果可能未确认，请先刷新评论，避免重复发表。" else ""
                        )
                    }
                }
            )
        }
    }
}
