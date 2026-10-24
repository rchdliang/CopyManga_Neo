package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import com.rchdliang.copymanga_neo.domain.reading.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

/** Actual desktop Compose production widgets; these are not Android device screenshots. */
@OptIn(ExperimentalTestApi::class)
class ReaderSettingsUiTest {
    private class Models : ReaderSrModels {
        override val state = MutableStateFlow<SrModelState>(SrModelState.Missing)
        override val downloadBytes = 2783649L
        var starts=0
        var deletes=0
        override fun startDownload() { starts++;state.value=SrModelState.Downloading(1200000,downloadBytes) }
        override fun cancelDownload() { state.value=SrModelState.Cancelled }
        override suspend fun refresh() {}
        override suspend fun download() { startDownload() }
        override suspend fun delete() { deletes++;state.value=SrModelState.Deleting }
    }
    private class Platform(override val models: ReaderSrModels): ReaderSrPlatform {
        override val budgetBytes=192L*1024*1024
        override val workingBytes=96L*1024*1024
        override val detail=MutableStateFlow("等待当前页加载")
        override val unavailableReason:String?=null
        override suspend fun source(page:SrPage):SrSource=error("No page selected")
        override suspend fun upscale(request:SrRequest):SrArtifact=error("No page selected")
    }
    @Composable private fun Settings(models:Models=remember { Models() },dark:Boolean=false,onDismiss:()->Unit={}) {
        var tools by remember { mutableStateOf(ReaderToolsSettings()) }
        var layout by remember { mutableStateOf("AUTO") }
        val scope=rememberCoroutineScope()
        val session=remember(scope,models) { ReaderSrSession(scope,Platform(models)) }
        DisposableEffect(session) { onDispose { session.close() } }
        MaterialTheme(colorScheme=if(dark) darkColorScheme() else lightColorScheme()) {
            ReaderSettingsDialog(ReadingDirection.RIGHT_TO_LEFT,PageScale.FIT_SCREEN,ScreenOrientationSetting.SYSTEM_DEFAULT,
                {},{},{},onDismiss,onResetPreferences={layout="SINGLE"},
                leadingSettings={ ReaderSpreadOptions(tools,{tools=it(tools)},"chapter",2,20,true,false,layout,{layout=it}) },
                qualitySettings={
                    ReaderSharpnessOptions(tools.sharpness,tools.sharpnessRestore,{tools=tools.copy(sharpness=0f)}) { tools=tools.copy(sharpness=it) }
                    ReaderSuperResolutionOptions(session)
                },
                extraSettings={ReaderToolsOptions(tools,{tools=it(tools)});ReaderOperationHelp()})
        }
    }
    @Test fun backReturnsFromChildAndCancelsUnconfirmedDownloadBeforeClosingHome() = runSkikoComposeUiTest(size=Size(520f,900f)) {
        val models=Models()
        var open by mutableStateOf(true)
        var dismisses=0
        setContent { if(open) Settings(models,onDismiss={dismisses++;open=false}) }
        onNodeWithText("本章配页 · 默认配页").performScrollTo().performClick()
        onNodeWithText("返回").performClick()
        onNodeWithText("阅读设置").assertExists();assertEquals(0,dismisses)
        onNodeWithTag("reader-setting-超分辨率").performScrollTo().performClick()
        onNodeWithText("下载超分模型？").assertExists()
        onNodeWithText("返回").performClick()
        onNodeWithText("阅读设置").assertExists()
        assertEquals(0,models.starts);assertEquals(0,dismisses)
        save("subpage-back-home-desktop")
        onNodeWithText("完成").performClick()
        onNodeWithText("阅读设置").assertDoesNotExist();assertEquals(1,dismisses)
    }

    @Test fun deleteBusyFailureRetryAndSuccessUseTypedDeleteAction() = runSkikoComposeUiTest(size=Size(520f,900f)) {
        val models=Models().apply { state.value=SrModelState.Ready }
        setContent { Settings(models) }
        onNodeWithText("模型管理").performScrollTo().performClick()
        onNodeWithText("删除模型").performClick()
        onNodeWithText("删除").performClick()
        onNodeWithText("正在删除…").assertExists()
        onNodeWithText("删除").assertDoesNotExist()
        assertEquals(1,models.deletes);assertEquals(0,models.starts)
        save("model-delete-busy-desktop")
        runOnIdle { models.state.value=SrModelState.Failed("文件暂时被占用，请重试",SrModelOperation.DELETE) }
        onNodeWithText("模型删除失败").assertExists()
        onNodeWithText("模型下载失败").assertDoesNotExist()
        onNodeWithText("删除模型？").assertExists()
        save("model-delete-failed-desktop")
        onNodeWithText("重试").performClick()
        onNodeWithText("正在删除…").assertExists()
        assertEquals(2,models.deletes);assertEquals(0,models.starts)
        // 返回只离开子页；后台结果在首页继续可见。
        onNodeWithText("返回").performClick()
        onNodeWithText("阅读设置").assertExists()
        onNodeWithText("正在删除…").performScrollTo().assertExists()
        runOnIdle { models.state.value=SrModelState.Missing }
        onNodeWithText("阅读设置").assertExists()
        onNodeWithText("模型未下载 · 2.65 MiB").performScrollTo().assertExists()
        save("model-delete-success-desktop")
    }

    @Test fun successfulDeleteReturnsOnlyAfterMissingState() = runSkikoComposeUiTest(size=Size(520f,900f)) {
        val models=Models().apply { state.value=SrModelState.Ready }
        setContent { Settings(models) }
        onNodeWithText("模型管理").performScrollTo().performClick()
        onNodeWithText("删除模型").performClick()
        onNodeWithText("删除").performClick()
        onNodeWithText("删除模型？").assertExists()
        runOnIdle { models.state.value=SrModelState.Missing }
        onNodeWithText("阅读设置").assertExists()
        assertEquals(1,models.deletes);assertEquals(0,models.starts)
    }
    @Test fun narrowSettingsAndPairingInvalidInputUseOneDialog() = runSkikoComposeUiTest(size=Size(390f,760f)) {
        setContent { Settings() }
        onNodeWithText("阅读设置").assertExists()
        onNodeWithText("自动").assertExists()
        onNodeWithText("仅修改本漫画。").assertDoesNotExist()
        save("narrow-settings-desktop")
        onNodeWithText("本章配页 · 默认配页").performScrollTo().performClick()
        onNodeWithText("起始页").performTextReplacement("0")
        onNodeWithText("请输入 1–19 的页码").assertExists()
        onNodeWithText("应用配页").assertIsNotEnabled()
        save("pairing-invalid-desktop")
    }
    @Test fun modelConsentProgressCancellationAndLateCompletionRespectDisabledChoice() = runSkikoComposeUiTest(size=Size(520f,900f)) {
        val models=Models()
        setContent { Settings(models) }
        val toggle=hasTestTag("reader-setting-超分辨率")
        onNode(toggle).performScrollTo().performClick()
        assertEquals(0,models.starts)
        save("model-download-confirm-desktop")
        onNodeWithText("暂不").performClick();assertEquals(0,models.starts)
        onNode(toggle).performScrollTo().performClick()
        onNodeWithText("下载并启用").performClick()
        assertEquals(1,models.starts)
        onNodeWithText("取消下载").performScrollTo()
        save("model-download-progress-desktop")
        onNodeWithText("取消下载").performClick()
        onNodeWithText("已取消下载").performScrollTo().assertExists()
        save("model-download-cancelled-desktop")
        onNodeWithText("重新下载").performClick()
        onNode(toggle).performScrollTo().performClick()
        runOnIdle { models.state.value=SrModelState.Ready }
        onNode(toggle).assertIsOff()
        onNodeWithText("模型管理").performScrollTo().performClick()
        save("model-management-desktop")
    }
    @Test fun darkSharpeningAndFailedModelAreRealTypedStates() = runSkikoComposeUiTest(size=Size(600f,900f)) {
        val models=Models()
        models.state.value=SrModelState.Failed("网络暂不可用，可重试")
        setContent { Settings(models,true) }
        onNodeWithTag("reader-sharpen-switch").performScrollTo().performClick()
        onNodeWithText("恢复默认").performScrollTo()
        save("sharpen-slider-dark-desktop")
        onNodeWithText("模型下载失败").performScrollTo()
        save("model-download-failed-desktop")
    }
    @Test fun rtlPageCounterUsesAscendingRawPageRange() = runSkikoComposeUiTest(size=Size(960f,600f)) {
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                MaterialTheme {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier) {
                        ReaderBottomBar(24,2f,{},{},{},{},{},{},{},true,null,false,{},visiblePageLabel=readerPageRangeLabel(listOf(3,2),24))
                    }
                }
            }
        }
        onNodeWithText("2–3 / 24").assertExists()
        val pixels=onAllNodes(isRoot())[0].captureToImage().toPixelMap()
        val out=BufferedImage(pixels.width,pixels.height,BufferedImage.TYPE_INT_ARGB)
        for(y in 0 until pixels.height) for(x in 0 until pixels.width) out.setRGB(x,y,pixels[x,y].toArgb())
        val target=File("build/reports/reader-settings-ui/rtl-counter-desktop.png");target.parentFile.mkdirs();ImageIO.write(out,"png",target)
    }
    private fun ComposeUiTest.save(name:String) {
        val pixels=onAllNodes(isRoot())[1].captureToImage().toPixelMap()
        val out=BufferedImage(pixels.width,pixels.height,BufferedImage.TYPE_INT_ARGB)
        for(y in 0 until pixels.height) for(x in 0 until pixels.width) out.setRGB(x,y,pixels[x,y].toArgb())
        val target=File("build/reports/reader-settings-ui/$name.png");target.parentFile.mkdirs();ImageIO.write(out,"png",target)
    }
}
