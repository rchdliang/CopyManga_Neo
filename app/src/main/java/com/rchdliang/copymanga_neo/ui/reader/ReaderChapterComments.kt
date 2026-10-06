package com.rchdliang.copymanga_neo.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.CommentKind
import com.rchdliang.copymanga_neo.domain.model.CommentPage
import com.rchdliang.copymanga_neo.domain.model.CommentTarget
import com.rchdliang.copymanga_neo.domain.repository.CommentClosedException
import com.rchdliang.copymanga_neo.domain.repository.CommentRepository
import com.rchdliang.copymanga_neo.ui.comments.CommentFeed
import com.rchdliang.copymanga_neo.ui.comments.CommentsPanel
import com.rchdliang.copymanga_neo.ui.comments.CommentsViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderChapterCommentsUiState(
    val page: CommentPage? = null, val loading: Boolean = false,
    val closed: Boolean = false, val error: String? = null
)

/** 底栏与章末评论共用状态，首次显示任一入口时加载；禁评响应移除章末评论页。 */
@HiltViewModel
class ReaderChapterCommentsViewModel @Inject constructor(private val repository: CommentRepository) :
    ViewModel() {
    private val state = MutableStateFlow(ReaderChapterCommentsUiState())
    val uiState = state.asStateFlow()
    private var chapter: String? = null
    private var job: Job? = null
    private val handler = CoroutineExceptionHandler { _, _ ->
        state.value = state.value.copy(loading = false, error = "评论加载失败")
    }

    /** 完整评论区刷新、分页或发表后同步底栏总数，避免保留旧角标。 */
    fun updateFeed(uuid: String, feed: CommentFeed) {
        job?.cancel()
        chapter = uuid
        state.value = ReaderChapterCommentsUiState(
            page = CommentPage(feed.items, feed.total, feed.nextOffset)
        )
    }

    fun load(uuid: String, more: Boolean = false, refresh: Boolean = false) {
        if (job?.isActive == true || state.value.closed || (!more && !refresh && chapter == uuid && state.value.page != null)) return
        chapter = uuid
        val old = state.value.page
        val offset = if (more) old?.nextOffset ?: return else 0
        state.value = state.value.copy(loading = true, error = null)
        job = viewModelScope.launch(handler) {
            repository.getComments(CommentTarget(CommentKind.CHAPTER, uuid), offset).fold(
                onSuccess = { page ->
                    ensureActive()
                    state.value = ReaderChapterCommentsUiState(
                        page = if (more && old != null)
                            page.copy(comments = (old.comments + page.comments).distinctBy { it.id }) else page
                    )
                },
                onFailure = { error ->
                    ensureActive()
                    state.value = state.value.copy(
                        loading = false, closed = error is CommentClosedException,
                        error = if (error is CommentClosedException) null else "评论暂时不可用，点击重试"
                    )
                }
            )
        }
    }
}


/** 章末直接提供完整评论内容及输入框，不再经过另一个评论入口。 */
@Composable
fun ReaderChapterComments(
    page: ReaderPageEntry,
    onClosed: () -> Unit,
    onShowControls: () -> Unit,
    modifier: Modifier = Modifier,
    refreshVersion: Int = 0,
    viewModel: ReaderChapterCommentsViewModel = hiltViewModel(key = "reader-comments:${page.chapterUuid}"),
    commentsViewModel: CommentsViewModel = hiltViewModel(key = "reader-comments-full:${page.chapterUuid}")
) {
    LaunchedEffect(refreshVersion) {
        if (refreshVersion > 0 && commentsViewModel.uiState.value.target?.id == page.chapterUuid) {
            commentsViewModel.load(refresh = true)
        }
    }
    CommentsPanel(
        target = CommentTarget(CommentKind.CHAPTER, page.chapterUuid),
        title = page.chapterName,
        onDismiss = onShowControls,
        viewModel = commentsViewModel,
        embedded = true,
        modifier = modifier,
        onFeedChanged = { viewModel.updateFeed(page.chapterUuid, it) },
        onClosed = onClosed
    )
}
