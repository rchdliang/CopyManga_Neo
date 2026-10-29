package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.reading.*
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme

@Composable
internal fun ReaderInlineDesignContent(quality: Boolean, dark: Boolean = false) {
    CopyManga_NeoTheme(darkTheme = dark) {
        Surface {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (quality) "画质增强" else "显示设置", style = MaterialTheme.typography.titleLarge)
                if (quality) {
                    var strength by remember { mutableStateOf(READER_SHARPNESS_RESTORE) }
                    ReaderQualitySection { ReaderSharpnessControls(true, strength, READER_SHARPNESS_RESTORE, { strength = 0f }, { strength = it }) }
                    var restoreTools by remember { mutableStateOf(ReaderToolsSettings()) }
                    ReaderQualitySection {
                        ReaderRestoreControls(restoreTools.restoreSelection(), null, null,
                            { restoreTools = restoreTools.withRestoreMode(it) }, { restoreTools = restoreTools.withRestoreLevel(it) })
                    }
                } else {
                    var layout by remember { mutableStateOf("双页") }
                    var direction by remember { mutableStateOf(ReadingDirection.RIGHT_TO_LEFT) }
                    var orientation by remember { mutableStateOf(ScreenOrientationSetting.SYSTEM_DEFAULT) }
                    var scale by remember { mutableStateOf(PageScale.FIT_SCREEN) }
                    ReaderInlineChoice(Icons.Outlined.ViewWeek, "页面布局", layout, listOf("单页", "双页", "自动"), { it }) { layout = it }
                    ReaderInlineChoice(Icons.Outlined.SwapHoriz, "阅读方向", direction, ReadingDirection.entries, { it.choiceLabel }) { direction = it }
                    ReaderInlineChoice(Icons.Outlined.ScreenRotation, "屏幕方向", orientation, ScreenOrientationSetting.entries, { it.label }) { orientation = it }
                    ReaderInlineChoice(Icons.Outlined.AspectRatio, "页面缩放", scale, PageScale.entries, { it.choiceLabel }) { scale = it }
                    TextButton(onClick = {}) { Text("重置显示设置") }
                }
            }
        }
    }
}

@Preview(name = "内嵌设置-窄屏", widthDp = 390, heightDp = 800, showBackground = true)
@Composable private fun ReaderInlineDisplayPreview() = ReaderInlineDesignContent(false)
@Preview(name = "内嵌设置-宽屏深色", widthDp = 560, heightDp = 800, showBackground = true)
@Composable private fun ReaderInlineDisplayDarkPreview() = ReaderInlineDesignContent(false, true)
@Preview(name = "画质增强-内嵌模式", widthDp = 390, heightDp = 800, showBackground = true)
@Composable private fun ReaderInlineQualityPreview() = ReaderInlineDesignContent(true)
@Preview(name = "画质增强-宽屏深色", widthDp = 560, heightDp = 800, showBackground = true)
@Composable private fun ReaderInlineQualityDarkPreview() = ReaderInlineDesignContent(true, true)

@Preview(name = "阅读器-悬浮页码", widthDp = 390, heightDp = 420, showBackground = true)
@Composable private fun ReaderRestingOverlayPreview() = ReaderRestingOverlayContent()
@Composable internal fun ReaderRestingOverlayContent() {
    CopyManga_NeoTheme(darkTheme = true) {
        Surface(color = androidx.compose.ui.graphics.Color(0xFF575454)) {
            Box(Modifier.fillMaxSize()) {
                ReaderStatusOverlay("09:41", ReaderBatteryStatus(85), Modifier.align(androidx.compose.ui.Alignment.TopCenter),
                    pages = readerRestingPageLabel("7", 24))
                ReaderRestingOverlay("示例章节",
                    Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
            }
        }
    }
}
