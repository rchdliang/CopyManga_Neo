package com.rchdliang.copymanga_neo.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.ui.profile.ProfileListItem

@Composable
internal fun ReaderSettingsLink(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String? = null, index: Int = 0, count: Int = 1,
    enabled: Boolean = true, onClick: () -> Unit) {
    ProfileListItem(icon, label, null, index, count, onClick, enabled = enabled,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (value != null) Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp))
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        })
}

@Composable
internal fun ReaderSettingsOverview(qualityAvailable: Boolean, display: () -> Unit, turns: () -> Unit,
    quality: () -> Unit, loading: () -> Unit, pairing: @Composable () -> Unit, help: @Composable () -> Unit) {
    val count = if (qualityAvailable) 4 else 3
    ReaderSettingsLink(Icons.Outlined.DisplaySettings, "显示设置", index = 0, count = count, onClick = display)
    ReaderSettingsLink(Icons.Outlined.Swipe, "翻页设置", index = 1, count = count, onClick = turns)
    if (qualityAvailable) ReaderSettingsLink(Icons.Outlined.Tune, "画质增强", index = 2, count = count, onClick = quality)
    ReaderSettingsLink(Icons.Outlined.CloudDownload, "图片加载", index = count - 1, count = count, onClick = loading)
    Spacer(Modifier.height(17.dp))
    pairing(); help()
}

@Composable
internal fun ReaderDisplaySettings(direction: ReadingDirection, scale: PageScale, orientation: ScreenOrientationSetting,
    directionChange: (ReadingDirection) -> Unit, scaleChange: (PageScale) -> Unit,
    orientationChange: (ScreenOrientationSetting) -> Unit, reset: () -> Unit, layout: @Composable () -> Unit) {
    layout()
    ReaderInlineChoice(Icons.Outlined.SwapHoriz, "阅读方向", direction, ReadingDirection.entries, { it.choiceLabel }, onSelect = directionChange)
    ReaderInlineChoice(Icons.Outlined.ScreenRotation, "屏幕方向", orientation, ScreenOrientationSetting.entries, { it.label }, presentation = ReaderChoicePresentation.MENU, onSelect = orientationChange)
    ReaderInlineChoice(Icons.Outlined.AspectRatio, "页面缩放", scale, PageScale.entries, { it.choiceLabel }, onSelect = scaleChange)
    TextButton(onClick = reset) { Text("重置显示设置", color = MaterialTheme.colorScheme.primary) }
}

@Composable
internal fun ReaderQualitySection(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

internal val ReadingDirection.choiceLabel: String get() = when(this) {
    ReadingDirection.LEFT_TO_RIGHT -> "左→右"; ReadingDirection.RIGHT_TO_LEFT -> "右→左"; ReadingDirection.TOP_TO_BOTTOM -> "向下"
}
internal val PageScale.choiceLabel: String get() = when(this) {
    PageScale.FIT_SCREEN -> "整页"; PageScale.FIT_HEIGHT -> "等高"; PageScale.FIT_WIDTH -> "等宽"
}
