package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import androidx.lifecycle.ViewModelStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import com.rchdliang.copymanga_neo.domain.model.ProxyMode
import com.rchdliang.copymanga_neo.domain.model.NetworkProbe
import com.rchdliang.copymanga_neo.domain.model.NetworkSelection
import com.rchdliang.copymanga_neo.domain.model.RequestMode
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

    @Test fun phoneTestsTheUnsavedDraftAndSavesOnlyAfterConfirmation() = runSkikoComposeUiTest(size = Size(360f, 800f)) {
        val repo = FakeNetwork(listOf(NetworkProbe("probe.example.test", 42), NetworkProbe("fast.example.test", 12)))
        repo.settings.value = repo.settings.value.copy(routes = listOf(listOf("probe.example.test", "fast.example.test")))
        val vm = NetworkSettingsViewModel(repo); store.put("network", vm)
        val saved = repo.settings.value
        var exits = 0
        var advanced by mutableStateOf(false)
        val goBack = { if (advanced) advanced = false else exits += 1 }
        platform.backDispatcher.register { goBack(); true }
        setContent { CompositionLocalProvider(LocalUiPlatform provides platform) { CopyManga_NeoTheme { AppToastHost { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            NetworkSettingsScreen(goBack, vm, advanced, onOpenAdvanced = { advanced = true })
        } } } } }
        onNodeWithText("网络与连接").assertIsDisplayed()
        onAllNodesWithText("连接测试").assertCountEquals(1)
        onNodeWithText("测试").assertIsDisplayed()
        val connection = onNodeWithText("连接方式").getUnclippedBoundsInRoot()
        val diagnostics = onNodeWithText("连接测试").getUnclippedBoundsInRoot()
        assertTrue(connection.bottom <= diagnostics.top, "手机的连接测试应排在连接方式下方")
        saveRender("network-phone-desktop")
        onNodeWithText("网页模式").performClick()
        onNodeWithText("手动").performClick()
        onNode(hasSetTextAction()).performScrollTo().performTextReplacement("127.0.0.1:7890")
        onNodeWithText("测试草稿").performScrollTo().performClick()
        onNodeWithText("probe.example.test", substring = true).performScrollTo().assertIsDisplayed()
        runOnIdle {
            val tested = repo.testedDrafts.single()
            assertEquals(RequestMode.WEB, tested.requestMode)
            assertEquals(ProxyMode.MANUAL, tested.proxyMode)
            assertEquals("127.0.0.1:7890", tested.proxyAddress)
            assertEquals(saved, repo.settings.value)
            assertEquals(0, repo.saves)
        }
        onNodeWithText("选择最快项").performScrollTo().performClick()
        runOnIdle {
            assertEquals(NetworkSelection.FIXED, vm.uiState.value.draft.selection)
            assertEquals("fast.example.test", vm.uiState.value.draft.fixedHost)
            assertEquals(saved, repo.settings.value)
            assertEquals(0, repo.saves)
        }
        onNodeWithText("直连").performScrollTo().performClick()
        onNodeWithText("probe.example.test", substring = true).assertDoesNotExist()
        onNode(hasSetTextAction()).assertDoesNotExist()
        onNodeWithText("有未保存的更改").assertIsDisplayed()
        onNodeWithText("保存").assertIsDisplayed()
        val saveBounds = onNodeWithText("保存").getUnclippedBoundsInRoot()
        onNodeWithText("高级连接配置").performScrollTo()
        assertEquals(saveBounds, onNodeWithText("保存").getUnclippedBoundsInRoot(), "滚动概览不能移走保存栏")
        onNodeWithText("高级连接配置").performClick()
        onNodeWithText("服务地址与版本").assertExists()
        onNodeWithText("配置管理").assertExists()
        for (label in listOf("连接测试", "测试", "测试草稿", "重测", "选择最快项")) {
            onNodeWithText(label).assertDoesNotExist()
        }
        runOnIdle {
            assertTrue(advanced)
            assertTrue(platform.backDispatcher.dispatchBack())
        }
        onNodeWithText("网络与连接").assertIsDisplayed()
        onAllNodesWithText("连接测试").assertCountEquals(1)
        onNodeWithText("测试草稿").assertExists()
        runOnIdle {
            assertFalse(advanced)
            assertTrue(vm.uiState.value.dirty)
            platform.backDispatcher.startGesture(UiBackEvent(0f, true))
            platform.backDispatcher.progressGesture(UiBackEvent(.7f, true))
        }
        onNodeWithText("继续编辑").assertDoesNotExist()
        runOnIdle {
            platform.backDispatcher.cancelGesture()
            assertEquals(0, exits); assertEquals(0, repo.saves)
            assertTrue(platform.backDispatcher.dispatchBack())
        }
        onNodeWithText("继续编辑").performClick()
        assertEquals(0, exits); assertEquals(0, repo.saves)
        onNodeWithContentDescription("返回").performClick()
        onNodeWithText("保存并返回").performClick()
        waitForIdle()
        assertEquals(1, exits); assertEquals(1, repo.saves)
        assertEquals(RequestMode.WEB, repo.settings.value.requestMode)
        assertEquals(ProxyMode.DIRECT, repo.settings.value.proxyMode)
    }
    @Test fun tabletUsesTwoColumnsAndKeepsSavedSettingsWhenLaterEditsAreDiscarded() = runSkikoComposeUiTest(size = Size(1080f, 720f)) {
        val repo = FakeNetwork(); val vm = NetworkSettingsViewModel(repo); store.put("network", vm)
        var width by mutableStateOf(839.dp)
        var exits = 0
        setContent {
            CompositionLocalProvider(LocalUiPlatform provides platform, LocalDensity provides Density(1f)) {
                CopyManga_NeoTheme(darkTheme = true) {
                    AppToastHost {
                        Box(Modifier.width(width).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                            NetworkSettingsScreen({ exits++ }, vm)
                        }
                    }
                }
            }
        }
        val left = onNodeWithText("连接方式")
        val right = onNodeWithText("连接测试")
        assertTrue(left.getUnclippedBoundsInRoot().bottom <= right.getUnclippedBoundsInRoot().top)
        runOnIdle { width = 840.dp }
        val leftBounds = left.getUnclippedBoundsInRoot()
        val rightBounds = right.getUnclippedBoundsInRoot()
        assertTrue(leftBounds.right <= rightBounds.left, "平板的连接方式和连接测试不能重叠")
        assertTrue(kotlin.math.abs(leftBounds.top.value - rightBounds.top.value) <= 2f, "两栏标题应顶部对齐")
        assertTrue(onNodeWithText("App 模式").getUnclippedBoundsInRoot().right <=
            onNodeWithText("测试").getUnclippedBoundsInRoot().left)
        runOnIdle { width = 1080.dp }
        saveRender("network-tablet-desktop")
        onNodeWithText("测试").performClick()
        onNodeWithText("重测").performClick()
        runOnIdle {
            assertEquals(listOf(repo.settings.value, repo.settings.value), repo.testedDrafts)
            assertEquals(0, repo.saves)
        }
        onNodeWithText("漫画详情预加载").performScrollTo().performClick()
        onNodeWithText("完成").performClick()
        onNodeWithText("直连").performScrollTo().performClick()
        onNodeWithText("保存").performClick()
        runOnIdle {
            assertEquals(ProxyMode.DIRECT, repo.settings.value.proxyMode)
            assertEquals(1, repo.saves)
            assertEquals(0, exits)
        }
        onNodeWithText("跟随系统").performScrollTo().performClick()
        onNodeWithContentDescription("返回").performClick()
        onNodeWithText("不保存").performClick()
        runOnIdle {
            assertEquals(1, exits)
            assertEquals(1, repo.saves)
            assertEquals(repo.settings.value, vm.uiState.value.draft)
            assertFalse(vm.uiState.value.dirty)
        }
    }
    @Test fun systemOnlyPlatformCorrectsLegacyProxyAndCanExpandCollapsedProbeResults() =
        runSkikoComposeUiTest(size = Size(1080f, 800f)) {
            val results = (1..6).map { NetworkProbe("host-$it.example.test", it.toLong()) }
            val repo = FakeNetwork(results)
            repo.settings.value = repo.settings.value.copy(proxyMode = ProxyMode.MANUAL, proxyAddress = "127.0.0.1:7890")
            val vm = NetworkSettingsViewModel(repo)
            store.put("network", vm)
            val systemOnly = object : UiPlatform by platform {
                override val supportedProxyModes = listOf(ProxyMode.SYSTEM)
            }
            setContent {
                CompositionLocalProvider(LocalUiPlatform provides systemOnly) {
                    CopyManga_NeoTheme { AppToastHost { NetworkSettingsScreen({}, vm) } }
                }
            }
            onNodeWithText("直连").assertDoesNotExist()
            onNodeWithText("手动").assertDoesNotExist()
            onNode(hasSetTextAction()).assertDoesNotExist()
            onNodeWithText("跟随系统").performClick()
            runOnIdle {
                assertEquals(ProxyMode.SYSTEM, vm.uiState.value.draft.proxyMode)
                assertEquals(ProxyMode.MANUAL, repo.settings.value.proxyMode)
            }
            onNodeWithText("测试草稿").performClick()
            runOnIdle { assertEquals(ProxyMode.SYSTEM, repo.testedDrafts.single().proxyMode) }
            results.take(4).forEach { onNodeWithText(it.host).assertExists() }
            results.drop(4).forEach { onNodeWithText(it.host).assertDoesNotExist() }

            onNodeWithText("查看全部 6 个地址").performScrollTo().performClick()
            results.forEach { onNodeWithText(it.host).assertExists() }
            onNodeWithText("收起结果").performScrollTo().performClick()
            results.take(4).forEach { onNodeWithText(it.host).assertExists() }
            results.drop(4).forEach { onNodeWithText(it.host).assertDoesNotExist() }
            onNodeWithText("查看全部 6 个地址").assertExists()
            onNodeWithText("保存").performClick()
            runOnIdle {
                assertEquals(ProxyMode.SYSTEM, repo.settings.value.proxyMode)
                assertEquals(1, repo.saves)
            }
        }

    private fun ComposeUiTest.saveRender(name: String) {
        val pixels = onRoot().captureToImage().toPixelMap()
        val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) image.setRGB(x, y, pixels[x, y].toArgb())
        val file = File("build/reports/ui-audit/$name.png"); file.parentFile.mkdirs(); ImageIO.write(image, "png", file)
    }
}
