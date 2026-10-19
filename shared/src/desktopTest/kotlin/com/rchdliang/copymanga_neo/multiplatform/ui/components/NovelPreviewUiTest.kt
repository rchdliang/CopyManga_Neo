package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NovelPreviewUiTest {
    private val seed = NovelBook("same-path", "轻小说标题", "", listOf(NovelTag("作者", "author")))
    private val detail = seed.copy(brief = "轻小说的完整简介", themes = listOf(NovelTag("小说题材", "tag")), popular = 1200, updated = "2026-10-19")

    @Test fun longPressSharesOverlayAndNeverRequestsComicMetadata() = runSkikoComposeUiTest(size = Size(800f, 500f)) {
        var preview by mutableStateOf(NovelPreviewUiState())
        var opened = false
        val comicRequests = mutableListOf<String>()
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    BookPresentationHost(ComicPresentationUiState(ready = true),
                        { paths, _ -> comicRequests += paths }, {}, preview,
                        { book -> preview = preview.copy(book = book?.let { detail }) }, {}) {
                        Box(Modifier.padding(24.dp)) {
                            NovelTransitionCard(seed, "home", { opened = true }, Modifier.width(144.dp))
                        }
                    }
                }
            }
        }
        onNodeWithText("轻小说标题").performTouchInput { longClick() }
        onNodeWithText("轻小说的完整简介").assertIsDisplayed()
        onNodeWithText("小说题材").assertIsDisplayed()
        assertTrue(comicRequests.isEmpty())
        onRoot().performTouchInput { click(Offset(760f, 470f)) }
        onNodeWithText("轻小说的完整简介").assertDoesNotExist()
        assertFalse(opened)
        onNodeWithText("轻小说标题").performTouchInput { longClick() }
        onNodeWithText("轻小说的完整简介").performClick()
        assertTrue(opened)
        onNodeWithText("轻小说的完整简介").assertDoesNotExist()
        assertTrue(comicRequests.isEmpty())
    }

    @Test fun failedNovelPreviewRetriesWithoutOpeningTheBook() = runSkikoComposeUiTest(size = Size(800f, 500f)) {
        var preview by mutableStateOf(NovelPreviewUiState())
        var retries = 0
        var opened = false
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    BookPresentationHost(ComicPresentationUiState(ready = true), { _, _ -> error("Comic request") }, {},
                        preview, { book -> preview = preview.copy(book = book, error = if (book == null) null else "offline") },
                        { retries++; preview = preview.copy(book = detail, error = null) }) {
                        Box(Modifier.padding(24.dp)) {
                            NovelTransitionCard(seed, "browse", { opened = true }, Modifier.width(144.dp))
                        }
                    }
                }
            }
        }
        onNodeWithText("轻小说标题").performTouchInput { longClick() }
        onNodeWithText("重试").performClick()
        assertEquals(1, retries)
        assertFalse(opened)
        onNodeWithText("轻小说的完整简介").assertIsDisplayed()
    }

    @Test fun bookshelfLongPressKeepsSelectionInsteadOfOpeningPreview() = runSkikoComposeUiTest {
        var selections = 0
        var previews = 0
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    BookPresentationHost(ComicPresentationUiState(ready = true), { _, _ -> error("Comic request") }, {},
                        NovelPreviewUiState(), { if (it != null) previews++ }, {}) {
                        NovelTransitionCard(seed, "bookshelf", {}, Modifier.width(144.dp),
                            onLongClick = { selections++ })
                    }
                }
            }
        }
        onNodeWithText("轻小说标题").performTouchInput { longClick() }
        assertEquals(1, selections)
        assertEquals(0, previews)
    }
}
