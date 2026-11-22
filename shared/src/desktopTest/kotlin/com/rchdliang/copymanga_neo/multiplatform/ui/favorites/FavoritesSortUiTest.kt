package com.rchdliang.copymanga_neo.multiplatform.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import coil3.ImageLoader
import coil3.PlatformContext
import com.rchdliang.copymanga_neo.domain.model.FavoriteComic
import com.rchdliang.copymanga_neo.domain.model.FavoriteItem
import com.rchdliang.copymanga_neo.domain.model.FavoritesSort
import com.rchdliang.copymanga_neo.multiplatform.ui.components.*
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FavoritesSortUiTest {
    private val platform = object : UiPlatform {
        override val backDispatcher = UiBackDispatcher()
        override suspend fun shareText(title: String, text: String) = Result.success(Unit)
        override fun formatDate(epochMilliseconds: Long) = ""
    }

    @Test fun directionToggleKeepsFieldAndFieldChangeKeepsDirection() = runSkikoComposeUiTest(size = Size(320f, 240f)) {
        var sort by mutableStateOf(FavoritesSort.ADDED)
        setContent { CopyManga_NeoTheme { FavoritesSortControl(sort, true) { sort = it } } }
        onNodeWithText("降序").performClick()
        runOnIdle { assertEquals(FavoritesSort.ADDED_ASC, sort) }
        onNodeWithContentDescription("排序方式").performClick()
        onNodeWithText("更新时间").performClick()
        runOnIdle { assertEquals(FavoritesSort.UPDATED_ASC, sort) }
        onNodeWithText("升序").performClick()
        runOnIdle { assertEquals(FavoritesSort.UPDATED, sort) }
        onNodeWithContentDescription("排序方式").performClick()
        onNodeWithText("阅读时间").performClick()
        runOnIdle { assertEquals(FavoritesSort.READ, sort) }
    }

    @Test fun compactControlsRenderInBothThemesAndDisappearWhenLoggedOut() = runSkikoComposeUiTest(size = Size(360f, 640f)) {
        var loggedIn by mutableStateOf(true)
        var dark by mutableStateOf(false)
        var sort by mutableStateOf(FavoritesSort.ADDED)
        setContent {
            CompositionLocalProvider(LocalUiPlatform provides platform) {
                CopyManga_NeoTheme(darkTheme = dark) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        FavoritesContent(
                            uiState = FavoritesUiState(isLoggedIn = loggedIn, sort = sort),
                            onComicClick = {}, onNavigateToProfile = {}, onRefresh = {},
                            onExitSelectionMode = {}, onSelectAll = {}, onInvertSelection = {},
                            onToggleSelection = {}, onEnterSelectionMode = {}, onRemoveSelected = {},
                            onSortSelected = { sort = it }
                        )
                    }
                }
            }
        }
        onNodeWithContentDescription("排序方式").assertIsDisplayed()
        onNodeWithText("降序").assertIsDisplayed()
        onNodeWithText("最新在前").assertDoesNotExist()
        saveRender("favorites-sort-light")
        runOnIdle { dark = true }
        onNodeWithText("降序").performClick()
        onNodeWithText("升序").assertIsDisplayed()
        saveRender("favorites-sort-dark")
        onNodeWithContentDescription("排序方式").performClick()
        onNodeWithText("阅读时间").assertIsDisplayed()
        runOnIdle { loggedIn = false }
        onNodeWithContentDescription("排序方式").assertDoesNotExist()
        onNodeWithText("升序").assertDoesNotExist()
        onNodeWithText("阅读时间").assertDoesNotExist()
        saveRender("favorites-sort-logged-out")
    }

    private val favorites = List(60) { index ->
        val comic = FavoriteComic(
            uuid = "comic-$index", bDisplay = true, name = "漫画 $index", pathWord = "comic-$index",
            author = null, theme = null, cover = null, status = 0, popular = null,
            datetimeUpdated = null, lastChapterId = "", lastChapterName = "", browse = null
        )
        FavoriteItem(index, null, false, null, comic)
    }

    private fun ComposeUiTest.setShelfContent(content: @Composable () -> Unit) {
        setContent {
            val loader = remember { ImageLoader.Builder(PlatformContext.INSTANCE).build() }
            DisposableEffect(loader) { onDispose { loader.shutdown() } }
            CompositionLocalProvider(LocalUiPlatform provides platform, LocalAppImageLoader provides loader) {
                CopyManga_NeoTheme { content() }
            }
        }
    }

    @Test fun scrollingHidesSortAndReturningToTopRestoresIt() = runSkikoComposeUiTest(size = Size(360f, 640f)) {
        var refreshes = 0
        setShelfContent {
            FavoritesContent(
                uiState = FavoritesUiState(isLoggedIn = true, favorites = favorites),
                onComicClick = {}, onNavigateToProfile = {}, onRefresh = { refreshes++ },
                onExitSelectionMode = {}, onSelectAll = {}, onInvertSelection = {},
                onToggleSelection = {}, onEnterSelectionMode = {}, onRemoveSelected = {}
            )
        }
        val grid = onNode(hasScrollToIndexAction())
        val sort = onNodeWithContentDescription("排序方式")
        val toTop = onNodeWithContentDescription("返回顶部")
        sort.assertIsDisplayed()
        toTop.assertDoesNotExist()
        val expandedTop = grid.fetchSemanticsNode().boundsInRoot.top
        grid.performTouchInput { swipeUp() }
        sort.assertIsNotDisplayed()
        toTop.assertDoesNotExist()
        val collapsedTop = grid.fetchSemanticsNode().boundsInRoot.top
        onRoot().performTouchInput {
            down(center)
            moveBy(Offset(0f, 26f))
        }
        val partialTop = grid.fetchSemanticsNode().boundsInRoot.top
        assertTrue(partialTop > collapsedTop && partialTop < expandedTop)
        onRoot().performTouchInput { moveBy(Offset(0f, 6f)) }
        assertEquals(partialTop + 6f, grid.fetchSemanticsNode().boundsInRoot.top, 1f)
        onRoot().performTouchInput { moveBy(Offset(0f, -4f)) }
        assertEquals(partialTop + 2f, grid.fetchSemanticsNode().boundsInRoot.top, 1f)
        onRoot().performTouchInput { advanceEventTime(200); up() }
        assertEquals(partialTop + 2f, grid.fetchSemanticsNode().boundsInRoot.top, 1f)
        grid.performTouchInput { swipe(center, center + Offset(0f, 90f), durationMillis = 500) }
        sort.assertIsDisplayed()
        assertEquals(expandedTop, grid.fetchSemanticsNode().boundsInRoot.top)
        toTop.assertIsDisplayed()
        val visibleButtonTop = toTop.fetchSemanticsNode().boundsInRoot.top
        mainClock.autoAdvance = false
        try {
            onRoot().performTouchInput {
                down(center)
                moveBy(Offset(0f, -70f))
            }
            mainClock.advanceTimeBy(112)
            assertTrue(toTop.fetchSemanticsNode().boundsInRoot.top > visibleButtonTop)
            onRoot().performTouchInput { advanceEventTime(200); up() }
        } finally {
            mainClock.autoAdvance = true
        }
        toTop.assertDoesNotExist()
        grid.performTouchInput { swipe(center, center + Offset(0f, 90f), durationMillis = 500) }
        toTop.assertIsDisplayed()
        toTop.performClick()
        onNodeWithText("漫画 0").assertIsDisplayed()
        toTop.assertDoesNotExist()
        runOnIdle { assertEquals(0, refreshes) }
        grid.performTouchInput { swipeDown() }
        runOnIdle { assertEquals(1, refreshes) }
    }

    @Test fun selectingAScrolledCardKeepsItsPositionAndShowsFixedActions() = runSkikoComposeUiTest(size = Size(360f, 640f)) {
        var shelf by mutableStateOf(FavoritesUiState(isLoggedIn = true, favorites = favorites))
        setShelfContent {
            FavoritesContent(
                uiState = shelf,
                onComicClick = {}, onNavigateToProfile = {}, onRefresh = {},
                onEnterSelectionMode = { shelf = shelf.copy(isSelectionMode = true, selectedIds = setOf(it)) },
                onExitSelectionMode = { shelf = shelf.copy(isSelectionMode = false, selectedIds = emptySet()) },
                onSelectAll = { shelf = shelf.copy(selectedIds = favorites.map { it.uuid }.toSet()) },
                onInvertSelection = { shelf = shelf.copy(selectedIds = favorites.map { it.uuid }.toSet() - shelf.selectedIds) },
                onToggleSelection = {}, onRemoveSelected = {}
            )
        }
        onNode(hasScrollToIndexAction()).performScrollToIndex(32)
        val card = onNodeWithText("漫画 32")
        val bounds = card.fetchSemanticsNode().boundsInRoot
        card.performTouchInput { longClick() }
        onNodeWithText("轻小说").assertDoesNotExist()
        onNodeWithText("已选 1 部").assertIsDisplayed()
        onNodeWithText("移除 1 部").assertIsDisplayed()
        assertEquals(bounds, card.fetchSemanticsNode().boundsInRoot)
        val check = onNodeWithContentDescription("已选择", useUnmergedTree = true)
        val checkCenter = check.fetchSemanticsNode().boundsInRoot.center
        assertEquals(bounds.center.x, checkCenter.x, 1f)
        assertEquals(bounds.center.y, checkCenter.y, 1f)
        saveRender("favorites-selection")
        onNodeWithContentDescription("全选").performClick()
        onNodeWithText("已选 60 部").assertIsDisplayed()
        onNodeWithContentDescription("反选").performClick()
        onNodeWithText("移除 0 部").assertIsNotEnabled()
        onNodeWithContentDescription("取消选择").performClick()
        onNodeWithText("轻小说").assertIsDisplayed()
        check.assertDoesNotExist()
        assertEquals(bounds, card.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun emptyShelvesShowOneIndicatorUntilRefreshFinishes() = runSkikoComposeUiTest(size = Size(360f, 640f)) {
        var shelf by mutableStateOf(FavoritesUiState(isLoggedIn = true, isLoading = true))
        setShelfContent {
            FavoritesContent(
                uiState = shelf, onComicClick = {}, onNavigateToProfile = {}, onRefresh = {},
                onExitSelectionMode = {}, onSelectAll = {}, onInvertSelection = {},
                onToggleSelection = {}, onEnterSelectionMode = {}, onRemoveSelected = {}
            )
        }
        val loading = SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate)
        onAllNodes(loading).assertCountEquals(1)
        onNodeWithText("还没有收藏").assertDoesNotExist()
        runOnIdle { shelf = shelf.copy(selectedKind = FavoritesKind.NOVEL, isLoading = false, novelsLoading = true) }
        onAllNodes(loading).assertCountEquals(1)
        runOnIdle { shelf = shelf.copy(novelsLoading = false) }
        onAllNodes(loading).assertCountEquals(0)
        onNodeWithText("还没有收藏").assertIsDisplayed()
    }

    private fun ComposeUiTest.saveRender(name: String) {
        val pixels = onRoot().captureToImage().toPixelMap()
        val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) image.setRGB(x, y, pixels[x, y].toArgb())
        val file = File("build/reports/ui-audit/$name.png")
        file.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
    }
}
