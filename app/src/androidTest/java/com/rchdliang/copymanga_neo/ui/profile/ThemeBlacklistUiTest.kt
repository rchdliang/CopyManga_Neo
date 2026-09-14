package com.rchdliang.copymanga_neo.ui.profile

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rchdliang.copymanga_neo.domain.model.Theme
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ThemeBlacklistUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val themes =
        listOf(Theme("校园", "xiaoyuan"), Theme("恐怖", "kongbu"), Theme("科幻", "kehuan"))

    @Test
    fun blacklistRemovalRequiresConfirmation() {
        val state =
            mutableStateOf(ProfileUiState(isBlacklistLoading = false, blockedThemes = themes))
        compose.setContent {
            CopyManga_NeoTheme {
                ThemeBlacklistPage(
                    uiState = state.value,
                    onRemove = { theme ->
                        state.value =
                            state.value.copy(blockedThemes = state.value.blockedThemes - theme)
                    },
                    onNavigateBack = {}
                )
            }
        }
        compose.onNodeWithText("校园").assertIsDisplayed()
        saveScreenshot("blacklist")
        compose.onNodeWithText("校园").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("校园").assertIsDisplayed()
        compose.onNodeWithText("校园").performClick()
        compose.onNode(hasText("移除") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("校园").assertDoesNotExist()
        compose.onNodeWithText("恐怖").assertIsDisplayed()
    }

    @Test
    fun blacklistManagementIsAvailableWithoutLogin() {
        var navigated = false
        compose.setContent {
            CopyManga_NeoTheme {
                ProfileMainPage(
                    uiState = ProfileUiState(isBlacklistLoading = false, blockedThemes = themes),
                    onRefresh = {},
                    onLogout = {},
                    onLoginClick = {},
                    savedPasswordProvider = { null },
                    onLogin = { _, _, _, _ -> },
                    onDismissLogin = {},
                    onExportCredential = {},
                    onImportCredential = {},
                    onNavigateToHistory = {},
                    onNavigateToSettings = {},
                    onNavigateToBlacklist = { navigated = true }
                )
            }
        }
        compose.onNodeWithText("标签黑名单").assertIsDisplayed()
        saveScreenshot("profile")
        compose.onNodeWithText("标签黑名单").performClick()
        compose.runOnIdle { assertTrue(navigated) }
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
