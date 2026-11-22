package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.ReaderInfoItem
import com.rchdliang.copymanga_neo.domain.model.ReaderInfoPosition
import com.rchdliang.copymanga_neo.domain.model.ReaderInfoSettings
import com.rchdliang.copymanga_neo.domain.model.ReaderInfoStyle
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings

@Composable
internal fun ReaderInfoOptions(
    settings: ReaderInfoSettings, onToolsChange: ((ReaderToolsSettings) -> ReaderToolsSettings) -> Unit
) {
    var selected by remember { mutableStateOf(ReaderInfoItem.PAGES) }
    val item = selected
    val style = settings[item]
    fun edit(change: (ReaderInfoStyle) -> ReaderInfoStyle) {
        onToolsChange { tools -> tools.copy(readingInfo = tools.readingInfo.update(item, change)) }
    }

    var freeArea by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Text("拖动预览中的信息可自由定位，点按可调整样式。所有漫画共用此设置。",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Box(Modifier.fillMaxWidth().height(184.dp).testTag("reader-info-preview").clip(MaterialTheme.shapes.large).background(
        Brush.linearGradient(listOf(Color(0xFF353535), Color(0xFFDADADA)))
    )) {
        ReaderInfoOverlay("示例章节", "7 / 24", "09:41", ReaderBatteryStatus(85), settings,
            freeAreaModifier = Modifier.onGloballyPositioned { freeArea = it }) { target ->
            readerInfoDragModifier(target, { freeArea }, onSelect = { selected = target }) { x, y ->
                onToolsChange { tools -> tools.copy(readingInfo = tools.readingInfo.update(target) {
                    it.copy(position = ReaderInfoPosition.CUSTOM, horizontalFraction = x, verticalFraction = y)
                }) }
            }
        }
    }
    ReaderInlineChoice(Icons.Outlined.Info, "信息项", item, ReaderInfoItem.entries, { it.label }) { selected = it }
    ReaderInlineChoice(Icons.Outlined.Place, "显示位置", style.position, ReaderInfoPosition.entries, { it.label }) { position ->
        edit { it.copy(position = position) }
    }
    if (style.position != ReaderInfoPosition.HIDDEN) {
        ReaderInlineChoice(Icons.Outlined.FormatSize, "字号", style.fontSizeSp, (10..28).toList(), { "$it sp" }) { size ->
            edit { it.copy(fontSizeSp = size) }
        }
        ReaderInlineChoice(Icons.Outlined.BorderColor, "描边宽度", style.outlineDp, (0..4).toList(),
            { if (it == 0) "无描边" else "$it dp" }) { width -> edit { it.copy(outlineDp = width) } }
        ReaderInlineChoice(Icons.Outlined.Palette, "文字颜色", style.darkText, listOf(false, true),
            { if (it) "黑色" else "白色" }) { dark -> edit { it.copy(darkText = dark) } }
    }
    TextButton(onClick = { onToolsChange { it.copy(readingInfo = ReaderInfoSettings()) } }) {
        Text("重置全部阅读信息")
    }
}

private val ReaderInfoItem.label get() = when (this) {
    ReaderInfoItem.CLOCK -> "时间"
    ReaderInfoItem.BATTERY -> "电量"
    ReaderInfoItem.CHAPTER -> "章节名"
    ReaderInfoItem.PAGES -> "页码"
}

private val ReaderInfoPosition.label get() = when (this) {
    ReaderInfoPosition.TOP_LEFT -> "左上"
    ReaderInfoPosition.TOP_CENTER -> "顶部居中"
    ReaderInfoPosition.TOP_RIGHT -> "右上"
    ReaderInfoPosition.BOTTOM_LEFT -> "左下"
    ReaderInfoPosition.BOTTOM_CENTER -> "底部居中"
    ReaderInfoPosition.BOTTOM_RIGHT -> "右下"
    ReaderInfoPosition.CUSTOM -> "自由位置"
    ReaderInfoPosition.HIDDEN -> "隐藏"
}
