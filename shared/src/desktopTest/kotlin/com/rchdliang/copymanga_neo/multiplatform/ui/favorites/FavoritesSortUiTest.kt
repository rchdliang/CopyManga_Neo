package com.rchdliang.copymanga_neo.multiplatform.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.rchdliang.copymanga_neo.domain.model.FavoritesSort
import com.rchdliang.copymanga_neo.multiplatform.ui.components.*
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

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

    private fun ComposeUiTest.saveRender(name: String) {
        val pixels = onRoot().captureToImage().toPixelMap()
        val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) image.setRGB(x, y, pixels[x, y].toArgb())
        val file = File("build/reports/ui-audit/$name.png")
        file.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
    }
}
