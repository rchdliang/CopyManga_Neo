package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import androidx.lifecycle.ViewModelStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import com.rchdliang.copymanga_neo.multiplatform.ui.components.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.rchdliang.copymanga_neo.multiplatform.ui.components.AppToastHost
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class NetworkScreenUiTest {
    private val store = ViewModelStore()
    private val platform = object : UiPlatform {
        override val backDispatcher = UiBackDispatcher()
        override suspend fun shareText(title: String, text: String) = error("No sharing in test")
        override fun formatDate(epochMilliseconds: Long) = ""
    }
    @BeforeTest fun before() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun after() { store.clear(); Dispatchers.resetMain() }

    @Test fun phoneEditContinueThenSaveAndReturn() = runSkikoComposeUiTest(size = Size(360f, 800f)) {
        val repo = FakeNetwork(); val vm = NetworkSettingsViewModel(repo); store.put("network", vm)
        var exits = 0
        setContent { CompositionLocalProvider(LocalUiPlatform provides platform) { CopyManga_NeoTheme { AppToastHost { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { NetworkSettingsScreen({ exits++ }, vm) } } } } }
        onNodeWithText("网络与连接").assertIsDisplayed()
        saveRender("network-phone-desktop")
        onNodeWithText("代理").performClick()
        onNodeWithText("直连").performClick()
        onNodeWithText("完成").performClick()
        onNodeWithText("有未保存的更改").assertIsDisplayed()
        onNodeWithText("保存").assertIsDisplayed()
        onNodeWithContentDescription("返回").performClick()
        onNodeWithText("继续编辑").performClick()
        assertEquals(0, exits); assertEquals(0, repo.saves)
        onNodeWithContentDescription("返回").performClick()
        onNodeWithText("保存并返回").performClick()
        waitForIdle()
        assertEquals(1, exits); assertEquals(1, repo.saves)
    }
    @Test fun tabletOverviewDoesNotStretchControlsAcrossWindow() = runSkikoComposeUiTest(size = Size(1080f, 720f)) {
        val vm = NetworkSettingsViewModel(FakeNetwork()); store.put("network", vm)
        setContent { CompositionLocalProvider(LocalUiPlatform provides platform) { CopyManga_NeoTheme(darkTheme = true) { AppToastHost { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { NetworkSettingsScreen({}, vm) } } } } }
        onNodeWithText("漫画详情预加载").assertIsDisplayed()
        assertTrue(onNodeWithText("漫画详情预加载").getUnclippedBoundsInRoot().left.value > 0f)
        saveRender("network-tablet-desktop")
    }
    private fun ComposeUiTest.saveRender(name: String) {
        val pixels = onRoot().captureToImage().toPixelMap()
        val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) image.setRGB(x, y, pixels[x, y].toArgb())
        val file = File("build/reports/ui-audit/$name.png"); file.parentFile.mkdirs(); ImageIO.write(image, "png", file)
    }
}
