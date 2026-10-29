package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import com.rchdliang.copymanga_neo.domain.reading.*

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

@Preview(name = "阅读设置-内嵌分段", showBackground = true, widthDp = 390, heightDp = 400)
@Composable private fun ReaderSegmentedChoicePreview() = ReaderPreviewFrame("页面布局") {
    ReaderInlineChoice(Icons.Outlined.ViewWeek, "页面布局", "DOUBLE", listOf("SINGLE", "DOUBLE", "AUTO"),
        { when(it) { "DOUBLE" -> "双页"; "AUTO" -> "自动"; else -> "单页" } }, onSelect = {})
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
        Box(Modifier.fillMaxSize().background(Color.White)) {
            ReaderStatusOverlay("09:41", ReaderBatteryStatus(85), Modifier.align(Alignment.TopCenter), pages = "2–3 / 24")
            ReaderRestingOverlay("预览章节", Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Preview(name = "阅读器-描边文字-黑底", showBackground = true, widthDp = 700, heightDp = 180)
@Composable private fun ReaderOverlayBlackPreview() {
    CopyManga_NeoTheme(darkTheme = true) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            ReaderStatusOverlay("09:41", ReaderBatteryStatus(85), Modifier.align(Alignment.TopCenter), pages = "2–3 / 24")
            ReaderRestingOverlay("预览章节", Modifier.align(Alignment.BottomCenter))
        }
    }
}


@Preview(name = "阅读设置-画质增强", showBackground = true, widthDp = 600, heightDp = 850)
@Composable private fun ReaderQualitySettingsPreview() = ReaderPreviewFrame("画质增强") {
    ReaderQualitySection { ReaderSharpnessControls(true, READER_SHARPNESS_RESTORE, READER_SHARPNESS_RESTORE, {}, {}) }
    ReaderQualitySection { ReaderRestoreControls(ReaderRestoreSelection(ReaderRestoreMode.OFF), null, null, {}, {}) }
}
