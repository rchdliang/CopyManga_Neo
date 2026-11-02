package com.rchdliang.copymanga_neo.multiplatform.ui.comments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.CommentKind
import com.rchdliang.copymanga_neo.domain.model.CommentTarget
import com.rchdliang.copymanga_neo.domain.model.MangaComment
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class CommentsStaggeredLayoutUiTest {
    @Test fun columnsUseContentHeightAndScrollTogetherWithFullWidthFooter() = runSkikoComposeUiTest(size = Size(1100f, 750f)) {
        lateinit var list: LazyStaggeredGridState
        var width by mutableStateOf(1100.dp)
        val target = CommentTarget(CommentKind.CHAPTER, "fixture")
        val comments = (0..19).map { index ->
            MangaComment("$index", "读者 $index", null,
                if (index == 0) (1..8).joinToString("\n") { "这是长评论的第 $it 行。" } else "这是一条简短评论。",
                "2026-11-01")
        }
        val state = CommentsUiState(target = target, feed = CommentFeed(comments, comments.size))
        setContent {
            list = rememberLazyStaggeredGridState()
            CompositionLocalProvider(LocalInspectionMode provides true) {
                CopyManga_NeoTheme(darkTheme = false, dynamicColor = false) {
                    Box(Modifier.width(width).fillMaxHeight()) {
                        CommentsContent(target, "测试章节", state, CommentMoment.now(), list, false, false,
                            onCompose = {}, onCloseComposer = {}, onDismiss = {}, onBackToComments = {},
                            onRefresh = {}, onRetry = {}, onRetryPermission = {}, onLoadReplyPreview = {}, onShowReplies = {},
                            onDraftChange = {}, onPost = {}, embedded = true, modifier = Modifier.testTag("comments"))
                    }
                }
            }
        }
        waitForIdle()
        fun item(id: Int) = list.layoutInfo.visibleItemsInfo.single { it.key == "comment:$id" }
        val long = item(0)
        val short = item(1)
        val next = item(2)
        assertEquals(long.offset.y, short.offset.y)
        assertTrue(long.size.height > short.size.height)
        assertEquals(short.offset.x, next.offset.x)
        assertEquals(short.offset.y + short.size.height, next.offset.y, "The shorter column must not wait for the longer comment")
        assertTrue(next.offset.y < long.offset.y + long.size.height)
        for (lane in list.layoutInfo.visibleItemsInfo.filter { it.key.toString().startsWith("comment:") }.groupBy { it.offset.x }.values) {
            lane.sortedBy { it.offset.y }.zipWithNext().forEach { (a, b) ->
                assertEquals(a.offset.y + a.size.height, b.offset.y, "Each column must keep the same card padding without row filler")
            }
        }
        val pixels = onRoot().captureToImage().toPixelMap()
        val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) image.setRGB(x, y, pixels[x, y].toArgb())
        val file = File("build/reports/comments/staggered-comments.png")
        file.parentFile.mkdirs(); ImageIO.write(image, "png", file)

        val beforeLeft = item(0).offset.y
        val beforeRight = item(1).offset.y
        onNodeWithTag("comments").performMouseInput { moveTo(Offset(550f, 350f)); scroll(1f) }
        waitForIdle()
        val leftDelta = item(0).offset.y - beforeLeft
        val rightDelta = item(1).offset.y - beforeRight
        assertTrue(leftDelta < 0)
        assertEquals(leftDelta, rightDelta, "Both columns must move by the same scroll offset")

        runOnIdle { list.requestScrollToItem(comments.size) }; waitForIdle()
        val footer = list.layoutInfo.visibleItemsInfo.single { it.key == "footer:${comments.size}" }
        assertEquals(0, footer.offset.x)
        assertTrue(footer.size.width >= 1000)
        assertTrue(list.layoutInfo.visibleItemsInfo.filter { it.key != footer.key }.all { it.offset.y + it.size.height <= footer.offset.y })
        assertFalse(list.canScrollForward)

        runOnIdle { width = 480.dp; list.requestScrollToItem(0) }; waitForIdle()
        assertEquals(item(0).offset.x, item(1).offset.x)
        assertEquals(item(0).offset.y + item(0).size.height, item(1).offset.y)
        assertEquals(comments.size + 1, list.layoutInfo.totalItemsCount)
    }

    @Test fun composerKeepsDraftAndDisablesPostingUntilPermissionIsConfirmed() = runSkikoComposeUiTest(size = Size(480f, 800f)) {
        val target = CommentTarget(CommentKind.CHAPTER, "fixture")
        var state by mutableStateOf(CommentsUiState(
            target = target, loggedIn = true, draft = "保留章节评论草稿",
            postingAllowed = false, checkingPermission = true
        ))
        var retries = 0
        var posts = 0
        setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                CopyManga_NeoTheme(darkTheme = false, dynamicColor = false) {
                    CommentsContent(target, "测试章节", state, CommentMoment.now(), rememberLazyStaggeredGridState(), false, false,
                        onCompose = {}, onCloseComposer = {}, onDismiss = {}, onBackToComments = {},
                        onRefresh = {}, onRetry = {}, onRetryPermission = { retries++ }, onLoadReplyPreview = {}, onShowReplies = {},
                        onDraftChange = { state = state.copy(draft = it) }, onPost = { posts++ }, embedded = true)
                }
            }
        }
        onNodeWithText("正在确认评论权限…").assertDoesNotExist()
        onNodeWithText("保留章节评论草稿").assertIsNotEnabled()
        onNodeWithContentDescription("发表评论").assertIsNotEnabled()
        onNodeWithText("保留章节评论草稿").assertExists()
        onNodeWithText("下载评论").assertDoesNotExist()

        runOnIdle { state = state.copy(checkingPermission = false, permissionError = "无法确认评论权限，请重试") }
        onNodeWithText("无法确认评论权限，请重试").assertDoesNotExist()
        onNodeWithText("保留章节评论草稿").assertIsNotEnabled()
        onNodeWithText("重试").performClick()
        runOnIdle { assertEquals(1, retries); assertEquals(0, posts) }

        runOnIdle { state = state.copy(permissionError = null) }
        onNodeWithText("这部漫画已关闭评论发表").assertDoesNotExist()
        onNodeWithContentDescription("发表评论").assertIsNotEnabled()
        runOnIdle { state = state.copy(postingAllowed = true) }
        onNode(hasSetTextAction()).assertIsEnabled()
        onNodeWithText("说说你的看法…（3–200 字）").assertDoesNotExist()
        onNodeWithContentDescription("发表评论").assertIsEnabled().performClick()
        runOnIdle { assertEquals(1, posts); assertEquals("保留章节评论草稿", state.draft); state = state.copy(posting = true) }
        onNodeWithContentDescription("正在发表").assertIsNotEnabled()
        runOnIdle { state = state.copy(posting = false) }

        onNode(hasSetTextAction()).performTextReplacement("字".repeat(201))
        onNodeWithText("201 / 200").assertExists()
        onNodeWithContentDescription("发表评论").assertIsNotEnabled()
        runOnIdle { state = state.copy(loggedIn = false, draft = "保留章节评论草稿") }
        onNodeWithText("保留章节评论草稿").assertIsNotEnabled()
        onNodeWithText("请先在“我的”登录后再发表评论").assertDoesNotExist()
        onNodeWithContentDescription("发表评论").assertIsNotEnabled()

        runOnIdle { state = state.copy(draft = "") }
        onNodeWithText("请先在“我的”登录后再发表评论").assertExists()
        onNodeWithText("0 / 200").assertExists()
        runOnIdle { state = state.copy(loggedIn = true, postingAllowed = false, checkingPermission = true) }
        onAllNodesWithText("正在确认评论权限…").assertCountEquals(1)
        runOnIdle { state = state.copy(checkingPermission = false, permissionError = "无法确认评论权限，请重试") }
        onAllNodesWithText("无法确认评论权限，请重试").assertCountEquals(1)
        runOnIdle { state = state.copy(permissionError = null) }
        onAllNodesWithText("这部漫画已关闭评论发表").assertCountEquals(1)
        runOnIdle { state = state.copy(postingAllowed = true) }
        onNodeWithText("说说你的看法…（3–200 字）").assertExists()
        onNodeWithContentDescription("发表评论").assertIsNotEnabled()
    }


    @Test fun offlineCommentsShowPartialSnapshotAndOnlyOfferExplicitOnlineRefresh() = runSkikoComposeUiTest(size = Size(480f, 800f)) {
        val target = CommentTarget(CommentKind.CHAPTER, "offline")
        val comment = MangaComment("local", "读者", "https://invalid.test/avatar.png", "离线评论示例", "2026-11-03")
        val state = CommentsUiState(target = target, loggedIn = true, postingAllowed = false,
            feed = CommentFeed(items = listOf(comment), total = 10, downloaded = true, localOnly = true))
        var refreshes = 0
        setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                CopyManga_NeoTheme(darkTheme = false, dynamicColor = false) {
                    CommentsContent(target, "离线章节", state, CommentMoment.now(), rememberLazyStaggeredGridState(), false, false,
                        onCompose = {}, onCloseComposer = {}, onDismiss = {}, onBackToComments = {},
                        onRefresh = { refreshes++ }, onRetry = {}, onRetryPermission = {}, onLoadReplyPreview = {}, onShowReplies = {},
                        onDraftChange = {}, onPost = {}, embedded = true)
                }
            }
        }
        onNodeWithText("离线章节 · 离线评论").assertExists()
        onNodeWithText("离线评论未完整保存，当前可查看 1 条").assertExists()
        onNodeWithText("正在查看离线评论，联网刷新后可发表评论").assertExists()
        onNodeWithContentDescription("发表评论").assertIsNotEnabled()
        onNodeWithText("0 / 200").assertExists()
        onNodeWithText("下载评论").assertDoesNotExist()
        onNodeWithText("联网查看最新评论").performClick()
        runOnIdle { assertEquals(1, refreshes) }
        onNodeWithText("离线评论示例").performTouchInput { longClick() }
        onNodeWithText("复制").assertExists()
        onNodeWithText("举报").assertDoesNotExist()
    }

}
