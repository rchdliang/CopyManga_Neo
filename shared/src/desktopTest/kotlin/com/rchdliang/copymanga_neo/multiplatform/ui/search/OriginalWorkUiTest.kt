package com.rchdliang.copymanga_neo.multiplatform.ui.search

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BookDetailAuthor
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BookDetailHeader
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class OriginalWorkUiTest {
    @Test fun originalWorkLinkUsesIdentifierWhileLegacyNameRemainsPlainText() = runSkikoComposeUiTest {
        var selected: ComicOriginalWork? = null
        setContent {
            MaterialTheme {
                BookDetailHeader("作品", null, listOf(BookDetailAuthor("作者")), "连载中", "2026-10-19",
                    originalWorks = listOf(ComicOriginalWork("Love Live!", "LoveLive"), ComicOriginalWork("旧名称")),
                    onOriginalWork = { selected = it })
            }
        }
        val text = onNodeWithText("原作：Love Live!、旧名称")
        val layouts = mutableListOf<TextLayoutResult>()
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        text.performTouchInput { click(layouts.single().getBoundingBox(5).center) }
        assertEquals(ComicOriginalWork("Love Live!", "LoveLive"), selected)
    }

    @Test fun originalListHasOwnHeaderAndRestoresScrollAfterOpeningComic() = runSkikoComposeUiTest(size = Size(640f, 640f)) {
        var detail by mutableStateOf(false)
        var backed = false
        val state = SearchUiState(compactLayout = true, total = 40,
            searchResults = (0..39).map { ComicSummary("漫画 $it", "book$it", null, null, null, null, null, null, null) })
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    val holder = rememberSaveableStateHolder()
                    if (detail) TextButton(onClick = { detail = false }) {
                        Text("返回列表", color = MaterialTheme.colorScheme.primary)
                    } else holder.SaveableStateProvider("original") {
                        OriginalWorkContent("Love Live!", "LoveLive", state,
                            { detail = true }, { backed = true }, {}, {}, {}, {})
                    }
                }
            }
        }
        onNodeWithText("Love Live!").assertIsDisplayed()
        onNodeWithText("共 40 部漫画").assertIsDisplayed()
        onNodeWithText("搜索结果").assertDoesNotExist()
        onNodeWithText("搜索").assertDoesNotExist()
        onAllNodes(hasSetTextAction()).assertCountEquals(0)
        onNodeWithContentDescription("分享").assertDoesNotExist()
        onNodeWithContentDescription("返回").performClick()
        assertTrue(backed)
        onNode(hasScrollAction()).performScrollToNode(hasText("漫画 30"))
        val before = onNodeWithText("漫画 30").getUnclippedBoundsInRoot().top
        onNodeWithText("漫画 30").performClick()
        onNodeWithText("返回列表").performClick()
        onNodeWithText("漫画 30").assertIsDisplayed()
        assertEquals(before, onNodeWithText("漫画 30").getUnclippedBoundsInRoot().top)
    }
}
