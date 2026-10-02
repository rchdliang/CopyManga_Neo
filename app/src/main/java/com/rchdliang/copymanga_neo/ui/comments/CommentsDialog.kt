package com.rchdliang.copymanga_neo.ui.comments

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.model.CommentKind
import com.rchdliang.copymanga_neo.domain.model.CommentTarget
import com.rchdliang.copymanga_neo.domain.model.CommentText
import com.rchdliang.copymanga_neo.domain.model.MangaComment
import com.rchdliang.copymanga_neo.ui.preview.PreviewAwareAsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/** 手机底部弹层、平板右侧面板共用评论状态，不退出当前阅读章节。 */
@Composable
fun CommentsDialog(
    target: CommentTarget,
    title: String,
    onDismiss: () -> Unit,
    postingAllowed: Boolean = true,
    onLogin: (() -> Unit)? = null,
    comicPathWord: String? = null,
    viewModel: CommentsViewModel = hiltViewModel(),
    initialReport: MangaComment? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentTime by produceState(initialValue = ZonedDateTime.now()) {
        while (true) {
            value = ZonedDateTime.now()
            delay(60_000.milliseconds)
        }
    }
    var confirmDiscard by remember(target) { mutableStateOf(false) }
    var loginAfterDiscard by remember(target) { mutableStateOf(false) }
    var composerOpen by remember(target) { mutableStateOf(false) }
    var fabVisible by remember(target, state.parent?.id) { mutableStateOf(true) }
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        state.message?.takeIf { it.contains("举报") }?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }
    val dismiss = {
        if (!state.posting) {
            loginAfterDiscard = false
            if (viewModel.hasDraft()) confirmDiscard = true else onDismiss()
        }
    }
    val closeComposer = {
        if (!state.posting) {
            keyboard?.hide()
            composerOpen = false
        }
    }
    val back = {
        when {
            state.posting || state.reporting -> Unit
            state.reportTarget != null -> viewModel.closeReport()
            composerOpen -> closeComposer()
            state.parent != null -> viewModel.backToComments()
            else -> dismiss()
        }
    }
    DisposableEffect(target, viewModel, comicPathWord) {
        viewModel.open(target, postingAllowed, comicPathWord)
        onDispose { viewModel.close() }
    }
    LaunchedEffect(target, initialReport?.id, state.target) {
        if (state.target == target && initialReport != null) viewModel.openReport(initialReport)
    }
    val rootList = rememberLazyListState()
    val replyList = rememberLazyListState()
    LaunchedEffect(target) {
        rootList.requestScrollToItem(0)
        replyList.requestScrollToItem(0)
    }
    val listState = if (state.parent == null) rootList else replyList
    LaunchedEffect(state.parent?.id) {
        if (state.parent != null) replyList.scrollToItem(0)
    }
    val scrollThreshold = with(LocalDensity.current) { 8.dp.roundToPx() }
    LaunchedEffect(listState) {
        var lastIndex = listState.firstVisibleItemIndex
        var lastOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                listState.isScrollInProgress
            )
        }.distinctUntilChanged().collect { (index, offset, scrolling) ->
            if (!scrolling) {
                if (index == 0 && offset == 0) fabVisible = true
                lastIndex = index
                lastOffset = offset
                return@collect
            }
            val atStart = index == 0 && offset == 0
            if (!atStart && index == lastIndex && abs(offset - lastOffset) < scrollThreshold) return@collect
            fabVisible = atStart || index < lastIndex || (index == lastIndex && offset < lastOffset)
            lastIndex = index
            lastOffset = offset
        }
    }
    // 数据变更必须完成布局后才判断触底，避免分页结果回来时连续抢跑下一页。
    LaunchedEffect(listState, target, state.parent?.id) {
        snapshotFlow {
            val feed = state.feed
            val layout = listState.layoutInfo
            val expectedItems = feed.items.size + 1 + if (state.parent != null) 1 else 0
            val atEnd = layout.totalItemsCount == expectedItems &&
                    layout.visibleItemsInfo.lastOrNull()?.index == expectedItems - 1
            feed.nextOffset.takeIf {
                state.target == target && atEnd && !feed.loading && feed.error == null && !state.posting
            }
        }.distinctUntilChanged().collect { offset ->
            if (offset != null) viewModel.load(refresh = false)
        }
    }
    LaunchedEffect(state.posting, state.message) {
        if (!state.posting && state.message == "发表成功") {
            keyboard?.hide()
            composerOpen = false
        }
    }
    Dialog(
        onDismissRequest = back,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = !state.posting,
            dismissOnClickOutside = false
        )
    ) {
        BackHandler(enabled = !state.posting, onBack = back)
        CommentsContent(
            target = target, title = title, state = state, currentTime = currentTime,
            listState = listState, fabVisible = fabVisible, composerOpen = composerOpen,
            onCompose = { reply ->
                if (reply == null) viewModel.startComment() else viewModel.startReply(reply)
                composerOpen = true
            },
            onCloseComposer = closeComposer, onDismiss = dismiss,
            onBackToComments = viewModel::backToComments,
            onRefresh = { viewModel.load(refresh = true, fromPullToRefresh = true) },
            onRetry = { viewModel.load(refresh = state.feed.items.isEmpty() || state.feed.refreshFailed) },
            onRetryPermission = viewModel::retryPermission,
            onLoadReplyPreview = viewModel::loadReplyPreview,
            onShowReplies = viewModel::showReplies,
            onDraftChange = viewModel::setDraft, onPost = viewModel::post,
            onReport = viewModel::openReport,
            onLogin = if (onLogin != null) ({
                if (viewModel.hasDraft()) {
                    loginAfterDiscard = true
                    confirmDiscard = true
                } else {
                    onDismiss()
                    onLogin()
                }
            }) else null
        )
        if (state.reportTarget != null) CommentReportDialog(
            state = state, onDismiss = viewModel::closeReport,
            onRetry = { state.reportTarget?.let(viewModel::openReport) },
            onSubmit = viewModel::report
        )
        if (confirmDiscard) AlertDialog(
            onDismissRequest = { confirmDiscard = false; loginAfterDiscard = false },
            title = { Text("放弃未发表的内容？", color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDismiss()
                    if (loginAfterDiscard) onLogin?.invoke()
                }) {
                    Text("放弃并关闭", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmDiscard = false; loginAfterDiscard = false; composerOpen = true
                }) {
                    Text("继续编辑", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}

/** 纯界面组件：运行时与静态 Preview 共用，不创建 ViewModel 或发起网络请求。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CommentsContent(
    target: CommentTarget,
    title: String,
    state: CommentsUiState,
    currentTime: ZonedDateTime,
    listState: androidx.compose.foundation.lazy.LazyListState,
    fabVisible: Boolean,
    composerOpen: Boolean,
    onCompose: (MangaComment?) -> Unit,
    onCloseComposer: () -> Unit,
    onDismiss: () -> Unit,
    onBackToComments: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onRetryPermission: () -> Unit,
    onLoadReplyPreview: (MangaComment) -> Unit,
    onShowReplies: (MangaComment) -> Unit,
    onDraftChange: (String) -> Unit,
    onPost: () -> Unit,
    modifier: Modifier = Modifier,
    onReport: (MangaComment) -> Unit = {},
    onLogin: (() -> Unit)? = null
) {
    val pullState = rememberPullToRefreshState()
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        val wide = maxWidth >= 840.dp
        val fabBottomInset = with(LocalDensity.current) {
            (if (wide) 32.dp else 16.dp).roundToPx()
        } + WindowInsets.navigationBars.getBottom(LocalDensity.current)
        val panelAlignment = if (wide) Alignment.BottomEnd else Alignment.BottomCenter
        val panelModifier = if (wide) {
            Modifier
                .padding(16.dp)
                .width(480.dp)
                .fillMaxHeight()
        } else {
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        }
        // 全屏 Dialog 的窗口外没有可点击区域，在面板背后提供独立的关闭区域。
        Box(
            Modifier
                .matchParentSize()
                .clickable(
                    enabled = !state.posting && !state.reporting,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
        Surface(
            modifier = Modifier
                .align(panelAlignment)
                .then(panelModifier),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                if (!wide) Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(32.dp, 4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.parent != null) IconButton(
                        onClick = onBackToComments,
                        enabled = !state.posting
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回评论") }
                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                if (state.parent != null) "评论回复" else if (target.kind == CommentKind.COMIC) "漫画评论" else "章节评论",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    state.feed.total.toString(),
                                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                        Text(
                            title, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss, enabled = !state.posting) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (!state.postingAllowed) Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        when {
                            state.checkingPermission -> "正在确认评论权限…"
                            state.permissionError != null -> state.permissionError
                            else -> "这部漫画已关闭评论发表"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (state.permissionError != null) TextButton(onClick = onRetryPermission) {
                        Text("重试", color = MaterialTheme.colorScheme.primary)
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (state.target != target) {
                        LoadingIndicator(Modifier.align(Alignment.Center))
                    } else {
                        PullToRefreshBox(
                            isRefreshing = state.feed.refreshing,
                            onRefresh = {
                                if (!state.posting && !state.reporting && !state.feed.loading && !composerOpen) onRefresh()
                            },
                            state = pullState,
                            modifier = Modifier.fillMaxSize(),
                            indicator = {
                                PullToRefreshDefaults.LoadingIndicator(
                                    state = pullState,
                                    isRefreshing = state.feed.refreshing,
                                    modifier = Modifier.align(Alignment.TopCenter)
                                )
                            }
                        ) {
                            LazyColumn(
                                state = listState, modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 96.dp)
                            ) {
                                state.parent?.let { parent ->
                                    item(key = "parent") {
                                        CommentCard(
                                            comment = parent,
                                            currentTime = currentTime,
                                            onReport = onReport,
                                            onReply = if (state.postingAllowed) ({
                                                onCompose(parent)
                                            }) else null,
                                            enabled = !state.posting
                                        )
                                        CommentDivider()
                                        Text(
                                            if (state.feed.totalExact) "共 ${state.feed.total} 条回复" else "已加载 ${state.feed.items.size} 条回复",
                                            Modifier.padding(
                                                start = 68.dp,
                                                top = 16.dp,
                                                bottom = 8.dp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                                items(state.feed.items, key = { "comment:${it.id}" }) { comment ->
                                    val preview = state.replyPreviews[comment.id]
                                    if (state.parent == null && comment.replyCount > 0 && target.kind == CommentKind.COMIC) {
                                        LaunchedEffect(comment.id, state.previewGeneration) {
                                            onLoadReplyPreview(comment)
                                        }
                                    }
                                    CommentCard(
                                        comment = comment,
                                        currentTime = currentTime,
                                        previews = preview?.comments.orEmpty(),
                                        previewTotal = preview?.total,
                                        threadUsername = state.parent?.username,
                                        onReport = onReport,
                                        onClick = if (state.parent == null && target.kind == CommentKind.COMIC &&
                                            (preview?.total ?: comment.replyCount) > 0
                                        ) ({
                                            onShowReplies(comment)
                                        }) else null,
                                        onReply = if (target.kind == CommentKind.COMIC && state.postingAllowed) ({
                                            onCompose(comment)
                                        }) else null,
                                        onShowReplies = if (state.parent == null && target.kind == CommentKind.COMIC && preview?.comments?.isNotEmpty() == true) ({
                                            onShowReplies(comment)
                                        }) else null,
                                        enabled = !state.posting
                                    )
                                    CommentDivider()
                                }
                                // 固定页脚不能成为首屏或分页加载的滚动锚点。
                                item(key = "footer:${state.feed.items.size}") {
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        when {
                                            state.feed.refreshing -> Unit
                                            state.feed.loading -> LoadingIndicator()

                                            state.feed.error != null -> {
                                                Text(
                                                    state.feed.error,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                                TextButton(
                                                    onClick = onRetry,
                                                    enabled = !state.posting
                                                ) {
                                                    Text(
                                                        "重试",
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }

                                            state.feed.items.isEmpty() -> Text(
                                                if (state.parent == null) "还没有评论" else "暂无可见回复",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                        }
                                        state.message?.let {
                                            Text(
                                                it,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = fabVisible && !composerOpen && !state.posting && state.postingAllowed && state.target == target,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        enter = slideInVertically(
                            initialOffsetY = { it + fabBottomInset },
                            animationSpec = tween(250, easing = LinearOutSlowInEasing)
                        ),
                        exit = slideOutVertically(
                            targetOffsetY = { it + fabBottomInset },
                            animationSpec = tween(200, easing = FastOutLinearInEasing)
                        )
                    ) {
                        FloatingActionButton(
                            onClick = { onCompose(null) },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Reply,
                                if (state.parent == null) "写评论" else "回复评论"
                            )
                        }
                    }
                }
            }
        }
        if (composerOpen) {
            // 关闭输入面板仅收起键盘；草稿和回复对象保留在 ViewModel 中。
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
                    .clickable(enabled = !state.posting, onClick = onCloseComposer)
            )
            val composerModifier = if (wide) Modifier
                .padding(horizontal = 16.dp)
                .width(480.dp) else Modifier.fillMaxWidth()
            Surface(
                modifier = Modifier
                    .align(panelAlignment)
                    .imePadding()
                    .then(composerModifier)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                CommentComposer(
                    state = state,
                    onDraftChange = onDraftChange,
                    onPost = onPost,
                    onClose = onCloseComposer,
                    onLogin = onLogin
                )
            }
        }
    }
}

@Composable
private fun CommentDivider() {
    HorizontalDivider(
        Modifier.padding(start = 68.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
internal fun CommentCard(
    comment: MangaComment,
    currentTime: ZonedDateTime,
    previews: List<MangaComment> = emptyList(),
    previewTotal: Int? = null,
    threadUsername: String? = null,
    onReply: (() -> Unit)? = null,
    onShowReplies: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onReport: (MangaComment) -> Unit = {},
    enabled: Boolean = true
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var menuComment by remember(comment.id) { mutableStateOf<MangaComment?>(null) }
    val longPress: (MangaComment) -> Unit = { menuComment = it }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = enabled, onClick = { onClick?.invoke() },
                    onLongClickLabel = "复制或举报",
                    onLongClick = { longPress(comment) }
                )
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (comment.avatar.isNullOrBlank()) {
                Icon(
                    Icons.Default.AccountCircle,
                    null,
                    Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                PreviewAwareAsyncImage(
                    model = comment.avatar, contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape), contentScale = ContentScale.Crop
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    comment.username.ifBlank { "匿名用户" },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    CommentTime.format(comment.createdAt, currentTime),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    commentText(comment, showUsername = false, threadUsername = threadUsername),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (onReply != null) Row(
                    Modifier
                        .heightIn(min = 20.dp)
                        .combinedClickable(
                            enabled = enabled, role = Role.Button,
                            onClick = onReply, onLongClick = { longPress(comment) }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Reply,
                        null,
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "回复",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onShowReplies != null) ReplyPreviewContainer(
                    previews = previews, total = previewTotal, threadUsername = comment.username,
                    enabled = enabled, onClick = onShowReplies,
                    onLongPress = longPress, onContainerLongPress = { longPress(comment) }
                )
            }
        }
        DropdownMenu(expanded = menuComment != null, onDismissRequest = { menuComment = null }) {
            DropdownMenuItem(
                text = { Text("复制", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    menuComment?.let { selected ->
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(ClipData.newPlainText("评论", selected.content))
                            )
                        }
                    }
                    menuComment = null
                }
            )
            DropdownMenuItem(
                text = { Text("举报", color = MaterialTheme.colorScheme.onSurface) },
                onClick = { menuComment?.let(onReport); menuComment = null }
            )
        }
    }
}

/** 在首帧按实际可用宽度测量截断，静态 Preview 不依赖排版回调后的重组。 */
@Composable
private fun ReplyPreviewContainer(
    previews: List<MangaComment>,
    total: Int?,
    threadUsername: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongPress: (MangaComment) -> Unit,
    onContainerLongPress: () -> Unit
) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodyMedium
    val texts = previews.take(2)
        .map { commentText(it, showUsername = true, threadUsername = threadUsername) }
    Surface(
        modifier = Modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            val textConstraints = Constraints(maxWidth = constraints.maxWidth)
            val truncated = texts.any { text ->
                measurer.measure(
                    text = text, style = style, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, constraints = textConstraints
                ).hasVisualOverflow
            }
            val showCount = total == null || total > texts.size || truncated
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                texts.forEachIndexed { index, text ->
                    Text(
                        text, modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                enabled = enabled, onClick = onClick,
                                onLongClick = { onLongPress(previews[index]) },
                                onLongClickLabel = "复制或举报"
                            ), color = MaterialTheme.colorScheme.onSurface,
                        style = style, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                if (showCount) Row(
                    Modifier
                        .heightIn(min = 24.dp)
                        .combinedClickable(
                            enabled = enabled, onClick = onClick, onLongClick = onContainerLongPress
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        total?.let { "查看 $it 条回复" } ?: "查看回复",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Icon(
                        Icons.Default.ChevronRight, null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/** 回复预览使用连续富文本，不引入头像、时间、表格列或内部分隔线。 */
@Composable
private fun commentText(
    comment: MangaComment,
    showUsername: Boolean,
    threadUsername: String? = null
) = buildAnnotatedString {
    val nameStyle =
        SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
    if (showUsername) withStyle(nameStyle) { append(comment.username.ifBlank { "匿名用户" }) }
    val parentUsername = comment.parentUsername?.takeIf { it.isNotBlank() && it != threadUsername }
    parentUsername?.let { parent ->
        append(if (showUsername) " 回复 " else "回复 ")
        withStyle(nameStyle) { append("@$parent") }
    }
    if (showUsername || parentUsername != null) append("：")
    append(comment.content)
}

@Composable
private fun CommentComposer(
    state: CommentsUiState,
    onDraftChange: (String) -> Unit,
    onPost: () -> Unit,
    onClose: () -> Unit,
    onLogin: (() -> Unit)?
) {
    val reply = state.replyTo ?: state.parent
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val inspectionMode = LocalInspectionMode.current
    LaunchedEffect(state.loggedIn, state.postingAllowed, inspectionMode) {
        if (!inspectionMode && state.loggedIn && state.postingAllowed) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (reply == null) "写评论" else "回复 ${reply.username.ifBlank { "匿名用户" }}",
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onClose, enabled = !state.posting) {
                Icon(
                    Icons.Default.Close,
                    "收起输入框，保留草稿"
                )
            }
        }
        when {
            !state.postingAllowed -> Text(
                "这部漫画已关闭评论发表",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            !state.loggedIn -> {
                Text(
                    if (onLogin == null) "请先在“我的”登录后再发表评论" else "登录后可以发表评论",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onLogin != null) TextButton(onClick = onLogin) {
                    Text(
                        "去登录",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            else -> {
                TextField(
                    value = state.draft, onValueChange = onDraftChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    enabled = !state.posting,
                    minLines = 3, maxLines = 4,
                    shape = MaterialTheme.shapes.medium,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    placeholder = {
                        Text(
                            "说说你的看法…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    isError = CommentText.length(state.draft) > CommentText.MAX_LENGTH,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "3–200 字",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${CommentText.length(state.draft)} / 200",
                        color = if (CommentText.length(state.draft) > CommentText.MAX_LENGTH) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = onPost,
                        enabled = !state.posting && CommentText.isValid(state.draft)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (state.posting) "正在发表…" else "发表",
                            color = if (!state.posting && CommentText.isValid(state.draft)) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(
                                alpha = 0.38f
                            )
                        )
                    }
                }
            }
        }
        state.message?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
