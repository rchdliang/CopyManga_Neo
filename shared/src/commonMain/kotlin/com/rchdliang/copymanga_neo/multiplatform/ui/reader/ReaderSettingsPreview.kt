package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import com.rchdliang.copymanga_neo.domain.reading.*
import kotlinx.coroutines.flow.MutableStateFlow

/** 预览只调用纯 UI / 假服务；不读取漫画、账号、磁盘模型或启动网络与推理。 */
@Composable
private fun ReaderPreviewFrame(title: String, dark: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    CopyManga_NeoTheme(darkTheme = dark) {
        Surface {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp)); content()
            }
        }
    }
}

@Composable
private fun ReaderOverviewPreviewContent() {
    val tools=ReaderToolsSettings()
    ReaderSettingsOverview(true, {}, {}, {}, {},
        pairing = { ReaderPairingOption(tools, {}, "preview", 0, 20) }, help = { ReaderOperationHelp() })
}

@Preview(name = "阅读设置-首页-窄屏", showBackground = true, widthDp = 390, heightDp = 720)
@Composable private fun ReaderSettingsOverviewNarrowPreview() = ReaderPreviewFrame("阅读设置") { ReaderOverviewPreviewContent() }

@Preview(name = "阅读设置-首页-宽屏深色", showBackground = true, widthDp = 900, heightDp = 600)
@Composable private fun ReaderSettingsOverviewWideDarkPreview() = ReaderPreviewFrame("阅读设置", true) {
    Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) { ReaderOverviewPreviewContent() }
}

@Composable
private fun ReaderDisplayPreviewContent() {
    ReaderDisplaySettings(ReadingDirection.RIGHT_TO_LEFT, PageScale.FIT_SCREEN, ScreenOrientationSetting.SYSTEM_DEFAULT,
        {}, {}, {}, {}, { ReaderSpreadOptions(ReaderToolsSettings(), {}, "preview", 0, 20, true, false, "DOUBLE", {}) })
}
@Preview(name = "阅读设置-显示设置", showBackground = true, widthDp = 390, heightDp = 700)
@Composable private fun ReaderDisplaySettingsPreview() = ReaderPreviewFrame("显示设置") { ReaderDisplayPreviewContent() }

@Preview(name = "阅读设置-显示设置-深色", showBackground = true, widthDp = 600, heightDp = 700)
@Composable private fun ReaderDisplaySettingsDarkPreview() = ReaderPreviewFrame("显示设置", true) { ReaderDisplayPreviewContent() }

@Preview(name = "阅读设置-单选列表", showBackground = true, widthDp = 390, heightDp = 400)
@Composable private fun ReaderSingleChoicePreview() = ReaderPreviewFrame("页面布局") {
    ReaderChoiceOptions("DOUBLE", listOf("SINGLE", "DOUBLE", "AUTO"),
        { when(it) { "DOUBLE" -> "双页"; "AUTO" -> "自动"; else -> "单页" } }, {})
}

@Preview(name = "阅读设置-翻页设置", showBackground = true, widthDp = 390, heightDp = 700)
@Composable private fun ReaderTurningSettingsPreview() = ReaderPreviewFrame("翻页设置") { ReaderToolsOptions(ReaderToolsSettings(), {}) }

@Preview(name = "阅读设置-图片加载", showBackground = true, widthDp = 390, heightDp = 540)
@Composable private fun ReaderLoadingSettingsPreview() = ReaderPreviewFrame("图片加载") { ReaderLoadingOptions(ReaderToolsSettings(), {}) }

@Preview(name = "阅读设置-配页输入错误", showBackground = true, widthDp = 390, heightDp = 560)
@Composable private fun ReaderPairingErrorPreview() = ReaderPreviewFrame("本章配页") {
    ReaderPairingEditor(ReaderToolsSettings(), {}, "preview", -1, 20, {})
}

@Preview(name = "阅读器-描边文字-白底", showBackground = true, widthDp = 390, heightDp = 180)
@Composable private fun ReaderOverlayWhitePreview() {
    CopyManga_NeoTheme {
        Box(Modifier.fillMaxSize().background(Color.White)) { ReaderRestingOverlay("预览章节", "2–3 / 24", Modifier.align(Alignment.BottomCenter)) }
    }
}

@Preview(name = "阅读器-描边文字-黑底", showBackground = true, widthDp = 700, heightDp = 180)
@Composable private fun ReaderOverlayBlackPreview() {
    CopyManga_NeoTheme(darkTheme = true) {
        Box(Modifier.fillMaxSize().background(Color.Black)) { ReaderRestingOverlay("预览章节", "2–3 / 24", Modifier.align(Alignment.BottomCenter)) }
    }
}

private class PreviewReaderModels(initial: SrModelState) : ReaderSrModels {
    override val state=MutableStateFlow(initial)
    override val downloadBytes=2783649L
    override fun startDownload() {}
    override fun cancelDownload() {}
    override suspend fun refresh() {}
    override suspend fun download() {}
    override suspend fun delete() {}
}

@Composable
private fun previewSrSession(state: SrModelState): ReaderSrSession {
    val previewModels=remember(state) { PreviewReaderModels(state) }
    val scope=rememberCoroutineScope()
    val session=remember(previewModels,scope) { ReaderSrSession(scope, object : ReaderSrPlatform {
        override val models=previewModels
        override val unavailableReason: String?=null
        override val budgetBytes=192L*1024*1024
        override val workingBytes=96L*1024*1024
        override val detail=MutableStateFlow("CPU · 预览状态")
        override suspend fun source(page: SrPage): SrSource=error("Preview must never request source images")
        override suspend fun upscale(request: SrRequest): SrArtifact=error("Preview must never run inference")
    }) }
    DisposableEffect(session) { onDispose { session.close() } }
    return session
}

@Preview(name = "阅读设置-画质增强", showBackground = true, widthDp = 600, heightDp = 850)
@Composable private fun ReaderQualitySettingsPreview() = ReaderPreviewFrame("画质增强") {
    ReaderQualitySection { ReaderSharpnessControls(true, READER_SHARPNESS_RESTORE, READER_SHARPNESS_RESTORE, {}, {}) }
    ReaderQualitySection { ReaderSuperResolutionOptions(previewSrSession(SrModelState.Missing)) }
}

@Preview(name = "超分模型-首次确认", showBackground = true, widthDp = 390, heightDp = 320)
@Composable private fun ReaderModelConsentPreview() = ReaderPreviewFrame("下载超分模型？") {
    ReaderModelDownloadConfirmation(2783649L, {}, {})
}

@Preview(name = "超分模型-下载进度", showBackground = true, widthDp = 390, heightDp = 360)
@Composable private fun ReaderModelProgressPreview() = ReaderPreviewFrame("模型管理") {
    ReaderQualitySection { ReaderModelProgress(remember { PreviewReaderModels(SrModelState.Downloading(1200000L,2783649L)) }, {}) }
}

@Preview(name = "超分模型-下载失败", showBackground = true, widthDp = 390, heightDp = 360)
@Composable private fun ReaderModelDownloadFailurePreview() = ReaderPreviewFrame("模型管理", true) {
    ReaderQualitySection { ReaderModelProgress(remember { PreviewReaderModels(SrModelState.Failed("网络暂不可用，请重试")) }, {}) }
}

@Preview(name = "超分模型-已就绪", showBackground = true, widthDp = 390, heightDp = 420)
@Composable private fun ReaderModelReadyPreview() = ReaderPreviewFrame("模型管理") {
    val session=previewSrSession(SrModelState.Ready)
    ReaderModelManagement(session,session.models!!)
}

@Preview(name = "超分模型-删除中", showBackground = true, widthDp = 390, heightDp = 360)
@Composable private fun ReaderModelDeletingPreview() = ReaderPreviewFrame("删除模型？") {
    val session=previewSrSession(SrModelState.Deleting)
    ReaderModelDeleteConfirmation(session,session.models!!)
}

@Preview(name = "超分模型-删除失败", showBackground = true, widthDp = 390, heightDp = 380)
@Composable private fun ReaderModelDeleteFailurePreview() = ReaderPreviewFrame("删除模型？", true) {
    val session=previewSrSession(SrModelState.Failed("文件暂时被占用，请重试",SrModelOperation.DELETE))
    ReaderModelDeleteConfirmation(session,session.models!!)
}
