package com.rchdliang.copymanga_neo.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rchdliang.copymanga_neo.R
import com.rchdliang.copymanga_neo.domain.model.CommentKind
import com.rchdliang.copymanga_neo.domain.model.CommentReplyPreview
import com.rchdliang.copymanga_neo.domain.model.CommentTarget
import com.rchdliang.copymanga_neo.domain.model.MangaComment
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme
import java.time.ZonedDateTime

private val previewTarget = CommentTarget(CommentKind.COMIC, "preview-comic")
private val previewNow = ZonedDateTime.parse("2026-10-01T16:15:00+08:00")
private val previewAvatar =
    "android.resource://com.rchdliang.copymanga_neo/${R.drawable.preview_comic_cover}"

/** 固定时间、本地头像和静态状态；不创建 Hilt 对象，也不请求接口。 */
private fun commentPreviewState(): CommentsUiState {
    val single = MangaComment(
        "single",
        "結城理",
        previewAvatar,
        "和大变没区别",
        "2026-10-01 16:05:00",
        replyCount = 1
    )
    val multiple = MangaComment(
        "multiple",
        "woshilaoxiao",
        previewAvatar,
        "这漫画是日更的？",
        "2026-09-30 09:00:00",
        replyCount = 5
    )
    val hidden = MangaComment(
        "hidden",
        "哥的冷酷，零下八度",
        previewAvatar,
        "突然成为公寓管理员",
        "2026-09-28 13:08:00",
        replyCount = 0
    )
    val old =
        MangaComment("old", "四叶海月", null, "画风很舒服，先收藏，等更新。", "2024-03-20 13:08:00")
    val shortReply =
        MangaComment("short", "yaname486", null, "网址里面有app下载", "2026-10-01 16:08:00")
    val replies = listOf(
        MangaComment(
            "r1",
            "woshilaoxiao",
            null,
            "那确实相当慢了",
            "2026-10-01 13:08:00",
            parentUsername = "路人二十五"
        ),
        MangaComment(
            "r2",
            "路人二十五",
            null,
            "当然不是了，因为早就更新了这么多话，这边搬运太慢了。我还是很喜欢这部漫画的画风，希望后面能够继续保持这样的节奏，慢慢更新也没有关系。",
            "2026-09-30 09:00:00"
        )
    )
    return CommentsUiState(
        target = previewTarget,
        feed = CommentFeed(listOf(single, multiple, hidden, old), total = 137),
        loggedIn = true,
        replyPreviews = mapOf(
            single.id to CommentReplyPreview(listOf(shortReply), total = 1),
            multiple.id to CommentReplyPreview(replies, total = 5),
            hidden.id to CommentReplyPreview(emptyList(), total = 0)
        )
    )
}

@Composable
private fun CommentsPreview(
    dark: Boolean = false,
    state: CommentsUiState = remember { commentPreviewState() },
    composerOpen: Boolean = false,
    embedded: Boolean = false
) {
    val target =
        if (embedded) CommentTarget(CommentKind.CHAPTER, "preview-chapter") else previewTarget
    CopyManga_NeoTheme(darkTheme = dark, dynamicColor = false) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            CommentsContent(
                target = target,
                title = "突然成为公寓管理员",
                state = state.copy(target = target),
                currentTime = previewNow,
                listState = rememberLazyListState(),
                fabVisible = true,
                composerOpen = composerOpen,
                onCompose = {},
                onCloseComposer = {},
                onDismiss = {},
                onBackToComments = {},
                onRefresh = {},
                onRetry = {},
                onRetryPermission = {},
                onLoadReplyPreview = {},
                onShowReplies = {},
                onDraftChange = {},
                onPost = {},
                embedded = embedded,
            )
        }
    }
}

@Preview(name = "评论区 · 手机", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun CommentsPhonePreview() = CommentsPreview()

@Preview(name = "章末评论 · 手机", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun ChapterCommentsPhonePreview() = CommentsPreview(embedded = true)

@Preview(name = "章末评论 · 平板横屏双列", widthDp = 1200, heightDp = 800, showBackground = true)
@Composable
private fun ChapterCommentsTabletPreview() = CommentsPreview(embedded = true)

@Preview(name = "章末评论 · 深色", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun ChapterCommentsDarkPreview() = CommentsPreview(dark = true, embedded = true)

@Preview(name = "评论区 · 深色", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun CommentsDarkPreview() = CommentsPreview(dark = true)

@Preview(name = "评论区 · 平板侧栏", widthDp = 1200, heightDp = 800, showBackground = true)
@Composable
private fun CommentsTabletPreview() = CommentsPreview()

@Preview(name = "评论区 · 单行回复", widthDp = 412, heightDp = 640, showBackground = true)
@Composable
private fun CommentsSingleReplyPreview() {
    val base = remember { commentPreviewState() }
    CommentsPreview(state = base.copy(feed = CommentFeed(base.feed.items.take(1), total = 1)))
}

@Preview(name = "评论区 · 空列表", widthDp = 412, heightDp = 640, showBackground = true)
@Composable
private fun CommentsEmptyPreview() =
    CommentsPreview(state = CommentsUiState(target = previewTarget, loggedIn = true))

@Preview(name = "评论区 · 加载中", widthDp = 412, heightDp = 640, showBackground = true)
@Composable
private fun CommentsLoadingPreview() = CommentsPreview(
    state = CommentsUiState(
        target = previewTarget,
        feed = CommentFeed(loading = true),
        loggedIn = true
    )
)

@Preview(name = "评论区 · 已关闭发表", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun CommentsClosedPreview() {
    CommentsPreview(state = remember { commentPreviewState().copy(postingAllowed = false) })
}

@Preview(name = "评论区 · 输入面板", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun CommentsComposerPreview() {
    CommentsPreview(
        state = remember { commentPreviewState().copy(draft = "这部的日常互动很可爱，期待更新！") },
        composerOpen = true
    )
}
