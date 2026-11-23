package com.rchdliang.copymanga_neo.multiplatform.ui.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.DownloadedComicInfo
import com.rchdliang.copymanga_neo.multiplatform.app.MainNavigationShell
import com.rchdliang.copymanga_neo.multiplatform.app.MainTab
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DownloadsSelectionLayoutTest {
    @Test fun selectionControlsStayVisibleWithAScrolledGridAndBottomSafeArea() =
        runSkikoComposeUiTest(size = Size(402f, 874f)) {
            val books = List(40) { index ->
                DownloadedBookItem.Comic(DownloadedComicInfo(
                    pathWord = "comic-$index", name = "漫画 $index", cover = null,
                    authorNames = "", statusDisplay = "", lastUpdate = null, lastChapterName = null,
                    brief = null, totalChapters = 1, downloadedChapters = 1, downloadAt = 0
                ))
            }
            var shelf by mutableStateOf(DownloadsUiState(isLoading = false, downloadedBooks = books))
            var bottom by mutableStateOf(0.dp)
            setContent {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    MaterialTheme {
                        val insets = WindowInsets(bottom = bottom)
                        MainNavigationShell(MainTab.DOWNLOADS, {}, false, null, 0, insets) {
                            Box(Modifier.fillMaxSize().windowInsetsPadding(insets)) {
                                DownloadsContent(
                                    uiState = shelf, onComicClick = {}, onExitSelectionMode = {},
                                    onSelectAll = {}, onInvertSelection = {}, onPause = {}, onResume = {},
                                    onCancel = {}, onToggleSelection = {}, onDeleteSelected = {},
                                    onEnterSelectionMode = { shelf = shelf.copy(isSelectionMode = true, selectedKeys = setOf(it)) }
                                )
                            }
                        }
                    }
                }
            }
            val grid = onNode(hasScrollToIndexAction())
            grid.performScrollToIndex(21)
            onNodeWithText("漫画 21").performTouchInput { longClick() }
            val selectAll = onNodeWithContentDescription("全选")
            selectAll.assertIsDisplayed()
            grid.performScrollToIndex(35)
            assertEquals(selectAll.getUnclippedBoundsInRoot(), selectAll.getBoundsInRoot())
            val delete = onNodeWithText("删除 1 部")
            val initialBottom = delete.getBoundsInRoot().bottom
            runOnIdle { bottom = 34.dp }
            assertEquals(initialBottom - 34.dp, delete.getBoundsInRoot().bottom)
        }
}
