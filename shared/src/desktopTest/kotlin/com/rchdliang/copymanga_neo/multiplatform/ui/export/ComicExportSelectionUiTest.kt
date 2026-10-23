package com.rchdliang.copymanga_neo.multiplatform.ui.export

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.multiplatform.app.RouteLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class ComicExportSelectionUiTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test fun rangeSelectionFeedsFormatSheetAndCancelledPickerKeepsSelection() = runSkikoComposeUiTest(size = Size(400f, 800f)) {
        val plan = ComicExportPlan(listOf(ComicExportComic("comic", "测试漫画", "", (1..3).map {
            ComicExportChapter("chapter-$it", "第 $it 话", it, "default", listOf(ComicExportPage("$it.jpg", 10, "jpg")))
        })))
        val repository = ComicExportSelectionTest.FakeExports(plan)
        val selection = ComicExportSelectionViewModel(repository, SavedStateHandle(mapOf("comics" to "[\"comic\"]")))
        val export = ComicExportViewModel(repository, SavedStateHandle())
        val owner = RouteLifecycleOwner().apply { update(Lifecycle.State.RESUMED, true) }
        var pickerCalls = 0
        var navigatedBack = false
        val platform = object : ComicExportPlatform {
            override suspend fun pickDestination(location: ComicExportLocation): Result<String?> {
                pickerCalls++
                return Result.success(null)
            }
            override fun formatFileSize(bytes: Long) = "$bytes B"
        }
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalDensity provides Density(1f),
                LocalComicExportPlatform provides platform) {
                MaterialTheme { ComicExportSelectionScreen(selection, export) { navigatedBack = true } }
            }
        }
        onNodeWithText("导出 3 章").assertIsEnabled()
        onNodeWithText("区间").performClick()
        onNodeWithText("起始序号").performTextReplacement("2")
        onNodeWithText("结束序号").performTextReplacement("3")
        onNodeWithText("选择", substring = false).performClick()
        onNodeWithText("导出 2 章").assertIsEnabled()
        onRoot().saveScreenshot("selection-phone")

        onNodeWithText("导出 2 章").performClick()
        onNodeWithText("1 部漫画 · 2 章 · 约 20 B").assertIsDisplayed()
        onNodeWithText("按漫画打包").assertIsSelected()
        onNodeWithText("每部一个 ZIP，章节分文件夹").assertIsDisplayed()
        onNodeWithTag("comic-export-options").saveScreenshot("options-comic-phone")
        onNodeWithText("按章节打包").performClick()
        onNodeWithText("ZIP", substring = false).assertIsSelected()
        runOnIdle { assertEquals(ComicExportFormat.CHAPTER_ZIP, export.uiState.value.format) }
        onNodeWithText("CBZ", substring = false).performClick()
        onNodeWithText("按章节打包").performClick()
        onNodeWithText("CBZ", substring = false).assertIsSelected()
        onNodeWithTag("comic-export-options").saveScreenshot("options-chapter-phone")
        onNodeWithText("按漫画打包").performClick()
        onNodeWithText("CBZ", substring = false).assertDoesNotExist()
        runOnIdle { assertEquals(ComicExportFormat.ZIP, export.uiState.value.format) }
        onNodeWithText("选择保存位置").performClick()
        runOnIdle {
            assertEquals(1, pickerCalls)
            assertNull(repository.startedPlan)
            assertFalse(navigatedBack)
            export.dismiss()
        }
        onNodeWithText("导出 2 章").assertIsEnabled()
        onNodeWithText("清空").performClick()
        onNodeWithText("导出 0 章").assertIsNotEnabled()
    }

    private fun SemanticsNodeInteraction.saveScreenshot(name: String) {
        val pixels = captureToImage().toPixelMap()
        val bitmap = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) bitmap.setRGB(x, y, pixels[x, y].toArgb())
        val screenshot = File("build/reports/comic-export/$name.png")
        screenshot.parentFile.mkdirs()
        ImageIO.write(bitmap, "png", screenshot)
    }
}
