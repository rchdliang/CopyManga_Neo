package com.rchdliang.copymanga_neo.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.CommentKind
import com.rchdliang.copymanga_neo.domain.model.CommentPage
import com.rchdliang.copymanga_neo.domain.model.CommentTarget
import com.rchdliang.copymanga_neo.domain.model.MangaComment
import com.rchdliang.copymanga_neo.domain.repository.CommentClosedException
import com.rchdliang.copymanga_neo.domain.repository.CommentRepository
import com.rchdliang.copymanga_neo.ui.comments.CommentCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
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

@Composable
fun ReaderChapterComments(
    page: ReaderPageEntry,
    onOpen: () -> Unit,
    onClosed: () -> Unit,
    onShowControls: () -> Unit,
    onReport: (MangaComment) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderChapterCommentsViewModel = hiltViewModel(key = "reader-comments:${page.chapterUuid}")
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(page.chapterUuid) { viewModel.load(page.chapterUuid) }
    LaunchedEffect(state.closed) { if (state.closed) onClosed() }
    Surface(modifier, color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    page.chapterName,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
                TextButton(onClick = onShowControls) {
                    Text("阅读菜单", color = MaterialTheme.colorScheme.primary)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "章节评论",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = onOpen) {
                    Text(
                        "查看全部 / 发表",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            HorizontalDivider()
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 100.dp)) {
                items(state.page?.comments.orEmpty(), key = { it.id }) { comment ->
                    CommentCard(comment, ZonedDateTime.now(), onReport = onReport)
                    HorizontalDivider()
                }
                item {
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (state.page?.comments?.isEmpty() == true) Text(
                        "还没有评论",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                    state.error?.let { message ->
                        TextButton(onClick = {
                            viewModel.load(
                                page.chapterUuid,
                                more = state.page?.nextOffset != null
                            )
                        }) {
                            Text(
                                message,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (state.page?.nextOffset != null && !state.loading) TextButton(onClick = {
                        viewModel.load(
                            page.chapterUuid,
                            true
                        )
                    }) { Text("加载更多", color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}
