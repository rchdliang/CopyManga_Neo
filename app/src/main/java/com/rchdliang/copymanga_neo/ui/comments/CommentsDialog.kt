package com.rchdliang.copymanga_neo.ui.comments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.rchdliang.copymanga_neo.domain.model.CommentKind
import com.rchdliang.copymanga_neo.domain.model.CommentTarget
import com.rchdliang.copymanga_neo.domain.model.CommentText
import com.rchdliang.copymanga_neo.domain.model.MangaComment

/** 手机和平板共用的评论窗口，阅读器不需要退出当前章节。 */
@Composable
fun CommentsDialog(
    target: CommentTarget,
    title: String,
    onDismiss: () -> Unit,
    postingAllowed: Boolean = true,
    onLogin: (() -> Unit)? = null,
    viewModel: CommentsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDiscard by remember(target) { mutableStateOf(false) }
    val dismiss = {
        if (!state.posting) {
            if (viewModel.hasDraft()) confirmDiscard = true else onDismiss()
        }
    }
    DisposableEffect(target, viewModel) {
        viewModel.open(target, postingAllowed)
        onDispose { viewModel.close() }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(target, state.parent?.id) { listState.scrollToItem(0) }
    Dialog(
        onDismissRequest = { if (state.parent != null && !state.posting) viewModel.backToComments() else dismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !state.posting,
            dismissOnClickOutside = false
        )
    ) {
        BackHandler(enabled = !state.posting) { if (state.parent != null) viewModel.backToComments() else dismiss() }
        Surface(
            Modifier
                .padding(12.dp)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .imePadding(),
            shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.parent != null) IconButton(
                        onClick = viewModel::backToComments,
                        enabled = !state.posting
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回评论")
                    }
                    Column(Modifier
                        .weight(1f)
                        .padding(start = 8.dp)) {
                        Text(
                            if (target.kind == CommentKind.COMIC) "漫画评论" else "章节评论",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            title,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { viewModel.load(true) },
                        enabled = !state.posting && !state.feed.loading
                    ) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                    IconButton(
                        onClick = dismiss,
                        enabled = !state.posting
                    ) { Icon(Icons.Default.Close, "关闭") }
                }
                HorizontalDivider()
                if (state.target != target) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                } else {
                    LazyColumn(Modifier
                        .weight(1f)
                        .fillMaxWidth(), state = listState) {
                        state.parent?.let { parent ->
                            item(key = "parent") {
                                CommentCard(parent, onReply = null)
                                HorizontalDivider()
                            }
                        }
                        item(key = "total") {
                            Text(
                                "共 ${state.feed.total} 条",
                                Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(state.feed.items, key = { "comment:${it.id}" }) { comment ->
                            CommentCard(
                                comment,
                                onReply = if (target.kind == CommentKind.COMIC && !state.posting) ({
                                    viewModel.showReplies(comment)
                                }) else null
                            )
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        }
                        item(key = "footer") {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (state.feed.loading) CircularProgressIndicator(Modifier.size(28.dp))
                                else if (state.feed.error != null) {
                                    Text(
                                        state.feed.error!!,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    TextButton(
                                        onClick = { viewModel.load(true) },
                                        enabled = !state.posting
                                    ) {
                                        Text("重试", color = MaterialTheme.colorScheme.primary)
                                    }
                                } else if (state.feed.items.isEmpty()) Text(
                                    "还没有评论",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                else if (state.feed.nextOffset != null) TextButton(onClick = {
                                    viewModel.load(
                                        false
                                    )
                                }, enabled = !state.posting) {
                                    Text("加载更多", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        state.message?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        when {
                            !state.postingAllowed -> Text(
                                "这部漫画已关闭评论发表",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            !state.loggedIn -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (onLogin == null) "请先在“我的”登录后再发表评论" else "登录后可以发表评论",
                                    Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (onLogin != null) TextButton(onClick = { onDismiss(); onLogin() }) {
                                    Text("去登录", color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            else -> {
                                val reply = state.replyTo ?: state.parent
                                if (reply != null) Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "回复 ${reply.username.ifBlank { "匿名用户" }}",
                                        Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (state.replyTo != null && state.replyTo != state.parent) IconButton(
                                        onClick = viewModel::cancelReply,
                                        enabled = !state.posting
                                    ) {
                                        Icon(Icons.Default.Close, "取消回复")
                                    }
                                }
                                OutlinedTextField(
                                    value = state.draft,
                                    onValueChange = viewModel::setDraft,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !state.posting,
                                    maxLines = 4,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                    placeholder = {
                                        Text(
                                            "说说你的看法（3～200 字）",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    isError = CommentText.length(state.draft) > CommentText.MAX_LENGTH
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${CommentText.length(state.draft)} / 200",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = viewModel::post,
                                        enabled = !state.posting && CommentText.isValid(state.draft)
                                    ) {
                                        Text(
                                            if (state.posting) "正在发表…" else "发表",
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (confirmDiscard) AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃未发表的内容？", color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false; onDismiss()
                }) { Text("放弃并关闭", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(
                        "继续编辑",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            })
    }
}

@Composable
private fun CommentCard(comment: MangaComment, onReply: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (comment.avatar.isNullOrBlank()) Icon(
                Icons.Default.AccountCircle,
                null,
                Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else AsyncImage(
                comment.avatar,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    comment.username.ifBlank { "匿名用户" },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    comment.createdAt,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        comment.parentUsername?.takeIf { it.isNotBlank() }?.let {
            Text(
                "回复 $it",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        SelectionContainer { Text(comment.content, color = MaterialTheme.colorScheme.onSurface) }
        if (onReply != null) TextButton(onClick = onReply) {
            Text(
                if (comment.replyCount > 0) "查看回复（${comment.replyCount}）" else "回复",
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
