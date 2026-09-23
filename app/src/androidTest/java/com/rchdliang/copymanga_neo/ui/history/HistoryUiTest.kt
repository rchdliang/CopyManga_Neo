package com.rchdliang.copymanga_neo.ui.history

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rchdliang.copymanga_neo.R
import com.rchdliang.copymanga_neo.domain.model.Author
import com.rchdliang.copymanga_neo.domain.model.BrowseHistoryItem
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HistoryUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun historyShowsBothChaptersAndClearRequiresConfirmation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val item = BrowseHistoryItem(
            1, "示例漫画", "example-comic",
            "android.resource://${context.packageName}/${R.drawable.preview_comic_cover}",
            listOf(Author("示例作者", null)), "read", "第01话", "latest", "第68话"
        )
        val state = mutableStateOf(
            HistoryUiState(
                isCheckingLogin = false,
                isLoggedIn = true,
                items = listOf(item)
            )
        )
        var clearCalls = 0
        var clickedComic: String? = null
        compose.setContent {
            CopyManga_NeoTheme {
                HistoryContent(
                    uiState = state.value,
                    onNavigateBack = {},
                    onComicClick = { clickedComic = it },
                    onLoginClick = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onRequestClear = {
                        state.value = state.value.copy(showClearConfirmation = true)
                    },
                    onDismissClear = {
                        state.value = state.value.copy(showClearConfirmation = false)
                    },
                    onConfirmClear = {
                        clearCalls++
                        state.value =
                            state.value.copy(items = emptyList(), showClearConfirmation = false)
                    }
                )
            }
        }
        compose.onNodeWithText("上次读到：第01话").assertIsDisplayed()
        compose.onNodeWithText("最新章节：第68话").assertIsDisplayed()
        compose.onNodeWithText("示例漫画").performClick()
        compose.runOnIdle { assertEquals("example-comic", clickedComic) }
        saveScreenshot("history")
        compose.onNodeWithText("清空全部").performClick()
        saveScreenshot("history-confirm")
        compose.runOnIdle { assertEquals(0, clearCalls) }
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("示例漫画").assertIsDisplayed()
        compose.onNodeWithText("清空全部").performClick()
        compose.onNodeWithText("确认清空").performClick()
        compose.runOnIdle { assertEquals(1, clearCalls) }
        compose.onNodeWithText("暂无浏览记录").assertIsDisplayed()
        compose.onNodeWithText("清空全部").assertIsNotEnabled()
    }

    @Test
    fun expiredSessionOffersLoginAndCannotClear() {
        var loginCalls = 0
        compose.setContent {
            CopyManga_NeoTheme {
                HistoryContent(
                    uiState = HistoryUiState(isCheckingLogin = false),
                    onNavigateBack = {},
                    onComicClick = {},
                    onLoginClick = { loginCalls++ },
                    onRefresh = {},
                    onLoadMore = {},
                    onRequestClear = {},
                    onDismissClear = {},
                    onConfirmClear = {}
                )
            }
        }
        compose.onNodeWithText("清空全部").assertIsNotEnabled()
        compose.onNodeWithText("前往登录").performClick()
        compose.runOnIdle { assertEquals(1, loginCalls) }
    }

    private fun saveScreenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File(
            context.getExternalFilesDir(null),
            "$name-${context.resources.configuration.screenWidthDp}.png"
        )
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
